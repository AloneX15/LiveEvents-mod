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
OUT = ROOT / 'generated' / 'v2' / VARIANT
OUT.mkdir(parents=True, exist_ok=True)
random.seed(2107)
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
scene = bpy.context.scene
scene.frame_start, scene.frame_end = 1, 240
scene.render.fps = 24
scene.render.engine = 'BLENDER_EEVEE'
scene.eevee.taa_render_samples = 8
scene.eevee.volumetric_tile_size = '8'
scene.eevee.volumetric_samples = 24
scene.eevee.use_volumetric_shadows = True
scene.eevee.use_volume_custom_range = True
scene.eevee.volumetric_start = 0.1
scene.eevee.volumetric_end = 100
scene.render.threads_mode = 'FIXED'
scene.render.threads = 4
scene.cycles.samples = 32
scene.render.resolution_x, scene.render.resolution_y = 480, 270
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
for i in range(0):
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

# v2 art direction. All resources are original procedural work by TakumiStudios.
green = material('Magnesium accent', (0.486, 1, 0.42), 2)
water_mat = material('Water splash', (0.15, 0.45, 0.65), 0.12)
snow_mat = material('Snow ejecta', (0.85, 0.94, 1))
if VARIANT == 'water':
    ground.diffuse_color = (0.02, 0.1, 0.16, 1)
    ground.node_tree.nodes.get('Principled BSDF')
    for node in ground.node_tree.nodes:
        if node.type == 'BSDF_PRINCIPLED':
            node.inputs['Base Color'].default_value = (0.025, 0.12, 0.2, 1)
            node.inputs['Roughness'].default_value = 0.2
if VARIANT == 'snow':
    for node in ground.node_tree.nodes:
        if node.type == 'BSDF_PRINCIPLED': node.inputs['Base Color'].default_value = (0.65, 0.76, 0.86, 1)

def beam(name, start, end, width, mat):
    middle = (Vector(start) + Vector(end)) * 0.5
    bpy.ops.mesh.primitive_cone_add(vertices=12, radius1=width, radius2=0.02, depth=(Vector(end)-Vector(start)).length, location=middle)
    obj = bpy.context.object
    obj.name = name
    obj.rotation_euler = (Vector(end)-Vector(start)).to_track_quat('Z','Y').to_euler()
    obj.data.materials.append(mat)
    return obj

# Parent approach, then three continuous fragments. Each has a warning and a matching impact.
origin = Vector((-25, 0, 28))
split = origin.lerp(Vector((0,0,0.6)), 47/71)
key(meteor,48,split,1)
for i, target in enumerate([(-3,-2,0.6),(0,0,0.6),(3,2,0.6)]):
    for name in ('Flight fragment %d'%i, 'Fragment incandescent core %d'%i):
        obj=bpy.data.objects[name]
        key(obj,47,split,0.001)
        key(obj,48,split,1)
    # Continuous head-to-tail geometry follows the fragment, never an array of glowing beads.
    direction=(split-Vector(target)).normalized()
    trail=beam('Tapered fragment trail %d'%i,(0,0,0),direction*10,0.4,fire)
    key(trail,47,split,0.001);key(trail,48,split,1);key(trail,72,target,1);key(trail,78,scale=0.001)
    for frame in (48,72):
        trail.location=split if frame==48 else Vector(target)
        trail.keyframe_insert(data_path='location',frame=frame)
    accent=sphere('Green magnesium %d'%i,split,0.58,green)
    key(accent,47,split,0.001);key(accent,48,split,0.9);key(accent,58,Vector(split).lerp(Vector(target),10/24),0.65);key(accent,65,scale=0.001)
    bpy.ops.mesh.primitive_torus_add(major_radius=2,minor_radius=0.04,major_segments=48,minor_segments=6,location=(target[0],target[1],0.08))
    warning=bpy.context.object;warning.name='Warning 1.5 seconds %d'%i;warning.data.materials.append(fire)
    key(warning,35,scale=0.001);key(warning,36,scale=1);key(warning,71,scale=1);key(warning,72,scale=0.001)
    # Secondary impact volumes stay aligned to the fragment endpoints.
    if i != 1:
        for j in range(10):
            a=random.uniform(0,math.tau)
            obj=sphere('Secondary impact %d %d'%(i,j),(target[0],target[1],0.2),random.uniform(0.5,1),dust)
            key(obj,72,scale=0.001);key(obj,100,(target[0]+math.cos(a)*2,target[1]+math.sin(a)*2,random.uniform(1,4)),1)
            key(obj,220,(target[0]+3,target[1],6),0.001)

