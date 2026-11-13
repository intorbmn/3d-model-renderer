//! Loads a plain glTF/GLB (no VRM extension) through the VRM path.
//!
//! The model file is not part of the repository, so the test only runs when
//! `MMD_TEST_GLB` points at one: `MMD_TEST_GLB=/path/model.glb cargo test --release --test generic_gltf -- --nocapture`

use mmd_engine::model::load_vrm;

#[test]
fn generic_glb_loads_and_keeps_bind_pose() {
    let Some(path) = std::env::var_os("MMD_TEST_GLB") else {
        eprintln!("MMD_TEST_GLB not set; skipping");
        return;
    };

    let mut model = load_vrm(&path).expect("generic glTF should load without a VRM extension");
    assert!(model.vertex_count() > 0);
    assert!(model.index_count() > 0 && model.index_count() % 3 == 0);
    assert!(model.submesh_count() > 0);
    assert!(model.material_count() > 0);

    let bbox = |positions: &[glam::Vec3]| {
        positions.iter().fold(
            (glam::Vec3::splat(f32::INFINITY), glam::Vec3::splat(f32::NEG_INFINITY)),
            |(lo, hi), p| (lo.min(*p), hi.max(*p)),
        )
    };

    let (rest_lo, rest_hi) = bbox(&model.update_positions);
    let rest_height = rest_hi.y - rest_lo.y;
    eprintln!(
        "vertices={} tris={} submeshes={} materials={} bones={} textures={}",
        model.vertex_count(),
        model.index_count() / 3,
        model.submesh_count(),
        model.material_count(),
        model.bone_manager.bone_count(),
        model.texture_paths.len()
    );
    eprintln!("rest bbox lo={rest_lo:?} hi={rest_hi:?}");
    assert!((rest_height - 20.0).abs() < 0.01, "generic glTF is normalised to 20 units, got {rest_height}");

    // A plain glTF faces +Z, which is also what the engine expects, so it must not be turned around:
    // the toes of the test model point towards +Z.
    let foot_z = mean_z(&model.update_positions, rest_lo.y, rest_lo.y + rest_height * 0.035);
    let ankle_z = mean_z(&model.update_positions, rest_lo.y + rest_height * 0.06, rest_lo.y + rest_height * 0.12);
    eprintln!("foot z={foot_z:.3} ankle z={ankle_z:.3}");
    assert!(foot_z > ankle_z, "model faces -Z: toes at z={foot_z}, ankles at z={ankle_z}");

    // First person: without named head/eye bones the eye position is estimated from the mesh.
    let eye = model.get_eye_bone_animated_position();
    eprintln!("estimated eye={eye:?}");
    assert!(eye.y > rest_lo.y + rest_height * 0.85 && eye.y < rest_hi.y, "eye height out of range: {eye:?}");
    assert!(eye.x.abs() < rest_height * 0.05, "eye not centred: {eye:?}");
    model.set_first_person_mode(true);
    let hidden = (0..model.material_count()).filter(|&i| !model.is_material_visible(i)).count();
    eprintln!("materials hidden in first person: {hidden}/{}", model.material_count());
    assert!(hidden > 0, "first person should hide the head materials");
    assert!(hidden < model.material_count(), "first person must not hide the whole model");
    model.set_first_person_mode(false);

    // With no animation playing, skinning must leave the mesh where the file put it.
    for _ in 0..3 {
        model.tick_animation(1.0 / 30.0);
    }
    let (lo, hi) = bbox(&model.update_positions);
    eprintln!("skinned bbox lo={lo:?} hi={hi:?}");
    assert!(model.update_positions.iter().all(|p| p.is_finite()));
    assert!((lo - rest_lo).abs().max_element() < 0.05, "bind pose moved: {lo:?} vs {rest_lo:?}");
    assert!((hi - rest_hi).abs().max_element() < 0.05, "bind pose moved: {hi:?} vs {rest_hi:?}");
}

fn mean_z(positions: &[glam::Vec3], y_min: f32, y_max: f32) -> f32 {
    let band: Vec<f32> = positions
        .iter()
        .filter(|p| p.y >= y_min && p.y <= y_max)
        .map(|p| p.z)
        .collect();
    assert!(!band.is_empty(), "no vertices between y={y_min} and y={y_max}");
    band.iter().sum::<f32>() / band.len() as f32
}
