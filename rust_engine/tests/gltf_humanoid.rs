//! Prints the humanoid mapping recognised for a plain glTF/GLB given via `MMD_TEST_GLB`, and checks
//! that a recognised model gets the MMD bones VMD motions drive.

use mmd_engine::model::{describe_gltf_humanoid, load_vrm};

#[test]
fn recognised_humanoid_gets_mmd_bones() {
    let Some(path) = std::env::var_os("MMD_TEST_GLB") else {
        eprintln!("MMD_TEST_GLB not set; skipping");
        return;
    };

    let mapping = describe_gltf_humanoid(&path).expect("glTF should open");
    for (humanoid, bone) in &mapping {
        println!("MAP\t{humanoid}\t{bone}");
    }
    if mapping.is_empty() {
        eprintln!("rig not recognised as a humanoid");
        return;
    }

    let model = load_vrm(&path).expect("model should load");
    for name in ["全ての親", "センター", "下半身", "上半身", "頭", "左腕", "右ひじ", "左足", "右ひざ", "左足首", "左足ＩＫ", "右足ＩＫ"] {
        assert!(model.bone_manager.find_bone_by_name(name).is_some(), "missing MMD bone {name}");
    }

    // Held items: in the rest pose the hand frame must be unrotated, as it is for a PMX model, even
    // though the loader turned the T-pose arms down with rest rotations.
    let mut model = model;
    model.tick_animation(0.0);
    for (side, hand) in [("right", model.get_right_hand_matrix()), ("left", model.get_left_hand_matrix())] {
        let (_, rotation, _) = hand.to_scale_rotation_translation();
        let angle = rotation.angle_between(glam::Quat::IDENTITY).to_degrees();
        println!("HAND\t{side}\trest rotation {angle:.2} deg");
        assert!(angle < 1.0, "{side} hand frame is rotated {angle} deg in the rest pose");
    }

    // Play the bundled walk motion: a recognised humanoid must actually move, and stay sane.
    let rest: Vec<glam::Vec3> = model.update_positions.clone();
    let walk = concat!(env!("CARGO_MANIFEST_DIR"), "/../common/src/main/resources/assets/mmdskin/default_anim/walk.vmd");
    let animation = std::sync::Arc::new(mmd_engine::animation::VmdAnimation::load(walk).expect("walk.vmd"));
    model.set_layer_animation(0, Some(animation));
    model.play_layer(0);
    let (mut moved_max, mut lowest, mut highest) = (0.0f32, f32::INFINITY, f32::NEG_INFINITY);
    for _ in 0..20 {
        model.tick_animation(1.0 / 30.0);
        assert!(model.update_positions.iter().all(|p| p.is_finite()), "non-finite vertex while animating");
        for (now, before) in model.update_positions.iter().zip(&rest) {
            moved_max = moved_max.max((*now - *before).length());
        }
        for p in &model.update_positions {
            lowest = lowest.min(p.y);
            highest = highest.max(p.y);
        }
    }
    // Optional: dump the animated vertices (x y z per line) for a visual check.
    if let Some(dump) = std::env::var_os("MMD_TEST_DUMP") {
        let text: String = model.update_positions.iter().map(|p| format!("{} {} {}\n", p.x, p.y, p.z)).collect();
        std::fs::write(dump, text).expect("write vertex dump");
    }
    println!("ANIM\tmax vertex displacement={moved_max:.2}\tlowest y={lowest:.2}\thighest y={highest:.2}");
    assert!(moved_max > 1.0, "the walk motion did not move the model (max displacement {moved_max})");
    assert!(lowest > -3.0, "the model sank through the ground: lowest y={lowest}");
    assert!(highest < 30.0, "the model blew up: highest y={highest}");
}
