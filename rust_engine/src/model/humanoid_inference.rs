//! Humanoid bone detection for plain glTF models.
//!
//! VRM files say which node is the hips, the left upper arm and so on. A plain glTF/GLB does not, so
//! without help its bones keep their file names, nothing matches the MMD bone names that VMD motions
//! drive, and the model stands still. This module recovers the same node → VRM humanoid name mapping
//! from two sources, in order:
//!
//! 1. a `bones.json` sidecar next to the model (`<model file stem>.bones.json` or `bones.json`),
//!    mapping VRM humanoid bone names to bone names in the file, for rigs the heuristics get wrong;
//! 2. the bone names themselves, for the common conventions: VRM/Unity (`LeftUpperLeg`), VRoid
//!    (`J_Bip_L_UpperLeg`), Mixamo (`mixamorig:LeftUpLeg`), Cats Blender Plugin (`Left leg`), Blender
//!    (`upper_leg.L`), Unreal (`thigh_l`) and English-named MMD exports (`72.joint_LeftHipD`).
//!
//! The result is only used when the core body bones are all present; a partial match would animate
//! half a body.

use std::collections::HashMap;
use std::path::Path;

/// Humanoid bones a model must have before its mapping is trusted.
const REQUIRED: &[&str] = &[
    "hips",
    "spine",
    "head",
    "leftUpperArm",
    "leftLowerArm",
    "leftHand",
    "rightUpperArm",
    "rightLowerArm",
    "rightHand",
    "leftUpperLeg",
    "leftLowerLeg",
    "leftFoot",
    "rightUpperLeg",
    "rightLowerLeg",
    "rightFoot",
];

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
enum Side {
    Center,
    Left,
    Right,
}

/// Returns node index → VRM humanoid bone name for the given `(node index, bone name)` joints, or an
/// empty map when the model cannot be recognised as a humanoid.
pub(crate) fn resolve_humanoid(model_path: &Path, joints: &[(usize, String)]) -> HashMap<usize, String> {
    if let Some(mapping) = load_sidecar(model_path, joints) {
        if is_complete(&mapping) {
            log::info!("humanoid mapping: {} bones from bones.json", mapping.len());
            return mapping;
        }
        log::warn!("bones.json does not name every required humanoid bone; falling back to bone names");
    }

    let mapping = infer_from_names(joints);
    if is_complete(&mapping) {
        log::info!("humanoid mapping: {} bones recognised from bone names", mapping.len());
        mapping
    } else {
        log::info!(
            "humanoid mapping: bone names not recognised ({} matched); the model will not be animated",
            mapping.len()
        );
        HashMap::new()
    }
}

fn is_complete(mapping: &HashMap<usize, String>) -> bool {
    REQUIRED
        .iter()
        .all(|required| mapping.values().any(|name| name == required))
}

/// Reads `<stem>.bones.json` or `bones.json` from the model's directory:
/// `{ "hips": "Bone name in the file", "leftUpperLeg": "...", ... }`.
fn load_sidecar(model_path: &Path, joints: &[(usize, String)]) -> Option<HashMap<usize, String>> {
    let dir = model_path.parent()?;
    let stem = model_path.file_stem()?.to_string_lossy();
    let candidates = [dir.join(format!("{stem}.bones.json")), dir.join("bones.json")];
    let path = candidates.iter().find(|candidate| candidate.is_file())?;

    let text = match std::fs::read_to_string(path) {
        Ok(text) => text,
        Err(error) => {
            log::warn!("cannot read {}: {}", path.display(), error);
            return None;
        }
    };
    let parsed: HashMap<String, String> = match serde_json::from_str(&text) {
        Ok(parsed) => parsed,
        Err(error) => {
            log::warn!("{} is not a JSON object of strings: {}", path.display(), error);
            return None;
        }
    };

    let mut mapping = HashMap::new();
    for (humanoid_name, bone_name) in parsed {
        match joints.iter().find(|(_, name)| *name == bone_name) {
            Some((node, _)) => {
                mapping.insert(*node, humanoid_name);
            }
            None => log::warn!(
                "{}: bone '{}' (for '{}') is not in the model",
                path.display(),
                bone_name,
                humanoid_name
            ),
        }
    }
    Some(mapping)
}

