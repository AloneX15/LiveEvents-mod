"""Run with Blender --background --python this_file.py. Creado por TakumiStudios.

Procedural animated volume study and sprite exports for a future client renderer.
Only clears the current in-memory scene; saves a separate preview project.
"""
import math
import random
import json
import sys
from pathlib import Path

import bpy
from mathutils import Vector

ROOT = Path(__file__).resolve().parent
args = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
VARIANT = args[0] if args else 'earth'
if VARIANT not in ('earth', 'water', 'snow'):
    raise ValueError('Expected earth, water or snow')
OUT = ROOT / 'generated' / VARIANT
OUT.mkdir(parents=True, exist_ok=True)
random.seed(2107)
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
scene = bpy.context.scene
scene.frame_start, scene.frame_end = 1, 240
scene.render.fps = 24
scene.render.engine = 'BLENDER_EEVEE'
scene.eevee.taa_render_samples = 32
scene.eevee.volumetric_tile_size = '2'
scene.eevee.volumetric_samples = 64
scene.eevee.use_volumetric_shadows = True
scene.eevee.use_volume_custom_range = True
scene.eevee.volumetric_start = 0.1
scene.eevee.volumetric_end = 100
scene.render.threads_mode = 'FIXED'
scene.render.threads = 4
scene.cycles.samples = 32
scene.render.resolution_x, scene.render.resolution_y = 768, 432
scene.render.resolution_percentage = 100
scene.world.color = (0.012, 0.018, 0.035)


def material(name, color, emission=0):
    mat = bpy.data.materials.new(name)
    mat.diffuse_color = (*color, 1)
    mat.use_nodes = True
    mat.node_tree.nodes.clear()
    shader = mat.node_tree.nodes.new('ShaderNodeBsdfPrincipled')
    output = mat.node_tree.nodes.new('ShaderNodeOutputMaterial')
    mat.node_tree.links.new(shader.outputs['BSDF'], output.inputs['Surface'])
    shader.inputs['Base Color'].default_value = (*color, 1)
    shader.inputs['Roughness'].default_value = 0.85
    shader.inputs['Emission Color'].default_value = (*color, 1)
    shader.inputs['Emission Strength'].default_value = emission
    return mat


rock = material('Meteor rock', (0.055, 0.035, 0.025))
core = material('Core ivory', (1, 0.90, 0.68), 8)
fire = material('Trail orange', (1, 0.20, 0.015), 3)
dust = bpy.data.materials.new('Procedural turbulent ' + VARIANT)
dust.use_nodes = True
nodes, links = dust.node_tree.nodes, dust.node_tree.links
nodes.clear()
output = nodes.new('ShaderNodeOutputMaterial')
volume = nodes.new('ShaderNodeVolumePrincipled')
volume.inputs['Color'].default_value = {
    'earth': (0.30, 0.23, 0.17, 1),
    'water': (0.72, 0.84, 0.91, 1),
    'snow': (0.94, 0.97, 1, 1),
}[VARIANT]
volume.inputs['Anisotropy'].default_value = 0.15
coords = nodes.new('ShaderNodeTexCoord')
noise = nodes.new('ShaderNodeTexNoise')
noise.noise_dimensions = '4D'
noise.inputs['Scale'].default_value = 5
noise.inputs['Detail'].default_value = 4
noise.inputs['Roughness'].default_value = 0.7
noise.inputs['W'].default_value = 0
noise.inputs['W'].keyframe_insert(data_path='default_value', frame=72)
noise.inputs['W'].default_value = 2
noise.inputs['W'].keyframe_insert(data_path='default_value', frame=240)
links.new(coords.outputs['Generated'], noise.inputs['Vector'])
contrast = nodes.new('ShaderNodeMapRange')
contrast.inputs['From Min'].default_value = 0.38
contrast.inputs['From Max'].default_value = 0.65
contrast.clamp = True
links.new(noise.outputs['Fac'], contrast.inputs['Value'])
distance = nodes.new('ShaderNodeVectorMath')
distance.operation = 'DISTANCE'
distance.inputs[1].default_value = (0.5, 0.5, 0.5)
links.new(coords.outputs['Generated'], distance.inputs[0])
edge = nodes.new('ShaderNodeMapRange')
edge.inputs['From Min'].default_value = 0.12
edge.inputs['From Max'].default_value = 0.50
edge.inputs['To Min'].default_value = 1
edge.inputs['To Max'].default_value = 0
edge.clamp = True
links.new(distance.outputs['Value'], edge.inputs['Value'])
multiply = nodes.new('ShaderNodeMath')
multiply.operation = 'MULTIPLY'
links.new(edge.outputs['Result'], multiply.inputs[0])
links.new(contrast.outputs['Result'], multiply.inputs[1])
density = nodes.new('ShaderNodeMath')
density.operation = 'MULTIPLY'
density.inputs[1].default_value = 3.5 if VARIANT == 'earth' else 1.8
links.new(multiply.outputs[0], density.inputs[0])
links.new(density.outputs[0], volume.inputs['Density'])
links.new(volume.outputs['Volume'], output.inputs['Volume'])
ground = material('Ground', (0.045, 0.055, 0.065))


