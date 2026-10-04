#!/usr/bin/env bash
#
# `docker buildx build` with the push retried, and the pushed image's digest on stdout.
#
#     ./hack/buildx-push.sh . --push --platform linux/amd64,linux/arm64 --tag "$IMAGE:$TAG" ...
#
# Everything after the first argument is buildx's own; this adds `--metadata-file`, reads the digest
# out of it, and runs the whole build again when it fails.
#
# What it is for: the registry is reached over one home uplink, and a push of this image is marginal
# on it. 0.77.0 failed twice in a row with `499 Client Closed Request` from the proxy in front of
# Harbor, on a blob PUT, about a minute into `pushing layers` -- and so did the cache export beside
# it, which uploads every stage's layers to the same registry at the same time. Nothing about the
# build was wrong: the same Dockerfile and the same layer sizes had published the release before it,
# the largest layer is 85MB, and Harbor answered healthy throughout. The failure is the upload.
#
# A retry is cheap because of how a registry push works: every blob that landed is still there, and
# buildx asks before it sends, so an attempt after a partial one uploads only what is missing. The
# build itself is cached too. That is also why the backoff is short -- there is no server to give
# time to recover, only a link to stop being busy.
#
# It replaces docker/build-push-action, which has no retry input and no way to add one from outside
# a `uses:` step. What is lost with it is the job summary that action writes; the digest it exported
# as a step output is written here instead, as `digest=` into GITHUB_OUTPUT when CI sets it.
#
# Retrying forever would hide a registry that is actually broken, so ATTEMPTS bounds it and the
# warning says which attempt it was: three 499s in a row is the uplink, and three 401s is a robot
# account that expired.
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
