# House

A Light Phone tool for a few Home Assistant shortcuts: switch lights and light groups on and off, open and close blinds and garage doors, and run scenes and scripts. It's deliberately not a full Home Assistant app; for everything else, use Home Assistant itself. Settings are in [`lighttool.toml`](lighttool.toml); the code is in [`src/main/kotlin/com/zachgrosser/house/`](src/main/kotlin/com/zachgrosser/house/).

## What it does

- **Main screen:** the shortcuts you chose, each with its state on the right: Light's on/off switch for lights, switches, fans and toggles; "Open"/"Closed" for covers; "Run" for scenes and scripts; "Press" for buttons. Tap a row to act. The new state shows straight away and Home Assistant confirms it.
- **EDIT:** every supported entity in your Home Assistant, grouped by kind. Tick the ones you want; they appear in the order ticked. Light groups show "Group of N".
- **Settings:** the address, the access token (scanned from a QR code or typed), a connection check, and "Forget", which removes everything House saved.
- **About:** privacy, trademark, source code, version.

What House shows on purpose, and what it leaves out:

| Shown | Home Assistant domains | What a tap does |
|---|---|---|
| Lights (and light groups), switches, fans, toggles | `light`, `switch`, `fan`, `input_boolean` | `turn_off` if on, `turn_on` if off |
| Blinds and garage doors | `cover` | `close_cover` if open or opening, `open_cover` otherwise |
| Scenes, scripts | `scene`, `script` | `turn_on` |
| Buttons | `button`, `input_button` | `press` |

Left out: locks and alarms (a mistaken tap shouldn't unlock a door), climate, media players, cameras, sensors, brightness and colour. A scene or script in Home Assistant can do any of those, and House can run it.

## Setting it up

1. **Address.** Type it on the phone: `homeassistant.local`, an IP such as `192.168.1.20`, or a full address like `https://abc123.ui.nabu.casa`. Without `http://` or `https://`, House assumes `http://…:8123`, Home Assistant's usual address on a home network.
2. **Access token.** In Home Assistant: your profile → **Security** → **Long-lived access tokens** → **Create token**. Tokens are about 180 characters, so a QR code is the easy way onto the phone. The QR can hold the token alone, or the address and the token separated by a space or new line. Make the QR on your own computer, not a website. For example, with [`qrencode`](https://fukuchi.org/works/qrencode/) (`brew install qrencode`), paste the token when asked:

   ```bash
   read -rs "TOKEN?Token: " && qrencode -t ansiutf8 "http://homeassistant.local:8123 $TOKEN"; unset TOKEN
   ```

   (That's zsh, macOS's default shell; in bash use `read -rsp "Token: " TOKEN`.) It prints the QR in the terminal; scan it with **Settings → Scan access token**.

House checks the connection as soon as both are set and shows "Connected to <your home's name>".

**Plain http.** Most home setups use `http://`, which Android blocks unless a tool declares Light's `cleartext-http` capability. House does (see `lighttool.toml`). Over plain http on your home Wi-Fi the token travels unencrypted on that network, as it does for Home Assistant's own web page there; use an `https://` address for access from outside the home.

**Not yet supported:** self-signed https certificates, and Home Assistant's own sign-in page (House uses a long-lived token instead, which is simpler on a phone without a browser).

## How it works

| File | What it does |
|---|---|
| `HomeScreen.kt` | The main screen: shortcuts and their states; tapping one |
| `PickerScreen.kt` | EDIT: choosing shortcuts |
| `SettingsScreen.kt` | Address, token, connection check, Forget; About |
| `InputScreens.kt` | Light's text entry (with its keyboard) and QR scanner, each as a screen that returns what was entered |
| `HomeAssistantApi.kt` | Requests to Home Assistant's [REST API](https://developers.home-assistant.io/docs/api/rest/) |
| `Models.kt` | Which entities House supports, what a tap does, statuses, reading Home Assistant's JSON, tidying addresses, reading setup QR codes |
| `HouseStore.kt` | Saves the address, token and shortcuts as one JSON file in the tool's private storage |
| `HouseUi.kt` | Shared look: theme, list rows, status, headings |
| `HousePreviews.kt` | Android Studio previews of every screen at Light Phone III and Light Flip sizes |

Requests House makes, all to the address you entered, each with `Authorization: Bearer <token>`:

| When | Request |
|---|---|
| Checking the connection | `GET /api/config` (for the home's name and version) |
| Opening EDIT | `GET /api/states` (everything, filtered on the phone) |
| Showing the main screen, or Refresh | `GET /api/states/<entity_id>` for each shortcut, all at once (much less data than every entity) |
| Tapping a shortcut | `POST /api/services/<domain>/<service>` with `{"entity_id": "…"}` |

House makes no other requests and has no ads, analytics or tracking. The token is stored in the tool's private storage (not readable by other tools) and never leaves the phone except to your Home Assistant.

## Testing

```bash
./gradlew :tool:testDebugUnitTest -DlightSdk.toolOnly=true
```

`ModelsTest` covers addresses, tap actions, statuses, reading Home Assistant's JSON and QR codes; `HouseStoreTest` covers saving.

To try House on the emulator without your real home, run a stand-in Home Assistant on your computer that answers the four requests above, and use the address `10.0.2.2:8123` (the emulator's name for your computer). See [DEVELOPING.md](../DEVELOPING.md) for the emulator and installing on a phone.

## Ideas for later

- Reordering shortcuts, and renaming them on the phone.
- Brightness for lights (Home Assistant's `brightness_pct`).
- Live updates through Home Assistant's WebSocket API instead of refreshing.
- Setting up from a computer through Light's Tool Manager (an upload in the browser instead of a QR code).
- Keypad navigation for the Light Flip, if it has no touchscreen.

## License

The code in this folder is under the [MIT License](LICENSE). Home Assistant is a trademark of the Open Home Foundation; House isn't made by, affiliated with or endorsed by Home Assistant, the Open Home Foundation or Light.
