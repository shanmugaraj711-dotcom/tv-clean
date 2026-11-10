#!/bin/bash
# Rebrand cleanyourtv-app -> TV Clean (ai.promptstudio.tvclean).
# NOTE (2026-11-10): the first rebrand used in.promptstudioai.tvclean, but `in` is a Kotlin
# keyword and cannot be the first segment of a package name - kotlinc rejects every
# `package in.promptstudioai...` line. The base package is ai.promptstudio.tvclean
# instead. The Android applicationId and the TV launcher package follow the same base.
# Run from ~/workspace/our-tvclean. Only touches the app modules;
# fixtures/ docs/ site/ docker/ e2e/ third_party/ are left alone.
set -e
cd ~/workspace/our-tvclean

MODULES="adb android app core feature tv ui build-logic testing benchmark"

echo "== 1. Text replacements in source files =="
# Order matters: longest/most specific first.
#  - com.gathin.tvhome.theme -> ai.promptstudio.tvclean.launcher.theme
#  - com.gathin.tvhome        -> ai.promptstudio.tvclean.launcher
#  - tv.cleanyour             -> ai.promptstudio.tvclean
#  - tv/cleanyour             -> ai/promptstudio/tvclean  (resource paths)
#  - CleanYourTv              -> TvClean
#  - cleanyourtv              -> tvclean  (plugin ids, tmp/restore dirs, properties)
#  - Clean Your TV            -> TV Clean  (display strings)
for m in $MODULES; do
  [ -d "$m" ] || continue
  grep -rl --include="*.kt" --include="*.kts" --include="*.xml" --include="*.json" \
    --include="*.gradle" --include="*.properties" --include="*.md" \
    -e "com\.gathin\.tvhome" -e "tv\.cleanyour" -e "tv/cleanyour" \
    -e "CleanYourTv" -e "cleanyourtv" -e "Clean Your TV" "$m" 2>/dev/null | while read -r f; do
    sed -i \
      -e 's/com\.gathin\.tvhome\.theme/ai.promptstudio.tvclean.launcher.theme/g' \
      -e 's/com\.gathin\.tvhome/ai.promptstudio.tvclean.launcher/g' \
      -e 's/tv\.cleanyour/ai.promptstudio.tvclean/g' \
      -e 's|tv/cleanyour|ai/promptstudio/tvclean|g' \
      -e 's/CleanYourTv/TvClean/g' \
      -e 's/cleanyourtv/tvclean/g' \
      -e 's/Clean Your TV/TV Clean/g' \
      "$f"
  done
done

echo "== 2. Rename cyt_ resources to tvclean_ =="
for m in $MODULES; do
  [ -d "$m" ] || continue
  grep -rl --include="*.xml" --include="*.kt" --include="*.kts" -e "cyt_" "$m" 2>/dev/null | while read -r f; do
    sed -i 's/cyt_/tvclean_/g' "$f"
  done
  # rename the resource files themselves
  find "$m" -name "cyt_*" -type f 2>/dev/null | while read -r f; do
    dir=$(dirname "$f")
    base=$(basename "$f")
    mv "$f" "$dir/tvclean_${base#cyt_}"
  done
done

echo "== 3. Rename package directories tv/cleanyour -> ai/promptstudio/tvclean =="
for m in $MODULES; do
  [ -d "$m" ] || continue
  find "$m" -type d -path "*/tv/cleanyour" 2>/dev/null | while read -r d; do
    parent=$(dirname "$(dirname "$d")")  # parent of tv/
    mkdir -p "$parent/ai/promptstudio"
    mv "$d" "$parent/ai/promptstudio/tvclean"
    # remove the now-empty tv/ dir if it has nothing else
    rmdir "$parent/tv" 2>/dev/null || true
  done
done

echo "== 4. Root project name =="
sed -i 's/rootProject\.name = "cleanyourtv-app"/rootProject.name = "tvclean-app"/' settings.gradle.kts

echo "== 5. Remaining counts (should be ~0 in modules) =="
grep -r "tv\.cleanyour\|cleanyourtv\|CleanYourTv\|com\.gathin" $MODULES --include="*.kt" --include="*.kts" --include="*.xml" --include="*.json" 2>/dev/null | grep -v "Binary" | wc -l
grep -rn "tv\.cleanyour\|cleanyourtv\|CleanYourTv\|com\.gathin" $MODULES --include="*.kt" --include="*.kts" --include="*.xml" 2>/dev/null | head -20
echo "done"
