# YouTube Ad Blocker (tiện ích Chrome)

Tiện ích Chrome (Manifest V3) giúp chặn quảng cáo trên YouTube.

## Tính năng
- **Tự động bỏ qua quảng cáo video**: bấm nút "Bỏ qua", tắt tiếng và tua nhanh quảng cáo không bỏ qua được.
- **Ẩn quảng cáo hiển thị**: banner trang chủ, quảng cáo trong danh sách video, quảng cáo bên cạnh trình phát, lớp phủ quảng cáo.
- **Chặn tên miền quảng cáo** (doubleclick, googlesyndication, `/pagead/`, `/api/stats/ads`...) bằng `declarativeNetRequest`.
- **Tự đóng hộp thoại cảnh báo trình chặn quảng cáo** của YouTube.
- **Cửa sổ popup**: bật/tắt nhanh và xem số quảng cáo video đã bỏ qua.

## Cài đặt
1. Tải thư mục `youtube-adblocker` về máy.
2. Mở Chrome, vào `chrome://extensions`.
3. Bật **Chế độ dành cho nhà phát triển** (Developer mode) ở góc trên bên phải.
4. Bấm **Tải tiện ích đã giải nén** (Load unpacked) và chọn thư mục `youtube-adblocker`.
5. Mở/tải lại YouTube.

## Cấu trúc
```
manifest.json       Khai báo tiện ích
rules.json          Luật chặn tên miền/đường dẫn quảng cáo
src/content.js      Bỏ qua quảng cáo video, đóng lớp phủ
src/hide.css        Ẩn các khối quảng cáo
src/background.js   Bật/tắt luật chặn, đếm số quảng cáo
src/popup.*         Giao diện popup
icons/              Biểu tượng
```

## Lưu ý
YouTube thường xuyên thay đổi giao diện; nếu quảng cáo xuất hiện trở lại, hãy cập nhật các bộ chọn trong `src/hide.css` và `src/content.js`.
