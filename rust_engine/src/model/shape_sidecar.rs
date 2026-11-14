//! Character-maker edits for glTF/VRM models from a `shape.json` next to the model file.
//!
//! The file is written by the mod when it extracts a model card made with 3D Model Renderer Studio.
//! Like the character makers of ILLGAMES titles, the studio shapes a character by moving, turning
//! and scaling bones of its skeleton (the "shape bones": cf_s_* for the body, cf_J_* for the face)
//! and by hiding parts. This module bakes those edits into the model before it is built: the
//! vertices are skinned to the edited rest pose and the bind matrices are updated, so the rest of
//! the engine sees an ordinary model whose rest pose is the edited one.
//!
//! ```json
//! { "version": 1,
//!   "bones": { "cf_s_head": { "s": [1.1, 1.1, 1.1] }, "cf_J_Eye_tx_L": { "t": [0.002, 0, 0] } },
//!   "hidden": { "materials": ["SVS m_cloth_cf_top_tshirt00 9140"], "meshes": [] } }
//! ```
//!
//! `t` is added to the bone's position (in its parent's axes, in the file's units), `r` is a turn in
//! degrees (X, Y, Z, applied after the bone's own rotation), `s` multiplies the bone's scale.
//! Hidden materials and meshes are dropped (their triangles collapse).

use std::collections::{HashMap, HashSet};
use std::path::{Path, PathBuf};

use glam::{EulerRot, Mat3, Mat4, Quat, Vec3};
use serde_json::Value;

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct BoneEdit {
    pub translation: Vec3,
    pub rotation_deg: Vec3,
    pub scale: Vec3,
}

impl Default for BoneEdit {
    fn default() -> Self {
        Self { translation: Vec3::ZERO, rotation_deg: Vec3::ZERO, scale: Vec3::ONE }
    }
}

#[derive(Debug, Clone, Default)]
pub struct ShapeEdit {
    pub bones: HashMap<String, BoneEdit>,
    pub hidden_materials: HashSet<String>,
    pub hidden_meshes: HashSet<String>,
}

impl ShapeEdit {
    pub fn is_empty(&self) -> bool {
        self.bones.is_empty() && self.hidden_materials.is_empty() && self.hidden_meshes.is_empty()
    }
}

/// Where the sidecar for `model_path` would be: `<stem>.shape.json`, then `shape.json`.
pub fn sidecar_path(model_path: &Path) -> Option<PathBuf> {
    let dir = model_path.parent()?;
    let stem = model_path.file_stem()?.to_string_lossy();
    [dir.join(format!("{stem}.shape.json")), dir.join("shape.json")]
        .into_iter()
        .find(|p| p.is_file())
}

pub fn load(model_path: &Path) -> Option<ShapeEdit> {
    let path = sidecar_path(model_path)?;
    let text = std::fs::read_to_string(&path).ok()?;
    match serde_json::from_str::<Value>(&text) {
        Ok(json) => Some(parse(&json)),
        Err(e) => {
            log::warn!("shape.json is not valid JSON ({}): {e}", path.display());
            None
        }
    }
}

fn vec3(value: Option<&Value>, default: Vec3) -> Vec3 {
    match value.and_then(Value::as_array) {
        Some(a) if a.len() >= 3 => {
            let get = |i: usize, d: f32| a[i].as_f64().map(|v| v as f32).filter(|v| v.is_finite()).unwrap_or(d);
            Vec3::new(get(0, default.x), get(1, default.y), get(2, default.z))
        }
        _ => default,
    }
}

pub fn parse(json: &Value) -> ShapeEdit {
    let mut edit = ShapeEdit::default();
    if let Some(bones) = json.get("bones").and_then(Value::as_object) {
        for (name, b) in bones {
            let e = BoneEdit {
                translation: vec3(b.get("t"), Vec3::ZERO),
                rotation_deg: vec3(b.get("r"), Vec3::ZERO).clamp(Vec3::splat(-180.0), Vec3::splat(180.0)),
                scale: vec3(b.get("s"), Vec3::ONE).clamp(Vec3::splat(0.05), Vec3::splat(20.0)),
            };
            if e != BoneEdit::default() {
                edit.bones.insert(name.clone(), e);
            }
        }
    }
    let names = |key: &str| -> HashSet<String> {
        json.get("hidden")
            .and_then(|h| h.get(key))
            .and_then(Value::as_array)
            .into_iter()
            .flatten()
            .filter_map(|v| v.as_str().map(str::to_string))
            .collect()
    };
    edit.hidden_materials = names("materials");
    edit.hidden_meshes = names("meshes");
    edit
}

