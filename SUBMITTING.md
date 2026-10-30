# Submitting House to Light

Light builds and signs community tools from a **public** GitHub repo, then reviews them for their Tool Library. Their steps are in [README.md, "Submitting Your Tool"](README.md#submitting-your-tool); this is our checklist around them.

## Before submitting

- [ ] **Merged into `main`.** Light reads the tool's package name from the default branch. (A pull request that brings in Light's SDK uses **Create a merge commit**, not squash.)
- [ ] **Builds against Light's build-server SDK** (see [DEVELOPING.md](DEVELOPING.md#checking-against-lights-build-server)).
- [ ] **Latest Light SDK merged in.** Light requires it (see [DEVELOPING.md §6](DEVELOPING.md#6-getting-lights-latest-sdk)).
- [ ] **The Tool workflow is green on `main`**, especially the release build, which matches what Light's server runs.
- [ ] **Tried the release APK** on the emulator or a phone (DEVELOPING.md §5): connect (address, then token by QR and by typing), Check connection, EDIT and tick a few, tap each kind (light, light group, switch, cover, scene, script), a wrong token, Home Assistant switched off, Forget, About.
- [ ] **Checked both screen sizes** in `HousePreviews.kt` or with `scripts/emulator.sh screen flip`.
- [ ] **Version bumped** in `tool/lighttool.toml`: `versionCode` must be higher than the last build sent to Light; `versionName` is what people see (e.g. `1.0.0`).
- [ ] **`id` is `com.zachgrosser.house`** and `serverPackage` is `com.lightos`. The id can never change after the first submission.
- [ ] **About reviewed:** privacy (what goes to Home Assistant, where the token is kept) and the trademark line still true.
- [ ] **Against Light's [Tool Guidelines](TOOL_GUIDELINES.md):** intentional (a short list of shortcuts the person chose; no feeds or notifications), private (talks only to their own Home Assistant; no tracking; disclosed), no ads, open source (MIT), built with the SDK, Light's design components, Home Assistant's name used only to describe what House works with.
- [ ] **We can explain every change in our own words.** Light's team reviews each submitted build before approving it.

## The repo is public

Light builds tools from public repos only. This one has been public since October 2026, with `main` protected (see [COLLABORATING.md §7](COLLABORATING.md#7-good-to-know)). It contains no keys or passwords: the signing key in `sdk/keys/` is Light's public development key, and `local.properties` is never committed.

## On Light's dashboard

1. At [dashboard.thelightphone.com](https://dashboard.thelightphone.com): **Settings → Account → Developer Account**, turn developer mode on.
2. **Manage Custom Tools → Submit New Tool**, and give the repo: `https://github.com/zachgrosser/light-phone-house`.
3. **Add at least one image.** Screenshots from the emulator work: the camera button on the emulator's side toolbar saves one. Good choices: the main screen with shortcuts, EDIT, Settings connected. Use a test Home Assistant so no real names or addresses show.
4. On the tool's page, **Submit build** with:
   - **Git ref:** a tag such as `house-v0.1.0`, made on `main` (`git tag house-v0.1.0 && git push origin house-v0.1.0`), or a commit SHA.
   - **Changelog:** a sentence or two.
5. Wait for the build. If it fails, **View details** under Builds shows why; **Retry build** reruns the same ref. When it works, **Download APK** appears and the version waits for Light's approval.
6. Light emails with feedback. Questions: tools@thelightphone.com.

## Suggested listing text

> **House** — A few shortcuts to your Home Assistant: switch lights and light groups on and off, open and close blinds, run scenes and scripts. Connects directly to your own Home Assistant with an access token. No ads or tracking. Home Assistant is a trademark of the Open Home Foundation; House isn't affiliated with or endorsed by it.
