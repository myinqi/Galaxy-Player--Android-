# Galaxy Player (Android)

Copyright (c) 2026 [Tarja Ravenveil (TarRav)](https://github.com/myinqi). Licensed under the MIT License. See [LICENSE](LICENSE).

The phone app is the Betsy Galaxy Player: the same star map, shipyard, flight and transport. Songs come from the Betsy app API. Playback goes through ExoPlayer, so it keeps playing in the background and in Android Auto.

Apple CarPlay cannot be shipped inside an Android app. A car with CarPlay needs a separate iOS app. This project connects to Android Auto.

## Connect

In Galaxy admin, open **Mobile app API**, enable the API and copy the token. It is shown once.

On the phone, enter the API address. The admin shows it, for example `https://your-betsy-host/galaxy/api/app/v1`. On a LAN that can also be `http://192.168.x.x/galaxy/api/app/v1`. Then paste the token.

The app sends `Authorization: Bearer` on every request. It does not use the Discord login. Showcase channels are the released slowmode channels. We Love Music appears only when the partner API is already on. The partner key stays on the server.

## What stays on the phone

The app API is a read-only catalog. It does not expose the Discord explorer, so these Betsy player pieces are stored on the device and follow the same rules:

- expedition credits, daily cap, repeat cooldown and forward-seek penalty
- shipyard purchases and the equipped hull, trail, scanner, engine and worlds
- bookmarks
- WLM star ratings (they are not posted back to We Love Music)
- heard-in-full songs, auto-continue and skip

Suno New Songs is not part of the app API, so that source is not in the phone player.

## Android Auto

Install the app, connect the phone, and open Galaxy Player in Android Auto. The browse tree lists showcase channels and, when enabled, We Love Music playlists and genres. Play, pause, seek, next and previous control the same expedition. While the phone app is open, the star map follows the car.

Enable the app under Android Auto's developer or unknown-sources setting if the head unit hides apps that are not from a store.

## Build

The SDK used on this machine lives outside the repo. Point the build at it:

```bash
echo "sdk.dir=$HOME/android-sdk" > local.properties
export JAVA_HOME="$HOME/jdks/jdk-21"
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.
