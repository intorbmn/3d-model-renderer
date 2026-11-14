//! VRM 模型加载入口

use glam::{Mat4, Quat, Vec2, Vec3};
use std::collections::HashMap;
use std::path::Path;

use super::runtime::MmdModel;
use super::{vrm_extensions, vrm_material, vrm_mesh, vrm_morph, vrm_skeleton};
use crate::{MmdError, Result};

pub struct LoadedVrmModel {
    pub model: MmdModel,
    pub extensions: vrm_extensions::VrmExtensions,
}

/// The rest-pose world rotation of every node: from the inverse bind matrices for joints (the pose
/// the skeleton is built from), from the node hierarchy for the rest.
fn node_rest_rotations(
    document: &gltf::Document,
    skin: &gltf::Skin,
    buffers: &[gltf::buffer::Data],
) -> HashMap<usize, Quat> {
    let mut rotations: HashMap<usize, Quat> = vrm_skeleton::compute_node_world_transforms(document)
        .into_iter()
        .map(|(node, m)| (node, m.to_scale_rotation_translation().1))
        .collect();
    let reader = skin.reader(|buffer| Some(&buffers[buffer.index()]));
    if let Some(ibms) = reader.read_inverse_bind_matrices() {
        for (joint, ibm) in skin.joints().zip(ibms) {
            let bind = Mat4::from_cols_array_2d(&ibm).inverse();
            let rotation = bind.to_scale_rotation_translation().1;
            if rotation.is_finite() {
                rotations.insert(joint.index(), rotation);
            }
        }
    }
    rotations
}

/// Brings spring-bone data into the skeleton's space: scaled to model units, with the X/Z flip
/// the mesh got, and collider offsets turned from the collider bone's own axes into model axes
/// (the runtime skeleton's bones are unrotated in the rest pose, like PMX bones).
fn convert_spring_space(
    data: &mut vrm_extensions::SpringBoneData,
    rest_rotations: &HashMap<usize, Quat>,
    unit_scale: f32,
    xz_sign: f32,
) {
    let flip = |v: Vec3| Vec3::new(xz_sign * v.x, v.y, xz_sign * v.z);
    for collider in &mut data.colliders {
        let rotation = rest_rotations.get(&collider.node).copied().unwrap_or(Quat::IDENTITY);
        let convert = |v: [f32; 3]| (flip(rotation * Vec3::from_array(v)) * unit_scale).to_array();
        collider.shape = match collider.shape {
            vrm_extensions::ColliderShape::Sphere { offset, radius } => {
                vrm_extensions::ColliderShape::Sphere { offset: convert(offset), radius: radius * unit_scale }
            }
            vrm_extensions::ColliderShape::Capsule { offset, tail, radius } => vrm_extensions::ColliderShape::Capsule {
                offset: convert(offset),
                tail: convert(tail),
                radius: radius * unit_scale,
            },
        };
    }
    for spring in &mut data.springs {
        for joint in &mut spring.joints {
            // UniVRM moves the tail by stiffness and gravity in metres per second, tuned for a
            // human-sized model. Every model here is human-sized in model units (VRM by its metres,
            // plain glTF normalised by height), so these scale by the metre, not by the file unit.
            joint.hit_radius *= unit_scale;
            joint.stiffness *= vrm_skeleton::VRM_TO_PMX_SCALE;
            joint.gravity_power *= vrm_skeleton::VRM_TO_PMX_SCALE;
            joint.gravity_dir = flip(Vec3::from_array(joint.gravity_dir)).normalize_or_zero().to_array();
        }
    }
}

