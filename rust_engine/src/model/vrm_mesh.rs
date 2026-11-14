//! glTF 网格数据 → RuntimeVertex 转换

use std::collections::HashMap;

use glam::{Vec2, Vec3};

use super::vrm_skeleton::VIRTUAL_BONE_COUNT;
use super::{RuntimeVertex, SubMesh, VertexWeight};
use crate::Result;

/// Morph Target 偏移数据
#[derive(Clone, Debug)]
pub(crate) struct MorphTargetData {
    pub position_offsets: Vec<Vec3>,
    pub normal_offsets: Vec<Vec3>,
}

/// 合并后的网格数据
#[derive(Clone, Debug)]
pub(crate) struct MergedMesh {
    pub vertices: Vec<RuntimeVertex>,
    pub indices: Vec<u32>,
    pub weights: Vec<VertexWeight>,
    pub submeshes: Vec<SubMesh>,
    pub morph_targets: Vec<MorphTargetData>,
}

/// 合并所有 mesh primitives 为统一的顶点/索引数组
/// `xz_sign`: -1.0 turns the model half a turn about Y (X and Z negated), +1.0 keeps the file's facing.
/// The engine expects models to face +Z; see `vrm_loader::facing_sign`.
pub(crate) fn merge_meshes(
    document: &gltf::Document,
    buffers: &[gltf::buffer::Data],
    xz_sign: f32,
) -> Result<MergedMesh> {
    let mut vertices = Vec::new();
    let mut indices = Vec::new();
    let mut weights = Vec::new();
    let mut submeshes = Vec::new();
    let mut max_morph_count: usize = 0;
    let mut primitive_morphs: Vec<(usize, Vec<MorphTargetData>)> = Vec::new();
    let joint_maps = skin_joint_maps(document);

    for mesh in document.meshes() {
        let joint_map = joint_maps.get(&mesh.index());
        for primitive in mesh.primitives() {
            let reader = primitive.reader(|buffer| Some(&buffers[buffer.index()]));
            let vertex_offset = vertices.len() as u32;

            // 朝向对齐：xz_sign = -1 时 X-flip + Z-flip（等价于绕 Y 轴旋转 180°），+1 时保持原样
            let positions: Vec<Vec3> = reader
                .read_positions()
                .map(|iter| iter.map(|p| Vec3::new(xz_sign * p[0], p[1], xz_sign * p[2])).collect())
                .unwrap_or_default();
            let vertex_count = positions.len();

            // NORMAL — 同样 X-flip + Z-flip
            let normals: Vec<Vec3> = reader
                .read_normals()
                .map(|iter| iter.map(|n| Vec3::new(xz_sign * n[0], n[1], xz_sign * n[2])).collect())
                .unwrap_or_else(|| vec![Vec3::new(0.0, 0.0, 1.0); vertex_count]);

            // TEXCOORD_0 → RuntimeVertex.uv（V 轴翻转：glTF V=0 在顶部，纹理加载时做了 flip_vertical 需要匹配）
            let uvs: Vec<Vec2> = reader
                .read_tex_coords(0)
                .map(|tc| {
                    tc.into_f32()
                        .map(|uv| Vec2::new(uv[0], 1.0 - uv[1]))
                        .collect()
                })
                .unwrap_or_else(|| vec![Vec2::ZERO; vertex_count]);

            for i in 0..vertex_count {
                vertices.push(RuntimeVertex {
                    position: positions[i],
                    normal: normals[i],
                    uv: uvs[i],
                });
            }

            // JOINTS_0 + WEIGHTS_0 → VertexWeight::Bdef4
            let joints: Option<Vec<[u16; 4]>> =
                reader.read_joints(0).map(|j| j.into_u16().collect());
            let weight_values: Option<Vec<[f32; 4]>> =
                reader.read_weights(0).map(|w| w.into_f32().collect());

            match (joints, weight_values) {
                (Some(mut j), Some(w)) => {
                    if let Some(map) = joint_map {
                        for joints in &mut j {
                            for joint in joints.iter_mut() {
                                *joint = map.get(*joint as usize).copied().unwrap_or(0);
                            }
                        }
                    }
                    for i in 0..vertex_count {
                        let offset = VIRTUAL_BONE_COUNT as i32;
                        weights.push(VertexWeight::Bdef4 {
                            bones: [
                                j[i][0] as i32 + offset,
                                j[i][1] as i32 + offset,
                                j[i][2] as i32 + offset,
                                j[i][3] as i32 + offset,
                            ],
                            weights: w[i],
                        });
                    }
                }
                _ => {
                    weights.extend(std::iter::repeat_n(VertexWeight::default(), vertex_count));
                }
            }

            // 索引数据 — X+Z 双翻转（偶数次）绕序不变
            let index_begin = indices.len() as u32;
            let index_count = match reader.read_indices() {
                Some(idx) => {
                    let raw: Vec<u32> = idx.into_u32().collect();
                    let count = raw.len() as u32;
                    for tri in raw.chunks(3) {
                        if tri.len() == 3 {
                            indices.push(tri[0] + vertex_offset);
                            indices.push(tri[1] + vertex_offset);
                            indices.push(tri[2] + vertex_offset);
                        }
                    }
                    count
                }
                None => 0,
            };

            // SubMesh 生成
            let material_id = primitive.material().index().unwrap_or(0);
            submeshes.push(SubMesh::new(index_begin, index_count, material_id as i32));

            // Morph targets — X-flip + Z-flip
            let prim_targets: Vec<MorphTargetData> = reader
                .read_morph_targets()
                .map(|(positions, normals, _tangents)| {
                    let position_offsets = positions
                        .map(|iter| iter.map(|p| Vec3::new(xz_sign * p[0], p[1], xz_sign * p[2])).collect())
                        .unwrap_or_else(|| vec![Vec3::ZERO; vertex_count]);
                    let normal_offsets = normals
                        .map(|iter| iter.map(|n| Vec3::new(xz_sign * n[0], n[1], xz_sign * n[2])).collect())
                        .unwrap_or_default();
                    MorphTargetData {
                        position_offsets,
                        normal_offsets,
                    }
                })
                .collect();
            max_morph_count = max_morph_count.max(prim_targets.len());
            primitive_morphs.push((vertex_offset as usize, prim_targets));
        }
    }

    let total_vertices = vertices.len();
    let morph_targets = merge_morph_targets(primitive_morphs, max_morph_count, total_vertices);

    Ok(MergedMesh {
        vertices,
        indices,
        weights,
        submeshes,
        morph_targets,
    })
}

