#!/usr/bin/env bash
#
# `docker buildx build` with the push retried, and the pushed image's digest on stdout.
#
#     ./hack/buildx-push.sh . --push --platform linux/amd64,linux/arm64 --tag "$IMAGE:$TAG" ...
#
# Everything after the first argument is buildx's own; this adds `--metadata-file`, reads the digest
# out of it, and runs the whole build again when it fails.
#
# What it is for: the registry is reached over one home uplink, and a push of this image sometimes
# fails there with nothing wrong with the build.
#
# It is not what fixed 0.77.0 and 0.78.0, and that is worth saying because this script was written
# believing it would. Those failed every attempt at the same point, and the cause was a clock rather
# than a pipe: Harbor logged "client disconnected during blob PUT ... unexpected EOF" five times for
# one 85MB layer, with 36-74MB copied and a response duration between 59.0s and 61.5s every time.
# That is Traefik's respondingTimeouts.readTimeout, 60s by default, capping how long a whole request
# body may take to read. Raising it on the cluster is what let the push through. A retry cannot help
# a limit every attempt reaches alike; keep that in mind before reading the next failure as weather.
#
# Where it does help is a push that failed once and would succeed again, and there it is cheap: every
# blob that landed is still in the registry and buildx asks before it sends, so the next attempt
# uploads only what is missing. The build itself is cached too. That is also why the backoff is
# short -- there is no server to give time to recover, only a link to stop being busy.
#
# It replaces docker/build-push-action, which has no retry input and no way to add one from outside
# a `uses:` step. What is lost with it is the job summary that action writes; the digest it exported
# as a step output is written here instead, as `digest=` into GITHUB_OUTPUT when CI sets it.
#
# Retrying forever would hide a registry that is actually broken, so ATTEMPTS bounds it and the
# warning says which attempt it was. Three failures alike are a limit rather than bad luck, and the
# place to look next is the registry's own log: three 401s is a robot account that expired, and
# three cut at the same second is a timeout in front of it.
set -euo pipefail

ATTEMPTS="${ATTEMPTS:-3}"

metadata="$(mktemp)"
trap 'rm -f "${metadata}"' EXIT

for attempt in $(seq 1 "${ATTEMPTS}"); do
	if docker buildx build --metadata-file "${metadata}" "$@"; then
		digest="$(jq -r '."containerimage.digest" // empty' "${metadata}")"
		if [[ -z "${digest}" ]]; then
			echo "the build succeeded and reported no image digest, so there is nothing to sign" >&2
			exit 1
		fi
		if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
			echo "digest=${digest}" >> "${GITHUB_OUTPUT}"
		fi
		echo "${digest}"
		exit 0
	fi
	if [[ "${attempt}" -lt "${ATTEMPTS}" ]]; then
		echo "::warning title=build retried::attempt ${attempt} of ${ATTEMPTS} failed; what already reached the registry is kept, so the next one sends the rest" >&2
		sleep $((attempt * 15))
	fi
done

echo "the build failed ${ATTEMPTS} times; the last attempt's output is above" >&2
exit 1
