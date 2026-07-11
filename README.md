# Bongard

A tiny Android app for swiping through Bongard problems — visual puzzles
where six panels on the left share a rule that the six panels on the right
break, and your job is to figure out the rule.

<p align="center">
  <img src="docs/screenshot.png" width="320" alt="Screenshot of the app showing a Bongard problem">
</p>

## Credits

**All of the problems in this app come from [Harry Foundalis' index of
Bongard problems](https://www.foundalis.com/res/bps/bpidx.htm).** Foundalis
collected, curated, and typeset hundreds of problems from dozens of
designers, and wrote his PhD dissertation on solving them — *Phaeaco: A
Cognitive Architecture Inspired by Bongard's Problems* (Indiana University,
2006, advised by Douglas Hofstadter). His [research
pages](https://www.foundalis.com/res/diss_research.html) are the best place
to read about what Bongard problems are and why they're interesting. This
app is nothing more than a convenient offline viewer for his collection.

The original 100 problems were invented by the Russian computer scientist
**Mikhail M. Bongard** for his 1967 book *Pattern Recognition*. Later
problems were designed by Douglas Hofstadter and many others; the designer
of each problem is shown under it in the app.

The problem images and solution texts belong to their respective designers
and to Foundalis' collection — they are not included in this repository
(the build scrapes them), and the APK bundles them for personal study use
only. If you hold rights to any of this material and want it removed,
open an issue.

## How it works

The app bundles all of the problems (images + solutions, scraped once at
build time) so it works fully offline:

- Swipe left/right through every problem; your position is saved across
  restarts.
- **Show solution** reveals the left/right rules, scraped from the site's
  solutions pages.
- Designer attribution is shown under each problem.
- Sun/moon toggle switches between white-on-black (default) and
  black-on-white.
- Tap the "n / total" counter to jump straight to a problem number.
- Works in portrait and landscape.

## Install

Grab the latest `bongard-vX.Y.apk` from the
[Releases page](https://github.com/mpdairy/bongard/releases), open it on
your phone, and allow installing from unknown sources when prompted.

## Build from source

You need Python 3, an Android SDK, and Gradle.

```
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
python3 scraper/scrape.py    # one-time: downloads images + solutions into app assets
gradle assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`; sideload it
with `adb install` or by copying it to the phone. The scraper caches
everything it downloads under `scraper/cache/`, so re-runs don't hammer
the site.

## Cutting a release

Bump `versionCode`/`versionName` in `app/build.gradle.kts`, then:

```
gradle assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk bongard-vX.Y.apk
gh release create vX.Y bongard-vX.Y.apk --title "vX.Y" --notes "..."
```
