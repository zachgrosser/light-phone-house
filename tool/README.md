# House

A few Home Assistant shortcuts on the Light Phone.

- The main screen lists the shortcuts you picked. Tap one to switch it on or off, open or close it, or run it.
- **EDIT** lists everything House can control in your Home Assistant. Tick what you want.
- **Settings** is where you enter your Home Assistant address and access token.

## What it supports

| Kind | Domains | Tap does |
|---|---|---|
| Lights, light groups, switches, fans, toggles | `light`, `switch`, `fan`, `input_boolean` | on ↔ off |
| Blinds, garage doors | `cover` | open ↔ close |
| Scenes, scripts | `scene`, `script` | run |
| Buttons | `button`, `input_button` | press |

Locks and alarms are left out on purpose, since a mistaken tap shouldn't unlock a door. Thermostats, media and sensors are left out to keep House simple. If you need any of those, make a scene or script in Home Assistant and add that to House.

## Setting it up

**Address:** something like `homeassistant.local`, `192.168.1.20` or `https://abc123.ui.nabu.casa`. If you leave off `http://`, House assumes `http://...:8123`.

**Token:** in Home Assistant, go to your profile → Security → Long-lived access tokens → Create token. It's long, so the easiest way onto the phone is a QR code. The code can hold just the token, or the address and token separated by a space. Make it on your own computer, not a website. With `qrencode` (`brew install qrencode`):

```bash
read -rs "TOKEN?Token: " && qrencode -t ansiutf8 "http://homeassistant.local:8123 $TOKEN"; unset TOKEN
```

Then scan it in Settings → Scan access token.

Plain `http://` works because House uses Light's `cleartext-http` option. Self-signed https certificates aren't supported yet.

## How it works

| File | |
|---|---|
| `HomeScreen.kt` | Main screen and tapping shortcuts |
| `PickerScreen.kt` | EDIT |
| `SettingsScreen.kt` | Settings and About |
| `InputScreens.kt` | Text entry and QR scanning |
| `HomeAssistantApi.kt` | Talking to Home Assistant |
| `Models.kt` | What each tap does, parsing, addresses, QR codes |
| `HouseStore.kt` | Saving settings on the phone |
| `HouseUi.kt` | Shared UI pieces |
| `HousePreviews.kt` | Previews at both screen sizes |

House uses Home Assistant's [REST API](https://developers.home-assistant.io/docs/api/rest/):

- `GET /api/config` to check the connection
- `GET /api/states` for EDIT
- `GET /api/states/<id>` for each shortcut on the main screen
- `POST /api/services/<domain>/<service>` when you tap

It doesn't connect to anything else.

## Tests

```bash
./gradlew :tool:testDebugUnitTest -DlightSdk.toolOnly=true
```

## License

MIT ([LICENSE](LICENSE)). Home Assistant is a trademark of the Open Home Foundation. House isn't affiliated with or endorsed by Home Assistant or Light.
