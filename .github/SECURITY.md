# Security

House stores a Home Assistant access token, and anyone with that token can control someone's home. So if you find a security problem, please report it privately: go to the **Security** tab and choose **Report a vulnerability**. Don't include a real token or address.

Issues with Light's SDK go to Light, and issues with Home Assistant go to [Home Assistant](https://www.home-assistant.io/security/).

## What House does with your data

- Your address, token and shortcuts are stored only in House's private storage on the phone.
- The token is only ever sent to your Home Assistant. Over plain `http://` it isn't encrypted on your home network (same as Home Assistant's own web page), so use `https://` outside the home.
- "Forget" in Settings deletes everything. To revoke a token, delete it in Home Assistant under your profile → Security.
