"""Validate the checked-in Blender/runtime contract without installing Blender in CI."""
import json
import math
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "art/meteor_shower/generated/v2"
ASSETS = ROOT / "src/main/resources/assets/liveevents"

def png_size(path):
    data = path.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", path
    w, h, depth, color = struct.unpack(">IIBB", data[16:26])
    assert depth == 8 and color == 6, (path, depth, color)
    return w, h

for variant in ("earth", "water", "snow"):
    folder = ART / variant
    manifest = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
    assert manifest["variant"] == variant
    assert manifest["impact_frame"] - manifest["warning_frame"] == 36
    assert manifest["fps"] == 24 and manifest["fragment_count"] == 3
    assert png_size(folder / "smoke_atlas.png") == (1024, 512)
    for i in range(8):
        assert png_size(folder / f"smoke_{i:02}.png") == (256, 256)
    assert (folder / "smoke_atlas.png").read_bytes() == (ASSETS / f"textures/effect/meteor_{variant}.png").read_bytes()
    assert (folder / "preview.mp4").stat().st_size > 1000
    assert (folder / "meteor_impact.blend").stat().st_size > 10000
    assert all((folder / f"view_{i:02}.png").is_file() for i in range(5))
    mesh = json.loads((folder / "meteor_mesh.json").read_text(encoding="utf-8"))
    assert mesh["format"] == "liveevents.mesh.v1" and mesh["coordinates"] == "Minecraft Y-up"
    assert 0 < len(mesh["vertices"]) <= 4096 and 0 < len(mesh["triangles"]) <= 8192
    assert all(len(v) == 3 and all(math.isfinite(n) and abs(n) <= 16 for n in v) for v in mesh["vertices"])
    assert all(len(t) == 3 and all(type(i) is int and 0 <= i < len(mesh["vertices"]) for i in t) for t in mesh["triangles"])
report = json.loads((ART / "validation.json").read_text(encoding="utf-8"))
assert all(report[v]["validated"] for v in ("earth", "water", "snow"))
assert (ART / "earth/meteor_mesh.json").read_bytes() == (ASSETS / "meteor/meteor_mesh.json").read_bytes()
print("Meteor Blender/runtime contracts validated (earth, water, snow)")

# The CI checks committed audio without installing synthesis dependencies.
sounds = json.loads((ASSETS / "sounds.json").read_text(encoding="utf-8"))
translations = [json.loads((ASSETS / "lang" / f"{lang}.json").read_text(encoding="utf-8"))
                for lang in ("en_us", "es_es")]
for name in ("whistle", "impact_near", "impact_far", "debris", "tail"):
    event = sounds[f"meteor.{name}"]
    assert all(event["subtitle"] in lang and lang[event["subtitle"]] for lang in translations), name
    assert event["sounds"] == [{"name": f"liveevents:meteor/{name}"}], name
    path = ASSETS / "sounds" / "meteor" / f"{name}.ogg"
    data = path.read_bytes()
    offset, pages, granule = 0, 0, 0
    while offset < len(data):
        assert data[offset:offset+4] == b"OggS" and data[offset+4] == 0, path
        assert offset+27 <= len(data), path
        segments = data[offset+26]
        body = offset+27+segments
        assert body <= len(data), path
        end = body+sum(data[offset+27:body])
        assert end <= len(data), path
        granule = max(granule, struct.unpack_from("<Q", data, offset+6)[0])
        if pages == 0:
            assert data[body:body+7] == b"\x01vorbis", path
            assert data[body+11] == 1, (path, "Expected mono audio")
            assert struct.unpack_from("<I", data, body+12)[0] == 44100, path
        offset, pages = end, pages+1
    assert pages >= 3 and granule > 44100 and data[-1:] != b"", path
print("Meteor audio contracts validated (5 mono Vorbis resources and subtitles)")
