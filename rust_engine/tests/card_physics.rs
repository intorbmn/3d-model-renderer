//! A `physics.json` next to a glTF model (what the mod writes for a card) gives it spring bones
//! that react to the model moving through the world. Uses `MMD_TEST_GLB` (a skinned glTF/GLB).

use std::collections::HashMap;

use mmd_engine::model::load_vrm;

fn gltf_joints(path: &std::path::Path) -> Vec<(String, Option<String>)> {
    let gltf = gltf::Gltf::open(path).expect("open glTF");
    let mut parent = HashMap::new();
    for node in gltf.document.nodes() {
        for child in node.children() {
            parent.insert(child.index(), node.name().unwrap_or("").to_string());
        }
    }
    let skin = gltf.document.skins().next().expect("skin");
    skin.joints()
        .map(|j| (j.name().unwrap_or("").to_string(), parent.get(&j.index()).cloned()))
        .collect()
}

#[test]
fn sidecar_springs_swing_when_the_model_moves() {
    let Some(source) = std::env::var_os("MMD_TEST_GLB") else {
        eprintln!("MMD_TEST_GLB not set; skipping");
        return;
    };
    let source = std::path::PathBuf::from(source);
    let joints = gltf_joints(&source);
    // A hair chain: a hair joint whose parent is also a hair joint, plus that parent.
    let Some((child, Some(parent))) = joints
        .iter()
        .find(|(name, parent)| {
            name.to_lowercase().contains("hair")
                && parent.as_ref().is_some_and(|p| p.to_lowercase().contains("hair"))
        })
        .cloned()
    else {
        eprintln!("no hair chain in this model; skipping");
        return;
    };

    let dir = std::env::temp_dir().join(format!("card_physics_{}", std::process::id()));
    std::fs::create_dir_all(&dir).unwrap();
    let model_path = dir.join(source.file_name().unwrap());
    std::fs::copy(&source, &model_path).unwrap();
    let sidecar = serde_json::json!({
        "version": 1,
        "groups": [{ "name": "Hair", "kind": "hair", "stiffness": 1.0, "drag": 0.4,
                     "gravity": 0.0, "radius": 0.0, "chains": [[parent, child]] }],
        "colliders": []
    });
    std::fs::write(dir.join("physics.json"), sidecar.to_string()).unwrap();

    let run = |moving: bool| {
        let mut model = load_vrm(&model_path).expect("load");
        let bone = model.bone_manager.find_bone_by_name(&parent).expect("hair bone");
        for i in 0..40 {
            let z = if moving { i as f32 * 1.0 } else { 0.0 };
            model.set_model_position_and_yaw(0.0, 0.0, z, 0.0);
            model.tick_animation(1.0 / 60.0);
        }
        model.bone_manager.get_global_transform(bone)
    };
    let still = run(false);
    let moving = run(true);
    let (_, r_still, _) = still.to_scale_rotation_translation();
    let (_, r_moving, _) = moving.to_scale_rotation_translation();
    let angle = r_still.angle_between(r_moving).to_degrees();
    println!("hair '{parent}' swings {angle:.2} deg when moving at 60 units/s");
    std::fs::remove_dir_all(&dir).ok();
    assert!(angle > 1.0, "the spring chain did not react to the model moving");
}
