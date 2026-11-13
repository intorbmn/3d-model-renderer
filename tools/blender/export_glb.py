"""Exports the armature and its meshes of a .blend to GLB for 3D Model Renderer.

Usage (Blender 4.2 or newer):

    blender --background model.blend --python tools/blender/export_glb.py -- output.glb

Materials built from VRM/MToon node groups are not understood by Blender's glTF exporter, which then
writes them without textures. Each material is rebuilt as a plain Principled BSDF that uses the
material's main image for base colour and alpha. The .blend itself is not saved.
"""
import sys

import bpy

args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
if len(args) == 2:
    # Called as a library (bpy module): open the source file without running its embedded scripts.
    bpy.ops.wm.open_mainfile(filepath=args[0], use_scripts=False, load_ui=False)
    dst = args[1]
elif len(args) == 1:
    dst = args[0]
else:
    raise SystemExit("usage: blender --background model.blend --python export_glb.py -- output.glb")


def main_image(mat):
    if not mat.node_tree:
        return None
    images = [n.image for n in mat.node_tree.nodes if n.type == 'TEX_IMAGE' and n.image and n.image.size[0] > 0]
    return images[0] if images else None

used = {s.material for o in bpy.data.objects if o.type == 'MESH' for s in o.material_slots if s.material}
for mat in used:
    image = main_image(mat)
    tree = mat.node_tree
    if tree is None:
        continue
    tree.nodes.clear()
    out = tree.nodes.new('ShaderNodeOutputMaterial')
    bsdf = tree.nodes.new('ShaderNodeBsdfPrincipled')
    bsdf.inputs['Roughness'].default_value = 1.0
    bsdf.inputs['Metallic'].default_value = 0.0
    tree.links.new(bsdf.outputs['BSDF'], out.inputs['Surface'])
    if image is not None:
        tex = tree.nodes.new('ShaderNodeTexImage')
        tex.image = image
        tree.links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
        has_alpha = image.depth == 32 and not image.name.lower().endswith(('.jpg', '.jpeg'))
        if has_alpha:
            tree.links.new(tex.outputs['Alpha'], bsdf.inputs['Alpha'])
    print("MATERIAL", repr(mat.name), "->", image.name if image else None)

arm = next((o for o in bpy.data.objects if o.type == 'ARMATURE'), None)
if arm is None:
    raise SystemExit("no armature in this file: 3D Model Renderer needs a rigged model")
for o in bpy.data.objects:
    o.select_set(o.type in ('ARMATURE', 'MESH'))
bpy.context.view_layer.objects.active = arm
arm.data.pose_position = 'REST'
res = bpy.ops.export_scene.gltf(filepath=dst, export_format='GLB', use_selection=True, export_yup=True,
                                export_apply=False, export_animations=False, export_skins=True,
                                export_morph=True, export_def_bones=False, export_image_format='AUTO')
print("EXPORT", res)