parent_trail=beam('Parent continuous trail',(0,0,0),(-8,0,9),0.7,fire)
key(parent_trail,1,origin,1);key(parent_trail,48,split,1);key(parent_trail,49,scale=0.001)
# Make linear motion continuous; warning transitions are explicitly constant.
for obj in scene.objects:
    if obj.animation_data and obj.animation_data.action:
        for layer in obj.animation_data.action.layers:
            for strip in layer.strips:
                for bag in strip.channelbags:
                    for curve in bag.fcurves:
                        for point in curve.keyframe_points:
                            point.interpolation='CONSTANT' if obj.name.startswith('Warning') else 'LINEAR'

# Surface-specific dynamics and aftermath.
if VARIANT == 'water':
    for i in range(36):
        angle=i*math.tau/36
        obj=sphere('Splash crown %02d'%i,(0,0,0.1),0.22,water_mat)
        key(obj,72,scale=0.001);key(obj,76,scale=1)
        key(obj,94,(math.cos(angle)*5,math.sin(angle)*5,random.uniform(3,8)),1)
        key(obj,130,(math.cos(angle)*10,math.sin(angle)*10,0.05),0.05)
    for i in range(3):
        bpy.ops.mesh.primitive_torus_add(major_radius=1,minor_radius=0.035,location=(0,0,0.06+i*0.01))
        obj=bpy.context.object;obj.name='Water ripple %d'%i;obj.data.materials.append(water_mat)
        key(obj,72+i*8,scale=0.001);key(obj,85+i*8,scale=2);key(obj,230,scale=18-i*2);key(obj,240,scale=0.001)
else:
    bpy.ops.mesh.primitive_uv_sphere_add(segments=32,ring_count=12,radius=1,location=(0,0,-0.4))
    crater=bpy.context.object;crater.name='Cooling crater reference';crater.data.materials.append(rock)
    key(crater,71,scale=0.001);key(crater,75,scale=(4,4,0.6));key(crater,240,scale=(4,4,0.6))
    if VARIANT=='earth':
        for i in range(12):
            a=random.uniform(0,math.tau)
            ember=sphere('Residual ember %02d'%i,(math.cos(a)*2.5,math.sin(a)*2.5,0.1),0.15,fire)
            key(ember,71,scale=0.001);key(ember,77,scale=1);key(ember,180,scale=0.6);key(ember,240,scale=0.001)
    else:
        for obj in scene.objects:
            if obj.name.startswith(('Dust cap','Dust stem')):
                # Lower, spreading snow powder instead of the tall water/vapour plume.
                for layer in obj.animation_data.action.layers:
                    for strip in layer.strips:
                        for bag in strip.channelbags:
                            for curve in bag.fcurves:
                                if curve.data_path=='location' and curve.array_index==2:
                                    for point in curve.keyframe_points: point.co[1]*=0.65

scene.timeline_markers.new('Warnings begin: 1.5s before impact',frame=36)
scene.frame_set(140)
scene.render.resolution_x,scene.render.resolution_y=960,540
scene.eevee.taa_render_samples=32
scene.eevee.volumetric_tile_size='4'
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'meteor_impact.blend'))
scene.render.resolution_x,scene.render.resolution_y=480,270
scene.eevee.taa_render_samples=8
scene.eevee.volumetric_tile_size='8'
# Explicit runtime format: triangulated Minecraft Y-up coordinates, independently validated by the mod.
meteor.data.calc_loop_triangles()
mesh={'format':'liveevents.mesh.v1','author':'TakumiStudios','coordinates':'Minecraft Y-up',
      'vertices':[[v.co.x,v.co.z,-v.co.y] for v in meteor.data.vertices],
      'triangles':[list(t.vertices) for t in meteor.data.loop_triangles]}