/// Splits a bone name into its side and a normalised core: lower-case ASCII letters and digits only,
/// with rig prefixes and the side marker removed. `"mixamorig:LeftUpLeg"` → `(Left, "upleg")`.
fn normalise(raw: &str) -> (Side, String) {
    // Tokens: runs of letters/digits, additionally split at lower→upper case changes.
    let mut tokens: Vec<String> = Vec::new();
    let mut current = String::new();
    let mut previous_lower = false;
    for ch in raw.chars() {
        if ch.is_ascii_alphanumeric() {
            if ch.is_ascii_uppercase() && previous_lower && !current.is_empty() {
                tokens.push(std::mem::take(&mut current));
            }
            previous_lower = ch.is_ascii_lowercase();
            current.push(ch.to_ascii_lowercase());
        } else {
            previous_lower = false;
            if !current.is_empty() {
                tokens.push(std::mem::take(&mut current));
            }
        }
    }
    if !current.is_empty() {
        tokens.push(current);
    }

    // A leading index ("72.joint_LeftHipD") and rig prefixes carry no meaning.
    if tokens.len() > 1 && tokens[0].chars().all(|c| c.is_ascii_digit()) {
        tokens.remove(0);
    }
    tokens.retain(|token| !matches!(token.as_str(), "joint" | "mixamorig" | "bip" | "bip01" | "bip001" | "j" | "def" | "c"));

    let mut side = Side::Center;
    let mut core = String::new();
    for token in &tokens {
        match token.as_str() {
            "left" | "l" => side = Side::Left,
            "right" | "r" => side = Side::Right,
            other => core.push_str(other),
        }
    }

    // "LeftArm" without a case change inside a token ("leftarm"), or "Lefthand".
    if side == Side::Center {
        if let Some(rest) = core.strip_prefix("left") {
            side = Side::Left;
            core = rest.to_string();
        } else if let Some(rest) = core.strip_prefix("right") {
            side = Side::Right;
            core = rest.to_string();
        }
    }
    (side, core)
}

/// Body part for a normalised core name. `"leg"` is ambiguous and resolved by the caller.
fn classify(core: &str, side: Side) -> Option<&'static str> {
    let part = match core {
        "hips" | "hip" | "pelvis" | "hipmaster" if side == Side::Center => "hips",
        "spine" | "torso" | "spine01" => "spine",
        "chest" | "spine1" | "torso2" | "spine02" => "chest",
        "upperchest" | "spine2" | "torso3" | "spine03" => "upperChest",
        "neck" | "neck01" => "neck",
        "head" => "head",
        "shoulder" | "clavicle" | "collar" => "Shoulder",
        "upperarm" | "arm" | "uparm" => "UpperArm",
        "lowerarm" | "forearm" | "elbow" => "LowerArm",
        "hand" | "wrist" => "Hand",
        "upperleg" | "upleg" | "thigh" | "hip" => "UpperLeg",
        "lowerleg" | "calf" | "shin" | "knee" => "LowerLeg",
        "leg" => "Leg",
        "foot" | "ankle" => "Foot",
        "toe" | "toes" | "toebase" | "ball" => "Toes",
        "eye" => "Eye",
        "jaw" => "jaw",
        _ => return None,
    };
    Some(part)
}

fn sided(side: Side, part: &str) -> Option<String> {
    match side {
        Side::Left => Some(format!("left{part}")),
        Side::Right => Some(format!("right{part}")),
        Side::Center => None,
    }
}

/// `"index2"` → `("Index", 2)`; also accepts `"handindex2"` (Mixamo) and `"indexfinger2"` (Cats).
fn classify_finger(core: &str) -> Option<(&'static str, u32)> {
    let digits_at = core.find(|c: char| c.is_ascii_digit())?;
    let (name, number) = core.split_at(digits_at);
    let number: u32 = number.parse().ok()?;
    let name = name.strip_prefix("hand").unwrap_or(name);
    let name = name.strip_suffix("finger").unwrap_or(name);
    let finger = match name {
        "thumb" => "Thumb",
        "index" => "Index",
        "middle" => "Middle",
        "ring" => "Ring",
        "little" | "pinky" => "Little",
        _ => return None,
    };
    Some((finger, number))
}

