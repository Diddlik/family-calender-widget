# Release to Google Play closed testing

Every push to `main` runs `.github/workflows/play-internal.yml`: unit tests, a signed release bundle (`.aab`) with `versionCode` = GitHub run number, and an upload to the **closed testing (Alpha)** track (`alpha`). Until the secrets below exist, the workflow only runs the tests and skips signing and upload.

`versionName` stays in `app/build.gradle.kts` and is changed by hand for each user-visible version.

## One-time setup

### 1. Create the upload key (locally, never commit it)

```bash
keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

Store `upload.jks` and both passwords in a password manager. Google Play App Signing holds the actual app signing key; if the upload key is lost, it can be reset in the Play Console.

### 2. Create the app in the Play Console

1. Play Console → Create app: name "Family Calendar Widget", app, free.
2. Complete the required setup tasks under "Set up your app". The answers, the privacy policy URL, listing texts and all graphics are in [`store/`](../store/README.md).
3. Testing → Closed testing → Alpha: select countries and the tester email list.

### 3. First upload by hand

Google Play only accepts API uploads after the package exists, which requires one manual upload. Build the bundle locally with the upload key and upload it in Internal testing → Create new release:

```bash
SIGNING_STORE_FILE=/path/to/upload.jks SIGNING_STORE_PASSWORD=... SIGNING_KEY_ALIAS=upload SIGNING_KEY_PASSWORD=... \
  ./gradlew bundleRelease
# result: app/build/outputs/bundle/release/app-release.aab (versionCode 1)
```

Accept Play App Signing when asked.

### 4. Service account for the Play API

1. Google Cloud Console: create (or pick) a project, enable the "Google Play Android Developer API".
2. IAM → Service accounts → Create, then Keys → Add key → JSON. Download the file.
3. Play Console → Users and permissions → Invite new user → the service account email. Grant app access to "Family Calendar Widget" with "Release to testing tracks" (and "View app information").

### 5. GitHub secrets and variables

Repository → Settings → Secrets and variables → Actions:

| Name | Type | Value |
| --- | --- | --- |
| `SIGNING_KEYSTORE_BASE64` | Secret | `base64 -w0 upload.jks` (Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("upload.jks"))`) |
| `SIGNING_STORE_PASSWORD` | Secret | keystore password |
| `SIGNING_KEY_ALIAS` | Secret | `upload` |
| `SIGNING_KEY_PASSWORD` | Secret | key password |
| `PLAY_SERVICE_ACCOUNT_JSON` | Secret | full content of the service account JSON file |
| `PLAY_RELEASE_STATUS` | Variable | `draft` until the first review is done, then `completed` |

While the app has never been reviewed, Google Play accepts only **draft** releases through the API: the workflow uploads them and you press "Review and roll out" once in the Play Console. After the first review is approved, set `PLAY_RELEASE_STATUS` to `completed` and every push to `main` submits a closed-test release, subject to Google review.

## For testers

Testers accept the opt-in link from Closed testing → Alpha → Testers ("Copy link") once, then install and update the app through the Play Store like any other app.

The workflow filename and concurrency group remain unchanged to preserve run-number version codes and serialize uploads with earlier internal-testing runs. The initial manual upload can still use internal testing; subsequent CI uploads target Alpha. Closed-test updates may require Google review before testers receive them.

## Release notes and versions

Release notes for each upload come from `store/whatsnew/whatsnew-<locale>` (max. 500 characters per language). For a user-visible release, raise `versionName` in `app/build.gradle.kts` and update both files in the same change. The tester opt-in link is `https://play.google.com/apps/testing/io.github.diddlik.calendarwidget`; only accounts on the tester list can join.
