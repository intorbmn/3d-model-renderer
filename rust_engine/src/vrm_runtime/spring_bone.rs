//! Spring-bone runtime adapted from UniVRM's spring-bone logic (MIT), with the studio's additions:
//!
//! * a fixed time step (60 Hz, up to four steps a frame), so a slow frame does not throw hair
//!   through the body;
//! * jiggle chains (bust, hips): a damped spring around the rest pose instead of a swing;
//! * a limit on how far a joint may turn away from its rest direction;
//! * colliders that know the rest pose: a joint that already sits inside a collider at rest (hair
//!   roots on the scalp) is only kept from going deeper, instead of being pushed out for good;
//! * collisions resolved towards the side the tail came from, so a moving arm or shoulder cannot
//!   pull hair through to its far side and trap it there.

use std::f32::consts::TAU;

use glam::{Mat4, Quat, Vec3};

use crate::model::{
    ColliderShape, SpringBoneCollider, SpringBoneData, SpringBoneJoint, SpringBoneSpring, SpringMode,
};
use crate::skeleton::BoneManager;

/// Physics step, in seconds.
const STEP: f32 = 1.0 / 60.0;
/// At most this many steps a frame; time beyond that is dropped (a long hitch, a loading pause).
const MAX_STEPS: u32 = 4;
/// A tail further than this many bone lengths from its bone is reset to the rest pose.
const RESET_DISTANCE: f32 = 4.0;

#[derive(Clone, Copy, Debug)]
enum TailSpace {
    World,
    Center(usize),
}

#[derive(Clone, Copy, Debug)]
struct TailState {
    current_tail: Vec3,
    prev_tail: Vec3,
    space: TailSpace,
}

impl TailState {
    fn new(
        space: TailSpace,
        current_tail_world: Vec3,
        prev_tail_world: Vec3,
        bones: &BoneManager,
    ) -> Self {
        let current_tail = to_tail_space(space, current_tail_world, bones);
        let prev_tail = to_tail_space(space, prev_tail_world, bones);
        Self {
            current_tail,
            prev_tail,
            space,
        }
    }

    fn current_world(self, bones: &BoneManager) -> Vec3 {
        from_tail_space(self.space, self.current_tail, bones)
    }

    fn prev_world(self, bones: &BoneManager) -> Vec3 {
        from_tail_space(self.space, self.prev_tail, bones)
    }

    fn advance(&mut self, current_tail_world: Vec3, next_tail_world: Vec3, bones: &BoneManager) {
        self.prev_tail = to_tail_space(self.space, current_tail_world, bones);
        self.current_tail = to_tail_space(self.space, next_tail_world, bones);
    }
}

#[derive(Clone, Debug)]
struct SpringJointRuntime {
    bone_index: usize,
    bone_axis: Vec3,
    rest_length: f32,
    init_local_rotation: Quat,
    hit_radius: f32,
    stiffness: f32,
    gravity_power: f32,
    gravity_dir: Vec3,
    drag_force: f32,
    state: TailState,
    /// Tail velocity of a jiggle joint (model units per second).
    velocity: Vec3,
    /// Per collider of the chain: how close the tail may come to it (collider radius plus hit
    /// radius, or less when the tail is closer than that at rest; 0 = ignored).
    collider_limits: Vec<f32>,
    /// The first joint of a jiggle chain also moves (bounces) on a spring of its own.
    bounce: Option<Bounce>,
}

/// Position spring of a jiggle chain's first bone: the whole mass bounces and lags, like the
/// breast physics of character-action games (Zenless Zone Zero, ...), while the joints below add
/// the swing.
#[derive(Clone, Debug)]
struct Bounce {
    /// Rest position relative to the parent bone.
    rest_local: Vec3,
    /// Where the bone is (model space) and how fast it moves.
    position: Vec3,
    velocity: Vec3,
    /// Furthest it may move from its rest position.
    max_offset: f32,
}

#[derive(Clone, Debug)]
struct SpringChainRuntime {
    joints: Vec<SpringJointRuntime>,
    collider_indices: Vec<usize>,
    mode: SpringMode,
    max_angle: Option<f32>,
}

