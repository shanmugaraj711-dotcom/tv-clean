#!/usr/bin/env bash
# Fetch the binary files the build needs, from the upstream Clean Your TV repo
# at the pinned commit. Our GitHub push carries text files only (the connector
# cannot push binaries), so CI downloads these here. Every file below was
# verified byte-identical to the pinned commit on 2026-11-10 (the two APKs
# also match the pins in core/trust/Pins.kt). The launcher APK is still
# Gathin's bundled one until our own launcher is built and signed — see README.
# Idempotent: a file already present with the right sha256 is left alone.
set -euo pipefail
BASE="https://raw.githubusercontent.com/udhaykumarbala/cleanyourtv-app/03ad20ebec68284a362b9d90690876c18e212286"

fetch() { # local_path upstream_path sha256 size
  local local_path="$1" upstream_path="$2" want_sha="$3" want_size="$4"
  if [ -f "$local_path" ]; then
    if [ "$(sha256sum "$local_path" | cut -d' ' -f1)" = "$want_sha" ]; then
      echo "ok (cached): $local_path"
      return 0
    fi
    echo "stale, re-downloading: $local_path"
  fi
  mkdir -p "$(dirname "$local_path")"
  curl --retry 3 -fsSL "$BASE/$upstream_path" -o "$local_path"
  local got_sha got_size
  got_sha="$(sha256sum "$local_path" | cut -d' ' -f1)"
  got_size="$(stat -c%s "$local_path")"
  if [ "$got_sha" != "$want_sha" ] || [ "$got_size" != "$want_size" ]; then
    echo "ERROR: $local_path failed verification (got sha $got_sha size $got_size, want sha $want_sha size $want_size)" >&2
    exit 1
  fi
  echo "fetched: $local_path"
}

fetch "android/platform/src/main/assets/bundled/cleanyourtv-launcher.apk" "android/platform/src/main/assets/bundled/cleanyourtv-launcher.apk" "9fcc21c0eb81548c2432b3bf89c1d9c6b6ad17dcf7f7fc2e8d90d7ccbea7d60e" "540541"
fetch "android/platform/src/main/assets/bundled/cleanyourtv-curtain.apk" "android/platform/src/main/assets/bundled/cleanyourtv-curtain.apk" "eb21e112c97121a3ed7f4db1c7fbf2169bc172a14bb3f8ca781079de4651662e" "65955"
fetch "gradle/wrapper/gradle-wrapper.jar" "gradle/wrapper/gradle-wrapper.jar" "7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d" "47505"
fetch "core/spec/src/main/resources/ai/promptstudio/tvclean/core/spec/looks/arcade/wallpaper.jpg" "core/spec/src/main/resources/tv/cleanyour/core/spec/looks/arcade/wallpaper.jpg" "7373a817cf8d70b252cd8dfe91d8f0eff09061c6326cb3e7a89b1eaef0915892" "247428"
fetch "core/spec/src/main/resources/ai/promptstudio/tvclean/core/spec/looks/cinema/wallpaper.jpg" "core/spec/src/main/resources/tv/cleanyour/core/spec/looks/cinema/wallpaper.jpg" "efda9690edd4d5eddb72f50001986d91fadd5c69d361206a700ea495fa43889f" "274408"
fetch "core/spec/src/main/resources/ai/promptstudio/tvclean/core/spec/looks/console/wallpaper.jpg" "core/spec/src/main/resources/tv/cleanyour/core/spec/looks/console/wallpaper.jpg" "9a1c9dbe97d9076feb8a231de1da1aa483a6a4fbef2765f18b0873d17a5a2778" "218958"
fetch "core/spec/src/main/resources/ai/promptstudio/tvclean/core/spec/looks/diwali/wallpaper.jpg" "core/spec/src/main/resources/tv/cleanyour/core/spec/looks/diwali/wallpaper.jpg" "fdce13982a018f30498cf2a2611ded8144157ae8dad2b04333d35a8ccbd2c266" "208666"
fetch "core/spec/src/main/resources/ai/promptstudio/tvclean/core/spec/looks/horizon/wallpaper.jpg" "core/spec/src/main/resources/tv/cleanyour/core/spec/looks/horizon/wallpaper.jpg" "073d487c5e87438e4f99007601da6fbb20e4b1abc4a0c6aeb402df67d08fcd8f" "172606"
fetch "core/spec/src/main/resources/ai/promptstudio/tvclean/core/spec/looks/library/wallpaper.jpg" "core/spec/src/main/resources/tv/cleanyour/core/spec/looks/library/wallpaper.jpg" "11a728bb750c7ccbfff4e30d4ed92cbd6768d597530db791fb1d9d6d399fc440" "177191"
fetch "core/spec/src/main/resources/ai/promptstudio/tvclean/core/spec/looks/mist/wallpaper.jpg" "core/spec/src/main/resources/tv/cleanyour/core/spec/looks/mist/wallpaper.jpg" "b61129b8b649c0196281ad7dc8a87039a1a36e182053775d7f0e69eaf5c56396" "203103"
fetch "core/spec/src/main/resources/ai/promptstudio/tvclean/core/spec/pictures/glass.jpg" "core/spec/src/main/resources/tv/cleanyour/core/spec/pictures/glass.jpg" "bac0a76ba155222e630157219320a2dba5f0e5c17152606c04ab11ba6a64a855" "72980"
fetch "core/spec/src/main/resources/ai/promptstudio/tvclean/core/spec/pictures/ruska.jpg" "core/spec/src/main/resources/tv/cleanyour/core/spec/pictures/ruska.jpg" "a0333b2343dc21deb60819372d885941c4661420c1a2b5871bcc20edbb14adf1" "78902"
fetch "tv/curtain/src/main/res/font/curtain_outfit_light.ttf" "tv/curtain/src/main/res/font/curtain_outfit_light.ttf" "fd7005db28968e372627463221802a7aa109e431696faa04a1c27acc9bd12ec6" "29336"
fetch "tv/curtain/src/main/res/font/curtain_outfit_regular.ttf" "tv/curtain/src/main/res/font/curtain_outfit_regular.ttf" "dd423710d91f5a4242ddd70ecd164ce929ca9c85943bdcd2d5c5b0a4a72eefdd" "29292"
fetch "ui/design/src/main/res/font/tvclean_outfit.ttf" "ui/design/src/main/res/font/cyt_outfit.ttf" "fc7287273e66929776e2ba54f144fe699080bec29f61bf649d70d871468aeade" "110884"

fetch "android/platform/signatures/cleanyourtv-launcher/APKSigningBlock" "android/platform/signatures/cleanyourtv-launcher/APKSigningBlock" "27d7136077116d37acf35d1ffb98aaf080b3352ae2c3ce2e1458d33d24c71989" "8192"
fetch "android/platform/signatures/cleanyourtv-curtain/APKSigningBlock" "android/platform/signatures/cleanyourtv-curtain/APKSigningBlock" "c8eebc74a3a27931c94e0a2adbac4c53ce62e9df490a48f8f9b42a1b14d5c31d" "8192"

echo "binary assets: all verified"
