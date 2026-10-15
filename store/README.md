# Google Play store assets

| Asset | File | Play Console requirement |
| --- | --- | --- |
| App icon | `graphics/play-icon-512.png` | 512 × 512 PNG, 32-bit |
| Feature graphic | `graphics/feature-graphic-de-DE.png`, `graphics/feature-graphic-en-US.png` | 1024 × 500 PNG/JPEG, no alpha |
| Phone screenshots | `graphics/screenshots/de-DE/`, `graphics/screenshots/en-US/` | 2–8 images per language, 1080 × 2160 (max. aspect ratio 2:1) |
| Listing texts | `listing/de-DE.md`, `listing/en-US.md` | name ≤ 30, short ≤ 80, full ≤ 4000 characters |

Screenshots were taken on an emulator with synthetic calendars only (German ones via `adb shell cmd locale set-app-locales <package> --locales de-DE`). Never use real family events.

## Regenerating graphics

The SVG files are the sources; the launcher icon in `app/src/main/res/drawable/ic_launcher_*.xml` mirrors `graphics/icon.svg`. Requires ImageMagick with librsvg:

```bash
cd store/graphics
magick -background none play-icon.svg -resize 512x512 -define png:color-type=6 play-icon-512.png
for lang in de-DE en-US; do
  case $lang in
    de-DE) tagline="Die nächsten Familientermine auf einen Blick" ;;
    en-US) tagline="Your family's next events at a glance" ;;
  esac
  sed "s|TAGLINE|$tagline|" feature-graphic.svg > /tmp/fg.svg
  magick /tmp/fg.svg -resize 1024x500 -background "#1E5AA8" -flatten -alpha off "feature-graphic-$lang.png"
done
```

## Play Console answers

- **App category:** Productivity. **Contact email:** digiwo@gmail.com. **Website:** https://github.com/Diddlik/family-calender-widget
- **Privacy policy URL:** https://diddlik.github.io/family-calender-widget/privacy/ (GitHub Pages from `docs/` on `main`)
- **App access:** all functionality is available without special access; calendar permission is requested at runtime.
- **Ads:** no ads.
- **Content rating questionnaire:** utility app, no user-generated content shared, no violence etc.
- **Target audience:** 18+ (adults managing family calendars); not designed for children.
- **Data safety:**
  - Data collected: none. Calendar events are processed only on the device and never leave it, which Google Play does not count as collection.
  - Data shared: none.
  - Encrypted in transit: not applicable (no transfer).
  - Data deletion request: not applicable (no data collected).
- **Sensitive permission:** `READ_CALENDAR` is a runtime permission, not a restricted one; no declaration form is needed. `POST_NOTIFICATIONS` and `SCHEDULE_EXACT_ALARM` (user-granted) need no declaration either; the app does not use `USE_EXACT_ALARM`.
