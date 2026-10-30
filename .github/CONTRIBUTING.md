# Contributing

Thanks for wanting to help with House. Bug reports, testing on real setups and code are all welcome.

(The `CONTRIBUTING.md` and `CODE_OF_CONDUCT.md` at the root of the repo are Light's, for their SDK. This one is for House.)

## Where to start

- Found a bug? [Open an issue](https://github.com/zachgrosser/light-phone-house/issues/new/choose). Please don't paste your Home Assistant address or token.
- Want to code? Issues labelled [good first issue](https://github.com/zachgrosser/light-phone-house/labels/good%20first%20issue) are small. Leave a comment so we know you're on it.
- Have an idea? Open an issue first. House is meant to stay small, so it's worth talking it through before building.

## Making a change

1. Fork the repo and make a branch.
2. Set up your machine with [DEVELOPING.md](../DEVELOPING.md).
3. Run the tests and check your screens in `HousePreviews.kt`:
   ```bash
   ./gradlew :tool:testDebugUnitTest -DlightSdk.toolOnly=true
   ```
4. Open a pull request. We'll review it and the checks will run.

## Things to keep in mind

- Only change `tool/` and our own docs. The rest is Light's SDK.
- Light builds tools with an older SDK than the one in this repo, so newer SDK features can break their build. See [DEVELOPING.md](../DEVELOPING.md#lights-rules).
- Nothing risky on a single tap. That's why locks and alarms aren't in House.
- The access token stays on the phone and only goes to the person's own Home Assistant. No analytics.

## AI tools

Fine to use, but you're responsible for what you submit. Make sure you understand your changes and can explain them, and please write issues and PR descriptions yourself.

By contributing you agree your work is MIT licensed, like the rest of House.
