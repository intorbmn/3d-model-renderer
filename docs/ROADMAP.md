# Lộ trình phát triển 3D Model Renderer

Cập nhật: 2026-11-13. Tài liệu này gồm hai phần: mức độ đầy đủ so với bản gốc MMD Skin, và kế hoạch cho các tính năng mới. Thứ tự các giai đoạn là thứ tự đề xuất làm.

## Phần A. So với bản gốc (MMD Skin)

| Tính năng của bản gốc | Trạng thái trên 26.3 | Ghi chú |
|---|---|---|
| Nạp model PMX / PMD / VRM | Có | Cùng engine Rust |
| Nạp GLB / glTF thường (mới, bản gốc không có) | Có | Tự nhận diện xương humanoid theo tên để dùng animation VMD |
| Animation VMD, biểu cảm VPD, vật lý Bullet | Có | Cùng engine Rust |
| Vẽ model thay người chơi | Có | Backend mới `SubmitModelInstance` (skin trên CPU, vẽ qua pipeline của Minecraft) |
| Thay thế mob, model cảnh, sân khấu | Có | Dùng chung backend mới |
| Vật phẩm cầm tay bám theo xương tay | Có | |
| Xem trước trong inventory | Có | Hướng và tỉ lệ cần kiểm tra thêm trong game |
| Đồng bộ nhiều người chơi | Có | Giao thức giữ nguyên, đổi sang payload của 26.3 |
| Vòng menu, chọn model, cài đặt | Có | |
| Viền toon (outline) | Có, mới port | Vẽ bằng kỹ thuật inverted hull; chỉ bật khi bật toon trong cài đặt |
| Ẩn tay gốc ở góc nhìn thứ nhất | Có, mới port | `FirstPersonHandsMixin`; phần hook riêng cho tay VR của Vivecraft chưa port |
| Tô bóng toon (cel shading, rim, specular) | Chưa | Cần shader riêng; xem giai đoạn 4 |
| GPU skinning (compute shader) | Chưa | Mặc định tắt ở bản gốc; xem giai đoạn 4 |
| "MMD shader" riêng | Chưa | Mặc định tắt ở bản gốc; xem giai đoạn 4 |
| Tương thích Iris (shader pack) | Chưa | Phụ thuộc Iris có bản 26.3 |
| Tương thích Vivecraft (VR) | Một phần | Mixin tay VR còn; mixin renderer người chơi VR chưa port |
| Tương thích Touhou Little Maid | Chưa | Phụ thuộc mod đó có bản Fabric 26.3 |
| Forge / NeoForge | Không làm | Repo này chỉ hỗ trợ Fabric 26.3 |

Ba mục toon, GPU skinning và MMD shader đều tắt mặc định ở bản gốc, nên trải nghiệm mặc định hiện đã gần tương đương. Chúng phụ thuộc cùng một việc: tạo render pipeline riêng trên API đồ hoạ mới của 26.3, nên được gộp vào giai đoạn 4.

## Phần B. Kế hoạch tính năng mới

### Giai đoạn 1. Sửa trục model bị ngược trước ra sau

> **Tiến độ (2026-11-13).** Đã làm bước 1 và 2: GLB thường không còn bị xoay 180° (file VRM giữ nguyên cách xử lý cũ). Model không có tên xương đầu/mắt được ước lượng vùng đầu và vị trí mắt từ lưới để dùng góc nhìn thứ nhất. Còn lại: bước 3 đến 6 và lỗi vùng đen.

**Hiện trạng.** Loader glTF (`rust_engine/src/model/vrm_mesh.rs`, `vrm_skeleton.rs`) luôn lật trục X và Z (xoay 180° quanh trục Y) để đưa model VRM về không gian của PMX. Phép lật này đúng với VRM 1.0 (mặt hướng +Z) nhưng được áp cho mọi file glTF, kể cả GLB thường. Model GLB xuất từ PMX (ví dụ từ Sketchfab) vì vậy bị quay lưng lại. Phép biến đổi ở node gốc của file (Sketchfab hay thêm một node xoay) cũng chưa được tính vào lưới.

