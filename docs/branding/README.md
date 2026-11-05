# Petal Browser icon sources

The coloured and monochrome SVG files in this directory are the approved four-petal artwork. The launcher icon's shape must not be recreated using the former four-blade symbol.

The Android VectorDrawables in `app/src/main/res/drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml` and `ic_launcher_foreground_dynamic.xml` retain the exact four SVG path definitions under a 108 × 108 viewport and the original translate/scale transform. The coloured foreground is transparent outside the petals; the background layer is `#1A0D1E`. Android 13+ may tint the monochrome foreground automatically.

Always check the four petals in the adaptive-icon safety area, the themed mask and the launcher in light/dark mode. The fifteen PNG fallback images in `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}` are regenerated from the same SVG sources using `python tools/generate_petal_launcher_pngs.py` (requires CairoSVG 2.8.2 and Pillow 11.3.0). Do not edit the PNGs individually.
