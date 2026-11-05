# Petal Browser icon sources

The coloured and monochrome SVG files in this directory are the approved four-petal artwork. The launcher icon's shape must not be recreated using the former four-blade symbol.

The Android VectorDrawables in `app/src/main/res/drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml` and `ic_launcher_foreground_dynamic.xml` retain the exact four SVG path definitions under a 108 × 108 viewport and the original translate/scale transform. The coloured foreground is transparent outside the petals; the background layer is `#1A0D1E`. Android 13+ may tint the monochrome foreground automatically.

Always check the four petals in the adaptive-icon safety area, the themed mask and the launcher in light/dark mode. The fifteen PNG fallback images in `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}` are regenerated from the same SVG sources using `python tools/generate_petal_launcher_pngs.py` (requires CairoSVG 2.8.2 and Pillow 11.3.0). Do not edit the PNGs individually.

## Android launcher safe zone (5 November 2026)

Official Android adaptive-icon guidance requires a 108 × 108 dp layer with the essential logo inside the centered 66 dp diameter safe circle (minimum artwork approximately 48 dp).

The approved petal outlines have not changed. Their shared transform is now `translate(28.62 28.62) scale(0.47)`, replacing the oversized `translate(18 18) scale(0.666667)` transform. Their visible bounds are approximately 45 × 44 dp, and their farthest pixel lies less than 28 dp from the layer center, inside the 33 dp safe radius. The same transform is used by the colored, monochrome and dynamic VectorDrawables and must be used to regenerate each legacy PNG.

`python tools/verify_petal_icon_safe_zone.py` tests this requirement. Run `python tools/generate_petal_launcher_pngs.py` to regenerate the 15 PNG fallbacks before building. This change does not alter the colors, shape paths, versionCode or non-launcher UI.

## Requested 45 dp icon

The mark fits in a 45 × 45 dp box, with actual ink bounds about 45 × 44 dp. The size is below Android's preferred minimum of 48 dp; the user explicitly requested it. All four original vector outlines are unchanged. The verification script enforces the fit.
