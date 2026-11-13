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
