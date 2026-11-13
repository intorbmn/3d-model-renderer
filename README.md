# 3D Model Renderer

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
| Model GLB/glTF không có VRM | Nạp được; một số model hiển thị lỗi |
| Tay ở góc nhìn thứ nhất | Chưa port |
| Tương thích Touhou Little Maid, Vivecraft, Iris | Chưa port |
| Forge / NeoForge, bản Minecraft cũ | Không hỗ trợ trong repo này |

## Cài đặt

Yêu cầu: Minecraft 26.3, Fabric Loader 0.20.1+, Java 25, [Fabric API](https://modrinth.com/mod/fabric-api), [Cloth Config](https://modrinth.com/mod/cloth-config).

1. Tải file jar từ mục **Artifacts** của lượt chạy mới nhất trong tab [Actions](https://github.com/intorbmn/3d-model-renderer/actions) (`3d-model-renderer-fabric-26.3-windows-x64` hoặc `linux-x64`).
2. Bỏ jar vào thư mục `.minecraft/mods/`.
3. Chạy game một lần, mod sẽ tạo thư mục `.minecraft/3d-skin/`.

## Thêm model

Mỗi model nằm trong một thư mục riêng dưới `.minecraft/3d-skin/EntityPlayer/`:

```
.minecraft/3d-skin/
├── EntityPlayer/
│   ├── Miku/
│   │   ├── model.pmx        (hoặc .pmd, .vrm, .glb)
│   │   └── ...              (các file texture mà model dùng, giữ đúng đường dẫn)
│   └── ...
├── DefaultAnim/             animation mặc định (.vmd)
├── CustomAnim/              animation của bạn (.vmd)
├── DefaultMorph/            biểu cảm mặc định (.vpd)
└── CustomMorph/             biểu cảm của bạn (.vpd)
```

Trong game, giữ **Alt trái** để mở vòng menu, chọn **Model Switch** rồi chọn thư mục model.

Hướng dẫn chi tiết về cấu trúc thư mục, thứ tự nạp animation và cấu hình theo từng model nằm trong tài liệu gốc (tiếng Trung): [docs/README.mmdskin.zh.md](docs/README.mmdskin.zh.md). Phần lớn vẫn đúng với bản này, trừ các mục về Forge và Minecraft 1.20.1.

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
