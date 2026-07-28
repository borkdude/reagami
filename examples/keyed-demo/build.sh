#!/usr/bin/env bash
set -e
# OLD reagami = the last commit before keyed reconciliation landed, via a
# worktree; NEW = the working tree (../../src).
cd "$(dirname "$0")"
OLD_REF=8f12769
SQ=../../node_modules/.bin/squint; ES=../../node_modules/.bin/esbuild
git -C ../.. worktree add -f /tmp/reagami-old "$OLD_REF" 2>/dev/null || true
cp squint-old.edn squint.edn && $SQ compile
$ES out-old/oldmain.mjs --bundle --format=iife --outfile=bundle-old.js
cp squint-new.edn squint.edn && $SQ compile
$ES out-new/newmain.mjs --bundle --format=iife --outfile=bundle-new.js
rm -f squint.edn
echo "built. serve: python3 -m http.server  then open index.html"