#[derive(Clone, Debug)]
struct ResolvedCollider {
    node: usize,
    shape: ColliderShape,
}

pub struct SpringBoneRuntime {
    chains: Vec<SpringChainRuntime>,
    colliders: Vec<ResolvedCollider>,
    accumulator: f32,
}

impl SpringBoneRuntime {
    pub fn new(spring_bones: SpringBoneData, bones: &BoneManager) -> Self {
        let SpringBoneData {
            springs,
            colliders,
            collider_groups,
        } = spring_bones;

        let colliders = colliders
            .into_iter()
            .filter_map(|collider| resolve_collider(collider, bones))
            .collect::<Vec<_>>();

        let spring_meta = SpringBoneData {
            springs: Vec::new(),
            colliders: Vec::new(),
            collider_groups,
        };

        let chains = springs
            .into_iter()
            .filter_map(|spring| resolve_chain(spring, &spring_meta, &colliders, bones))
            .collect();

        Self {
            chains,
            colliders,
            accumulator: 0.0,
        }
    }

    #[allow(dead_code)]
    pub fn process(&mut self, bones: &mut BoneManager, delta_time: f32) {
        self.process_with_motion(bones, delta_time, |_| Vec3::ZERO);
    }

    /// Like `process`, with an extra acceleration (model units/s²) for a point in model space:
    /// the push from the model's own movement in the world (see `physics::frame_motion`).
    pub fn process_with_motion(
        &mut self,
        bones: &mut BoneManager,
        delta_time: f32,
        external: impl Fn(Vec3) -> Vec3,
    ) {
        if self.chains.is_empty() || !(delta_time > 0.0) {
            return;
        }
        self.accumulator = (self.accumulator + delta_time).min(STEP * MAX_STEPS as f32);
        let mut steps = 0;
        while self.accumulator >= STEP && steps < MAX_STEPS {
            self.step(bones, STEP, &external);
            self.accumulator -= STEP;
            steps += 1;
        }
        if steps == 0 {
            // No step this frame (a high frame rate): the animation has put the bones back in
            // their pose, so turn them to where the tails are.
            self.pose(bones);
        }
    }

