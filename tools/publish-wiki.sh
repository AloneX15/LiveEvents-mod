#!/usr/bin/env bash
# Publica la carpeta wiki/ en la wiki de GitHub del repositorio.
# Requisito: la wiki tiene que estar activada y con al menos una página creada desde la web
# (https://github.com/AloneX15/LiveEvents-mod/wiki → "Create the first page").
# Uso: node tools/build-wiki.mjs && bash tools/publish-wiki.sh
set -euo pipefail

REPO="${1:-AloneX15/LiveEvents-mod}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

git clone --depth 1 "https://github.com/$REPO.wiki.git" "$TMP/wiki"
find "$TMP/wiki" -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
cp -r "$ROOT/wiki/." "$TMP/wiki/"
cd "$TMP/wiki"
git add -A
if git diff --cached --quiet; then
    echo "La wiki ya está al día"
    exit 0
fi
git commit -q -m "Actualizar wiki desde docs/ ($(git -C "$ROOT" rev-parse --short HEAD 2>/dev/null || echo local))"
git push -q origin HEAD
echo "Wiki publicada: https://github.com/$REPO/wiki"
