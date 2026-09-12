#!/usr/bin/env bash
# Consistency: mod_version must be set, and every Stonecutter version must have a
# versions/<project>/gradle.properties whose fabric_api_version and mc_compat name the Minecraft
# version it is for. Copying that file for a new version and forgetting to edit it builds against
# the wrong Fabric API, which compiles and then fails at runtime.
#
# Monotonicity: with a base revision given, mod_version must have gone up, because the published
# image tag is built from it -- but only when something that ends up in the image changed. A README
# that told the truth about the tools was rejected for not bumping the mod, which teaches people to
# bump it for nothing and makes the number mean less.
set -o errexit -o nounset -o pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

fail() {
	echo "$*" >&2
	exit 1
}

version="$(sed -n 's/^mod_version=//p' "${ROOT}/gradle.properties" | tr -d '[:space:]')"
[[ -n "${version}" ]] || fail "mod_version is not set in gradle.properties"

versions_json="$("${ROOT}/gradlew" -p "${ROOT}" -q printVersions | tail -1)"
[[ "${versions_json}" == \[* ]] || fail "printVersions did not produce a version list: ${versions_json}"

while IFS=$'\t' read -r project minecraft; do
	properties="${ROOT}/versions/${project}/gradle.properties"
	[[ -f "${properties}" ]] || fail "${project} has no versions/${project}/gradle.properties"

	api="$(sed -n 's/^fabric_api_version=//p' "${properties}" | tr -d '[:space:]')"
	[[ -n "${api}" ]] || fail "${project}: fabric_api_version is not set"
	[[ "${api}" == *"+${minecraft}" ]] || fail "${project}: fabric_api_version ${api} is not for ${minecraft}"

	compat="$(sed -n 's/^mc_compat=//p' "${properties}" | tr -d '[:space:]')"
	[[ -n "${compat}" ]] || fail "${project}: mc_compat is not set"
	[[ "${compat}" == *"${minecraft}"* ]] || fail "${project}: mc_compat ${compat} does not name ${minecraft}"

	echo "${project}: fabric api ${api}, accepts ${compat}"
done < <(python3 -c '
import json, sys
for entry in json.loads(sys.argv[1]):
    print(entry["project"], entry["minecraft"], sep="\t")
' "${versions_json}")

base="${1:-}"
if [[ -z "${base}" ]]; then
	echo "version ${version} is consistent"
	exit 0
fi

# What the image is built from. Docs, the dev harness and CI's own files are not in it.
RELEASE_PATHS=(src/ versions/ docker/ gradle.properties build.gradle.kts settings.gradle.kts Dockerfile)

changed="$(git -C "${ROOT}" diff --name-only "${base}" HEAD || true)"
shipped="$(printf '%s\n' "${changed}" | grep -E "^($(IFS='|'; echo "${RELEASE_PATHS[*]}"))" || true)"

if [[ -z "${shipped}" ]]; then
	echo "version ${version} is consistent; nothing that ships changed"
	exit 0
fi

if ! previous="$(git -C "${ROOT}" show "${base}:gradle.properties" 2>/dev/null | sed -n 's/^mod_version=//p' | tr -d '[:space:]')"; then
	echo "version ${version} is consistent; ${base} has no gradle.properties to compare against"
	exit 0
fi
if [[ -z "${previous}" ]]; then
	echo "version ${version} is consistent; ${base} had no mod_version"
	exit 0
fi

[[ "${previous}" != "${version}" ]] || fail "mod_version is still ${version}; raise it"
highest="$(printf '%s\n%s\n' "${previous}" "${version}" | sort -V | tail -1)"
[[ "${highest}" == "${version}" ]] || fail "mod_version went down: ${previous} -> ${version}"

echo "version ${previous} -> ${version}"