fn remap_extensions_to_runtime_bones(
    mut extensions: vrm_extensions::VrmExtensions,
    skin: &gltf::Skin,
) -> vrm_extensions::VrmExtensions {
    let node_to_bone: HashMap<usize, usize> = skin
        .joints()
        .enumerate()
        .map(|(joint_index, node)| (node.index(), joint_index + vrm_skeleton::VIRTUAL_BONE_COUNT))
        .collect();

    extensions.first_person.bone = extensions
        .first_person
        .bone
        .and_then(|node_index| node_to_bone.get(&node_index).copied());

    extensions.node_constraints = extensions
        .node_constraints
        .into_iter()
        .filter_map(|constraint| {
            let target = node_to_bone.get(&constraint.target).copied();
            let source = match &constraint.kind {
                vrm_extensions::NodeConstraintKind::Roll { source, .. }
                | vrm_extensions::NodeConstraintKind::Aim { source, .. }
                | vrm_extensions::NodeConstraintKind::Rotation { source, .. } => {
                    node_to_bone.get(source).copied()
                }
            };

            match (target, source) {
                (Some(target), Some(source)) => Some(vrm_extensions::NodeConstraint {
                    target,
                    kind: match constraint.kind {
                        vrm_extensions::NodeConstraintKind::Roll {
                            roll_axis, weight, ..
                        } => vrm_extensions::NodeConstraintKind::Roll {
                            source,
                            roll_axis,
                            weight,
                        },
                        vrm_extensions::NodeConstraintKind::Aim {
                            aim_axis, weight, ..
                        } => vrm_extensions::NodeConstraintKind::Aim {
                            source,
                            aim_axis,
                            weight,
                        },
                        vrm_extensions::NodeConstraintKind::Rotation { weight, .. } => {
                            vrm_extensions::NodeConstraintKind::Rotation { source, weight }
                        }
                    },
                }),
                _ => {
                    log::warn!(
                        "VRM constraint dropped because source/target node is not part of the skin"
                    );
                    None
                }
            }
        })
        .collect();

    let mut old_to_new_collider = HashMap::new();
    let mut remapped_colliders = Vec::new();
    for (old_index, collider) in extensions.spring_bones.colliders.into_iter().enumerate() {
        if let Some(node) = node_to_bone.get(&collider.node).copied() {
            old_to_new_collider.insert(old_index, remapped_colliders.len());
            remapped_colliders.push(vrm_extensions::SpringBoneCollider { node, ..collider });
        } else {
            log::warn!(
                "VRM spring-bone collider dropped because collider node is not part of the skin"
            );
        }
    }

    let remapped_groups = extensions
        .spring_bones
        .collider_groups
        .into_iter()
        .map(|group| vrm_extensions::SpringBoneColliderGroup {
            colliders: group
                .colliders
                .into_iter()
                .filter_map(|index| old_to_new_collider.get(&index).copied())
                .collect(),
        })
        .collect::<Vec<_>>();

    let remapped_springs = extensions
        .spring_bones
        .springs
        .into_iter()
        .filter_map(|spring| {
            let joints = spring
                .joints
                .into_iter()
                .filter_map(|joint| {
                    node_to_bone
                        .get(&joint.node)
                        .copied()
                        .map(|node| vrm_extensions::SpringBoneJoint { node, ..joint })
                })
                .collect::<Vec<_>>();

            if joints.is_empty() {
                log::warn!(
                    "VRM spring-bone chain dropped because no joints could be mapped onto the skin"
                );
                return None;
            }

            Some(vrm_extensions::SpringBoneSpring {
                joints,
                collider_groups: spring
                    .collider_groups
                    .into_iter()
                    .filter(|index| *index < remapped_groups.len())
                    .collect(),
                center: spring
                    .center
                    .and_then(|center| node_to_bone.get(&center).copied()),
            })
        })
        .collect();

    extensions.spring_bones = vrm_extensions::SpringBoneData {
        springs: remapped_springs,
        colliders: remapped_colliders,
        collider_groups: remapped_groups,
    };

    extensions
}

/// 从 gltf::import 已解码的图片数据写入 PNG 临时文件，返回绝对路径数组
fn extract_textures(model_dir: &Path, images: &[gltf::image::Data]) -> Vec<String> {
    let mut paths = Vec::new();

    for (i, img_data) in images.iter().enumerate() {
        let filename = format!(".vrm_tex_{}.png", i);
        let full_path = model_dir.join(&filename);

        let result = (|| -> std::result::Result<(), Box<dyn std::error::Error>> {
            let (color_type, pixels) = match img_data.format {
                gltf::image::Format::R8G8B8 => (image::ColorType::Rgb8, &img_data.pixels[..]),
                gltf::image::Format::R8G8B8A8 => (image::ColorType::Rgba8, &img_data.pixels[..]),
                gltf::image::Format::R8 => (image::ColorType::L8, &img_data.pixels[..]),
                gltf::image::Format::R8G8 => (image::ColorType::La8, &img_data.pixels[..]),
                _ => {
                    return Err(format!("不支持的图片格式: {:?}", img_data.format).into());
                }
            };
            image::save_buffer(
                &full_path,
                pixels,
                img_data.width,
                img_data.height,
                color_type,
            )?;
            Ok(())
        })();

        match result {
            Ok(_) => {
                log::debug!(
                    "纹理提取: {} ({}x{})",
                    filename,
                    img_data.width,
                    img_data.height
                );
                paths.push(full_path.to_string_lossy().replace('\\', "/"));
            }
            Err(e) => {
                log::warn!("纹理写入失败 {}: {}", filename, e);
                paths.push(String::new());
            }
        }
    }

    paths
}