pub(crate) fn infer_from_names(joints: &[(usize, String)]) -> HashMap<usize, String> {
    // humanoid name → (node, preference); a higher preference replaces a lower one.
    let mut chosen: HashMap<String, (usize, u8)> = HashMap::new();
    let mut legs: Vec<(Side, usize, u8)> = Vec::new();
    let mut fingers: HashMap<(Side, &'static str), Vec<(u32, usize)>> = HashMap::new();

    for (node, raw) in joints {
        let (side, core) = normalise(raw);

        // MMD rigs skin the legs to "D" bones that follow the IK-driven ones; a glTF export loses that
        // link, so the D bone is the one that has to be driven.
        let (core, preference) = match core.strip_suffix('d') {
            Some(base) if matches!(base, "hip" | "knee" | "foot" | "leg" | "ankle") && side != Side::Center => {
                (base.to_string(), 2u8)
            }
            // A "pelvis" sits below the hips in rigs that have both (KKBP: Hips -> Pelvis, Spine);
            // it only stands in for the hips when there is no hips bone.
            _ if core == "pelvis" => (core, 0u8),
            _ => (core, 1u8),
        };

        if let Some((finger, number)) = classify_finger(&core) {
            if side != Side::Center {
                fingers.entry((side, finger)).or_default().push((number, *node));
            }
            continue;
        }

        let Some(part) = classify(&core, side) else {
            continue;
        };
        if part == "Leg" {
            legs.push((side, *node, preference));
            continue;
        }
        let name = if part.starts_with(|c: char| c.is_ascii_uppercase()) {
            match sided(side, part) {
                Some(name) => name,
                None => continue,
            }
        } else {
            part.to_string()
        };
        offer(&mut chosen, name, *node, preference);
    }

    // "Leg" is the thigh in Cats naming ("Left leg" + "Left knee") but the shin in Mixamo naming
    // ("LeftUpLeg" + "LeftLeg"): it takes whichever of the two is still missing.
    for (side, node, preference) in legs {
        let Some(upper) = sided(side, "UpperLeg") else {
            continue;
        };
        let lower = sided(side, "LowerLeg").unwrap_or_default();
        if !chosen.contains_key(&upper) {
            offer(&mut chosen, upper, node, preference);
        } else if !chosen.contains_key(&lower) {
            offer(&mut chosen, lower, node, preference);
        }
    }

    // Fingers: joints in numeric order from the palm outwards.
    for ((side, finger), mut joints_of_finger) in fingers {
        joints_of_finger.sort_unstable();
        joints_of_finger.dedup_by_key(|(number, _)| *number);
        let segments: &[&str] = if finger == "Thumb" {
            &["Metacarpal", "Proximal", "Distal"]
        } else {
            &["Proximal", "Intermediate", "Distal"]
        };
        for ((_, node), segment) in joints_of_finger.iter().zip(segments) {
            if let Some(name) = sided(side, &format!("{finger}{segment}")) {
                offer(&mut chosen, name, *node, 1);
            }
        }
    }

    chosen.into_iter().map(|(name, (node, _))| (node, name)).collect()
}

/// Keeps the first bone offered for a humanoid name unless a later one has a higher preference.
fn offer(chosen: &mut HashMap<String, (usize, u8)>, name: String, node: usize, preference: u8) {
    match chosen.get(&name) {
        Some((_, existing)) if *existing >= preference => {}
        _ => {
            chosen.insert(name, (node, preference));
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn hips_win_over_pelvis() {
        let names: Vec<String> = ["Center", "Pelvis", "Hips", "Spine", "Chest", "Neck", "Head", "Left arm", "Left elbow",
            "Left wrist", "Right arm", "Right elbow", "Right wrist", "Left leg", "Left knee", "Left ankle", "Right leg",
            "Right knee", "Right ankle"].iter().map(|s| s.to_string()).collect();
        let joints: Vec<(usize, String)> = names.into_iter().enumerate().collect();
        let mapping = infer_from_names(&joints);
        let hips = mapping.iter().find(|(_, h)| h.as_str() == "hips").map(|(n, _)| *n);
        assert_eq!(hips, Some(2));
    }

    fn infer(names: &[&str]) -> HashMap<String, String> {
        let joints: Vec<(usize, String)> = names.iter().enumerate().map(|(i, n)| (i, n.to_string())).collect();
        infer_from_names(&joints)
            .into_iter()
            .map(|(node, humanoid)| (humanoid, names[node].to_string()))
            .collect()
    }

    fn assert_maps(mapping: &HashMap<String, String>, expected: &[(&str, &str)]) {
        for (humanoid, bone) in expected {
            assert_eq!(mapping.get(*humanoid).map(String::as_str), Some(*bone), "{humanoid}");
        }
    }

    #[test]
    fn normalises_common_conventions() {
        assert_eq!(normalise("mixamorig:LeftUpLeg"), (Side::Left, "upleg".to_string()));
        assert_eq!(normalise("J_Bip_R_UpperArm"), (Side::Right, "upperarm".to_string()));
        assert_eq!(normalise("upper_arm.L"), (Side::Left, "upperarm".to_string()));
        assert_eq!(normalise("Left leg"), (Side::Left, "leg".to_string()));
        assert_eq!(normalise("72.joint_LeftHipD"), (Side::Left, "hipd".to_string()));
        assert_eq!(normalise("thigh_l"), (Side::Left, "thigh".to_string()));
        assert_eq!(normalise("Hips"), (Side::Center, "hips".to_string()));
    }

    #[test]
    fn cats_naming_uses_leg_as_thigh() {
        let mapping = infer(&[
            "Hips", "Spine", "Chest", "Neck", "Head", "Left leg", "Left knee", "Left ankle", "Left toe",
            "Right leg", "Right knee", "Right ankle", "Left shoulder", "Left arm", "Left elbow", "Left wrist",
            "Right shoulder", "Right arm", "Right elbow", "Right wrist", "Eye_L", "Eye_R", "Thumb0_L", "Thumb1_L",
            "Thumb2_L", "IndexFinger1_L", "IndexFinger2_L", "IndexFinger3_L", "Left toe_end",
        ]);
        assert_maps(
            &mapping,
            &[
                ("hips", "Hips"),
                ("leftUpperLeg", "Left leg"),
                ("leftLowerLeg", "Left knee"),
                ("leftFoot", "Left ankle"),
                ("leftToes", "Left toe"),
                ("rightUpperArm", "Right arm"),
                ("rightLowerArm", "Right elbow"),
                ("rightHand", "Right wrist"),
                ("leftEye", "Eye_L"),
                ("leftThumbMetacarpal", "Thumb0_L"),
                ("leftThumbDistal", "Thumb2_L"),
                ("leftIndexIntermediate", "IndexFinger2_L"),
            ],
        );
        let joints: Vec<(usize, String)> = Vec::new();
        assert!(!is_complete(&infer_from_names(&joints)));
    }

    #[test]
    fn mixamo_naming_uses_leg_as_shin() {
        let mapping = infer(&[
            "mixamorig:Hips", "mixamorig:Spine", "mixamorig:Spine1", "mixamorig:Spine2", "mixamorig:Neck",
            "mixamorig:Head", "mixamorig:LeftShoulder", "mixamorig:LeftArm", "mixamorig:LeftForeArm",
            "mixamorig:LeftHand", "mixamorig:LeftLeg", "mixamorig:LeftUpLeg", "mixamorig:LeftFoot",
            "mixamorig:LeftToeBase", "mixamorig:LeftHandIndex1", "mixamorig:LeftHandIndex2",
        ]);
        assert_maps(
            &mapping,
            &[
                ("leftUpperLeg", "mixamorig:LeftUpLeg"),
                ("leftLowerLeg", "mixamorig:LeftLeg"),
                ("leftLowerArm", "mixamorig:LeftForeArm"),
                ("chest", "mixamorig:Spine1"),
                ("upperChest", "mixamorig:Spine2"),
                ("leftIndexProximal", "mixamorig:LeftHandIndex1"),
            ],
        );
    }

    #[test]
    fn mmd_export_prefers_d_bones() {
        let mapping = infer(&[
            "13.joint_HipMaster", "60.joint_LeftHip", "61.joint_LeftKnee", "62.joint_LeftFoot",
            "72.joint_LeftHipD", "73.joint_LeftKneeD", "74.joint_LeftFootD", "11.joint_Torso", "12.joint_Torso2",
        ]);
        assert_maps(
            &mapping,
            &[
                ("hips", "13.joint_HipMaster"),
                ("leftUpperLeg", "72.joint_LeftHipD"),
                ("leftLowerLeg", "73.joint_LeftKneeD"),
                ("leftFoot", "74.joint_LeftFootD"),
                ("spine", "11.joint_Torso"),
                ("chest", "12.joint_Torso2"),
            ],
        );
    }

    #[test]
    fn unrecognised_rig_is_not_a_humanoid() {
        let joints: Vec<(usize, String)> = ["_556", "bone_12", "root"].iter().enumerate().map(|(i, n)| (i, n.to_string())).collect();
        assert!(!is_complete(&infer_from_names(&joints)));
    }
}