/// Local matrix of a node, optionally with an edit on top of its own transform.
fn local_matrix(node: &gltf::Node, edit: Option<&BoneEdit>) -> Mat4 {
    let (t, r, s) = node.transform().decomposed();
    let (mut t, mut r, mut s) = (Vec3::from(t), Quat::from_array(r), Vec3::from(s));
    if let Some(e) = edit {
        t += e.translation;
        let d = e.rotation_deg * (std::f32::consts::PI / 180.0);
        r = r * Quat::from_euler(EulerRot::XYZ, d.x, d.y, d.z);
        s *= e.scale;
    }
    Mat4::from_scale_rotation_translation(s, r, t)
}

/// World matrices of every node in the default scene, before and after the edits.
fn world_matrices(document: &gltf::Document, edit: &ShapeEdit) -> (HashMap<usize, Mat4>, HashMap<usize, Mat4>) {
    let mut before = HashMap::new();
    let mut after = HashMap::new();
    let mut stack: Vec<(gltf::Node, Mat4, Mat4)> = Vec::new();
    let scenes: Vec<_> = document.default_scene().into_iter().chain(document.scenes()).take(1).collect();
    for scene in scenes {
        for node in scene.nodes() {
            stack.push((node, Mat4::IDENTITY, Mat4::IDENTITY));
        }
    }
    while let Some((node, parent_before, parent_after)) = stack.pop() {
        let name = node.name().unwrap_or("");
        let w0 = parent_before * local_matrix(&node, None);
        let w1 = parent_after * local_matrix(&node, edit.bones.get(name));
        before.insert(node.index(), w0);
        after.insert(node.index(), w1);
        for child in node.children() {
            stack.push((child, w0, w1));
        }
    }
    (before, after)
}

/// Byte position of element `i` of an accessor, and the buffer it lives in.
fn element_location(accessor: &gltf::Accessor, i: usize) -> Option<(usize, usize)> {
    let view = accessor.view()?;
    if accessor.sparse().is_some() {
        return None;
    }
    let stride = view.stride().unwrap_or(accessor.size());
    Some((view.buffer().index(), view.offset() + accessor.offset() + i * stride))
}

fn read_f32s<const N: usize>(buffers: &[gltf::buffer::Data], accessor: &gltf::Accessor) -> Option<Vec<[f32; N]>> {
    if accessor.data_type() != gltf::accessor::DataType::F32 {
        return None;
    }
    (0..accessor.count())
        .map(|i| {
            let (b, at) = element_location(accessor, i)?;
            let bytes = buffers.get(b)?.get(at..at + N * 4)?;
            let mut out = [0f32; N];
            for (k, v) in out.iter_mut().enumerate() {
                *v = f32::from_le_bytes(bytes[k * 4..k * 4 + 4].try_into().ok()?);
            }
            Some(out)
        })
        .collect()
}

fn write_f32s<const N: usize>(buffers: &mut [gltf::buffer::Data], accessor: &gltf::Accessor, values: &[[f32; N]]) -> bool {
    for (i, value) in values.iter().enumerate() {
        let Some((b, at)) = element_location(accessor, i) else { return false };
        let Some(bytes) = buffers.get_mut(b).and_then(|d| d.0.get_mut(at..at + N * 4)) else { return false };
        for (k, v) in value.iter().enumerate() {
            bytes[k * 4..k * 4 + 4].copy_from_slice(&v.to_le_bytes());
        }
    }
    true
}

/// Collapses the triangles of an index accessor (all indices become 0).
fn collapse_indices(buffers: &mut [gltf::buffer::Data], accessor: &gltf::Accessor) {
    let width = accessor.data_type().size();
    for i in 0..accessor.count() {
        if let Some((b, at)) = element_location(accessor, i) {
            if let Some(bytes) = buffers.get_mut(b).and_then(|d| d.0.get_mut(at..at + width)) {
                bytes.fill(0);
            }
        }
    }
}