**Việc cần làm.**
1. Tách phép lật thành tham số của loader thay vì cố định.
2. Chọn hướng theo loại file: VRM 1.0 lật, VRM 0.x và glTF thường xét riêng.
3. Với glTF thường, tự đoán hướng mặt từ bộ xương: vị trí ngón chân so với gót, hoặc hướng pháp tuyến trung bình của vùng mặt.
4. Áp ma trận world của node chứa mesh khi gộp lưới.
5. Thêm tuỳ chọn "xoay model 180°" trong cài đặt riêng của từng model, để người dùng sửa tay khi tự đoán sai.
6. Thêm test Rust với file GLB mẫu (đã có khung `rust_engine/tests/generic_gltf.rs`).

**Kiểm chứng.** Model GLB lỗi hiện tại quay đúng mặt về phía trước; các model VRM và PMX đang đúng không đổi hướng.

**Liên quan.** Lỗi "vùng đen" trên cùng model nên xử lý trong giai đoạn này. Texture mặc định của Minecraft vốn đã ở chế độ lặp (repeat) nên đó không phải nguyên nhân; nghi vấn chính là pháp tuyến bị lật sai cùng với phép lật trục, khiến shader của Minecraft tô tối các mặt đó.

### Giai đoạn 2. Animation mẫu cho model chưa có animation

> **Tiến độ (2026-11-14).** Đã làm bước 2 và 5: model glTF/GLB thường được nhận diện bộ xương humanoid theo tên (VRM/Unity, VRoid, Mixamo, Cats, Blender, Unreal, MMD xuất tên tiếng Anh) hoặc theo tệp `bones.json`, rồi dùng được animation VMD mặc định. Đã kiểm bằng test với hai model xuất từ Blender. Còn lại: bước 1 (dò theo cấu trúc cho model hỏng tên xương), 3, 4 và 6.

**Hiện trạng.** Mod đã có sẵn 22 animation VMD mặc định (`assets/mmdskin/default_anim`: idle, walk, sprint, sneak, swim...). Chúng điều khiển xương theo tên tiếng Nhật chuẩn MMD. Model VRM dùng được nhờ bảng ánh xạ humanoid (`rust_engine/src/model/bone_mapping.rs`). Model GLB thường không có bảng này, và file từ Sketchfab còn bị hỏng tên xương, nên đứng yên ở tư thế gốc.

**Việc cần làm.**
1. **Nhận diện bộ xương theo cấu trúc.** Dò cây xương để tìm hông, cột sống, cổ, đầu, hai tay, hai chân dựa trên quan hệ cha con và vị trí (trái/phải theo X, trên/dưới theo Y), không dựa vào tên. Kết quả là một bảng ánh xạ humanoid giống của VRM.
2. **Nhận diện theo tên** cho các quy ước phổ biến (Mixamo, Unity Humanoid, Blender Rigify, tên MMD tiếng Anh) trước khi phải dò theo cấu trúc.
3. **Retarget.** Bù khác biệt tư thế gốc (A-pose so với T-pose) và tỉ lệ chân khi áp animation VMD.
4. **IK chân tối giản** cho model không có xương IK, để bàn chân không trượt.
5. **Tệp ánh xạ tay** (`bones.json` trong thư mục model) cho trường hợp tự nhận diện sai.
6. **Bộ animation mẫu dựng sẵn**: bổ sung các động tác còn thiếu so với danh sách trạng thái của Minecraft, đóng gói kèm mod.

**Kiểm chứng.** Model GLB mẫu đi, chạy, ngồi xổm được bằng animation mặc định; model PMX và VRM không đổi hành vi.

**Rủi ro.** Dò theo cấu trúc sẽ sai với model có nhiều xương phụ (váy, tóc, nơ). Vì vậy bước 5 là bắt buộc, không phải tuỳ chọn.

