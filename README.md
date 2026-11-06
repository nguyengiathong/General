# AdBlock Browser – Trình duyệt chặn quảng cáo cho Android

Ứng dụng trình duyệt web nhẹ cho Android (Kotlin, WebView) có tích hợp chặn quảng cáo.

## Tính năng

- **Chặn quảng cáo theo tên miền**: mọi request (script, ảnh, iframe, XHR…) tới tên miền quảng cáo/theo dõi đều bị chặn trước khi tải (`shouldInterceptRequest`). Chặn cả tên miền con.
- **Danh sách chặn mặc định** (`app/src/main/assets/adblock_hosts.txt`) gồm Google Ads, các mạng quảng cáo quốc tế và **mạng quảng cáo Việt Nam** (Admicro, Eclick, Adtima…).
- **Cập nhật danh sách chặn** từ Internet (StevenBlack hosts, ~100.000 tên miền) qua menu.
- **Ẩn khung quảng cáo** còn sót lại bằng CSS (cosmetic filter).
- **Chặn popup / tab tự mở** và **chặn chuyển hướng** tới trang quảng cáo.
- Chặn cookie bên thứ ba.
- **Whitelist**: cho phép quảng cáo trên trang bạn muốn ủng hộ.
- Bật/tắt chặn quảng cáo, xem số quảng cáo đã chặn trên trang và tổng cộng.
- Có thể đặt làm **trình duyệt mặc định** khi mở link.
- Thanh địa chỉ thông minh: gõ từ khóa sẽ tìm bằng DuckDuckGo.

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
| `app/src/main/java/com/adblock/browser/MainActivity.kt` | Giao diện trình duyệt, WebViewClient chặn request, menu |
| `app/src/main/assets/adblock_hosts.txt` | Danh sách tên miền bị chặn mặc định (có thể tự thêm) |

## Ghi chú

- Ứng dụng chỉ chặn quảng cáo **bên trong trình duyệt này**, không chặn trong app khác (việc đó cần VPN/DNS như "Private DNS" `dns.adguard-dns.com`).
- Quảng cáo YouTube chèn trong video phát từ cùng tên miền với nội dung nên có thể không chặn được hoàn toàn.
- APK release đang ký bằng debug key cho tiện cài; khi phát hành lên Play Store hãy cấu hình keystore riêng.

## Tải APK trực tiếp

Sau mỗi lần build, workflow tự commit APK vào `apk/AdBlockBrowser.apk`.
Link tải (mở trên điện thoại): https://github.com/nguyengiathong/General/raw/claude/bold-mendel-4a5aqd/apk/AdBlockBrowser.apk
