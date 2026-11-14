//! Physics groups for glTF/VRM models from a `physics.json` next to the model file.
//!
//! The file is written by the mod when it extracts a model card made with 3D Model Renderer
//! Studio, and can be written by hand. It describes spring-bone chains grouped the way the studio
//! shows them (hair, clothing, body, custom) and the colliders they bump into. When present it
//! replaces the model's own VRM spring bones, which the studio imports as groups first.
//!
//! Lengths, offsets and the gravity direction are in the glTF file's own units and axes; offsets
//! are relative to the collider bone. Bones are named by their glTF node names.
//!
//! ```json
//! { "version": 1,
//!   "groups": [ { "name": "Front hair", "kind": "hair", "enabled": true,
//!                 "stiffness": 1.0, "drag": 0.4, "gravity": 0.0, "gravityDir": [0, -1, 0],
//!                 "radius": 0.02, "chains": [["hair_f1", "hair_f2", "hair_f3"]],
//!                 "colliders": ["head"] } ],
//!   "colliders": [ { "name": "head", "bone": "Head", "offset": [0, 0.08, 0], "radius": 0.1,
//!                    "tail": null } ] }
//! ```

use std::collections::HashMap;
use std::path::{Path, PathBuf};

use serde_json::Value;

use super::vrm_extensions::{
    ColliderShape, SpringBoneCollider, SpringBoneColliderGroup, SpringBoneData, SpringBoneJoint,
    SpringBoneSpring, SpringMode, SpringSettings,
};

/// Group kinds the mod knows; anything else is treated as `custom`.
pub const GROUP_KINDS: [&str; 4] = ["hair", "clothing", "body", "custom"];

/// A parsed group, kept for logging and per-kind switches.
#[derive(Debug, Clone, PartialEq)]
pub struct GroupInfo {
    pub name: String,
    pub kind: String,
    pub chains: usize,
}

/// Where the sidecar for `model_path` would be: `<stem>.physics.json`, then `physics.json`.
pub fn sidecar_path(model_path: &Path) -> Option<PathBuf> {
    let dir = model_path.parent()?;
    let stem = model_path.file_stem()?.to_string_lossy();
    [dir.join(format!("{stem}.physics.json")), dir.join("physics.json")]
        .into_iter()
        .find(|p| p.is_file())
}

/// Reads the sidecar. `node_by_name` maps glTF node names to node indices. Returns `None` when
/// there is no sidecar or it cannot be read (the model then keeps its own spring bones).
pub fn load(model_path: &Path, node_by_name: &HashMap<String, usize>) -> Option<(SpringBoneData, Vec<GroupInfo>)> {
    let path = sidecar_path(model_path)?;
    let text = match std::fs::read_to_string(&path) {
        Ok(text) => text,
        Err(e) => {
            log::warn!("physics.json unreadable ({}): {e}", path.display());
            return None;
        }
    };
    match serde_json::from_str::<Value>(&text) {
        Ok(json) => Some(parse(&json, node_by_name)),
        Err(e) => {
            log::warn!("physics.json is not valid JSON ({}): {e}", path.display());
            None
        }
    }
}

fn f32_of(value: Option<&Value>, default: f32) -> f32 {
    value.and_then(Value::as_f64).map(|v| v as f32).filter(|v| v.is_finite()).unwrap_or(default)
}

fn vec3_of(value: Option<&Value>, default: [f32; 3]) -> [f32; 3] {
    match value.and_then(Value::as_array) {
        Some(a) if a.len() >= 3 => [f32_of(a.get(0), default[0]), f32_of(a.get(1), default[1]), f32_of(a.get(2), default[2])],
        _ => default,
    }
}