    fn step(&mut self, bones: &mut BoneManager, h: f32, external: &impl Fn(Vec3) -> Vec3) {
        for chain in &mut self.chains {
            for joint in &mut chain.joints {
                let Some(transform) = bones.get_bone(joint.bone_index) else {
                    continue;
                };
                let mut bone_position = transform.position();
                if let Some(bounce) = joint.bounce.as_mut() {
                    let parent = parent_transform_of(bones, joint.bone_index);
                    let rest = parent.transform_point3(bounce.rest_local);
                    let (omega, zeta) = jiggle_constants(joint.stiffness, joint.drag_force);
                    let target = rest + joint.gravity_dir * (joint.gravity_power * joint.rest_length * 0.5);
                    if !bounce.position.is_finite() || (bounce.position - rest).length() > bounce.max_offset * 4.0 + 1e-3 {
                        bounce.position = rest;
                        bounce.velocity = Vec3::ZERO;
                    }
                    let acceleration = (target - bounce.position) * (omega * omega)
                        - bounce.velocity * (2.0 * zeta * omega)
                        + external(bounce.position);
                    bounce.velocity += acceleration * h;
                    let mut next = bounce.position + bounce.velocity * h;
                    let offset = next - rest;
                    if offset.length() > bounce.max_offset {
                        next = rest + offset.normalize_or_zero() * bounce.max_offset;
                    }
                    bounce.velocity = (next - bounce.position) / h;
                    bounce.position = next;
                    bone_position = next;
                }
                let parent_rotation = parent_rotation_of(bones, joint.bone_index);
                let rest_dir = (parent_rotation * joint.init_local_rotation * joint.bone_axis).normalize_or_zero();
                let rest_tail = bone_position + rest_dir * joint.rest_length;
                let mut current_tail = joint.state.current_world(bones);
                let mut prev_tail = joint.state.prev_world(bones);
                if !current_tail.is_finite()
                    || !prev_tail.is_finite()
                    || (current_tail - bone_position).length() > joint.rest_length * RESET_DISTANCE
                {
                    current_tail = rest_tail;
                    prev_tail = rest_tail;
                    joint.velocity = Vec3::ZERO;
                }

                let mut next_tail = match chain.mode {
                    SpringMode::Spring => {
                        current_tail
                            + (current_tail - prev_tail) * (1.0 - joint.drag_force)
                            + rest_dir * (joint.stiffness * h)
                            + joint.gravity_dir * (joint.gravity_power * h)
                            + external(current_tail) * (h * h)
                    }
                    SpringMode::Jiggle => {
                        let (omega, zeta) = jiggle_constants(joint.stiffness, joint.drag_force);
                        let target = rest_tail + joint.gravity_dir * (joint.gravity_power * joint.rest_length);
                        let acceleration = (target - current_tail) * (omega * omega)
                            - joint.velocity * (2.0 * zeta * omega)
                            + external(current_tail);
                        joint.velocity += acceleration * h;
                        current_tail + joint.velocity * h
                    }
                };

                next_tail = keep_length(bone_position, next_tail, rest_dir, joint.rest_length);
                if let Some(max_angle) = chain.max_angle {
                    next_tail = limit_angle(bone_position, next_tail, rest_dir, joint.rest_length, max_angle);
                }

                for (slot, &collider_index) in chain.collider_indices.iter().enumerate() {
                    let limit = joint.collider_limits.get(slot).copied().unwrap_or(0.0);
                    let Some(collider) = self.colliders.get(collider_index) else {
                        continue;
                    };
                    if limit <= 0.0 {
                        continue;
                    }
                    if let Some(pushed_tail) = collide_tail(bones, collider, limit, next_tail, current_tail) {
                        next_tail = keep_length(bone_position, pushed_tail, rest_dir, joint.rest_length);
                    }
                }

                if chain.mode == SpringMode::Jiggle {
                    // What the constraints took away is not carried into the next step.
                    joint.velocity = (next_tail - current_tail) / h;
                }

                let world_rotation = world_rotation_from_tail(
                    bones,
                    joint.bone_index,
                    joint.init_local_rotation,
                    joint.bone_axis,
                    next_tail,
                );
                bones.set_global_transform(
                    joint.bone_index,
                    Mat4::from_rotation_translation(world_rotation, bone_position),
                );
                joint.state.advance(current_tail, next_tail, bones);
            }
        }
    }

    fn pose(&mut self, bones: &mut BoneManager) {
        for chain in &self.chains {
            for joint in &chain.joints {
                let Some(transform) = bones.get_bone(joint.bone_index) else {
                    continue;
                };
                let bone_position = joint.bounce.as_ref().map_or(transform.position(), |b| b.position);
                let tail = joint.state.current_world(bones);
                if !tail.is_finite() {
                    continue;
                }
                let world_rotation = world_rotation_from_tail(
                    bones,
                    joint.bone_index,
                    joint.init_local_rotation,
                    joint.bone_axis,
                    tail,
                );
                bones.set_global_transform(
                    joint.bone_index,
                    Mat4::from_rotation_translation(world_rotation, bone_position),
                );
            }
        }
    }

    #[cfg(test)]
    pub fn len(&self) -> usize {
        self.chains.len()
    }
}

/// Angular frequency (rad/s) and damping ratio of a jiggle joint: stiffness 0..4 gives 1..7 Hz,
/// drag 0..1 a damping ratio of 0.1..0.9.
fn jiggle_constants(stiffness: f32, drag: f32) -> (f32, f32) {
    let frequency = 1.0 + 1.5 * stiffness.clamp(0.0, 4.0);
    (TAU * frequency, 0.1 + 0.8 * drag.clamp(0.0, 1.0))
}

fn parent_transform_of(bones: &BoneManager, bone_index: usize) -> Mat4 {
    bones
        .get_bone(bone_index)
        .and_then(|bone| bone.parent_id())
        .and_then(|index| bones.get_bone(index))
        .map(|parent| parent.global_transform())
        .unwrap_or(Mat4::IDENTITY)
}

fn parent_rotation_of(bones: &BoneManager, bone_index: usize) -> Quat {
    bones
        .get_bone(bone_index)
        .and_then(|bone| bone.parent_id())
        .and_then(|index| bones.get_bone(index))
        .map(|parent| parent.rotation())
        .unwrap_or(Quat::IDENTITY)
}

