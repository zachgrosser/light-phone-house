# Submitting to Light

Light builds tools from a public GitHub repo and reviews them before they go in the Tool Library. Their steps are in [README.md](README.md#submitting-your-tool). This is our checklist.

## Before submitting

- [ ] Everything is merged into `main` and the Tool check is green
- [ ] Light's latest SDK is merged in ([DEVELOPING.md](DEVELOPING.md#updating-lights-sdk))
- [ ] It builds against the SDK version Light's server uses ([DEVELOPING.md](DEVELOPING.md#lights-rules))
- [ ] Tried the release APK: connecting, scanning a token, picking shortcuts, tapping each kind, a wrong token, Home Assistant offline, Forget, About
- [ ] Checked both screen sizes
- [ ] Raised `versionCode` in `tool/lighttool.toml` and updated `versionName`
- [ ] `id` is `com.zachgrosser.house` and `serverPackage` is `com.lightos`
- [ ] About still describes what House sends and stores
- [ ] Fits Light's [Tool Guidelines](TOOL_GUIDELINES.md)

## On Light's dashboard

1. At [dashboard.thelightphone.com](https://dashboard.thelightphone.com), turn on developer mode under Settings → Account → Developer Account.
2. Manage Custom Tools → Submit New Tool, with `https://github.com/zachgrosser/light-phone-house`.
3. Add a screenshot or two from the emulator, using a test Home Assistant so no real names show.
4. Submit a build with a tag made on `main`:
   ```bash
   git tag house-v0.1.0 && git push origin house-v0.1.0
   ```
5. If the build fails, the dashboard shows why. Once it works, Light reviews it and emails feedback. Questions go to tools@thelightphone.com.

## Listing text

> House: a few shortcuts to your Home Assistant. Switch lights on and off, open blinds, run scenes and scripts. Connects directly to your own Home Assistant. No ads or tracking. Not affiliated with Home Assistant.
