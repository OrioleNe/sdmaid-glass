# Thay đổi so với SD Maid SE gốc (bản "Glass")

Đây là bản chỉnh sửa của SD Maid SE (GPLv3). Các thay đổi:

- `app-common-ui/.../common/theming/GlassBackdrop.kt` (mới): hiệu ứng kính mờ tự viết bằng
  `GraphicsLayer` + `BlurEffect`, không thêm thư viện ngoài. Làm mờ thật từ Android 12 trở lên;
  máy cũ hơn dùng lớp màu gần đặc.
- `app/.../main/ui/dashboard/DashboardScreen.kt`: tạo `GlassBackdrop`, đánh dấu danh sách cuộn là nguồn.
- `app/.../main/ui/dashboard/bottom/DashboardBottomBar.kt`: thanh dưới dùng kính mờ khi có backdrop
  (tham số `glassBackdrop`, mặc định `null` = giao diện cũ).
- `.github/workflows/build-apk.yml` (mới): build APK FOSS debug trên GitHub Actions.

Mã chưa được biên dịch trước khi đóng gói; nếu build lỗi, xem log của bước "Build FOSS debug APK".
