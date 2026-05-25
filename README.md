# camera_v

Android + Flutter 混合工程：悬浮球触发静默拍照系统（Android 12~16）。

## 目录结构

```
android/app/src/main/kotlin/com/example/camera_v/
  service/FloatingCameraService.kt
  camera/Camera2Controller.kt
  overlay/FloatingBallView.kt
  accessibility/FloatingAccessibilityService.kt
  media/MediaStoreHelper.kt
  MainActivity.kt

lib/
  main.dart
  pages/home_page.dart
  pages/permission_page.dart
  pages/gallery_page.dart
  pages/settings_page.dart
```

## 核心能力

- 前台 `ForegroundService`（`foregroundServiceType="camera"`）常驻并管理 Camera2 生命周期。
- `TYPE_APPLICATION_OVERLAY` 可拖动悬浮球，点击后无可见预览进行拍照，长按则关闭 APP。
- 通过 `ImageReader` 拍照并写入 `MediaStore` 到 `Pictures/CameraV/`。
- 手机锁屏（`ACTION_SCREEN_OFF`）时会自动停止前台服务并关闭 APP。
- Flutter 通过 `MethodChannel("floating_camera_channel")` 与 Android 通信：
  - Flutter -> Android：`startService`, `stopService`, `takePhoto`, `toggleFloatingBall`, `switchCamera`
  - Android -> Flutter：`onPhotoSaved(uri)`, `onServiceStatusChanged`, `onError(message)`

## 构建说明

本仓库使用 Flutter 标准 Android Gradle 插件配置。构建前请确保：

1. 已安装 Flutter stable，并在 `android/local.properties` 配置 `flutter.sdk=...`
2. Android Studio / SDK 已安装（minSdk 31，targetSdk 35）