fn keep_length(bone_position: Vec3, tail: Vec3, fallback_dir: Vec3, length: f32) -> Vec3 {
    let direction = (tail - bone_position).normalize_or_zero();
    if direction.length_squared() <= 1e-6 {
        bone_position + fallback_dir * length
    } else {
        bone_position + direction * length
    }
}

fn limit_angle(bone_position: Vec3, tail: Vec3, rest_dir: Vec3, length: f32, max_angle: f32) -> Vec3 {
    let direction = (tail - bone_position).normalize_or_zero();
    if direction.length_squared() <= 1e-6 || rest_dir.length_squared() <= 1e-6 {
        return tail;
    }
    let angle = rest_dir.angle_between(direction);
    if !(angle > max_angle) {
        return tail;
    }
    let axis = rest_dir.cross(direction).normalize_or_zero();
    if axis.length_squared() <= 1e-6 {
        return bone_position + rest_dir * length;
    }
    bone_position + Quat::from_axis_angle(axis, max_angle) * rest_dir * length
}

fn resolve_collider(collider: SpringBoneCollider, bones: &BoneManager) -> Option<ResolvedCollider> {
    bones.get_bone(collider.node)?;
    Some(ResolvedCollider {
        node: collider.node,
        shape: collider.shape,
    })
}

fn resolve_chain(
    spring: SpringBoneSpring,
    data: &SpringBoneData,
    colliders: &[ResolvedCollider],
    bones: &BoneManager,
) -> Option<SpringChainRuntime> {
    let center = spring
        .center
        .filter(|index| bones.get_bone(*index).is_some());
    let space = center.map(TailSpace::Center).unwrap_or(TailSpace::World);
    let collider_indices = spring
        .collider_groups
        .iter()
        .filter_map(|index| data.collider_groups.get(*index))
        .flat_map(|group| group.colliders.iter().copied())
        .filter(|index| *index < colliders.len())
        .collect::<Vec<_>>();

    // With `last_is_tail`, the last joint only gives the one before it its tail.
    let simulated = if spring.settings.last_is_tail {
        spring.joints.len().saturating_sub(1)
    } else {
        spring.joints.len()
    };
    let mut joints = Vec::new();
    for joint_index in 0..simulated {
        let joint = &spring.joints[joint_index];
        let Some(mut resolved) = resolve_joint(joint, spring.joints.get(joint_index + 1), space, bones)
        else {
            continue;
        };
        let rest_tail = resolved.state.current_world(bones);
        resolved.collider_limits = collider_indices
            .iter()
            .map(|&index| rest_limit(bones, &colliders[index], resolved.hit_radius, rest_tail))
            .collect();
        joints.push(resolved);
    }

    if joints.is_empty() {
        None
    } else {
        if spring.settings.mode == SpringMode::Jiggle && spring.settings.translation > 0.0 {
            if let Some(first) = joints.first_mut() {
                if let Some(bone) = bones.get_bone(first.bone_index) {
                    first.bounce = Some(Bounce {
                        rest_local: bone.local_transform().to_scale_rotation_translation().2,
                        position: bone.position(),
                        velocity: Vec3::ZERO,
                        max_offset: first.rest_length * spring.settings.translation,
                    });
                }
            }
        }
        Some(SpringChainRuntime {
            joints,
            collider_indices,
            mode: spring.settings.mode,
            max_angle: spring.settings.max_angle,
        })
    }
}

/// How close a tail may come to a collider: its radius plus the hit radius, or (a little less
/// than) the distance it has at rest when it already sits closer than that.
fn rest_limit(bones: &BoneManager, collider: &ResolvedCollider, hit_radius: f32, rest_tail: Vec3) -> f32 {
    let Some((closest, radius)) = closest_on_collider(bones, collider, rest_tail) else {
        return 0.0;
    };
    let full = radius + hit_radius;
    let distance = (rest_tail - closest).length();
    if distance >= full {
        full
    } else {
        distance * 0.95
    }
}

