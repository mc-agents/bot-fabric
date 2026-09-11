#!/usr/bin/env python3
"""Fill a cache directory with everything a Fabric client needs, except the mod.

The client jar is not in the image on purpose: it is Mojang's to distribute, not ours. This
runs as an init container against a shared volume, or from the entrypoint when the cache it
finds is empty.

    fetch-minecraft.py --minecraft 26.1.2 --loader 0.19.5 --cache /mc

Leaves <cache>/versions/<minecraft>/launch.json naming the classpath, the main class and the
asset index, which is all the entrypoint needs to build a command line.
"""

import argparse
import hashlib
import json
import os
import pathlib
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor

VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
FABRIC_META = "https://meta.fabricmc.net/v2"
DEFAULT_MAVEN = "https://maven.fabricmc.net/"

OS_NAME = "linux"


def fetch(url):
    with urllib.request.urlopen(url, timeout=60) as response:
        return response.read()


def fetch_json(url):
    return json.loads(fetch(url))


def download(url, target, sha1=None):
    if target.exists() and (sha1 is None or sha1_of(target) == sha1):
        return target
    target.parent.mkdir(parents=True, exist_ok=True)
    body = fetch(url)
    if sha1 is not None:
        got = hashlib.sha1(body).hexdigest()
        if got != sha1:
            raise RuntimeError(f"{url} hashed {got}, expected {sha1}")
    tmp = target.with_suffix(target.suffix + ".part")
    tmp.write_bytes(body)
    tmp.replace(target)
    return target


def sha1_of(path):
    digest = hashlib.sha1()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def arch_names():
    machine = os.uname().machine
    if machine in ("x86_64", "amd64"):
        return {"x86_64", "amd64"}
    if machine in ("aarch64", "arm64"):
        return {"aarch64", "arm64"}
    return {machine}


def allowed(rules):
    """Mojang's rule list: last matching rule wins, default deny once rules exist."""
    if not rules:
        return True
    verdict = False
    for rule in rules:
        target = rule.get("os", {})
        if "name" in target and target["name"] != OS_NAME:
            continue
        if "arch" in target and target["arch"] not in arch_names():
            continue
        if "features" in target:
            continue
        verdict = rule.get("action") == "allow"
    return verdict


def maven_path(coordinate):
    parts = coordinate.split(":")
    group, artifact, version = parts[0], parts[1], parts[2]
    classifier = parts[3] if len(parts) > 3 else None
    name = f"{artifact}-{version}" + (f"-{classifier}" if classifier else "") + ".jar"
    return "/".join(group.split(".") + [artifact, version, name])


def resolve_version(minecraft):
    manifest = fetch_json(VERSION_MANIFEST)
    for entry in manifest["versions"]:
        if entry["id"] == minecraft:
            return fetch_json(entry["url"])
    raise SystemExit(f"minecraft {minecraft} is not in the version manifest")


def collect_mojang_libraries(meta, cache):
    jars = []
    missing = []
    for library in meta["libraries"]:
        if not allowed(library.get("rules")):
            continue
        artifact = library.get("downloads", {}).get("artifact")
        if artifact is None:
            missing.append(library["name"])
            continue
        target = cache / "libraries" / artifact["path"]
        download(artifact["url"], target, artifact.get("sha1"))
        jars.append(target)
    if missing:
        print(f"no artifact for this platform: {', '.join(missing)}", file=sys.stderr)
    return jars


def collect_fabric_libraries(minecraft, loader, cache):
    profile = fetch_json(f"{FABRIC_META}/versions/loader/{minecraft}/{loader}/profile/json")
    jars = []
    for library in profile["libraries"]:
        repository = library.get("url") or DEFAULT_MAVEN
        path = maven_path(library["name"])
        target = cache / "libraries" / path
        download(repository.rstrip("/") + "/" + path, target, library.get("sha1"))
        jars.append(target)
    return profile["mainClass"], jars


def download_assets(meta, cache, workers):
    index = meta["assetIndex"]
    index_file = cache / "assets" / "indexes" / f"{index['id']}.json"
    download(index["url"], index_file, index.get("sha1"))
    objects = json.loads(index_file.read_bytes())["objects"]

    def one(entry):
        digest = entry["hash"]
        target = cache / "assets" / "objects" / digest[:2] / digest
        if target.exists():
            return
        download(f"https://resources.download.minecraft.net/{digest[:2]}/{digest}", target, digest)

    with ThreadPoolExecutor(max_workers=workers) as pool:
        for _ in pool.map(one, objects.values()):
            pass
    return index["id"]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--minecraft", required=True)
    parser.add_argument("--loader", required=True)
    parser.add_argument("--cache", required=True, type=pathlib.Path)
    parser.add_argument("--workers", type=int, default=16)
    parser.add_argument("--skip-assets", action="store_true")
    args = parser.parse_args()

    cache = args.cache
    launch_file = cache / "versions" / args.minecraft / "launch.json"

    meta = resolve_version(args.minecraft)

    client = cache / "versions" / args.minecraft / "client.jar"
    download(meta["downloads"]["client"]["url"], client, meta["downloads"]["client"]["sha1"])

    jars = collect_mojang_libraries(meta, cache)
    main_class, fabric_jars = collect_fabric_libraries(args.minecraft, args.loader, cache)

    asset_index = meta["assetIndex"]["id"]
    if not args.skip_assets:
        asset_index = download_assets(meta, cache, args.workers)

    launch_file.parent.mkdir(parents=True, exist_ok=True)
    launch_file.write_text(json.dumps({
        "minecraft": args.minecraft,
        "loader": args.loader,
        "mainClass": main_class,
        "assetIndex": asset_index,
        "classpath": [str(jar.relative_to(cache)) for jar in fabric_jars + jars + [client]],
    }, indent=2) + "\n")
    print(f"cached minecraft {args.minecraft} with fabric loader {args.loader} in {cache}")


if __name__ == "__main__":
    main()
