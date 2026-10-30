# Developing

Written for a Mac with Apple silicon. Windows and Linux should work too, but the emulator script hasn't been tried there.

## Setup

1. Install [Android Studio](https://developer.android.com/studio) and run the setup wizard with the defaults.
2. Clone the repo (Android Studio's welcome screen has **Clone Repository**) and wait for Gradle sync to finish.
3. Light's build needs exactly **Java 17**. In Android Studio go to Settings → Build, Execution, Deployment → Build Tools → Gradle, and under **Gradle JDK** choose Download JDK → 17.

If Android Studio offers to upgrade AGP or Gradle, say no. Light picks those versions.

## Emulator

Light's emulator runs inside a normal Android virtual phone.

Create the virtual phone once in Device Manager: **Medium Phone**, **API 34**, **Android Open Source** (not Google Play), **ARM 64**. Name it `LightPhone` and don't start it yourself. Then:

```bash
scripts/emulator.sh setup LightPhone   # once, takes 15-30 minutes
```

After that, day to day:

```bash
scripts/emulator.sh start      # start the virtual phone
scripts/emulator.sh install    # build House and install it
```

In the emulator, set Settings → Allowed Tools → All Tools so House shows up. Our test builds aren't signed by Light.

To see the Light Flip's smaller screen, run `scripts/emulator.sh screen flip` (and `screen lp3` to go back). For a quicker look, open `HousePreviews.kt` in Android Studio and click Split to see every screen at both sizes.

To test without your real home, run a Home Assistant (or anything that answers the same API) on your computer and use `10.0.2.2:8123` as the address in House. That's how the emulator reaches your computer.

## Tests

```bash
./gradlew :tool:testDebugUnitTest -DlightSdk.toolOnly=true
```

`-DlightSdk.toolOnly=true` skips Light's example tools, which saves a few minutes.

## Checks on GitHub

Every pull request runs two checks: **Tool** (ours: tests, builds and downloadable APKs) and **PR Check** (Light's, about 12 minutes).

## Putting it on a real phone

Each Tool run on GitHub has debug and release APKs under Artifacts. They're signed with the SDK's development key, so the phone needs Settings → Developer → Allowed tools set to All tools.

There's no official way to install your own APK on LightOS yet. People use the File Manager's Tool Inbox ([unofficial installer](https://github.com/yarosz/lightphone-wifi-install)) or `adb install` over USB. Only install APKs you trust.

## Light's rules

Light's build server only takes `tool/build.gradle.kts`, `tool/lighttool.toml` and `tool/src/main/`. A few things to know:

- Don't write an `AndroidManifest.xml` or set the app id or version in `build.gradle.kts`. Light's plugin makes those from `lighttool.toml`.
- Some Android APIs are blocked, like `Context`, `Intent`, `LocalContext` and reflection. The build will tell you. Use the SDK's versions instead.
- Libraries have to be on Light's allow-list (`plugin/src/main/resources/com/thelightphone/plugin/allowed-dependencies.txt`).
- In `lighttool.toml`, the `id` can never change once submitted, and `serverPackage` must be `com.lightos` when committed.

Light's build server also uses its own, older SDK version (v0.2.0 as of October 2026). Something that builds fine here can fail there if it uses a newer SDK feature. To check:

```bash
git fetch upstream --tags
git worktree add ../house-sdk-check v0.2.0
rm -rf ../house-sdk-check/tool && cp -R tool ../house-sdk-check/tool
cp local.properties ../house-sdk-check/
(cd ../house-sdk-check && ./gradlew :tool:assembleRelease -DlightSdk.toolOnly=true -DlightSdk.abiFilters=arm64-v8a)
git worktree remove --force ../house-sdk-check
```

## Updating Light's SDK

```bash
git remote add upstream https://github.com/lightphone/light-sdk.git   # once
git checkout main && git pull
git checkout -b sdk-update
git fetch upstream && git merge upstream/main
git push -u origin sdk-update
```

Merge that pull request with **Create a merge commit**, not squash. Otherwise every later SDK update will conflict.
