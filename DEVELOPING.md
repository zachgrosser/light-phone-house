# Developing

How to set up your computer, run House on Light's emulator, check other screen sizes, run the tests, and get a build onto a phone. Written for a Mac with Apple silicon; Windows and Linux work too, but the emulator script hasn't been tried there.

## 1. One-time setup

**Install Android Studio.** Download it from [developer.android.com/studio](https://developer.android.com/studio) ("Mac with Apple chip" on an M-series Mac), open it and run the setup wizard with the **Standard** options.

**Get the code.** On Android Studio's welcome screen choose **Clone Repository**, sign in to GitHub and pick `light-phone-house`. Wait for the progress bar at the bottom ("Gradle sync") to finish. To try work that's still in a pull request, click the branch name at the top of the window, choose the branch under **Remote**, then **Checkout**.

**Install Java 17.** Light's build tools need exactly Java 17, which Android Studio doesn't include. Open **Settings → Build, Execution, Deployment → Build Tools → Gradle**, click the **Gradle JDK** menu, choose **Download JDK**, pick version **17** (any vendor) and click **Download**. Keep it selected.

> If Android Studio offers an **"AGP Upgrade Assistant"**, close it without upgrading: Light chooses those versions for the SDK. Ignore "Migrate to Gradle Daemon toolchain" too.

## 2. Light's emulator

Light's emulator runs inside an Android virtual phone and behaves like a Light Phone III. (If you've already set it up for Wiki, the same `LightPhone` virtual phone works: just `start` and `install` from this repo.)

**A test Home Assistant.** To try House without your real home, run Home Assistant (or any stand-in that answers its REST API) on your computer and enter `10.0.2.2:8123` as the address: that's the emulator's name for your computer.

**Create the virtual phone (once).** Open **Device Manager** (phone icon on the right edge), click **+**, then **Create Virtual Device**:

1. Pick **Medium Phone**. The script reshapes its screen to match the Light Phone III's.
2. Set **API** to **API 34** ("UpsideDownCake") and **Services** to **Android Open Source** (some versions say **No Google APIs**; never Google Play). Pick **ARM 64 v8a System Image** and download it.
3. Name it `LightPhone` and click **Finish**. **Don't press its play button**: the script starts it in the mode Light's emulator needs.

**Set it up (once, 15–30 minutes).** In Android Studio's **Terminal** tab:

```bash
scripts/emulator.sh setup LightPhone
```

It starts the virtual phone, installs Light's emulator into it as a system app, then builds and installs House. It stops with a plain explanation if something's wrong. When it prints **All set**, open **Settings → Allowed Tools → All Tools** in the emulator so House appears in the tool list. (Our test builds aren't signed by Light.)

**Every day after that:**

```bash
scripts/emulator.sh start      # start the virtual phone (leave its window open)
scripts/emulator.sh install    # rebuild House and install it after changing code
```

**Other screen sizes.** The Light Phone III is 1080 × 1240; the Light Flip (due April 2027) is 480 × 640.

```bash
scripts/emulator.sh screen flip   # the virtual phone takes the Flip's screen size
scripts/emulator.sh screen lp3    # and back
```

**Typing on a hardware keyboard.** With the emulator window in front, your computer's keyboard acts as a hardware keyboard plugged into the phone: typing on it goes into House's text entry screens (address and token). (If nothing happens, the setting is off: in **Device Manager**, edit the virtual phone, open **Advanced Settings** and tick **Enable keyboard input**.)

Faster still: open `tool/src/main/kotlin/com/zachgrosser/house/HousePreviews.kt` and click **Split** at the top right. Every screen appears at both sizes, without an emulator.

## 3. Tests

```bash
./gradlew :tool:testDebugUnitTest -DlightSdk.toolOnly=true
```

They cover addresses, tap actions, statuses, Home Assistant's JSON and setup QR codes (`ModelsTest`), and saving settings (`HouseStoreTest`). `-DlightSdk.toolOnly=true` skips building Light's example tools, which saves minutes.

## 4. What GitHub checks

Two workflows run automatically. Both show ✅ or ❌ on pull requests.

| Workflow | When | What |
|---|---|---|
| **Tool** (ours) | Every push that changes `tool/` | Unit tests; the release build exactly as Light's build server makes it (code shrinking on, arm64 only); downloadable test APKs |
| **PR Check** (Light's) | Every pull request | Builds and checks the whole SDK, including our tool. Takes about 12 minutes |

## 5. Putting a build on a real Light Phone III

Each **Tool** run on GitHub (**Actions** tab → **Tool** → a run) has an **Artifacts** section with a zip of two APKs:

- `…-debug.apk` for testing.
- `…-release.apk`, built like the version Light will sign, with code shrinking on. Test this one before submitting.

Both are signed with the SDK's public development key, so the phone only shows them when **Settings → Developer → Allowed tools** is **All tools**.

There's no official way to install your own APK on LightOS 582 yet: Light's sideloading guide ([docs/sideloading](docs/sideloading/README.md)) describes a Developer upload that hasn't reached phones. Until it does, developers use the File Manager's Tool Inbox ([unofficial installer](https://github.com/yarosz/lightphone-wifi-install)), or `adb install` with USB debugging. Only install APKs you trust, and close the File Manager when you're done.

## 6. Getting Light's latest SDK

Light updates the SDK often. Pull their changes on a branch and open a pull request like any other change:

```bash
git remote add upstream https://github.com/lightphone/light-sdk.git   # once per computer
git checkout main && git pull
git checkout -b sdk-update
git fetch upstream && git merge upstream/main
git push -u origin sdk-update
```

Merge that pull request with **Create a merge commit**, never **Squash and merge**: git needs Light's commits kept as they are to know what's already been merged next time.

Because we only edit `tool/` (plus our own docs and scripts), these merges rarely clash. If `tool/lighttool.toml` or `tool/build.gradle.kts` clashes, keep our values and take Light's new lines.