### Giai đoạn 3. Làm lại toàn bộ UI theo dạng tĩnh, đổi phong cách

**Hiện trạng.** Vòng menu (`ui/wheel/AbstractWheelScreen.java`) vẽ từng hình quạt bằng cách tô từng dòng ngang qua `fill()`, lặp lại mỗi khung hình, cộng thêm hiệu ứng lò xo cho từng ô. Đây là nguồn gây lag chính của UI: mỗi khung hình phát sinh hàng nghìn lệnh tô. Các màn hình khác (`ui/selector`, `ui/stage`, `ui/config`) tự vẽ nền mờ và khung riêng.

**Hướng thiết kế.**
- **Tĩnh.** Bố cục chỉ tính lại khi mở màn hình hoặc đổi kích thước cửa sổ. Không còn hiệu ứng chạy theo khung hình. Trạng thái hover chỉ đổi màu.
- **Thay vòng tròn bằng lưới hoặc danh sách.** Menu chính thành một bảng các nút chữ nhật, dùng widget có sẵn của Minecraft. Nút chữ nhật vẽ bằng một lệnh, và chữ dài không còn bị tràn như ở hình quạt.
- **Một bộ thành phần dùng chung.** Nút, danh sách cuộn, thanh trượt, ô xem trước model, tiêu đề; mọi màn hình dùng lại bộ này thay vì tự vẽ.
- **Phong cách.** Cần bạn chọn trước khi làm: phẳng tối giản, giống giao diện gốc của Minecraft, hay kiểu bảng điều khiển trong suốt. Mình sẽ dựng bản mẫu vài phương án để bạn duyệt.

**Việc cần làm.**
1. Chốt phong cách và sơ đồ các màn hình (bản mẫu trước, code sau).
2. Viết bộ thành phần dùng chung.
3. Làm lại menu chính, rồi chọn model, cài đặt model, động tác, biểu cảm, material, cảnh, sân khấu.
4. Chuyển toàn bộ chuỗi hiển thị sang file ngôn ngữ; thêm tiếng Việt (`vi_vn.json`).
5. Xoá mã vẽ vòng tròn cũ.

**Kiểm chứng.** Mở menu không làm tụt FPS so với khi đóng; không còn chữ tràn ở cả ba ngôn ngữ và ở các mức GUI scale 1 đến 4.

### Giai đoạn 4. Tối ưu và nâng cấp phần render

**Hiện trạng.** Mỗi khung hình, với mỗi model: engine Rust skin toàn bộ đỉnh trên CPU; Java chép vị trí và pháp tuyến sang mảng, nhân ma trận cho từng đỉnh, rồi gọi `addVertex` 4 lần cho mỗi tam giác (render type của entity vẽ theo quad). Model 100 nghìn tam giác tốn khoảng 400 nghìn lần gọi mỗi khung hình.

**Việc cần làm, theo thứ tự lợi ích trên chi phí.**
1. **Bỏ qua việc không cần thiết.** Không dựng lại lưới khi model ngoài tầm nhìn; giảm tần suất cập nhật animation cho model ở xa; không chép lại dữ liệu khi animation không đổi.
2. **Đo đạc.** Thêm số liệu thời gian từng bước (skin, chép, dựng lưới) vào HUD debug có sẵn, để mọi bước sau có số liệu so sánh.
3. **Pipeline riêng vẽ tam giác có chỉ mục.** Tạo render pipeline riêng trên API `renderpearl` của 26.3, vẽ thẳng bằng index buffer: bỏ được đỉnh thứ tư thừa và giữ buffer trên GPU giữa các khung hình.
4. **Shader riêng.** Khi đã có pipeline riêng thì port lại tô bóng toon, rim, specular và "MMD shader" của bản gốc (các file GLSL vẫn còn trong `assets/mmdskin/shader`).
5. **GPU skinning.** Đưa ma trận xương lên GPU và skin trong vertex shader, thay cho compute shader của bản gốc; CPU chỉ còn tính xương và vật lý.
6. **Dọn mã cũ.** Xoá các backend OpenGL cũ đang bị loại khỏi build khi mọi tính năng của chúng đã có bản thay thế.

