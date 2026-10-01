#!/usr/bin/env bash
# Oracle driver. Test tooling only; `mvn verify` never runs this.
#   run.sh generate                               T4 outputs -> oracle/generated/, then byte check
#   run.sh build                                  dotnet build -c Release of kusto-oracle
#   run.sh dump <corpus.jsonl> <out.jsonl.gz> [schemasDir]
#   run.sh facts                                  porting/dotnet-facts-cases.txt -> conformance dotnet-facts.json
#   run.sh probe                                  T0 questions as JSON on stdout
#   run.sh census <corpus.jsonl> [schemasDir]     throw / round-trip counts as JSON on stdout
#   run.sh regenerate                             generate, build, facts, dump every corpus
#   run.sh sources-sha256                         print the sources hash used in golden headers
set -euo pipefail

export TZ=UTC
export DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1
export DOTNET_CLI_TELEMETRY_OPTOUT=1
export DOTNET_NOLOGO=1

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/.." && pwd)"
upstream_repo="$root/upstream/kusto-query-language"
generated="$here/generated"
gen_proj="$here/kusto-oracle-gen/Kusto.Oracle.Gen.csproj"
gen_dll="$here/kusto-oracle-gen/bin/Release/net10.0/Kusto.Oracle.Gen.dll"
oracle_proj="$here/kusto-oracle/Kusto.Oracle.csproj"
oracle_dll="$here/kusto-oracle/bin/Release/net10.0/Kusto.Oracle.dll"
exception_map="$root/porting/exception-map.json"
resources="$root/kusto-language-conformance/src/test/resources"
server_kind="${KUSTO_ORACLE_SERVER_KIND:-Engine}"

die() { echo "run.sh: $*" >&2; exit 1; }

upstream_commit() { git -C "$upstream_repo" rev-parse HEAD; }

# SHA-256 over "<path>\t<git blob>\n" for every compiled C# file, sorted by path (bytewise).
# Paths are relative to the repository root. Upstream blobs come from the pinned commit;
# oracle/generated/*.cs and the oracle's own sources are hashed with `git hash-object`.
sources_sha256() {
  {
    git -C "$upstream_repo" ls-tree -r HEAD -- src/Kusto.Language \
      | awk -F'\t' '{ split($1, m, " "); p = $2;
                      if (p ~ /\.cs$/ && p !~ /\/CodeGen\//) printf "upstream/kusto-query-language/%s\t%s\n", p, m[3] }'
    local f
    for f in "$generated"/*.cs "$here"/kusto-oracle/src/*.cs; do
      [ -e "$f" ] || die "missing $f (run generate first)"
      printf '%s\t%s\n' "${f#"$root"/}" "$(git hash-object "$f")"
    done
  } | LC_ALL=C sort | sha256sum | cut -d' ' -f1
}

cmd_generate() {
  dotnet build "$gen_proj" -c Release -nologo -v q >&2
  rm -rf "$generated"
  mkdir -p "$generated"
  dotnet "$gen_dll" "$generated" >&2
  bash "$here/check-generated.sh"
}

cmd_build() {
  [ -e "$generated/GeneratedSyntaxNodes.cs" ] || cmd_generate
  dotnet build "$oracle_proj" -c Release -nologo -v q -clp:ErrorsOnly >&2
  echo "build: OK ($oracle_dll)" >&2
}

ensure_built() { [ -e "$oracle_dll" ] || cmd_build; }

oracle() { dotnet "$oracle_dll" "$@" --exception-map "$exception_map"; }

cmd_dump() {
  [ $# -ge 2 ] || die "usage: run.sh dump <corpus.jsonl> <out.jsonl.gz> [schemasDir]"
  local corpus="$1" out="$2" schemas="${3:-}"
  ensure_built
  local args=(dump "$corpus" --out "$out" --server-kind "$server_kind"
              --upstream "$(upstream_commit)" --sources-sha256 "$(sources_sha256)")
  [ -n "$schemas" ] && args+=(--schemas "$schemas")
  mkdir -p "$(dirname "$out")"
  oracle "${args[@]}"
}

cmd_facts() {
  ensure_built
  mkdir -p "$resources"
  oracle facts "$root/porting/dotnet-facts-cases.txt" --out "$resources/dotnet-facts.json"
}

cmd_probe() {
  ensure_built
  oracle probe
}

cmd_census() {
  [ $# -ge 1 ] || die "usage: run.sh census <corpus.jsonl> [schemasDir]"
  ensure_built
  local args=(census "$1" --server-kind "$server_kind")
  [ -n "${2:-}" ] && args+=(--schemas "$2")
  oracle "${args[@]}"
}

cmd_regenerate() {
  cmd_generate
  cmd_build
  cmd_facts
  local corpus_dir="$resources/corpus" golden_dir="$resources/goldens" schemas="$resources/schemas"
  shopt -s nullglob
  local corpora=("$corpus_dir"/*.jsonl)
  [ ${#corpora[@]} -gt 0 ] || { echo "regenerate: no corpora under $corpus_dir" >&2; return 0; }
  mkdir -p "$golden_dir"
  local c
  for c in "${corpora[@]}"; do
    local name; name="$(basename "$c" .jsonl)"
    cmd_dump "$c" "$golden_dir/$name.jsonl.gz" "$schemas"
  done
}

[ $# -ge 1 ] || die "usage: run.sh {generate|build|dump|facts|probe|census|regenerate|sources-sha256} ..."
command="$1"; shift
case "$command" in
  generate)       cmd_generate "$@" ;;
  build)          cmd_build "$@" ;;
  dump)           cmd_dump "$@" ;;
  facts)          cmd_facts "$@" ;;
  probe)          cmd_probe "$@" ;;
  census)         cmd_census "$@" ;;
  regenerate)     cmd_regenerate "$@" ;;
  sources-sha256) sources_sha256 ;;
  *)              die "unknown command: $command" ;;
esac
