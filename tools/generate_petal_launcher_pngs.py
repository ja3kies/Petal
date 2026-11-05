#!/usr/bin/env python3
"""Rebuild Petal's legacy PNG launchers from the approved SVG artwork.

Requires Pillow + CairoSVG. Adaptive VectorDrawable resources are unchanged.
Never modifies any non-launcher image.
"""
from __future__ import annotations

from io import BytesIO
from pathlib import Path
import re
import xml.etree.ElementTree as ET

import cairosvg
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
COLOR = ROOT / "docs/branding/petal-logo.svg"
MONO = ROOT / "docs/branding/petal-monochrome.svg"
RES = ROOT / "app/src/main/res"
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
BACKGROUND = '#1A0D1E'


def parse_paths(data: str) -> list[str]:
    root = ET.fromstring(data)
    group = next((node for node in root if node.tag.endswith("}g")), None)
    if group is None or group.attrib.get("transform") != "translate(25.92 25.92) scale(0.52)":
        raise ValueError("Unexpected approved logo transform")
    paths = [node.attrib["d"] for node in group if node.tag.endswith("}path")]
    if len(paths) != 4:
        raise ValueError("Expected exactly four petals")
    return paths


def raster(svg: str, pixels: int, round_mask: bool = False) -> bytes:
    # Supersample before Lanczos downscaling to maintain clean small icon edges.
    scale = 3
    png = cairosvg.svg2png(
        bytestring=svg.encode("utf-8"),
        output_width=pixels * scale,
        output_height=pixels * scale,
    )
    with Image.open(BytesIO(png)) as inp:
        img = inp.convert("RGBA")
    if round_mask:
        mask = Image.new("L", img.size, 0)
        draw = ImageDraw.Draw(mask)
        draw.ellipse((0, 0, img.width - 1, img.height - 1), fill=255)
        img.putalpha(mask)
    img = img.resize((pixels, pixels), Image.Resampling.LANCZOS)
    out = BytesIO()
    img.save(out, format="PNG", optimize=True, compress_level=9)
    return out.getvalue()


def main() -> None:
    color = COLOR.read_text(encoding="utf-8")
    mono = MONO.read_text(encoding="utf-8")
    if parse_paths(color) != parse_paths(mono):
        raise ValueError("Color and monochrome petals must share exactly the same paths")
    if '<rect width="108" height="108" fill="' + BACKGROUND + '"/>' not in color:
        raise ValueError("Approved SVG background unexpectedly changed")
    transparent = re.sub(
        r'<rect width="108" height="108" fill="#1A0D1E"\s*/>', "", color, count=1
    )
    if transparent == color:
        raise ValueError("Unable to isolate transparent foreground")

    produced = 0
    for density, size in DENSITIES.items():
        target_dir = RES / ("mipmap-" + density)
        if not target_dir.is_dir():
            raise FileNotFoundError(target_dir)
        variants = {
            "ic_launcher.png": raster(color, size),
            "ic_launcher_foreground.png": raster(transparent, size),
            "ic_launcher_round.png": raster(color, size, round_mask=True),
        }
        for name, payload in variants.items():
            path = target_dir / name
            if not path.is_file():
                raise FileNotFoundError(path)
            if not payload.startswith(b"\x89PNG\r\n\x1a\n"):
                raise ValueError("Invalid PNG: " + name)
            if path.read_bytes() != payload:
                path.write_bytes(payload)
            with Image.open(path) as image:
                if image.size != (size, size):
                    raise ValueError("Incorrect launcher dimensions: " + str(path))
            produced += 1
    print(f"PASS: {produced} launcher PNGs generated from approved SVGs")


if __name__ == "__main__":
    main()
