<img src="docs/logo.png" width="96" alt="Yomishiori logo">

# Yomishiori

An Android app for reading light novels, web novels and EPUBs, and keeping track of where you left off.
The library side is inspired by [Neko](https://github.com/nekomangaorg/Neko).

Everything stays on your phone. There's no account and no server.

## What it does

- Keeps everything in one library with covers, categories and reading status
- Reads EPUB files and remembers your exact position, even after changing the font size
- Opens web novels in a built-in browser and saves the page you're on as you read
- Lets one book have several sources, like the EPUB volumes plus the translator's site
- Backs up your library to a file and restores it

## Building

You need JDK 17 or newer and the Android SDK (platform 37).

```bash
./gradlew assembleDebug
```

Running the tests:

```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

Runs on Android 8.0 and newer.

## Good to know

- Some sites change chapters without changing the address. On those, tap the bookmark button to save your place.
- Only HTTPS sites load.
- The `.json` backup only has your library and progress. Use the `.zip` backup if you also want the EPUB files.
