# light-phone-house

A tool for the **Light Phone III**, built on [Light's official SDK](https://github.com/lightphone/light-sdk).

## House

A few Home Assistant shortcuts on the Light Phone: switch lights and light groups on and off, open and close blinds and garage doors, and run scenes and scripts. Choose what appears; everything else stays in Home Assistant.

- Connects straight to your own Home Assistant with a long-lived access token (scan it from a QR code or type it).
- No ads, analytics or tracking. Nothing goes anywhere but your Home Assistant.
- Works on the Light Phone III's screen and adapts to smaller ones like the Light Flip's.

How it works, what it supports and how to set it up: [`tool/README.md`](../tool/README.md).

## Where to start

| I want to… | Read |
|---|---|
| Set up my computer, run House on the emulator, run the tests | [DEVELOPING.md](../DEVELOPING.md) |
| Work on this with others (branches, pull requests, reviews) | [COLLABORATING.md](../COLLABORATING.md) |
| Send House to Light for their Tool Library | [SUBMITTING.md](../SUBMITTING.md) |
| Know what Light accepts | Light's [TOOL_GUIDELINES.md](../TOOL_GUIDELINES.md) |
| Understand the SDK itself | Light's [README](../README.md) and [docs](../docs) |

## What's in this repo

This repo is a copy of Light's SDK with our tool added. **Our code lives in `tool/`**; everything else comes from Light and is updated from their repo.

| Folder / file | What it is | Do we edit it? |
|---|---|---|
| `tool/` | **House**: its code, tests, settings (`lighttool.toml`) and license | ✅ Yes |
| `scripts/emulator.sh` | Sets up and runs Light's emulator on a Mac | ✅ Yes |
| `.github/` | This page, our build workflow, pull request and issue templates | ✅ Yes |
| `DEVELOPING.md`, `COLLABORATING.md`, `SUBMITTING.md` | Our guides | ✅ Yes |
| `CLAUDE.md` | Notes Claude Code reads at the start of every session (also a good summary for people) | ✅ Yes |
| `examples/` | Light's sample tools (weather, authenticator, …) | 👀 Read for ideas |
| `docs/`, `README.md`, `TOOL_GUIDELINES.md`, `CONTRIBUTING.md` | Light's documentation | 👀 Read |
| `sdk/`, `plugin/`, `lint-rules/`, `builder/`, `signer/`, … | The SDK itself | ❌ No, it comes from Light |

> GitHub shows this file (`.github/README.md`) as the front page instead of Light's README, so updates from Light never clash with it.

Our other Light Phone tool, **Wiki**, lives in [light-phone-tools](https://github.com/zachgrosser/light-phone-tools). Light builds one tool per repo (from its `tool/` folder), so each has its own.

## Licenses

- House's code (`tool/`): [MIT](../tool/LICENSE).
- Light's SDK (everything else): [MIT](../LICENSE), © The Light Phone.

Home Assistant is a trademark of the Open Home Foundation. This project isn't made by, affiliated with or endorsed by Home Assistant, the Open Home Foundation or Light.

## Who's working on this

- [@zachgrosser](https://github.com/zachgrosser)
- *(add your name here in your first pull request)*
