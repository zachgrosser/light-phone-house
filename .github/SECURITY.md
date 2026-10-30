# Security

House holds a Home Assistant access token, and anyone with that token can control someone's home. Security problems matter here.

## Reporting a problem

**Please don't open a public issue.** Report it privately instead: on GitHub, open the repo's **Security** tab and choose **Report a vulnerability**. Only the maintainer sees it. Say what's wrong and, if you can, how to reproduce it. Never include a real token or address.

Problems in Light's SDK or LightOS itself go to Light (see the root `CONTRIBUTING.md`); problems in Home Assistant go to [Home Assistant's security page](https://www.home-assistant.io/security/).

## What House does with your data

- The Home Assistant address, access token and shortcuts are stored in House's private storage on the phone. Other tools can't read it.
- House sends the token only to the address you entered, as an `Authorization: Bearer` header. Over plain `http://` on a home network, it isn't encrypted on that network, just as with Home Assistant's own web page there. Use an `https://` address anywhere else.
- House makes no other connections, and has no analytics or tracking.
- **Forget** in Settings deletes everything House stored. To revoke a token, delete it in Home Assistant: your profile, then **Security**.

Supported versions: the latest release, and `main`.
