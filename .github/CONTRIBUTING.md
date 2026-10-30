# Contributing to House

Thanks for helping. House is a small Light Phone tool: a few Home Assistant shortcuts, nothing more. Everyone taking part follows our [code of conduct](CODE_OF_CONDUCT.md).

> The root `CONTRIBUTING.md` and `CODE_OF_CONDUCT.md` belong to Light's SDK, which this repo is built on. They're about contributing to Light's SDK itself; this file is about House.

## Ways to help

- **Try it and report problems.** [Open an issue](https://github.com/zachgrosser/light-phone-house/issues/new/choose) with what happened and your Home Assistant version. Never paste your Home Assistant address or access token.
- **Pick up an issue.** Ones labelled [`good first issue`](https://github.com/zachgrosser/light-phone-house/labels/good%20first%20issue) are small and self-contained. Comment that you're taking it, so two people don't build the same thing.
- **Suggest something.** For a new feature, open an issue first and say how it keeps House small and calm. Light asks tools to be intentional: no feeds, no notifications pulling you back in.

## Making a change

1. **Fork** the repo (button at the top right on GitHub), then clone your fork. Regular collaborators can skip the fork and make a branch here instead. [COLLABORATING.md](../COLLABORATING.md) has both, step by step.
2. **Set up** Android Studio, Java 17 and Light's emulator: [DEVELOPING.md](../DEVELOPING.md).
3. **Make a branch** named after what you're doing (`git checkout -b add-brightness`).
4. **Check your work:**
   ```bash
   ./gradlew :tool:testDebugUnitTest -DlightSdk.toolOnly=true
   ```
   Look at your screens at both sizes in `tool/src/main/kotlin/com/zachgrosser/house/HousePreviews.kt`, and try the change on the emulator.
5. **Open a pull request** against `main` and fill in the checklist. Our **Tool** check (tests and builds) and Light's **PR Check** run on it. The first time you contribute, a maintainer approves the checks before they start.

A maintainer reviews every pull request before it's merged.

## What gets accepted

- **Only change `tool/`** (and our own docs: `.github/`, `DEVELOPING.md`, `COLLABORATING.md`, `SUBMITTING.md`, `CLAUDE.md`, `scripts/`). Everything else is Light's SDK; changes to it go to [lightphone/light-sdk](https://github.com/lightphone/light-sdk).
- **Use only SDK features Light's build server has.** Light builds House against an older pinned SDK than the one in this repo (v0.2.0 as of October 2026). If you use a newer SDK API, our checks pass but Light's build fails. [DEVELOPING.md](../DEVELOPING.md#checking-against-lights-build-server) shows how to check.
- **Light's rules for tool code:** no `AndroidManifest.xml`, no blocked Android APIs, only allow-listed libraries. The build tells you if you break one. [CLAUDE.md](../CLAUDE.md#lights-rules-the-build-fails-or-light-rejects-the-tool-otherwise) lists them.
- **Safety first.** Nothing that could do harm by mistake from a single tap (locks, alarms). The access token stays on the phone and goes only to the person's own Home Assistant.
- **Privacy.** No analytics, tracking or new places data goes. If something new is sent or stored, the About screen and `tool/README.md` must say so.
- **Tests** for logic (`Models.kt`, `HouseStore.kt` and anything like them), kept free of Android classes so they run on any computer.

## Using AI tools

You're welcome to use them (this project uses Claude Code; `CLAUDE.md` is its notes). You're responsible for what you submit: be able to explain every change in your own words, and write issues, pull request descriptions and replies yourself.

## License

By contributing, you agree that your contribution is licensed under the [MIT License](../tool/LICENSE), like the rest of House.