pub fn parse(json: &Value, node_by_name: &HashMap<String, usize>) -> (SpringBoneData, Vec<GroupInfo>) {
    let mut data = SpringBoneData::default();
    let mut infos = Vec::new();

    // Each named collider becomes a one-collider group, so a spring group can list any subset.
    let mut collider_group_by_name = HashMap::new();
    for collider in json.get("colliders").and_then(Value::as_array).into_iter().flatten() {
        let name = collider.get("name").and_then(Value::as_str).unwrap_or("").to_string();
        let Some(&node) = collider.get("bone").and_then(Value::as_str).and_then(|b| node_by_name.get(b)) else {
            log::warn!("physics.json: collider '{name}' names an unknown bone");
            continue;
        };
        let offset = vec3_of(collider.get("offset"), [0.0; 3]);
        let radius = f32_of(collider.get("radius"), 0.0).max(0.0);
        let shape = match collider.get("tail").filter(|t| !t.is_null()) {
            Some(tail) => ColliderShape::Capsule { offset, tail: vec3_of(Some(tail), offset), radius },
            None => ColliderShape::Sphere { offset, radius },
        };
        let index = data.colliders.len();
        data.colliders.push(SpringBoneCollider { node, shape });
        collider_group_by_name.insert(name, data.collider_groups.len());
        data.collider_groups.push(SpringBoneColliderGroup { colliders: vec![index] });
    }

    for group in json.get("groups").and_then(Value::as_array).into_iter().flatten() {
        let name = group.get("name").and_then(Value::as_str).unwrap_or("group").to_string();
        let kind = group.get("kind").and_then(Value::as_str).unwrap_or("custom");
        let kind = if GROUP_KINDS.contains(&kind) { kind } else { "custom" }.to_string();
        if group.get("enabled").and_then(Value::as_bool) == Some(false) {
            continue;
        }
        let stiffness = f32_of(group.get("stiffness"), 1.0).clamp(0.0, 4.0);
        let drag_force = f32_of(group.get("drag"), 0.4).clamp(0.0, 1.0);
        let gravity_power = f32_of(group.get("gravity"), 0.0).clamp(0.0, 10.0);
        let gravity_dir = vec3_of(group.get("gravityDir"), [0.0, -1.0, 0.0]);
        let hit_radius = f32_of(group.get("radius"), 0.0).max(0.0);
        // Body groups (bust, hips) bounce around their rest pose; the rest swing like hair.
        let mode = match group.get("mode").and_then(Value::as_str) {
            Some("jiggle") => SpringMode::Jiggle,
            Some("spring") => SpringMode::Spring,
            _ if kind == "body" => SpringMode::Jiggle,
            _ => SpringMode::Spring,
        };
        let default_angle = match kind.as_str() {
            "body" => 25.0,
            "clothing" => 70.0,
            _ => 80.0,
        };
        let max_angle = f32_of(group.get("maxAngle"), default_angle).clamp(1.0, 180.0).to_radians();
        let settings = SpringSettings { mode, last_is_tail: true, max_angle: Some(max_angle) };
        let collider_groups: Vec<usize> = group
            .get("colliders")
            .and_then(Value::as_array)
            .into_iter()
            .flatten()
            .filter_map(|c| c.as_str().and_then(|n| collider_group_by_name.get(n)).copied())
            .collect();

        let mut chains = 0;
        for chain in group.get("chains").and_then(Value::as_array).into_iter().flatten() {
            let joints: Vec<SpringBoneJoint> = chain
                .as_array()
                .into_iter()
                .flatten()
                .filter_map(|bone| bone.as_str().and_then(|b| node_by_name.get(b)).copied())
                .map(|node| SpringBoneJoint { node, hit_radius, stiffness, gravity_power, gravity_dir, drag_force })
                .collect();
            if joints.len() < 2 {
                // A spring needs a bone and a child to swing towards.
                log::warn!("physics.json: a chain in '{name}' has fewer than two known bones");
                continue;
            }
            chains += 1;
            data.springs.push(SpringBoneSpring { joints, collider_groups: collider_groups.clone(), center: None, settings });
        }
        infos.push(GroupInfo { name, kind, chains });
    }
    (data, infos)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_groups_and_colliders() {
        let names: HashMap<String, usize> =
            [("Head", 1), ("hair1", 2), ("hair2", 3), ("hair3", 4), ("skirt1", 5), ("skirt2", 6)]
                .into_iter()
                .map(|(n, i)| (n.to_string(), i))
                .collect();
        let json: Value = serde_json::from_str(
            r#"{ "version": 1,
                 "groups": [
                   { "name": "Hair", "kind": "hair", "stiffness": 0.8, "drag": 0.3, "radius": 0.02,
                     "chains": [["hair1", "hair2", "hair3"], ["missing", "hair1"]], "colliders": ["head"] },
                   { "name": "Skirt", "kind": "clothing", "enabled": false, "chains": [["skirt1", "skirt2"]] },
                   { "name": "Tail", "kind": "wings?", "chains": [["skirt1", "skirt2"]] } ],
                 "colliders": [ { "name": "head", "bone": "Head", "offset": [0, 0.1, 0], "radius": 0.1 },
                                { "name": "nope", "bone": "Nope", "radius": 1 } ] }"#,
        )
        .unwrap();
        let (data, infos) = parse(&json, &names);
        assert_eq!(data.colliders.len(), 1);
        assert_eq!(data.springs.len(), 2, "hair chain + tail chain; the disabled skirt is skipped");
        assert_eq!(data.springs[0].joints.len(), 3);
        assert_eq!(data.springs[0].collider_groups, vec![0]);
        assert!((data.springs[0].joints[0].stiffness - 0.8).abs() < 1e-6);
        assert_eq!(infos[0], GroupInfo { name: "Hair".into(), kind: "hair".into(), chains: 1 });
        assert_eq!(infos[1].kind, "custom");
    }
}
