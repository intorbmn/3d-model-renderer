//! How the model's own movement in the world pushes its physics.
//!
//! Physics (Bullet rigid bodies and spring bones) runs in model space, so walking, jumping and
//! turning are invisible to it unless their effect is added back. This module measures the model
//! frame's motion from the transform the game sets every frame and turns it into an acceleration
//! for any point of the model:
//!
//! * the frame's linear acceleration (starting, stopping, jumping, landing) as a fictitious force;
//! * the turn: Euler and centrifugal terms for a point away from the turning axis;
//! * air drag against the frame's velocity, so hair and skirts trail while moving steadily.
//!
//! Positions are in model units (the game passes the entity position divided by the model scale).

use glam::{Mat3, Mat4, Vec3};

/// Faster than this (model units per second, about 33 blocks/s) is a teleport, not movement.
const TELEPORT_SPEED: f32 = 370.0;
/// Upper bound of the acceleration handed to physics, about four times gravity.
const MAX_ACCEL: f32 = 400.0;
/// Air drag per second at full strength.
const DRAG_PER_SECOND: f32 = 2.0;

/// One frame of model motion, expressed in model space.
#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub struct FrameSample {
    /// Linear acceleration of the model frame.
    pub accel: Vec3,
    /// Linear velocity of the model frame.
    pub velocity: Vec3,
    /// Turn rate about the up axis (radians per second, right-handed).
    pub omega: f32,
    /// Change of the turn rate (radians per second squared).
    pub alpha: f32,
}

impl FrameSample {
    /// The acceleration a free point at `r` (model space) feels because the model moves.
    /// `strength` is the user's inertia setting: 0.5 (the default) gives the physical value.
    pub fn point_accel(&self, r: Vec3, strength: f32) -> Vec3 {
        if strength <= 0.0 {
            return Vec3::ZERO;
        }
        let k = (strength * 2.0).min(2.0);
        let up = Vec3::Y;
        let omega = up * self.omega;
        let alpha = up * self.alpha;
        // Only the horizontal offset from the turning axis matters for the turn terms.
        let r_h = Vec3::new(r.x, 0.0, r.z);
        let frame_point_accel = self.accel + alpha.cross(r_h) + omega.cross(omega.cross(r_h));
        let frame_point_velocity = self.velocity + omega.cross(r_h);
        let a = -frame_point_accel * k - frame_point_velocity * (DRAG_PER_SECOND * strength);
        a.clamp_length_max(MAX_ACCEL)
    }
}

/// Tracks the model transform between frames.
#[derive(Clone, Debug, Default)]
pub struct FrameMotion {
    prev_position: Option<Vec3>,
    prev_velocity: Vec3,
    prev_yaw: f32,
    prev_omega: f32,
    smoothed_accel: Vec3,
    smoothed_alpha: f32,
}

/// How much of the new acceleration each frame keeps; smooths out uneven frame times.
const SMOOTHING: f32 = 0.5;

impl FrameMotion {
    pub fn reset(&mut self) {
        *self = Self::default();
    }