def sphere(name, location, radius, mat, subdivisions=2):
    bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=subdivisions, radius=radius, location=location)
    obj = bpy.context.object
    obj.name = name
    obj.data.materials.append(mat)
    return obj


def key(obj, frame, location=None, scale=None):
    if location is not None:
        obj.location = location
        obj.keyframe_insert(data_path='location', frame=frame)
    if scale is not None:
        obj.scale = (scale,) * 3 if isinstance(scale, (float, int)) else scale
        obj.keyframe_insert(data_path='scale', frame=frame)


bpy.ops.mesh.primitive_plane_add(size=160)
bpy.context.object.name = 'Preview ground (not exported)'
bpy.context.object.data.materials.append(ground)

meteor = sphere('Meteor', (0, 0, 0), 0.8, rock, 3)
for vertex in meteor.data.vertices:
    vertex.co *= random.uniform(0.85, 1.15)
key(meteor, 1, (-25, 0, 28), 1)
key(meteor, 72, (0, 0, 0.8), 1)
key(meteor, 73, scale=0.001)
# Three children separate at frame 48 and reach distinct impact positions.
key(meteor, 48, (-8.45, 0, 9.99), 1)
key(meteor, 49, scale=0.001)
for i, target in enumerate([(-3, -2, 0.6), (0, 0, 0.6), (3, 2, 0.6)]):
    fragment = sphere('Flight fragment %d' % i, (0, 0, 0), 0.45, rock, 2)
    key(fragment, 47, (-8.45, 0, 9.99), 0.001)
    key(fragment, 48, (-8.45, 0, 9.99), 1)
    key(fragment, 72, target, 1)
    key(fragment, 74, scale=0.001)
    ember = sphere('Fragment incandescent core %d' % i, (0, 0, 0), 0.26, core)
    key(ember, 47, (-8.45, 0, 9.99), 0.001)
    key(ember, 48, (-8.45, 0, 9.99), 1)
    key(ember, 72, target, 1)
    key(ember, 74, scale=0.001)
for i in range(16):
    obj = sphere('Incandescent trail %02d' % i, (0, 0, 0), 0.65, core if i < 3 else fire)
    offset = Vector((-0.85 * i, 0, 0.92 * i))
    key(obj, 1, Vector((-25, 0, 28)) + offset, max(0.1, 1 - i / 18))
    key(obj, 72, Vector((0, 0, 0.8)) + offset, max(0.1, 1 - i / 18))
    key(obj, 84 + i, scale=0.001)

# Expanding ground ring and a three-dimensional mushroom silhouette.
bpy.ops.mesh.primitive_torus_add(major_radius=1, minor_radius=0.065, location=(0, 0, 0.12))
ring = bpy.context.object
ring.name = 'Expanding volumetric dust front'
ring.data.materials.append(dust)
key(ring, 71, scale=0.001)
key(ring, 74, scale=1)
key(ring, 125, scale=(18, 18, 3))
key(ring, 170, scale=0.001)
for i in range(76):
    angle = random.uniform(0, math.tau)
    cap = i >= 24
    radius = random.uniform(2.2, 5.8) if cap else random.uniform(0, 1.25)
    height = random.uniform(7, 10) if cap else random.uniform(0.6, 7.5)
    target = Vector((math.cos(angle) * radius, math.sin(angle) * radius, height))
    if VARIANT == 'water':
        target.z *= 1.3
        target.x *= 0.7
        target.y *= 0.7
    obj = sphere('Dust cap' if cap else 'Dust stem', (0, 0, 0.3), random.uniform(1.1, 2.2), dust)
    key(obj, 72, scale=0.001)
    key(obj, 82, target * 0.12, 0.25)
    key(obj, 140, target, 1)
    key(obj, 240, target + Vector((4, 0, 4)), 0.001)
for i in range(35):
    angle = random.uniform(0, math.tau)
    distance = random.uniform(3, 12)
    ejecta = fire if VARIANT == 'earth' else material('Ejecta %d' % i, (0.25, 0.55, 0.75) if VARIANT == 'water' else (0.9, 0.95, 1))
    obj = sphere('Ejected ember %02d' % i, (0, 0, 0.4), random.uniform(0.06, 0.22), ejecta)
    key(obj, 71, scale=0.001)
    key(obj, 73, scale=1)
    key(obj, 95, (math.cos(angle) * distance * 0.5, math.sin(angle) * distance * 0.5, random.uniform(2, 6)))
    key(obj, 130, (math.cos(angle) * distance, math.sin(angle) * distance, 0.15))
    key(obj, 180, scale=0.001)