/// Sign applied to X and Z when importing, so that every model faces +Z as the engine expects
/// (PMX is converted to the same convention by negating Z).
///
/// VRM files keep the half-turn the loader has always applied to them. A plain glTF follows the
/// glTF convention that the front of an asset faces +Z, so it is imported as it is; turning it too
/// made such models stand back to front.
fn facing_sign(is_generic_gltf: bool) -> f32 {
    if is_generic_gltf {
        1.0
    } else {
        -1.0
    }
}

/// 通用 glTF 的目标身高（PMX 单位）。常见 MMD 模型约 20 单位高（≈1.6 m）。
const GENERIC_TARGET_HEIGHT: f32 = 20.0;

/// 按顶点包围盒高度计算通用 glTF 的单位缩放；高度异常时退回 VRM 的米制缩放。
fn generic_unit_scale(vertices: &[super::RuntimeVertex]) -> f32 {
    let (min_y, max_y) = vertices
        .iter()
        .fold((f32::INFINITY, f32::NEG_INFINITY), |(lo, hi), v| {
            (lo.min(v.position.y), hi.max(v.position.y))
        });
    let height = max_y - min_y;
    if height.is_finite() && height > 1.0e-4 {
        GENERIC_TARGET_HEIGHT / height
    } else {
        vrm_skeleton::VRM_TO_PMX_SCALE
    }
}

/// 加载 VRM 模型，转换为 MmdModel 复用现有渲染管线
pub fn load_vrm<P: AsRef<Path>>(path: P) -> Result<MmdModel> {
    load_vrm_with_extensions(path).map(|loaded| loaded.model)
}

/// Reports which bones of a plain glTF/GLB would be treated as which VRM humanoid bones, as
/// `(humanoid name, bone name in the file)` pairs sorted by humanoid name. Empty when the rig is not
/// recognised. Lets tools show (and tests check) the mapping without loading the whole model.
pub fn describe_gltf_humanoid<P: AsRef<Path>>(path: P) -> Result<Vec<(String, String)>> {
    let path = path.as_ref();
    let gltf = gltf::Gltf::open(path).map_err(|e| MmdError::VrmParse(format!("glTF 加载失败: {}", e)))?;
    let Some(skin) = gltf.document.skins().next() else {
        return Ok(Vec::new());
    };
    let joints: Vec<(usize, String)> = skin
        .joints()
        .map(|node| (node.index(), node.name().unwrap_or("").to_string()))
        .collect();
    let mapping = super::humanoid_inference::resolve_humanoid(path, &joints);
    let mut described: Vec<(String, String)> = joints
        .into_iter()
        .filter_map(|(node, bone)| mapping.get(&node).map(|humanoid| (humanoid.clone(), bone)))
        .collect();
    described.sort();
    Ok(described)
}