    /// Feeds this frame's model transform; returns the motion in model space.
    pub fn update(&mut self, model_transform: Mat4, dt: f32) -> FrameSample {
        let dt = dt.max(1.0e-3);
        let position = model_transform.w_axis.truncate();
        let x_axis = model_transform.x_axis.truncate();
        // The game builds the transform as a turn about the up axis.
        let yaw = (-x_axis.z).atan2(x_axis.x);

        let Some(prev_position) = self.prev_position else {
            self.prev_position = Some(position);
            self.prev_yaw = yaw;
            return FrameSample::default();
        };

        let velocity = (position - prev_position) / dt;
        let mut turn = yaw - self.prev_yaw;
        while turn > std::f32::consts::PI {
            turn -= std::f32::consts::TAU;
        }
        while turn < -std::f32::consts::PI {
            turn += std::f32::consts::TAU;
        }
        let omega = turn / dt;
        self.prev_position = Some(position);
        self.prev_yaw = yaw;

        if !velocity.is_finite() || velocity.length() > TELEPORT_SPEED || omega.abs() > 60.0 {
            // A teleport or a snap turn: start over instead of flinging everything.
            self.prev_velocity = Vec3::ZERO;
            self.prev_omega = 0.0;
            self.smoothed_accel = Vec3::ZERO;
            self.smoothed_alpha = 0.0;
            return FrameSample::default();
        }

        let raw_accel = ((velocity - self.prev_velocity) / dt).clamp_length_max(MAX_ACCEL * 4.0);
        let raw_alpha = ((omega - self.prev_omega) / dt).clamp(-400.0, 400.0);
        self.smoothed_accel = self.smoothed_accel.lerp(raw_accel, SMOOTHING);
        self.smoothed_alpha += (raw_alpha - self.smoothed_alpha) * SMOOTHING;
        let accel = self.smoothed_accel;
        let alpha = self.smoothed_alpha;
        self.prev_velocity = velocity;
        self.prev_omega = omega;

        let to_local = Mat3::from_mat4(model_transform).transpose();
        FrameSample {
            accel: to_local * accel,
            velocity: to_local * velocity,
            omega,
            alpha,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use glam::Vec4;

    fn transform(x: f32, z: f32, yaw: f32) -> Mat4 {
        // Same construction as MmdModel::set_model_position_and_yaw.
        let (s, c) = yaw.sin_cos();
        Mat4::from_cols(
            Vec4::new(c, 0.0, s, 0.0),
            Vec4::new(0.0, 1.0, 0.0, 0.0),
            Vec4::new(-s, 0.0, c, 0.0),
            Vec4::new(x, 0.0, z, 1.0),
        )
    }

    #[test]
    fn walking_forward_pushes_hair_back() {
        let mut motion = FrameMotion::default();
        let dt = 1.0 / 60.0;
        motion.update(transform(0.0, 0.0, 0.0), dt);
        // The model's front is +Z; move forward at a steady 50 units/s.
        let mut sample = FrameSample::default();
        for i in 1..30 {
            sample = motion.update(transform(0.0, i as f32 * 50.0 * dt, 0.0), dt);
        }
        let a = sample.point_accel(Vec3::new(0.0, 15.0, -1.0), 0.5);
        assert!(a.z < -40.0, "steady walking should trail hair backwards, got {a}");
        assert!(a.x.abs() < 1e-3);
    }

    #[test]
    fn starting_to_move_jerks_backwards_and_stopping_forwards() {
        let mut motion = FrameMotion::default();
        let dt = 1.0 / 60.0;
        motion.update(transform(0.0, 0.0, 0.0), dt);
        let start = motion.update(transform(0.0, 1.0, 0.0), dt);
        assert!(start.point_accel(Vec3::ZERO, 0.5).z < -MAX_ACCEL * 0.9);
        motion.update(transform(0.0, 2.0, 0.0), dt);
        let stop = motion.update(transform(0.0, 2.0, 0.0), dt);
        let stop = if stop.point_accel(Vec3::ZERO, 0.5).z > 0.0 { stop } else { motion.update(transform(0.0, 2.0, 0.0), dt) };
        assert!(stop.point_accel(Vec3::ZERO, 0.5).z > 0.0);
    }

    #[test]
    fn direction_is_local_to_the_model() {
        // Facing the other way, the same world motion is "backwards" for the model.
        let mut motion = FrameMotion::default();
        let dt = 1.0 / 60.0;
        let yaw = std::f32::consts::PI;
        motion.update(transform(0.0, 0.0, yaw), dt);
        let mut sample = FrameSample::default();
        for i in 1..30 {
            sample = motion.update(transform(0.0, i as f32 * 50.0 * dt, yaw), dt);
        }
        assert!(sample.point_accel(Vec3::ZERO, 0.5).z > 40.0);
    }

    #[test]
    fn turning_swings_points_outwards_and_against_the_turn() {
        let mut motion = FrameMotion::default();
        let dt = 1.0 / 60.0;
        motion.update(transform(0.0, 0.0, 0.0), dt);
        let mut sample = FrameSample::default();
        for i in 1..30 {
            sample = motion.update(transform(0.0, 0.0, i as f32 * 3.0 * dt), dt);
        }
        // A point behind the model (hair at -Z) is flung further out (more negative Z).
        let a = sample.point_accel(Vec3::new(0.0, 15.0, -2.0), 0.5);
        assert!(a.z < 0.0, "centrifugal push expected, got {a}");
        // And it lags behind the turn: opposite to the frame's velocity at that point.
        let frame_velocity_at_point = Vec3::Y.cross(Vec3::new(0.0, 0.0, -2.0)) * sample.omega;
        assert!(a.dot(frame_velocity_at_point) < 0.0);
    }

    #[test]
    fn teleport_is_ignored() {
        let mut motion = FrameMotion::default();
        let dt = 1.0 / 60.0;
        motion.update(transform(0.0, 0.0, 0.0), dt);
        let sample = motion.update(transform(1000.0, 0.0, 0.0), dt);
        assert_eq!(sample, FrameSample::default());
    }

    #[test]
    fn zero_strength_turns_it_off() {
        let sample = FrameSample { accel: Vec3::ONE * 100.0, velocity: Vec3::ONE, omega: 3.0, alpha: 1.0 };
        assert_eq!(sample.point_accel(Vec3::ONE, 0.0), Vec3::ZERO);
    }
}
