//! A `shape.json` next to a glTF model (what the mod writes for a card made with the studio's
//! character maker) reshapes the rest pose: scaled/moved bones and hidden parts.
//! Uses `MMD_TEST_GLB` (a skinned glTF/GLB with a bone named by `MMD_TEST_SHAPE_BONE`, default
//! "Head").

use mmd_engine::model::load_vrm;

fn degenerate_triangles(model: &mmd_engine::model::MmdModel) -> usize {
    model.indices.chunks(3).filter(|t| t[0] == t[1] && t[1] == t[2]).count()
}

#[test]
fn shape_sidecar_scales_bones_and_hides_parts() {
    let Some(source) = std::env::var_os("MMD_TEST_GLB") else {
        eprintln!("MMD_TEST_GLB not set; skipping");
        return;
    };
    let bone = std::env::var("MMD_TEST_SHAPE_BONE").unwrap_or_else(|_| "Head".into());
    let source = std::path::PathBuf::from(source);
    let dir = std::env::temp_dir().join(format!("card_shape_{}", std::process::id()));
    std::fs::create_dir_all(&dir).unwrap();
    let model_path = dir.join(source.file_name().unwrap());
    std::fs::copy(&source, &model_path).unwrap();

    let plain = load_vrm(&model_path).expect("load");
    let material = plain.get_material_name(0).unwrap_or_default().to_string();
    let sidecar = serde_json::json!({
        "version": 1,
        "bones": { bone.clone(): { "s": [1.5, 1.5, 1.5] } },
        "hidden": { "materials": [material], "meshes": [] }
    });
    std::fs::write(dir.join("shape.json"), sidecar.to_string()).unwrap();
    let shaped = load_vrm(&model_path).expect("load with shape");
    std::fs::remove_dir_all(&dir).ok();

    assert_eq!(plain.vertices.len(), shaped.vertices.len());
    let moved = plain
        .vertices
        .iter()
        .zip(&shaped.vertices)
        .filter(|(a, b)| a.position.distance(b.position) > 1e-4)
        .count();
    let span = |m: &mmd_engine::model::MmdModel| {
        let ys: Vec<f32> = m.vertices.iter().map(|v| v.position.y).collect();
        ys.iter().cloned().fold(f32::MIN, f32::max) - ys.iter().cloned().fold(f32::MAX, f32::min)
    };
    println!(
        "scaling '{bone}' by 1.5 moved {moved} of {} vertices; height {:.2} -> {:.2}; degenerate triangles {} -> {}",
        plain.vertices.len(),
        span(&plain),
        span(&shaped),
        degenerate_triangles(&plain),
        degenerate_triangles(&shaped)
    );
    assert!(moved > 0, "the bone edit moved no vertices");
    assert!(span(&shaped) > span(&plain), "a bigger head should make the model taller");
    assert!(degenerate_triangles(&shaped) > degenerate_triangles(&plain), "the hidden material is still drawn");
}
