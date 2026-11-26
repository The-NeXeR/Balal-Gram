## Telegram messenger for Android

[Telegram](https://telegram.org) is a messaging app with a focus on speed and security. It’s superfast, simple and free.
This repo contains the official source code for [Telegram App for Android](https://play.google.com/store/apps/details?id=org.telegram.messenger).

## BalalGram Features

Features added on top of upstream Telegram for Android (`13.2.2`):

- **BalalGram Identity & Modern Build Setup:** Custom application ID (`com.balalgram.messenger`), app name (`BalalGram`), Android SDK 37 / NDK 30 / CMake 4 build support, and API credentials loaded from gitignored `local.properties` (`APP_ID` and `APP_HASH`).
- **WebSocket & HTTP/2 MTProto Transport:** Ported native `nghttp2` + `H2Transport` MTProto transport (`wss://kws<dc>.web.telegram.org/apiws` and HTTP/2 `POST /api` via auto-updated Cloudflare relays or a custom domain), configurable in **Settings → Data and Storage → Proxy Settings** and discoverable via Settings search (localized across all 10 bundled locales).
- **BalalGram Settings:** Added **Settings → BalalGram Settings** with built-in search and Settings Search integration for privacy, chat list, chat/forwarding, media, and WebSocket options.
- **Forwarding & Media Sending:** Added forward without quoting / As Copy, Fast Forward from messages and the media viewer, Forward As Album for multi-selected media, optional original date on forwarded messages, quick-share in private chats, GIF captions from prewritten text, and sending prewritten text alongside stickers.
- **Chat List & Chat Controls:** Added optional unmuted unread priority below pinned chats, local-only unlimited pinned chats (`syncPins` off), open archive on pulldown, multi-dialog quick Mark as Read and Profile Info actions, full exact subscriber/member counts, Go to Beginning, hide chat title/avatar in the header menu, and expanded Secret Chat self-destruct timer choices (2–40m, 2–16h, 2–32d).
- **Camera, Media & Privacy:** Added rear-camera default for round video messages, system camera toggle for photos, option to omit photo sticker metadata, colored last-seen dots on avatars (yellow ≤15m, orange ≤30m, red ≤60m), and Hide Sensitive Data toggles for phone, username, bio, and account ID.

<!-- Add new features above as they are implemented -->

## Creating your Telegram Application

We welcome all developers to use our API and source code to create applications on our platform.
There are several things we require from **all developers** for the moment.

1. [**Obtain your own api_id**](https://core.telegram.org/api/obtaining_api_id) for your application.
2. Please **do not** use the name Telegram for your app — or make sure your users understand that it is unofficial.
3. Kindly **do not** use our standard logo (white paper plane in a blue circle) as your app's logo.
3. Please study our [**security guidelines**](https://core.telegram.org/mtproto/security_guidelines) and take good care of your users' data and privacy.
4. Please remember to publish **your** code too in order to comply with the licences.

### API, Protocol documentation

Telegram API manuals: https://core.telegram.org/api

MTproto protocol manuals: https://core.telegram.org/mtproto

### Compilation Guide

**Note**: In order to support [reproducible builds](https://core.telegram.org/reproducible-builds), this repo contains dummy release.keystore,  google-services.json and filled variables inside BuildVars.java. Before publishing your own APKs please make sure to replace all these files with your own.

You will require Android Studio 2025.1.4, Android NDK 27.2.12479018 and Android SDK 36.

1. Clone the Telegram source code with its submodules:
   ```bash
   git clone --recursive --shallow-submodules https://github.com/DrKLO/Telegram.git Telegram
   ```
   In case you forgot the `--recursive` flag, change to the `Telegram` directory and run:
   ```bash
   git submodule init && git submodule update --init --recursive --depth=1
   ```
2. Copy your release.keystore into TMessagesProj/config
3. Fill out RELEASE_KEY_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_STORE_PASSWORD in gradle.properties to access your  release.keystore
4.  Go to https://console.firebase.google.com/, create two android apps with application IDs org.telegram.messenger and org.telegram.messenger.beta, turn on firebase messaging and download google-services.json, which should be copied to the same folder as TMessagesProj.
5. Open the project in the Studio (note that it should be opened, NOT imported).
6. Fill out values in TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java – there’s a link for each of the variables showing where and which data to obtain.
7. You are ready to compile Telegram.

### Localization

We moved all translations to https://translations.telegram.org/en/android/. Please use it.