**Kiểm chứng.** So FPS trước và sau với cùng một cảnh: 1 model nặng (100 nghìn tam giác) và 10 model nhẹ. Hình ảnh không đổi ở bước 1 đến 3.

**Rủi ro.** Bước 3 trở đi phụ thuộc API đồ hoạ mới của 26.3, vốn hỗ trợ cả OpenGL lẫn Vulkan; shader phải viết sao cho chạy được trên cả hai. Tương thích Iris nên xét sau bước 4, khi Iris có bản 26.3.

### Giai đoạn 5. Thêm định dạng model và "card" model

**Định dạng hiện nhận:** PMX, PMD, VRM, GLB, glTF (`.gltf` kèm file rời).

**Định dạng cân nhắc thêm.**

| Định dạng | Đánh giá |
|---|---|
| FBX | Engine đã có bộ đọc FBX nhưng chỉ dùng cho animation. Nạp cả lưới và trọng số xương là việc lớn; trước mắt nên chuyển qua Blender bằng `tools/blender/export_glb.py`. |
| glTF không có xương (đạo cụ, model tĩnh) | Làm được bằng cách tự tạo một xương gốc; hữu ích cho model cảnh, ít giá trị cho model người chơi. |
| OBJ, STL | Không có xương và không có material đầy đủ; chỉ nên hỗ trợ sau mục trên, cùng một đường xử lý. |
| `.blend` | Không đọc trực tiếp được (định dạng nội bộ của Blender, đổi theo từng phiên bản). Dùng script xuất ở trên. |

**Cơ chế card kiểu Koikatsu.** Card của Koikatsu là ảnh PNG có gắn thêm dữ liệu nhân vật ở cuối file. Dữ liệu đó là thông số cho trình tạo nhân vật của game (thanh trượt cơ thể, mã trang phục, mod), không chứa lưới 3D; muốn dựng lại model phải có chính game và tài nguyên của nó. Vì vậy mod không thể đọc thẳng card Koikatsu. Hai hướng khả thi:

1. **Card riêng của mod (đề xuất).** Một file PNG duy nhất: phần ảnh là ảnh xem trước, phần dữ liệu gắn sau là gói model (file model, texture, cấu hình, `bones.json`). Người dùng chỉ cần thả một file vào thư mục; màn hình chọn model hiển thị ảnh như một bộ sưu tập card; chia sẻ model chỉ là gửi một ảnh.
   - Việc cần làm: định nghĩa định dạng (chunk riêng trong PNG, có phiên bản và mã kiểm tra), bộ đọc trong engine, bộ đóng gói (lệnh trong game "tạo card từ model đang dùng", tự chụp ảnh xem trước), giao diện lưới card (làm cùng giai đoạn 3).
   - Rủi ro: file lớn (model vài chục MB nằm trong một ảnh); nhiều nền tảng chia sẻ ảnh sẽ nén lại và làm mất dữ liệu gắn kèm; chia sẻ card là phân phối lại model, phải tôn trọng điều khoản của tác giả model, nên card cần mang theo thông tin tác giả và giấy phép.
2. **Dùng model từ Koikatsu qua công cụ có sẵn.** Plugin KKBP xuất nhân vật ngay trong game ra file PMX, mod đọc được PMX. Việc này không cần sửa mod, chỉ cần viết hướng dẫn.

### Việc treo, chưa xếp giai đoạn

- Mixin renderer người chơi cho Vivecraft; tương thích Touhou Little Maid và Iris: chờ các mod đó có bản 26.3.
- Bản phát hành chính thức (workflow `build-mod.yml` đã có nhưng chưa chạy thử trên 26.3).
