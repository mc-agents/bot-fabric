#!/usr/bin/env python3
"""Sign a Microsoft account in to Minecraft, for a bot that joins online-mode servers.

    microsoft-auth.py login      prints a link to open, then the refresh token on stdout
    microsoft-auth.py session    BOT_AUTH_REFRESH_TOKEN in, the client's identity out

`login` is run once, by somebody with a browser. Only the token goes to stdout, so it can be
redirected straight into a file or a Secret while the link and the account's name go to the
terminal. `session` is the entrypoint's, at every start: name, UUID, access token and the epoch
second it expires at, one per line.

The client ID is the one azalea uses, Minecraft for Nintendo Switch's. An application registered
today is refused by api.minecraftservices.com until Mojang approves it, and this one also signs in
accounts under eighteen.
"""

import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

CLIENT_ID = "00000000441cc96b"
SCOPE = "service::user.auth.xboxlive.com::MBI_SSL"

DEVICE_CODE = "https://login.live.com/oauth20_connect.srf"
TOKEN = "https://login.live.com/oauth20_token.srf"
XBOX_LIVE = "https://user.auth.xboxlive.com/user/authenticate"
XSTS = "https://xsts.auth.xboxlive.com/xsts/authorize"
LOGIN_WITH_XBOX = "https://api.minecraftservices.com/authentication/login_with_xbox"
PROFILE = "https://api.minecraftservices.com/minecraft/profile"

# What XSTS answers for an account that cannot have a Minecraft token, by its XErr.
XSTS_REFUSALS = {
    2148916233: "the account has no Xbox profile; sign in once at https://www.xbox.com to make one",
    2148916235: "Xbox Live is not available in the account's country",
    2148916236: "the account needs adult verification (South Korea); sign in at https://www.xbox.com to complete it",
    2148916237: "the account needs adult verification (South Korea); sign in at https://www.xbox.com to complete it",
    2148916238: "the account is a child's and has to be added to a Microsoft family first",
}


class Refused(Exception):
    pass


def request(url, *, form=None, body=None, token=None):
    headers = {"Accept": "application/json"}
    data = None
    if form is not None:
        data = urllib.parse.urlencode(form).encode()
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    elif body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    if token is not None:
        headers["Authorization"] = f"Bearer {token}"
    try:
        with urllib.request.urlopen(urllib.request.Request(url, data, headers), timeout=30) as response:
            return response.status, json.loads(response.read() or b"{}")
    except urllib.error.HTTPError as error:
        raw = error.read()
        try:
            return error.code, json.loads(raw)
        except ValueError:
            return error.code, {"error": raw.decode(errors="replace").strip() or error.reason}


def device_login():
    """The Microsoft token of whoever opens the link, or Refused if nobody does in time."""
    _, code = request(DEVICE_CODE, form={"client_id": CLIENT_ID, "scope": SCOPE, "response_type": "device_code"})
    if "device_code" not in code:
        raise Refused(f"Microsoft gave no device code: {code}")
    print(f"open {code['verification_uri']}?otc={code['user_code']} and sign in", file=sys.stderr, flush=True)

    interval = code.get("interval", 5)
    deadline = time.monotonic() + code["expires_in"]
    while time.monotonic() < deadline:
        time.sleep(interval)
        status, answer = request(TOKEN, form={
            "client_id": CLIENT_ID,
            "device_code": code["device_code"],
            "grant_type": "urn:ietf:params:oauth:grant-type:device_code",
        })
        if status == 200:
            return answer
        error = answer.get("error")
        if error == "slow_down":
            interval += 5
        elif error != "authorization_pending":
            raise Refused(f"Microsoft refused the sign-in: {answer.get('error_description') or error}")
    raise Refused("nobody signed in before the code expired")


def refresh(refresh_token):
    status, answer = request(TOKEN, form={
        "client_id": CLIENT_ID,
        "scope": SCOPE,
        "grant_type": "refresh_token",
        "refresh_token": refresh_token,
    })
    if status != 200:
        raise Refused("Microsoft would not refresh BOT_AUTH_REFRESH_TOKEN"
                      f" ({answer.get('error_description') or answer.get('error')}); run `login` again")
    return answer


def minecraft(microsoft_token):
    """Microsoft token to Minecraft identity: Xbox Live, then XSTS for Minecraft, then Minecraft."""
    status, xbl = request(XBOX_LIVE, body={
        "Properties": {"AuthMethod": "RPS", "SiteName": "user.auth.xboxlive.com", "RpsTicket": microsoft_token},
        "RelyingParty": "http://auth.xboxlive.com",
        "TokenType": "JWT",
    })
    if status != 200:
        raise Refused(f"Xbox Live refused the account ({status})")

    status, xsts = request(XSTS, body={
        "Properties": {"SandboxId": "RETAIL", "UserTokens": [xbl["Token"]]},
        "RelyingParty": "rp://api.minecraftservices.com/",
        "TokenType": "JWT",
    })
    if status != 200:
        raise Refused(XSTS_REFUSALS.get(xsts.get("XErr"), f"XSTS refused the account ({status}, {xsts})"))

    user_hash = xsts["DisplayClaims"]["xui"][0]["uhs"]
    status, game = request(LOGIN_WITH_XBOX, body={"identityToken": f"XBL3.0 x={user_hash};{xsts['Token']}"})
    if status != 200:
        raise Refused(f"Minecraft refused the Xbox sign-in ({status}, {game})")

    status, profile = request(PROFILE, token=game["access_token"])
    if status == 404:
        raise Refused("the account has no Minecraft profile: it does not own the game, or has never picked a name")
    if status != 200:
        raise Refused(f"Minecraft would not give the profile ({status}, {profile})")

    return profile, game["access_token"], int(time.time()) + game["expires_in"]


def login():
    signed_in = device_login()
    # Gone all the way through, so a token that cannot reach a profile is refused now and not at
    # the bot's first start.
    profile, _, _ = minecraft(signed_in["access_token"])
    print(f"signed in as {profile['name']}; set BOT_NAME to it, since the account decides the name",
          file=sys.stderr)
    print(signed_in["refresh_token"])


def session():
    refresh_token = os.environ.get("BOT_AUTH_REFRESH_TOKEN", "").strip()
    if not refresh_token:
        raise Refused("BOT_AUTH is microsoft and BOT_AUTH_REFRESH_TOKEN is empty; run `login` to get one")
    profile, access_token, expires_at = minecraft(refresh(refresh_token)["access_token"])
    raw = profile["id"]
    uuid = f"{raw[:8]}-{raw[8:12]}-{raw[12:16]}-{raw[16:20]}-{raw[20:]}"
    print(profile["name"], uuid, access_token, expires_at, sep="\n")


def main():
    modes = {"login": login, "session": session}
    if len(sys.argv) != 2 or sys.argv[1] not in modes:
        raise SystemExit(f"usage: {sys.argv[0]} login|session")
    try:
        modes[sys.argv[1]]()
    except Refused as refused:
        raise SystemExit(str(refused))


if __name__ == "__main__":
    main()
