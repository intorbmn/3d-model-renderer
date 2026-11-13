# 3D Model Renderer

**English** | [Tiếng Việt](README.vi.md)

A Fabric mod for **Minecraft 26.3** that replaces players and mobs with 3D models (MMD PMX/PMD, VRM, glTF/GLB), with VMD animation and physics.

> **Forked from MMD Skin.** This project is a fork of [MMD Skin (MC-MMD-rust)](https://github.com/shiroha-233/MC-MMD-rust) by shiroha.
> Almost all of the engine and mod code comes from the original project; this repository ports it to Minecraft 26.3 Fabric and develops it further.
> See [Origin and credits](#origin-and-credits).

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## Status

In development; there is no official release yet.

| Area | Status |
|---|---|
| Minecraft 26.3, Fabric | Builds and runs |
| PMX/PMD models | Render |
| Plain GLB/glTF models | Load; animated when the skeleton is recognised (see below) |
| First-person view (vanilla hands hidden) | Ported, needs in-game testing |
| Toon outline | Ported, needs in-game testing |
| Toon shading, GPU skinning | Not ported yet |
| Touhou Little Maid, Vivecraft, Iris compatibility | Not ported yet |
| Forge / NeoForge, older Minecraft versions | Not supported in this repository |

Feature parity with the original mod and the plan for upcoming work are tracked in [docs/ROADMAP.md](docs/ROADMAP.md) (Vietnamese).

## Installation

Requirements: Minecraft 26.3, Fabric Loader 0.20.1+, Java 25, [Fabric API](https://modrinth.com/mod/fabric-api), [Cloth Config](https://modrinth.com/mod/cloth-config).

1. Download the jar from the **Artifacts** section of the latest run in the [Actions](https://github.com/intorbmn/3d-model-renderer/actions) tab (`3d-model-renderer-fabric-26.3-windows-x64` or `linux-x64`).
2. Put the jar in `.minecraft/mods/`.
3. Start the game once; the mod creates the `.minecraft/3d-skin/` folder.

## Adding models

Each model lives in its own folder under `.minecraft/3d-skin/EntityPlayer/`:

```
.minecraft/3d-skin/
├── Cards/                   model cards (.png) made with 3D Model Renderer Studio
├── EntityPlayer/
│   ├── Miku/
│   │   ├── model.pmx        (or .pmd, .vrm, .glb, .gltf)
│   │   └── ...              (the texture files the model uses, at the paths it expects)
│   └── ...
├── DefaultAnim/             default animations (.vmd)
├── CustomAnim/              your own animations (.vmd)
├── DefaultMorph/            default expressions (.vpd)
└── CustomMorph/             your own expressions (.vpd)
```

In game, hold **left Alt** to open the wheel menu, choose **Model Switch**, then pick the model folder.

The original documentation (in Chinese) covers the folder layout, animation load order and per-model settings in detail: [docs/README.mmdskin.zh.md](docs/README.mmdskin.zh.md). Most of it still applies, except the parts about Forge and Minecraft 1.20.1.

### Model cards

A model card is a single PNG: the picture is the character, and the whole model (mesh, textures, size and colour settings) is packed behind it, like a Koikatsu card. Make cards with [3D Model Renderer Studio](https://github.com/intorbmn/3d-model-renderer-studio), which runs in the browser, previews the model with the mod's motions and takes the same model types as the mod. Drop the `.png` into `3d-skin/Cards/`. Cards are listed first in the model selector, highlighted with a **CARD** badge; plain model folders keep working as before.

Koikatsu cards hold settings for that game's character maker, not a 3D model, so the mod cannot load them. Export the character to PMX with the KKBP exporter and turn the PMX into a card in the studio.

### GLB/glTF models and animation

The mod's motions drive bones by their standard MMD names. For a plain GLB/glTF the mod recognises a humanoid skeleton from the bone names of the common conventions: VRM/Unity (`LeftUpperLeg`), VRoid (`J_Bip_L_UpperLeg`), Mixamo (`mixamorig:LeftUpLeg`), Cats Blender Plugin (`Left leg`), Blender (`upper_leg.L`), Unreal (`thigh_l`). A recognised model uses the default motions; otherwise it stands still.

If the skeleton is recognised wrongly or not at all, put a `bones.json` next to the model file, mapping VRM humanoid bone names to the bone names in the file:

```json
{ "hips": "Pelvis", "spine": "Spine1", "head": "Head",
  "leftUpperArm": "L_Arm", "leftLowerArm": "L_Elbow", "leftHand": "L_Wrist",
  "leftUpperLeg": "L_Thigh", "leftLowerLeg": "L_Knee", "leftFoot": "L_Ankle" }
```

All 15 core bones are required: `hips`, `spine`, `head`, and `UpperArm`, `LowerArm`, `Hand`, `UpperLeg`, `LowerLeg`, `Foot` for both `left` and `right`.

### Exporting from Blender

```bash
blender --background model.blend --python tools/blender/export_glb.py -- output.glb
```

The script exports the armature and its meshes to GLB and rebuilds the materials as Principled BSDF, because Blender's glTF exporter drops the textures of VRM/MToon-style materials.

## Key bindings

| Key | Action |
|---|---|
| Hold left `Alt` | Main wheel: switch model, actions, expressions, materials, scene, stage, settings |

Keys can be changed in the game's *Controls* screen, under **3D Model Renderer**.

## Building from source

Requires JDK 25, Rust (stable), CMake and a C++ compiler (for Bullet3).

```bash
git clone --recursive https://github.com/intorbmn/3d-model-renderer
cd 3d-model-renderer
./gradlew :fabric:build
```

The jar is written to `fabric/build/libs/`. The build compiles the Rust engine in `rust_engine/` and bundles the native library for the current operating system.

The mod ID remains `mmdskin`, so configs, key bindings and the `3d-skin` folder from the original mod keep working.

## Origin and credits

- **[MMD Skin (MC-MMD-rust)](https://github.com/shiroha-233/MC-MMD-rust)** by shiroha233: the project this repository is forked from, including the Rust engine, the Minecraft mod and most features.
- **[KAIMyEntity](https://github.com/kjkjkAIStudio/KAIMyEntity)** by kjkjkAIStudio and tarsin: the first MMD mod for Minecraft.
- **[KAIMyEntity-C](https://github.com/Gengorou-C/KAIMyEntity-C)** by Gengorou-C: the direct predecessor of MMD Skin.
- asuka-mio: contributor to MMD Skin.

Full list of libraries and licenses: [ACKNOWLEDGMENTS.md](ACKNOWLEDGMENTS.md), [NOTICE](NOTICE), [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).

## License

MIT, see [LICENSE](LICENSE). The original project's copyright is retained.