/// Bakes `edit` into the glTF buffers. Returns how many vertices moved.
pub fn apply(document: &gltf::Document, buffers: &mut [gltf::buffer::Data], edit: &ShapeEdit) -> usize {
    let mut moved = 0;

    // Hidden parts: collapse their triangles, unless the indices are shared with a visible part.
    if !edit.hidden_materials.is_empty() || !edit.hidden_meshes.is_empty() {
        let mut hidden_indices = HashSet::new();
        let mut visible_indices = HashSet::new();
        for node in document.nodes() {
            let Some(mesh) = node.mesh() else { continue };
            let mesh_hidden = node.name().is_some_and(|n| edit.hidden_meshes.contains(n))
                || mesh.name().is_some_and(|n| edit.hidden_meshes.contains(n));
            for primitive in mesh.primitives() {
                let material_hidden = primitive.material().name().is_some_and(|n| edit.hidden_materials.contains(n));
                if let Some(indices) = primitive.indices() {
                    if mesh_hidden || material_hidden {
                        hidden_indices.insert(indices.index());
                    } else {
                        visible_indices.insert(indices.index());
                    }
                }
            }
        }
        for accessor in document.accessors() {
            if hidden_indices.contains(&accessor.index()) && !visible_indices.contains(&accessor.index()) {
                collapse_indices(buffers, &accessor);
            }
        }
    }

    if edit.bones.is_empty() {
        return 0;
    }
    let (before, after) = world_matrices(document, edit);

    for skin in document.skins() {
        let joints: Vec<usize> = skin.joints().map(|j| j.index()).collect();
        // Per joint: how the edit moves a point that follows the joint.
        let deltas: Vec<Mat4> = joints
            .iter()
            .map(|j| match (before.get(j), after.get(j)) {
                (Some(w0), Some(w1)) => *w1 * w0.inverse(),
                _ => Mat4::IDENTITY,
            })
            .collect();
        if deltas.iter().all(|d| d.abs_diff_eq(Mat4::IDENTITY, 1e-6)) {
            continue;
        }

        // Vertices of the meshes this skin deforms.
        let mut done = HashSet::new();
        for node in document.nodes().filter(|n| n.skin().is_some_and(|s| s.index() == skin.index())) {
            let Some(mesh) = node.mesh() else { continue };
            for primitive in mesh.primitives() {
                let Some(position) = primitive.get(&gltf::Semantic::Positions) else { continue };
                if !done.insert(position.index()) {
                    continue;
                }
                let (joint_sets, weight_sets) = {
                    let reader = primitive.reader(|b| Some(&buffers[b.index()]));
                    let joints: Option<Vec<[u16; 4]>> = reader.read_joints(0).map(|j| j.into_u16().collect());
                    let weights: Option<Vec<[f32; 4]>> = reader.read_weights(0).map(|w| w.into_f32().collect());
                    (joints, weights)
                };
                let (Some(joint_sets), Some(weight_sets)) = (joint_sets, weight_sets) else { continue };
                let Some(mut positions) = read_f32s::<3>(buffers, &position) else { continue };
                let normal = primitive.get(&gltf::Semantic::Normals);
                let mut normals = normal.as_ref().and_then(|n| read_f32s::<3>(buffers, n));
                for (i, p) in positions.iter_mut().enumerate() {
                    let (js, ws) = (joint_sets.get(i), weight_sets.get(i));
                    let (Some(js), Some(ws)) = (js, ws) else { continue };
                    let mut blend = Mat4::ZERO;
                    let mut total = 0.0;
                    for k in 0..4 {
                        if ws[k] > 0.0 {
                            blend += deltas.get(js[k] as usize).copied().unwrap_or(Mat4::IDENTITY) * ws[k];
                            total += ws[k];
                        }
                    }
                    if total <= 0.0 {
                        continue;
                    }
                    let blend = blend * (1.0 / total);
                    let v = blend.transform_point3(Vec3::from(*p));
                    if v != Vec3::from(*p) {
                        moved += 1;
                    }
                    *p = v.to_array();
                    if let Some(normals) = normals.as_mut() {
                        if let Some(n) = normals.get_mut(i) {
                            let linear = Mat3::from_mat4(blend).inverse().transpose();
                            *n = (linear * Vec3::from(*n)).normalize_or_zero().to_array();
                        }
                    }
                }
                write_f32s(buffers, &position, &positions);
                if let (Some(normal), Some(normals)) = (normal, normals) {
                    write_f32s(buffers, &normal, &normals);
                }
            }
        }

        // The bind pose becomes the edited pose: IBM' = IBM · delta⁻¹.
        if let Some(accessor) = skin.inverse_bind_matrices() {
            if let Some(ibms) = read_f32s::<16>(buffers, &accessor) {
                let updated: Vec<[f32; 16]> = ibms
                    .iter()
                    .zip(deltas.iter())
                    .map(|(ibm, d)| (Mat4::from_cols_array(ibm) * d.inverse()).to_cols_array())
                    .collect();
                write_f32s(buffers, &accessor, &updated);
            }
        }
    }
    moved
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_bones_and_hidden_parts() {
        let json: Value = serde_json::from_str(
            r#"{ "version": 1,
                 "bones": { "cf_s_head": { "s": [1.2, 1.2, 1.2] }, "noop": { "s": [1, 1, 1] },
                            "cf_J_Eye_tx_L": { "t": [0.01, 0, 0], "r": [0, 0, 400] } },
                 "hidden": { "materials": ["Shirt"], "meshes": ["Outfit 00"] } }"#,
        )
        .unwrap();
        let edit = parse(&json);
        assert_eq!(edit.bones.len(), 2, "an edit that changes nothing is dropped");
        assert_eq!(edit.bones["cf_s_head"].scale, Vec3::splat(1.2));
        assert_eq!(edit.bones["cf_J_Eye_tx_L"].rotation_deg.z, 180.0, "turns are clamped");
        assert!(edit.hidden_materials.contains("Shirt"));
        assert!(edit.hidden_meshes.contains("Outfit 00"));
    }
}