bpy.ops.object.light_add(type='POINT', location=(0, 0, 2))
impact_light = bpy.context.object
impact_light.name = 'Warm impact illumination'
impact_light.data.color = (1, 0.24, 0.04)
impact_light.data.shadow_soft_size = 3
for frame, energy in [(71, 0), (75, 1600), (95, 900), (140, 150), (200, 0)]:
    impact_light.data.energy = energy
    impact_light.data.keyframe_insert(data_path='energy', frame=frame)

bpy.ops.object.light_add(type='AREA', location=(2, -6, 18))
bpy.context.object.data.energy = 2200
bpy.context.object.data.shape = 'DISK'
bpy.context.object.data.size = 18
bpy.ops.object.camera_add(location=(24, -36, 18))
camera = bpy.context.object
camera.rotation_euler = (Vector((0, 0, 5)) - camera.location).to_track_quat('-Z', 'Y').to_euler()
camera.data.lens = 40
scene.camera = camera
for frame, name in [(1, 'Approach'), (72, 'Impact'), (95, 'Debris'), (140, 'Mushroom'), (240, 'Dissipation')]:
    scene.timeline_markers.new(name, frame=frame)
scene.frame_set(140)
bpy.ops.wm.save_as_mainfile(filepath=str(OUT / 'meteor_impact.blend'))
scene.render.image_settings.file_format = 'PNG'
scene.render.filepath = str(OUT / 'preview.png')
bpy.ops.render.render(write_still=True)

# Export a portable mesh description. This requires a custom Minecraft renderer;
# Blender volumes and shaders are deliberately not labelled as game-ready models.
mesh = meteor.data
(OUT / 'meteor_mesh.json').write_text(json.dumps({
    'format': 'liveevents.mesh.prototype.v1', 'author': 'TakumiStudios',
    'coordinates': 'Blender Z-up; convert to Minecraft Y-up in importer',
    'vertices': [list(v.co) for v in mesh.vertices],
    'faces': [list(p.vertices) for p in mesh.polygons],
}), encoding='utf-8')

# Bake the volume evolution into transparent sprites, without stage or sparks.
for obj in scene.objects:
    if obj.type == 'MESH' and not obj.name.startswith(('Dust cap', 'Dust stem')):
        obj.hide_render = True
scene.render.film_transparent = True
camera.data.type = 'ORTHO'
camera.data.ortho_scale = 27
camera.location = (19, -30, 16)
camera.rotation_euler = (Vector((2, 0, 7)) - camera.location).to_track_quat('-Z', 'Y').to_euler()
scene.render.resolution_x = scene.render.resolution_y = 256
scene.cycles.samples = 16
scene.render.image_settings.color_mode = 'RGBA'
frames = [72, 82, 95, 115, 140, 170, 205, 240]
atlas = bpy.data.images.new('Smoke atlas', width=1024, height=512, alpha=True)
atlas_pixels = [0.0] * (1024 * 512 * 4)
for index, frame in enumerate(frames):
    scene.frame_set(frame)
    path = OUT / ('smoke_%02d.png' % index)
    scene.render.filepath = str(path)
    bpy.ops.render.render(write_still=True)
    tile = bpy.data.images.load(str(path), check_existing=False)
    pixels = list(tile.pixels)
    x, y = (index % 4) * 256, (1 - index // 4) * 256
    for row in range(256):
        start = ((y + row) * 1024 + x) * 4
        atlas_pixels[start:start + 1024] = pixels[row * 1024:(row + 1) * 1024]
    bpy.data.images.remove(tile)
atlas.pixels.foreach_set(atlas_pixels)
atlas.filepath_raw = str(OUT / 'smoke_atlas.png')
atlas.file_format = 'PNG'
atlas.save()
(OUT / 'manifest.json').write_text(json.dumps({
    'author': 'TakumiStudios', 'variant': VARIANT, 'seed': 2107,
    'status': 'prototype; not integrated or performance-tested in Minecraft',
    'atlas': 'smoke_atlas.png', 'columns': 4, 'rows': 2, 'tile_size': 256,
    'order': 'left-to-right, top-to-bottom', 'alpha': 'straight',
    'frames': frames, 'fps': 24, 'impact_frame': 72,
    'times_after_impact_seconds': [(f - 72) / 24 for f in frames],
    'limitation': 'Single-view distant LOD sprites; use 3D or multi-view for close impacts',
}, indent=2), encoding='utf-8')