(OUT/'meteor_mesh.json').write_text(json.dumps(mesh),encoding='utf-8')
manifest={'author':'TakumiStudios','variant':VARIANT,'seed':2107,'fps':24,'impact_frame':72,'warning_frame':36,
          'fragment_count':3,'status':'Blender reference; runtime shading is reconstructed in Minecraft',
          'smoke':'procedural, not fluid simulation','mesh_format':'liveevents.mesh.v1','runtime_coordinates':'Minecraft Y-up',
          'frames':[72,82,95,115,140,170,205,240],'columns':4,'rows':2,'tile_size':256,
          'atlas':'smoke_atlas.png','alpha':'straight','order':'left-to-right, top-to-bottom',
          'limitation':'Single-view atlas for distant LOD only'}
(OUT/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
mode=args[1] if len(args)>1 else 'preview'
if mode=='scene':
    raise SystemExit(0)
scene.render.image_settings.file_format='PNG'
scene.render.image_settings.color_mode='RGBA'
scene.render.filepath=str(OUT/'preview.png')
bpy.ops.render.render(write_still=True)
if mode in ('views','all'):
    for i,(x,y,z) in enumerate([(24,-36,18),(-24,-36,18),(24,36,18),(-24,36,18),(10,-12,38)]):
        camera.location=(x,y,z)
        camera.rotation_euler=(Vector((0,0,5))-camera.location).to_track_quat('-Z','Y').to_euler()
        scene.render.filepath=str(OUT/('view_%02d.png'%i));bpy.ops.render.render(write_still=True)
    camera.location=(24,-36,18);camera.rotation_euler=(Vector((0,0,5))-camera.location).to_track_quat('-Z','Y').to_euler()
    for frame in (24,36,55,76,100,140,205,240):
        scene.frame_set(frame);scene.render.filepath=str(OUT/('stage_%03d.png'%frame));bpy.ops.render.render(write_still=True)
if mode in ('video','all'):
    scene.render.image_settings.media_type='VIDEO'
    scene.render.image_settings.file_format='FFMPEG'
    scene.render.image_settings.color_mode='RGB'
    scene.render.ffmpeg.format='MPEG4';scene.render.ffmpeg.codec='H264'
    scene.render.filepath=str(OUT/'preview.mp4')
    bpy.ops.render.render(animation=True)

# Distant-only atlas: each cell is rendered from one fixed view.
scene.render.image_settings.media_type='IMAGE'
scene.render.image_settings.file_format='PNG'
scene.render.image_settings.color_mode='RGBA'
for obj in scene.objects:
    if obj.type=='MESH' and not obj.name.startswith(('Dust cap','Dust stem','Secondary impact')): obj.hide_render=True
scene.render.film_transparent=True
camera.data.type='ORTHO';camera.data.ortho_scale=27
camera.location=(19,-30,16);camera.rotation_euler=(Vector((2,0,7))-camera.location).to_track_quat('-Z','Y').to_euler()
scene.render.resolution_x=scene.render.resolution_y=256
atlas=bpy.data.images.new('Smoke atlas v2',width=1024,height=512,alpha=True)
atlas_pixels=[0.0]*(1024*512*4)
for index,frame in enumerate(manifest['frames']):
    scene.frame_set(frame);path=OUT/('smoke_%02d.png'%index);scene.render.filepath=str(path);bpy.ops.render.render(write_still=True)
    tile=bpy.data.images.load(str(path),check_existing=False);pixels=list(tile.pixels)
    x,y=(index%4)*256,(1-index//4)*256
    for row in range(256):
        dest=((y+row)*1024+x)*4;atlas_pixels[dest:dest+1024]=pixels[row*1024:(row+1)*1024]
    bpy.data.images.remove(tile)
atlas.pixels.foreach_set(atlas_pixels);atlas.filepath_raw=str(OUT/'smoke_atlas.png');atlas.file_format='PNG';atlas.save()
