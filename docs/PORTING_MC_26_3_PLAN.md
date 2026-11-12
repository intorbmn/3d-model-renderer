# MPR porting plan: Minecraft 26.3 + Fabric

## Baseline and branch safety

- Stable baseline: `ci/rebuild-baseline`.
- Port branch: `port/mc-26.3`.
- The baseline is Minecraft 1.20.1 / Java 17 / Architectury Loom 1.7.435 / Gradle 8.8.
- Do not make porting changes on `ci/rebuild-baseline` or `1.20-vr`.

## Target toolchain

The current port branch is configured for:

- Minecraft: `26.3`
- Fabric Loader: `0.20.1`
- Fabric API: `0.166.0+26.3`
- Fabric Loom: `1.19-SNAPSHOT`
- Gradle wrapper: `9.8.2`
- Java release: `25`

Verify the actual versions and dependency availability in CI rather than treating configuration as proof of a successful build.

References:
- https://fabricmc.net/develop/
- https://github.com/FabricMC/fabric-example-mod/tree/26.3
- https://docs.fabricmc.net/develop/loom/

## Current migration state

- The root project uses the non-remap Fabric Loom plugin and Java 25.
- Gradle settings include `common` and `fabric`; Forge is not part of the 26.3 Gradle build.
- Fabric metadata targets Minecraft 26.3 and Java 25.
- The optional Touhou Little Maid mixin and its legacy compatibility layer are deferred from the first port milestone. The source file is excluded, not deleted.
- The Rust engine source and JNI boundary remain separate from the Minecraft Java port.
- Architectury API has been removed from the Gradle files. No Java source, mixin config or Fabric metadata referenced it, so nothing else needed to change.

## Remaining Java and game API work

The port still needs compilation and runtime verification against the actual 26.3 classes. Prioritize:

1. Fabric entrypoints and loader integration.
2. Networking and synchronization APIs.
3. Rendering and Mixin targets, especially player/world rendering.
4. OpenGL/Blaze3D usage and GPU skinning.
5. Input handling and key mappings.
6. GUI and HUD integration.

For each Mixin injection, verify the target method, descriptor, invocation, and ordinal against the actual 26.3 game classes. Do not assume a 1.20.1 descriptor or ordinal remains valid.

## Potential SDL3 migration points to verify

Minecraft 26.3 moves the platform input/windowing stack from GLFW toward SDL. Search for direct GLFW calls and verify each one against the 26.3 input API. A source reference that still compiles may nevertheless behave incorrectly at runtime.

Test keyboard input, mouse buttons, cursor position, scroll input, window focus, and key mapping registration in-game. Do not classify every GLFW reference as broken without checking the relevant 26.3 API path.

## Native engine and JNI

- Keep the Rust engine and JNI boundary unchanged unless the Java port demonstrates a concrete requirement.
- The independent JNI smoke-test workflow uses `ci/jni-smoke/run.sh`. Its existence does not prove the test has passed; verification status depends on the latest successful CI run.
- Verify that the correct platform native library is staged once and loaded from the expected resource path.

## Runtime validation checklist

- Launch a clean 26.3 Fabric client with only MPR and required dependencies.
- Verify vanilla player-rendering fallback before enabling MMD replacement.
- Test local and remote players.
- Test first-person and third-person cameras.
- Test inventory preview and world rendering.
- Test a dedicated multiplayer server with two clients.
- Verify native library loading on each supported platform separately.

A successful Gradle build does not prove runtime compatibility or multiplayer synchronization.

## Definition of done for the first port milestone

- Fabric-only Gradle configuration succeeds on Java 25 / Gradle 9.8.2.
- Minecraft 26.3 and Fabric dependencies resolve.
- Rust native library builds and is staged exactly once.
- Java compile errors are enumerated and grouped.
- The client launches and the runtime checklist is exercised before claiming compatibility.

## Current implementation status (2026-11-13)

- The branch is configured for Java 25, Gradle 9.8.2, Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.166.0+26.3, and Fabric Loom 1.19-SNAPSHOT.
- The build no longer depends on Architectury API.
- The dedicated workflow `.github/workflows/ci-fabric-26-3.yml` is intended to build `:fabric:build` with Java 25 and recursively checked-out Rust submodules.
- The independent JNI workflow is configured separately; do not mark JNI verified until its run succeeds.
- This is build-infrastructure and porting work only. No runtime compatibility claim is made here.