fn resolve_joint(
    joint: &SpringBoneJoint,
    next_joint: Option<&SpringBoneJoint>,
    space: TailSpace,
    bones: &BoneManager,
) -> Option<SpringJointRuntime> {
    let bone = bones.get_bone(joint.node)?;
    let bone_transform = bone.global_transform();
    let child_world_position = if let Some(next_joint) = next_joint {
        bones.get_bone(next_joint.node)?.position()
    } else {
        virtual_tail_world_position(bone, bones)?
    };

    let local_child_position = bone_transform
        .inverse()
        .transform_point3(child_world_position);
    let rest_length = local_child_position.length();
    if rest_length <= 1e-6 {
        return None;
    }

    let init_local_rotation = bone
        .local_transform()
        .to_scale_rotation_translation()
        .1
        .normalize();
    let state = TailState::new(space, child_world_position, child_world_position, bones);

    Some(SpringJointRuntime {
        bone_index: joint.node,
        bone_axis: local_child_position.normalize(),
        rest_length: (child_world_position - bone.position()).length().max(1e-6),
        init_local_rotation,
        hit_radius: joint.hit_radius,
        stiffness: joint.stiffness,
        gravity_power: joint.gravity_power,
        gravity_dir: Vec3::from_array(joint.gravity_dir).normalize_or_zero(),
        drag_force: joint.drag_force.clamp(0.0, 1.0),
        state,
        velocity: Vec3::ZERO,
        collider_limits: Vec::new(),
        bounce: None,
    })
}

fn virtual_tail_world_position(bone: &crate::skeleton::Bone, bones: &BoneManager) -> Option<Vec3> {
    let parent_position = bone
        .parent_id()
        .and_then(|index| bones.get_bone(index))
        .map(|parent| parent.position())?;
    let direction = (bone.position() - parent_position).normalize_or_zero();
    if direction.length_squared() <= 1e-6 {
        return None;
    }
    Some(bone.position() + direction * 0.07)
}

fn to_tail_space(space: TailSpace, world: Vec3, bones: &BoneManager) -> Vec3 {
    match space {
        TailSpace::World => world,
        TailSpace::Center(center_index) => bones
            .get_bone(center_index)
            .map(|center| center.global_transform().inverse().transform_point3(world))
            .unwrap_or(world),
    }
}

fn from_tail_space(space: TailSpace, value: Vec3, bones: &BoneManager) -> Vec3 {
    match space {
        TailSpace::World => value,
        TailSpace::Center(center_index) => bones
            .get_bone(center_index)
            .map(|center| center.global_transform().transform_point3(value))
            .unwrap_or(value),
    }
}

fn world_rotation_from_tail(
    bones: &BoneManager,
    bone_index: usize,
    init_local_rotation: Quat,
    bone_axis: Vec3,
    next_tail: Vec3,
) -> Quat {
    let Some(bone) = bones.get_bone(bone_index) else {
        return Quat::IDENTITY;
    };
    let parent_rotation = bone
        .parent_id()
        .and_then(|index| bones.get_bone(index))
        .map(|parent| parent.rotation())
        .unwrap_or(Quat::IDENTITY);
    let baseline_rotation = parent_rotation * init_local_rotation;
    let from = (baseline_rotation * bone_axis).normalize_or_zero();
    let to = (next_tail - bone.position()).normalize_or_zero();
    if from.length_squared() <= 1e-6 || to.length_squared() <= 1e-6 {
        baseline_rotation
    } else {
        (Quat::from_rotation_arc(from, to) * baseline_rotation).normalize()
    }
}

/// The point of a collider's core (sphere centre or capsule segment) closest to `point`, and the
/// collider's radius.
fn closest_on_collider(bones: &BoneManager, collider: &ResolvedCollider, point: Vec3) -> Option<(Vec3, f32)> {
    let collider_transform = bones.get_bone(collider.node)?.global_transform();
    Some(match collider.shape {
        ColliderShape::Sphere { offset, radius } => {
            (collider_transform.transform_point3(Vec3::from_array(offset)), radius)
        }
        ColliderShape::Capsule {
            offset,
            tail,
            radius,
        } => {
            let start = collider_transform.transform_point3(Vec3::from_array(offset));
            let end = collider_transform.transform_point3(Vec3::from_array(tail));
            (closest_point_on_segment(start, end, point), radius)
        }
    })
}

