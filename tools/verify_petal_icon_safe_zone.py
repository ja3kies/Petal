#!/usr/bin/env python3
"""Check Petal's launcher foreground stays inside Android's 66dp safe circle."""
from io import BytesIO
from pathlib import Path
import math
import re
import xml.etree.ElementTree as ET
import cairosvg
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
SOURCES=[ROOT/'docs/branding/petal-logo.svg',ROOT/'docs/branding/petal-monochrome.svg']
VECTORS=[
  ROOT/'app/src/main/res/drawable/ic_launcher_foreground.xml',
  ROOT/'app/src/main/res/drawable/ic_launcher_monochrome.xml',
  ROOT/'app/src/main/res/drawable/ic_launcher_foreground_dynamic.xml',
]
TRANSFORM="translate(28.62 28.62) scale(0.47)"
AND_NS="{http://schemas.android.com/apk/res/android}"
EXPECTED={"translateX":"25.92","translateY":"25.92","scaleX":"0.52","scaleY":"0.52"}

def verify_svg(path):
    raw=path.read_text(encoding="utf-8")
    root=ET.fromstring(raw)
    group=next((node for node in root if node.tag.endswith("}g")),None)
    assert group is not None and group.attrib["transform"]==TRANSFORM,path
    petals=[e.attrib["d"] for e in group if e.tag.rsplit("}", 1)[-1] == "path"]
    assert len(petals)==4,path
    clear=re.sub(r'<rect width="108" height="108" fill="#1A0D1E"\s*/>','',raw,count=1)
    pix=648  # 6 px/dp at 108 dp
    img=Image.open(BytesIO(cairosvg.svg2png(bytestring=clear.encode(),output_width=pix,output_height=pix))).convert("RGBA")
    channel=img.getchannel("A")
    bbox=channel.getbbox()
    assert bbox is not None,path
    width=(bbox[2]-bbox[0])*108/pix
    height=(bbox[3]-bbox[1])*108/pix
    # 45 dp product request overrides Android's recommended 48 dp minimum.
    assert 44.5 <= width <= 45.5 and 43.5 <= height <= 45.5,(path,width,height)
    alpha=channel.load()
    extent=0.0
    for y in range(pix):
        for x in range(pix):
            if alpha[x,y]>=128:
                extent=max(extent,math.hypot((x+.5-pix/2)*108/pix,(y+.5-pix/2)*108/pix))
    assert extent <= 29,(path,extent)
    print(f"PASS {path.name}: {width:.1f}×{height:.1f} dp, radius {extent:.1f}/33.0 dp")
    return petals

def verify_vector(path,reference):
    root=ET.parse(path).getroot()
    group=next((e for e in root if e.tag.rsplit("}", 1)[-1] == "group"),None)
    assert group is not None,path
    for field,value in EXPECTED.items():
        assert group.attrib.get(AND_NS+field)==value,(path,field)
    petals=[e.attrib[AND_NS+"pathData"] for e in group if e.tag.rsplit("}", 1)[-1] == "path"]
    assert petals==reference,(path,"petal geometry mismatch")
    print(f"PASS {path.name}: transform and original 4 outlines match")

if __name__=="__main__":
    first=verify_svg(SOURCES[0])
    assert first==verify_svg(SOURCES[1]),"color/monochrome geometry diverged"
    for vector in VECTORS:verify_vector(vector,first)
