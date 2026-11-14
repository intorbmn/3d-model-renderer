//! Models stand on the ground: their lowest point (the soles) ends up at y = 0 after loading, also
//! after a few frames of skinning. ILLGAMES characters exported with KKBP have their feet a few
//! centimetres below the file's origin. `MMD_TEST_GLB` points at such a model (or any other GLB).

use mmd_engine::model::load_vrm;

#[test]
fn feet_rest_on_the_ground() {
    let Some(path) = std::env::var_os("MMD_TEST_GLB") else {
        eprintln!("MMD_TEST_GLB not set; skipping");
        return;
    };
    let mut model = load_vrm(&path).expect("model loads");
    let lowest = |positions: &[glam::Vec3]| positions.iter().map(|p| p.y).fold(f32::INFINITY, f32::min);
    let rest = lowest(&model.update_positions);
    for _ in 0..3 {
        model.tick_animation(1.0 / 30.0);
    }
    let skinned = lowest(&model.update_positions);
    eprintln!("lowest point: rest {rest:.3}, skinned {skinned:.3} (model is about 20 units tall)");
    assert!(rest.abs() < 0.3, "the feet should touch y = 0, lowest point at {rest}");
    assert!((skinned - rest).abs() < 0.05, "skinning moved the model: {rest} -> {skinned}");
}