/// Keeps `next_tail` at least `limit` away from the collider, pushing it out on the side the tail
/// came from (`current_tail`), so it cannot be pulled through to the other side.
fn collide_tail(
    bones: &BoneManager,
    collider: &ResolvedCollider,
    limit: f32,
    next_tail: Vec3,
    current_tail: Vec3,
) -> Option<Vec3> {
    let (closest, _) = closest_on_collider(bones, collider, next_tail)?;
    let offset = next_tail - closest;
    if offset.length_squared() >= limit * limit {
        return None;
    }
    let (closest_current, _) = closest_on_collider(bones, collider, current_tail)?;
    let from_current = current_tail - closest_current;
    let normal = if from_current.length_squared() > 1e-8 {
        from_current.normalize()
    } else if offset.length_squared() > 1e-8 {
        offset.normalize()
    } else {
        Vec3::Y
    };
    Some(closest + normal * limit)
}

fn closest_point_on_segment(start: Vec3, end: Vec3, point: Vec3) -> Vec3 {
    let segment = end - start;
    let length_squared = segment.length_squared();
    if length_squared <= 1e-6 {
        return start;
    }
    let t = ((point - start).dot(segment) / length_squared).clamp(0.0, 1.0);
    start + segment * t
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::model::{
        ColliderShape, SpringBoneCollider, SpringBoneColliderGroup, SpringBoneData, SpringSettings,
    };
    use crate::skeleton::{BoneLink, BoneManager};

    fn build_chain_bones() -> BoneManager {
        let mut bones = BoneManager::new();

        let mut root = BoneLink::new("root".to_string());
        root.initial_position = Vec3::ZERO;
        root.parent_index = -1;
        bones.add_bone(root);

        let mut bone_a = BoneLink::new("bone_a".to_string());
        bone_a.initial_position = Vec3::new(0.0, 1.0, 0.0);
        bone_a.parent_index = 0;
        bones.add_bone(bone_a);

        let mut bone_b = BoneLink::new("bone_b".to_string());
        bone_b.initial_position = Vec3::new(0.0, 2.0, 0.0);
        bone_b.parent_index = 1;
        bones.add_bone(bone_b);

        bones.build_hierarchy();
        bones
    }

    #[test]
    fn sphere_collision_should_push_tail_outside_radius() {
        let bones = build_chain_bones();
        let collider = ResolvedCollider {
            node: 0,
            shape: ColliderShape::Sphere {
                offset: [0.0, 1.0, 0.0],
                radius: 0.5,
            },
        };

        let pushed = collide_tail(&bones, &collider, 0.7, Vec3::new(0.0, 1.1, 0.0), Vec3::new(0.0, 1.1, 0.0)).unwrap();
        assert!((pushed - Vec3::new(0.0, 1.0, 0.0)).length() >= 0.7 - 1e-5);
    }

    #[test]
    fn capsule_collision_should_push_from_segment() {
        let bones = build_chain_bones();
        let collider = ResolvedCollider {
            node: 0,
            shape: ColliderShape::Capsule {
                offset: [0.0, 0.5, 0.0],
                tail: [0.0, 1.5, 0.0],
                radius: 0.2,
            },
        };

        let pushed = collide_tail(&bones, &collider, 0.4, Vec3::new(0.1, 1.0, 0.0), Vec3::new(0.1, 1.0, 0.0)).unwrap();
        assert!(pushed.x.abs() >= 0.39);
    }

    #[test]
    fn tail_state_should_round_trip_center_space() {
        let bones = build_chain_bones();
        let state = TailState::new(
            TailSpace::Center(1),
            Vec3::new(0.0, 2.0, 0.0),
            Vec3::new(0.0, 2.0, 0.0),
            &bones,
        );
        assert_eq!(state.current_world(&bones), Vec3::new(0.0, 2.0, 0.0));
    }

    #[test]
    fn process_should_keep_chain_initialized() {
        let mut bones = build_chain_bones();
        let runtime_data = SpringBoneData {
            springs: vec![SpringBoneSpring {
                joints: vec![
                    SpringBoneJoint {
                        node: 1,
                        hit_radius: 0.0,
                        stiffness: 1.0,
                        gravity_power: 0.0,
                        gravity_dir: [0.0, -1.0, 0.0],
                        drag_force: 0.2,
                    },
                    SpringBoneJoint {
                        node: 2,
                        hit_radius: 0.0,
                        stiffness: 1.0,
                        gravity_power: 0.0,
                        gravity_dir: [0.0, -1.0, 0.0],
                        drag_force: 0.2,
                    },
                ],
                collider_groups: vec![0],
                center: Some(1),
                settings: SpringSettings::default(),
            }],
            colliders: vec![SpringBoneCollider {
                node: 0,
                shape: ColliderShape::Sphere {
                    offset: [0.0, -10.0, 0.0],
                    radius: 0.1,
                },
            }],
            collider_groups: vec![SpringBoneColliderGroup { colliders: vec![0] }],
        };

        let mut runtime = SpringBoneRuntime::new(runtime_data, &bones);
        runtime.process(&mut bones, 0.016);

        assert_eq!(runtime.len(), 1);
        let bone = bones.get_bone(1).unwrap();
        assert!(bone.rotation().length_squared() > 0.0);
    }

    fn joint(node: usize, stiffness: f32, gravity: f32, drag: f32) -> SpringBoneJoint {
        SpringBoneJoint {
            node,
            hit_radius: 0.0,
            stiffness,
            gravity_power: gravity,
            gravity_dir: [0.0, -1.0, 0.0],
            drag_force: drag,
        }
    }

    fn tip(bones: &BoneManager) -> Vec3 {
        bones.get_bone(2).unwrap().position()
    }

    #[test]
    fn last_joint_of_a_studio_chain_is_only_a_tail() {
        let mut bones = build_chain_bones();
        let data = SpringBoneData {
            springs: vec![SpringBoneSpring {
                joints: vec![joint(1, 1.0, 0.0, 0.4), joint(2, 1.0, 0.0, 0.4)],
                collider_groups: vec![],
                center: None,
                settings: SpringSettings { last_is_tail: true, ..SpringSettings::default() },
            }],
            colliders: vec![],
            collider_groups: vec![],
        };
        let mut runtime = SpringBoneRuntime::new(data, &bones);
        assert_eq!(runtime.chains[0].joints.len(), 1, "bone_b is the tail, not a joint");
        runtime.process(&mut bones, 0.1);
        assert!((tip(&bones) - Vec3::new(0.0, 2.0, 0.0)).length() < 1e-3, "no force: stays at rest");
    }

    #[test]
    fn jiggle_sags_a_little_and_settles_at_rest_plus_sag() {
        // A bone pointing sideways (like a bust pointing forwards).
        let mut bones = BoneManager::new();
        let mut root = BoneLink::new("root".to_string());
        root.parent_index = -1;
        bones.add_bone(root);
        let mut a = BoneLink::new("a".to_string());
        a.initial_position = Vec3::new(0.0, 1.0, 0.0);
        a.parent_index = 0;
        bones.add_bone(a);
        let mut b = BoneLink::new("b".to_string());
        b.initial_position = Vec3::new(1.0, 1.0, 0.0);
        b.parent_index = 1;
        bones.add_bone(b);
        bones.build_hierarchy();
        let data = SpringBoneData {
            springs: vec![SpringBoneSpring {
                joints: vec![joint(1, 2.0, 0.1, 0.6), joint(2, 2.0, 0.1, 0.6)],
                collider_groups: vec![],
                center: None,
                settings: SpringSettings {
                    mode: SpringMode::Jiggle,
                    last_is_tail: true,
                    max_angle: Some(25f32.to_radians()),
                    translation: 0.0,
                },
            }],
            colliders: vec![],
            collider_groups: vec![],
        };
        let mut runtime = SpringBoneRuntime::new(data, &bones);
        for _ in 0..240 {
            runtime.process(&mut bones, 1.0 / 60.0);
        }
        let end = tip(&bones);
        assert!(end.y < 1.0 && end.y > 0.85, "sags by about a tenth of the length: {end}");
        assert!((end - Vec3::new(0.0, 1.0, 0.0)).length() - 1.0 < 1e-3, "keeps its length");
    }

    #[test]
    fn a_tail_inside_a_collider_at_rest_is_not_pushed_out() {
        let mut bones = build_chain_bones();
        let data = SpringBoneData {
            springs: vec![SpringBoneSpring {
                joints: vec![joint(1, 1.0, 0.0, 0.4), joint(2, 1.0, 0.0, 0.4)],
                collider_groups: vec![0],
                center: None,
                settings: SpringSettings { last_is_tail: true, ..SpringSettings::default() },
            }],
            // A sphere around the tail at rest (like a head collider around hair roots).
            colliders: vec![SpringBoneCollider {
                node: 0,
                shape: ColliderShape::Sphere { offset: [0.3, 2.0, 0.0], radius: 0.5 },
            }],
            collider_groups: vec![SpringBoneColliderGroup { colliders: vec![0] }],
        };
        let mut runtime = SpringBoneRuntime::new(data, &bones);
        for _ in 0..60 {
            runtime.process(&mut bones, 1.0 / 60.0);
        }
        assert!((tip(&bones) - Vec3::new(0.0, 2.0, 0.0)).length() < 0.02, "stays where it rests: {}", tip(&bones));
    }

    #[test]
    fn a_long_frame_is_split_into_steps() {
        let mut bones = build_chain_bones();
        let data = SpringBoneData {
            springs: vec![SpringBoneSpring {
                joints: vec![joint(1, 0.0, 5.0, 0.4), joint(2, 0.0, 5.0, 0.4)],
                collider_groups: vec![],
                center: None,
                settings: SpringSettings { last_is_tail: true, ..SpringSettings::default() },
            }],
            colliders: vec![],
            collider_groups: vec![],
        };
        let mut runtime = SpringBoneRuntime::new(data, &bones);
        runtime.process(&mut bones, 0.05); // three steps
        assert!(runtime.accumulator < STEP);
        runtime.process(&mut bones, 10.0); // a hitch: at most four steps, the rest dropped
        assert!(runtime.accumulator < STEP);
        assert!(tip(&bones).is_finite());
    }

    #[test]
    fn a_jiggle_root_bounces_when_its_parent_moves_and_settles_back() {
        let mut bones = BoneManager::new();
        let mut root = BoneLink::new("root".to_string());
        root.parent_index = -1;
        bones.add_bone(root);
        let mut a = BoneLink::new("bust".to_string());
        a.initial_position = Vec3::new(0.0, 1.0, 0.0);
        a.parent_index = 0;
        bones.add_bone(a);
        let mut b = BoneLink::new("tip".to_string());
        b.initial_position = Vec3::new(0.0, 1.0, 1.0);
        b.parent_index = 1;
        bones.add_bone(b);
        bones.build_hierarchy();
        let data = SpringBoneData {
            springs: vec![SpringBoneSpring {
                joints: vec![joint(1, 2.5, 0.0, 0.45), joint(2, 2.5, 0.0, 0.45)],
                collider_groups: vec![],
                center: None,
                settings: SpringSettings {
                    mode: SpringMode::Jiggle,
                    last_is_tail: true,
                    max_angle: Some(20f32.to_radians()),
                    translation: 0.3,
                },
            }],
            colliders: vec![],
            collider_groups: vec![],
        };
        let mut runtime = SpringBoneRuntime::new(data, &bones);
        // The body jumps up: the bust lags behind (stays lower than its rest), within its limit.
        let jump = |_: Vec3| Vec3::new(0.0, -60.0, 0.0);
        let mut lowest = f32::INFINITY;
        for _ in 0..6 {
            runtime.process_with_motion(&mut bones, 1.0 / 60.0, jump);
            lowest = lowest.min(bones.get_bone(1).unwrap().position().y);
        }
        assert!(lowest < 0.99, "the bust root moves, not only the tip: lowest y {lowest}");
        assert!(lowest >= 1.0 - 0.3 - 1e-3, "within 30% of the bone length: {lowest}");
        for _ in 0..240 {
            runtime.process(&mut bones, 1.0 / 60.0);
        }
        let settled = bones.get_bone(1).unwrap().position();
        assert!((settled - Vec3::new(0.0, 1.0, 0.0)).length() < 0.01, "settles back: {settled}");
    }
}