/// 合并多个 primitive 的 morph targets，按 target 索引对齐
fn merge_morph_targets(
    primitive_morphs: Vec<(usize, Vec<MorphTargetData>)>,
    max_morph_count: usize,
    total_vertices: usize,
) -> Vec<MorphTargetData> {
    if max_morph_count == 0 {
        return Vec::new();
    }

    (0..max_morph_count)
        .map(|target_idx| {
            let mut position_offsets = vec![Vec3::ZERO; total_vertices];
            let mut normal_offsets = vec![Vec3::ZERO; total_vertices];

            for (vertex_offset, targets) in &primitive_morphs {
                if let Some(target) = targets.get(target_idx) {
                    for (i, &pos) in target.position_offsets.iter().enumerate() {
                        position_offsets[vertex_offset + i] = pos;
                    }
                    for (i, &norm) in target.normal_offsets.iter().enumerate() {
                        normal_offsets[vertex_offset + i] = norm;
                    }
                }
            }

            MorphTargetData {
                position_offsets,
                normal_offsets,
            }
        })
        .collect()
}

/// The skeleton is built from the first skin. Meshes skinned with another skin (a model whose
/// pieces were exported with skeletons of their own, like cards from earlier studio versions with
/// an imported outfit) index that skin's joint list: this maps it onto the first skin's, by node,
/// falling back to the nearest ancestor the first skin has. Returns mesh index → joint map, only
/// for meshes whose joints need remapping.
pub(crate) fn skin_joint_maps(document: &gltf::Document) -> HashMap<usize, Vec<u16>> {
    let mut out = HashMap::new();
    let Some(primary) = document.skins().next() else {
        return out;
    };
    let primary_joint: HashMap<usize, u16> = primary
        .joints()
        .enumerate()
        .map(|(i, node)| (node.index(), i as u16))
        .collect();
    let mut parent: HashMap<usize, usize> = HashMap::new();
    for node in document.nodes() {
        for child in node.children() {
            parent.insert(child.index(), node.index());
        }
    }
    for node in document.nodes() {
        let (Some(mesh), Some(skin)) = (node.mesh(), node.skin()) else {
            continue;
        };
        if skin.index() == primary.index() {
            continue;
        }
        let map: Vec<u16> = skin
            .joints()
            .map(|joint| {
                let mut current = Some(joint.index());
                while let Some(index) = current {
                    if let Some(&k) = primary_joint.get(&index) {
                        return k;
                    }
                    current = parent.get(&index).copied();
                }
                0
            })
            .collect();
        if map.iter().enumerate().all(|(i, &k)| k as usize == i) {
            continue;
        }
        out.insert(mesh.index(), map);
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn maps_other_skins_onto_the_first() {
        // Nodes: 0 root bone, 1 child bone, 2 extra bone under 1 (only in skin 1), 3/4 meshes.
        let json = r#"{
          "asset": {"version": "2.0"},
          "buffers": [{"byteLength": 36, "uri": "data:application/octet-stream;base64,AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}],
          "bufferViews": [{"buffer": 0, "byteLength": 36}],
          "accessors": [{"bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3", "min": [0,0,0], "max": [0,0,0]}],
          "meshes": [{"primitives": [{"attributes": {"POSITION": 0}}]}, {"primitives": [{"attributes": {"POSITION": 0}}]}],
          "skins": [{"joints": [0, 1]}, {"joints": [1, 2, 0]}],
          "nodes": [
            {"name": "root", "children": [1]}, {"name": "child", "children": [2]}, {"name": "extra"},
            {"mesh": 0, "skin": 0}, {"mesh": 1, "skin": 1}
          ],
          "scenes": [{"nodes": [0, 3, 4]}], "scene": 0
        }"#;
        let gltf = gltf::Gltf::from_slice(json.as_bytes()).expect("valid glTF");
        let maps = skin_joint_maps(&gltf.document);
        assert!(!maps.contains_key(&0), "the first skin needs no map");
        assert_eq!(maps.get(&1), Some(&vec![1, 1, 0]), "child→1, extra→its parent child (1), root→0");
    }
}
