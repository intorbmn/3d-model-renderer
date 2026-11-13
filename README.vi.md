# 3D Model Renderer

[English](README.md) | **Tiếng Việt**

Mod Fabric cho **Minecraft 26.3**, thay hình người chơi và mob bằng model 3D (MMD PMX/PMD, VRM, glTF/GLB), có animation VMD và vật lý.

> **Fork từ MMD Skin.** Dự án này được fork từ [MMD Skin (MC-MMD-rust)](https://github.com/shiroha-233/MC-MMD-rust) của shiroha.
> Gần như toàn bộ engine và code mod đến từ bản gốc; repo này port sang Minecraft 26.3 Fabric và phát triển tiếp.
> Xem phần [Nguồn gốc và ghi công](#nguồn-gốc-và-ghi-công).

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## Trạng thái

Đang phát triển, chưa có bản phát hành chính thức.

| Hạng mục | Trạng thái |
|---|---|
| Minecraft 26.3, Fabric | Biên dịch và chạy được |
| Model PMX/PMD | Hiển thị được |
| Model GLB/glTF thường | Nạp được; có animation nếu nhận diện được bộ xương (xem bên dưới) |
| Card model (`3d-skin/Cards/*.png`, tạo bằng [studio](https://github.com/intorbmn/3d-model-renderer-studio)) | Nạp được, xếp đầu và đánh dấu nổi bật trong danh sách chọn model; cần test trong game |
| Vật lý: rigid body của PMX, spring bone của VRM/GLB/glTF kèm vùng va chạm | Chạy được; phản ứng khi đi, nhảy, xoay người; cần test trong game |
| Cầm vật phẩm với model GLB/glTF | Đã sửa góc xoay tay lúc nghỉ; cần test trong game |
| Góc nhìn thứ nhất (ẩn tay gốc) | Đã port, cần test trong game |
| Viền toon (outline) | Đã port, cần test trong game |
| Tô bóng toon, GPU skinning | Chưa port |
| Tương thích Touhou Little Maid, Vivecraft, Iris | Chưa port |
| Forge / NeoForge, bản Minecraft cũ | Không hỗ trợ trong repo này |

Mức độ đầy đủ so với bản gốc và kế hoạch các tính năng sắp tới: [docs/ROADMAP.md](docs/ROADMAP.md).

## Test bản này

Những thay đổi từ bản test trước, cần kiểm tra trong game:

- **Vật lý theo người chơi.** Tóc, váy, ngực phải giật lại khi bắt đầu hoặc dừng di chuyển, bị kéo về sau khi đi hoặc chạy, văng ra khi xoay người, và không bay loạn khi dịch chuyển. Độ mạnh: mục *Inertia Strength* trong cài đặt mod (50 = đúng như thật, mặc định).
- **Model có spring bone (VRM, card GLB/glTF)**: vùng va chạm đã đúng kích thước, tóc không còn xuyên vào đầu và người.
- **Card**: bỏ card tạo từ studio vào `3d-skin/Cards/`, mở danh sách chọn model (card nằm đầu, có nhãn **CARD** màu hồng), chọn card và xem kích thước, màu, nhóm vật lý.
- **Cầm vật phẩm** với model GLB/glTF: kiếm phải cầm lưỡi hướng ra trước, không bị ngửa nằm ngang.

## Cài đặt

Yêu cầu: Minecraft 26.3, Fabric Loader 0.20.1+, Java 25, [Fabric API](https://modrinth.com/mod/fabric-api), [Cloth Config](https://modrinth.com/mod/cloth-config).

1. Tải file jar từ mục **Artifacts** của lượt chạy mới nhất trong tab [Actions](https://github.com/intorbmn/3d-model-renderer/actions) (`3d-model-renderer-fabric-26.3-windows-x64` hoặc `linux-x64`).
2. Bỏ jar vào thư mục `.minecraft/mods/`.
3. Chạy game một lần, mod sẽ tạo thư mục `.minecraft/3d-skin/`.

## Thêm model

Mỗi model nằm trong một thư mục riêng dưới `.minecraft/3d-skin/EntityPlayer/`:

```
.minecraft/3d-skin/
├── Cards/                   card model (.png) tạo bằng 3D Model Renderer Studio
├── EntityPlayer/
│   ├── Miku/
│   │   ├── model.pmx        (hoặc .pmd, .vrm, .glb, .gltf)
│   │   └── ...              (các file texture mà model dùng, giữ đúng đường dẫn)
│   └── ...
├── DefaultAnim/             animation mặc định (.vmd)
├── CustomAnim/              animation của bạn (.vmd)
├── DefaultMorph/            biểu cảm mặc định (.vpd)
└── CustomMorph/             biểu cảm của bạn (.vpd)
```

Trong game, giữ **Alt trái** để mở vòng menu, chọn **Model Switch** rồi chọn thư mục model.

Hướng dẫn chi tiết về cấu trúc thư mục, thứ tự nạp animation và cấu hình theo từng model nằm trong tài liệu gốc (tiếng Trung): [docs/README.mmdskin.zh.md](docs/README.mmdskin.zh.md). Phần lớn vẫn đúng với bản này, trừ các mục về Forge và Minecraft 1.20.1.

### Card model

Card model là một file PNG: phần ảnh là nhân vật, phía sau gói toàn bộ model (lưới, texture, kích thước và màu), giống card Koikatsu. Tạo card bằng [3D Model Renderer Studio](https://github.com/intorbmn/3d-model-renderer-studio): chạy trên trình duyệt, xem trước model với các động tác của mod và nhận các loại model giống mod. Bỏ file `.png` vào `3d-skin/Cards/`. Card được xếp đầu danh sách chọn model và đánh dấu nổi bật bằng nhãn **CARD**; thư mục model thường vẫn dùng như cũ.

Card luôn có vật lý: studio chia các chuỗi spring bone thành nhóm tóc, quần áo, cơ thể và tuỳ chỉnh; mod chạy các nhóm đó cho model VRM/GLB/glTF thay cho spring bone có sẵn của model (model PMX giữ vật lý rigid body của nó). Studio còn tạo được nhân vật từ cơ thể gốc, và đọc card ILLGAMES (Koikatsu, AI Shoujo, Honey Select 2, Honey Come…) lên cơ thể đó: các card này chỉ chứa thông số cho trình tạo nhân vật, không có model 3D, nên mod không đọc trực tiếp được.

### Model GLB/glTF và animation

Animation của mod điều khiển xương theo tên chuẩn MMD. Với file GLB/glTF thường, mod tự nhận diện bộ xương người theo tên xương của các quy ước phổ biến: VRM/Unity (`LeftUpperLeg`), VRoid (`J_Bip_L_UpperLeg`), Mixamo (`mixamorig:LeftUpLeg`), Cats Blender Plugin (`Left leg`), Blender (`upper_leg.L`), Unreal (`thigh_l`). Nhận diện được thì model dùng được animation mặc định; không thì model đứng yên.

Nếu mod nhận diện sai hoặc không nhận ra, đặt file `bones.json` cạnh file model, ánh xạ tên humanoid của VRM sang tên xương trong file:

```json
{ "hips": "Pelvis", "spine": "Spine1", "head": "Head",
  "leftUpperArm": "L_Arm", "leftLowerArm": "L_Elbow", "leftHand": "L_Wrist",
  "leftUpperLeg": "L_Thigh", "leftLowerLeg": "L_Knee", "leftFoot": "L_Ankle" }
```

Cần đủ 15 xương chính: `hips`, `spine`, `head`, và `UpperArm`, `LowerArm`, `Hand`, `UpperLeg`, `LowerLeg`, `Foot` cho cả `left` lẫn `right`.

### Xuất model từ Blender

```bash
blender --background model.blend --python tools/blender/export_glb.py -- output.glb
```

Script xuất armature cùng các lưới ra GLB và dựng lại material về dạng Principled BSDF, vì trình xuất glTF của Blender bỏ mất texture của material kiểu VRM/MToon.

## Phím tắt

| Phím | Chức năng |
|---|---|
| Giữ `Alt` trái | Vòng menu chính: đổi model, chọn động tác, biểu cảm, material, cảnh, sân khấu, cài đặt |

Có thể đổi phím trong mục *Controls* của game, nhóm **3D Model Renderer**.

## Build từ mã nguồn

Cần: JDK 25, Rust (stable), CMake và trình biên dịch C++ (cho Bullet3).

```bash
git clone --recursive https://github.com/intorbmn/3d-model-renderer
cd 3d-model-renderer
./gradlew :fabric:build
```

File jar nằm ở `fabric/build/libs/`. Lệnh build sẽ tự build engine Rust trong `rust_engine/` và đóng gói thư viện native cho hệ điều hành đang dùng.

Mod ID vẫn giữ là `mmdskin` để cấu hình, phím tắt và thư mục `3d-skin` của bản gốc tiếp tục dùng được.

## Nguồn gốc và ghi công

- **[MMD Skin (MC-MMD-rust)](https://github.com/shiroha-233/MC-MMD-rust)**, của shiroha233: dự án gốc mà repo này fork từ đó, gồm engine Rust, mod Minecraft và phần lớn tính năng.
- **[KAIMyEntity](https://github.com/kjkjkAIStudio/KAIMyEntity)**, của kjkjkAIStudio và tarsin: mod MMD đầu tiên cho Minecraft.
- **[KAIMyEntity-C](https://github.com/Gengorou-C/KAIMyEntity-C)**, của Gengorou-C: tiền thân trực tiếp của MMD Skin.
- asuka-mio: người đóng góp cho MMD Skin.

Danh sách đầy đủ các thư viện và giấy phép: [ACKNOWLEDGMENTS.md](ACKNOWLEDGMENTS.md), [NOTICE](NOTICE), [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).

## Giấy phép

MIT, xem [LICENSE](LICENSE). Bản quyền của dự án gốc được giữ nguyên.