/// Loads a VRM model and returns both the runtime model and parsed VRM metadata.
pub fn load_vrm_with_extensions<P: AsRef<Path>>(path: P) -> Result<LoadedVrmModel> {
    let path = path.as_ref();
    let model_dir = path.parent().unwrap_or_else(|| Path::new("."));

    let (document, mut buffers, images) =
        gltf::import(path).map_err(|e| MmdError::VrmParse(format!("glTF 加载失败: {}", e)))?;

    // A plain glTF/GLB (no VRM extension) is loaded through the same path with empty VRM metadata.
    let is_generic_gltf = !vrm_extensions::has_vrm_extension(&document);
    let vrm_ext = if is_generic_gltf {
        log::info!("glTF 不含 VRM 扩展，按通用 glTF 模型加载: {}", path.display());
        vrm_extensions::VrmExtensions::generic(document.materials().len())
    } else {
        vrm_extensions::parse_vrm_extensions(&document)?
    };
    let xz_sign = facing_sign(is_generic_gltf);
    let mut mesh = vrm_mesh::merge_meshes(&document, &buffers, xz_sign)?;
    if mesh.vertices.is_empty() || mesh.indices.is_empty() {
        return Err(MmdError::VrmParse("glTF 不包含可渲染的网格".into()));
    }

    // VRM 使用米为单位，PMX 使用 1 单位 ≈ 0.08 米，需要缩放到 PMX 单位空间。
    // 通用 glTF 没有约定单位（例如从 PMX 转换并缩放过的模型），按包围盒高度归一化到常见 MMD 身高。
    let unit_scale = if is_generic_gltf {
        generic_unit_scale(&mesh.vertices)
    } else {
        vrm_skeleton::VRM_TO_PMX_SCALE
    };
    // Character-maker edits from a card (shape.json): baked into the rest pose. The unit scale stays
    // the one of the unedited model, so a bigger head makes a taller character instead of a smaller one.
    if let Some(edit) = super::shape_sidecar::load(path).filter(|e| !e.is_empty()) {
        let moved = super::shape_sidecar::apply(&document, &mut buffers, &edit);
        log::info!(
            "shape.json: {} bone edit(s), {} hidden material(s), {} vertices moved",
            edit.bones.len(),
            edit.hidden_materials.len(),
            moved
        );
        mesh = vrm_mesh::merge_meshes(&document, &buffers, xz_sign)?;
    }
    for v in &mut mesh.vertices {
        v.position *= unit_scale;
    }
    for target in &mut mesh.morph_targets {
        for offset in &mut target.position_offsets {
            *offset *= unit_scale;
        }
    }

    let skin = document
        .skins()
        .next()
        .ok_or_else(|| MmdError::VrmParse("glTF/VRM 缺少 skin 数据（仅支持带骨骼蒙皮的模型）".into()))?;
    // A plain glTF has no humanoid description; recover one from bones.json or the bone names so
    // that VMD motions (which drive MMD bone names) can animate it.
    let mut vrm_ext = vrm_ext;
    if is_generic_gltf {
        let joints: Vec<(usize, String)> = skin
            .joints()
            .map(|node| (node.index(), node.name().unwrap_or("").to_string()))
            .collect();
        vrm_ext.humanoid.map = super::humanoid_inference::resolve_humanoid(path, &joints);
    }
    // Physics groups from a card (physics.json) replace the model's own spring bones.
    let node_by_name: HashMap<String, usize> = document
        .nodes()
        .filter_map(|node| node.name().map(|name| (name.to_string(), node.index())))
        .collect();
    if let Some((springs, groups)) = super::physics_sidecar::load(path, &node_by_name) {
        for group in &groups {
            log::info!("physics group '{}' ({}): {} chain(s)", group.name, group.kind, group.chains);
        }
        vrm_ext.spring_bones = springs;
    }
    // Spring-bone data is in the file's units and axes; the skeleton is not.
    convert_spring_space(
        &mut vrm_ext.spring_bones,
        &node_rest_rotations(&document, &skin, &buffers),
        unit_scale,
        xz_sign,
    );

    let bone_manager =
        vrm_skeleton::build_skeleton(
            &document,
            &skin,
            &buffers,
            &vrm_ext.humanoid,
            unit_scale,
            xz_sign,
        )?;
    let vrm_ext = remap_extensions_to_runtime_bones(vrm_ext, &skin);

    let (materials, _placeholder_paths) = vrm_material::convert_materials(&document, &vrm_ext);

    let texture_paths = extract_textures(model_dir, &images);

    let morph_manager = vrm_morph::convert_morph_targets(
        &mesh.morph_targets,
        &vrm_ext.expressions,
        mesh.vertices.len(),
        materials.len(),
    );

    let name = path
        .file_stem()
        .and_then(|s| s.to_str())
        .unwrap_or("vrm_model")
        .to_string();

    let update_positions: Vec<Vec3> = mesh.vertices.iter().map(|v| v.position).collect();
    let update_normals: Vec<Vec3> = mesh.vertices.iter().map(|v| v.normal).collect();
    let update_uvs: Vec<Vec2> = mesh.vertices.iter().map(|v| v.uv).collect();

    let mut model = MmdModel::new();
    model.name = name;
    model.set_vrm(true);
    model.vertices = mesh.vertices;
    model.indices = mesh.indices;
    model.weights = mesh.weights;
    model.materials = materials;
    model.submeshes = mesh.submeshes;
    model.texture_paths = texture_paths;
    model.rigid_bodies = Vec::new();
    model.joints = Vec::new();
    model.update_positions = update_positions;
    model.update_normals = update_normals;
    model.update_uvs = update_uvs;
    model.bone_manager = bone_manager;
    model.morph_manager = morph_manager;

    model
        .morph_manager
        .set_material_count(model.materials.len());
    model.morph_manager.set_vertex_count(model.vertices.len());
    model.init_material_visibility();
    model.initialize_animation();
    model.tick_animation(0.0);
    model.initialize_vrm_runtime(vrm_ext.clone());

    log::info!(
        "VRM 加载完成: {} (顶点:{}, 面:{}, 材质:{}, 骨骼:{}, 纹理:{})",
        model.name,
        model.vertex_count(),
        model.index_count() / 3,
        model.material_count(),
        model.bone_manager.bone_count(),
        model.texture_paths.len(),
    );

    // Held items follow the hand frame. A PMX hand bone is unrotated in the rest pose; glTF joints
    // carry rest rotations (and the arms were turned down above), so measure them once and take
    // them out of the hand matrix.
    model.capture_hand_rest_frames();

    Ok(LoadedVrmModel {
        model,
        extensions: vrm_ext,
    })
}
