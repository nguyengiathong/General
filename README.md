# AdBlock Browser – Trình duyệt chặn quảng cáo cho Android

Ứng dụng trình duyệt web nhẹ cho Android (Kotlin, WebView) có tích hợp chặn quảng cáo.

## Tính năng

### Chặn quảng cáo toàn bộ điện thoại (nút BẬT/TẮT)
- Màn hình chính có nút **BẬT/TẮT** lớn. Khi bật, quảng cáo bị chặn trong **Chrome, Firefox, Cốc Cốc… và cả các app khác**.
- Cách hoạt động: app tạo một **VPN cục bộ** chỉ nhận các truy vấn DNS (không gửi dữ liệu ra máy chủ nào). Tên miền quảng cáo được trả lời "không tồn tại" nên quảng cáo không tải được; truy vấn khác được chuyển tới DNS thật (1.1.1.1 / 8.8.8.8). Lưu lượng web bình thường không đi qua VPN nên không làm chậm mạng.
- Lần bật đầu tiên app tự tải danh sách chặn đầy đủ (StevenBlack, ~100.000 tên miền), có nút cập nhật lại.
- Thông báo thường trực có nút **Tắt** nhanh; tự bật lại sau khi khởi động máy nếu trước đó đang bật.
- Hiển thị số lượt đã chặn và các tên miền bị chặn gần đây.

**Để chặn hiệu quả:**
- *Cài đặt → Mạng → DNS riêng tư*: chọn **Tắt** hoặc **Tự động** (nếu đặt tên nhà cung cấp riêng, DNS sẽ đi vòng qua app).
- *Chrome → Cài đặt → Quyền riêng tư và bảo mật → Dùng DNS bảo mật*: **Tắt**.
- Sau khi bật, đóng hẳn rồi mở lại trình duyệt.
- Chỉ dùng được một VPN tại một thời điểm (nếu đang dùng VPN khác thì app không bật được).

### Trình duyệt tích hợp
- Nút "Mở trình duyệt tích hợp": trình duyệt có thêm ẩn khung quảng cáo bằng CSS, chặn popup/chuyển hướng, whitelist từng trang.

## Lấy file APK

**Cách 1 – GitHub Actions (không cần cài gì):** mỗi lần push, workflow `Build APK` tự build.
Vào tab **Actions** → chọn lần chạy mới nhất → tải artifact **AdBlockBrowser-apk** → giải nén và cài `app-release.apk` lên điện thoại (bật "Cài ứng dụng không rõ nguồn gốc").

**Cách 2 – Android Studio:** mở thư mục dự án → *Run* lên thiết bị.

**Cách 3 – Dòng lệnh** (cần JDK 17 và Android SDK, đặt `ANDROID_HOME`):

```bash
./gradlew assembleDebug
```

APK nằm ở `app/build/outputs/apk/debug/app-debug.apk`.

## Cấu trúc

| File | Vai trò |
|---|---|
| `app/src/main/java/com/adblock/browser/AdBlocker.kt` | Nạp danh sách, kiểm tra URL quảng cáo, cập nhật từ Internet, CSS ẩn quảng cáo |
| `app/src/main/java/com/adblock/browser/MainActivity.kt` | Màn hình chính với nút BẬT/TẮT |
| `app/src/main/java/com/adblock/browser/AdBlockVpnService.kt` | VPN cục bộ lọc DNS cho toàn hệ thống |
| `app/src/main/java/com/adblock/browser/Packets.kt` | Phân tích/dựng gói IPv4/UDP/DNS |
| `app/src/main/java/com/adblock/browser/BrowserActivity.kt` | Trình duyệt tích hợp |
| `app/src/main/assets/adblock_hosts.txt` | Danh sách tên miền bị chặn mặc định (có thể tự thêm) |

## Ghi chú

- Chặn theo DNS không chặn được quảng cáo phục vụ từ cùng tên miền với nội dung, ví dụ quảng cáo YouTube chèn trong video hay bài đăng được tài trợ trên Facebook.
- APK release đang ký bằng debug key cho tiện cài; khi phát hành lên Play Store hãy cấu hình keystore riêng.

## Tải APK trực tiếp

Sau mỗi lần build, workflow tự commit APK vào `apk/AdBlockBrowser.apk`.
Link tải (mở trên điện thoại): https://github.com/nguyengiathong/General/raw/claude/bold-mendel-4a5aqd/apk/AdBlockBrowser.apk
