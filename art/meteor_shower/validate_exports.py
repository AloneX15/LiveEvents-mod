"""Validate exported image dimensions, alpha and atlas tile placement in Blender."""
import json
import sys
from pathlib import Path
import bpy

root = Path(__file__).resolve().parent / 'generated'
if '--' in sys.argv and 'v2' in sys.argv[sys.argv.index('--')+1:]: root /= 'v2'
report = {}
for variant in ('earth', 'water', 'snow'):
    folder = root / variant
    manifest = json.loads((folder / 'manifest.json').read_text(encoding='utf-8'))
    atlas = bpy.data.images.load(str(folder / manifest['atlas']), check_existing=False)
    assert tuple(atlas.size) == (1024, 512), variant
    atlas_pixels = list(atlas.pixels)
    coverage = []
    for index in range(8):
        tile = bpy.data.images.load(str(folder / ('smoke_%02d.png' % index)), check_existing=False)
        assert tuple(tile.size) == (256, 256), (variant, index)
        pixels = list(tile.pixels)
        alphas = pixels[3::4]
        assert min(alphas) == 0, (variant, index, 'missing transparent background')
        coverage.append(sum(a > 0.01 for a in alphas))
        x, y = (index % 4) * 256, (1 - index // 4) * 256
        for row in range(256):
            start = ((y + row) * 1024 + x) * 4
            # Compare alpha (RGB is re-encoded through the atlas color space).
            source = pixels[row * 1024 + 3:(row + 1) * 1024:4]
            target = atlas_pixels[start + 3:start + 1024:4]
            assert max(abs(a - b) for a, b in zip(source, target)) <= 1 / 255 + 1e-5
        bpy.data.images.remove(tile)
    assert max(coverage[1:7]) > 100, (variant, 'empty smoke')
    assert coverage[0] < 10 and coverage[-1] < 10, (variant, 'endpoints not clear')
    mesh = json.loads((folder / 'meteor_mesh.json').read_text(encoding='utf-8'))
    assert all(0 <= i < len(mesh['vertices']) for face in mesh.get('triangles', mesh.get('faces', [])) for i in face)
    assert (folder / 'meteor_impact.blend').stat().st_size > 10000
    if root.name == 'v2':
        assert manifest['impact_frame'] - manifest['warning_frame'] == manifest['fps'] * 1.5
        assert mesh['format'] == 'liveevents.mesh.v1'
        assert mesh['coordinates'] == 'Minecraft Y-up'
        assert (folder / 'preview.mp4').stat().st_size > 1000
        assert all((folder / ('view_%02d.png' % i)).is_file() for i in range(5))
    report[variant] = {'validated': True, 'nontransparent_pixels_per_frame': coverage}
    bpy.data.images.remove(atlas)
(root / 'validation.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
print(json.dumps(report))
