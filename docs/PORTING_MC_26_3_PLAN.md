# MPR porting plan: Minecraft 26.3 + Fabric

## Baseline and branch safety

- Stable baseline: `ci/rebuild-baseline`.
- Port branch: `port/mc-26.3`, created from the successful baseline.
- The baseline is Minecraft 1.20.1 / Java 17 / Architectury Loom 1.7.435 / Gradle 8.8.
- Do not make porting changes on `ci/rebuild-baseline`.

## Target toolchain (verify at each CI run)

The official Fabric Develop page currently lists:

- Minecraft: `26.3`
- Fabric Loader: `0.20.1`
- Fabric Loom: `1.19-SNAPSHOT`
- Fabric API: `0.166.0+26.3`

The official Fabric 26.3 example project uses:

- Plugin ID: `net.fabricmc.fabric-loom` (the non-remap Loom plugin for Minecraft 26.1+)
- Gradle wrapper: `9.8.2`
- Java release: `25`

Sources:
- https://fabricmc.net/develop/
- https://github.com/FabricMC/fabric-example-mod/tree/26.3
- https://docs.fabricmc.net/develop/loom/

## Confirmed migration blockers in this repository

1. The root build uses Architectury Plugin 3.4.162 and Architectury Loom 1.7.435.
2. The root project pins Minecraft 1.20.1 and official Mojang mappings.
3. The project is still multi-loader: `common`, `fabric`, and `forge`.
4. `common/build.gradle` uses the Architectury `common("fabric", "forge")` setup and a generated native-resource task.
5. The Fabric module still uses Architectury's `platformSetupLoomIde()` and `fabric()` DSL.
6. Fabric mixin config currently declares `JAVA_17`.
7. `fabric.mod.json` currently declares Minecraft `~1.20.1` and Java `>=17`.
8. The player renderer and world renderer rely on method-descriptor Mixin injections into vanilla rendering code. These are high-risk port points for 26.3.
9. The project has a substantial client rendering stack (OpenGL/GPU skinning, shaders, custom camera) and separate multiplayer sync/network handlers. These must be validated separately.

## Recommended migration sequence

### Phase A: isolate Fabric build infrastructure

- Keep the stable 1.20.1 branch untouched.
- Stop treating Forge as part of the 26.3 deliverable for the first port milestone.
- Replace the legacy Architectury Loom build path with the official 26.3 Fabric Loom path.
- Preserve common Java sources and Rust native build/resource staging, but wire them into a Fabric-only build without duplicate resource copying.
- First milestone: Gradle configures and resolves the 26.3 Minecraft/Fabric dependencies.

### Phase B: compile and repair Java API usage

- Compile common + Fabric Java against 26.3.
- Fix compile errors by subsystem, beginning with Fabric entrypoints, networking, and non-rendering helpers.
- Update mixin config compatibility to the Java level required by the target toolchain.
- Update `fabric.mod.json` dependency constraints only after the new build is configured.

### Phase C: renderer and mixin port

Prioritize these mixins:
- `FabricPlayerRendererMixin`
- `LevelRendererMixin`
- `GameRendererMixin`
- `EntityMixin`
- `CameraMixin`
- `ItemInHandRendererMixin`

For each injection, verify the target method and invocation against the actual 26.3 game classes. Do not assume a 1.20.1 descriptor or ordinal remains valid.

### Phase D: runtime validation

- Launch a clean 26.3 Fabric client with only MPR and required dependencies.
- Verify vanilla player rendering fallback before enabling MMD replacement.
- Verify local player, remote player, first-person camera, third-person camera, inventory preview, and world rendering.
- Test a dedicated multiplayer server and two clients separately. A successful client build does not prove multiplayer synchronization works.

### Phase E: native engine and packaging

- Keep the Rust engine source and JNI boundary unchanged unless the 26.3 Java port demonstrates a concrete need.
- Verify native library loading and package the correct platform binaries.
- Add Linux CI first, then Windows/macOS native builds if required.

## Important 26.3 platform notes

Fabric's official 26.3 announcement highlights a major focus on rendering changes and a move from GLFW to SDL. Any direct GLFW input handling and rendering injections need explicit review. The announcement also lists several removed Fabric APIs; they should only affect this mod if those APIs are actually referenced.

## Definition of done for the first port milestone

- Fabric-only Gradle configuration succeeds on Java 25 / Gradle 9.8.2.
- Minecraft 26.3 and Fabric dependencies resolve.
- Rust native library builds and is staged exactly once.
- Java compile errors are enumerated and grouped; no claim of runtime compatibility until the client launches.
