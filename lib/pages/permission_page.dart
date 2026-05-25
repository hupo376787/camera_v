import 'package:flutter/material.dart';

import '../main.dart';

class PermissionPage extends StatelessWidget {
  const PermissionPage({super.key});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        const Text('权限引导', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
        const SizedBox(height: 8),
        const Text('请依次完成悬浮窗、相机、无障碍、通知和前台服务权限。'),
        const SizedBox(height: 16),
        FilledButton(
          onPressed: () => CameraBridge.openOverlayPermission(),
          child: const Text('打开悬浮窗权限设置'),
        ),
        const SizedBox(height: 8),
        FilledButton(
          onPressed: () => CameraBridge.openCameraPermissionPage(),
          child: const Text('申请相机权限'),
        ),
        const SizedBox(height: 8),
        FilledButton(
          onPressed: () => CameraBridge.openAccessibilityPermission(),
          child: const Text('打开无障碍设置'),
        ),
        const SizedBox(height: 8),
        FilledButton(
          onPressed: () => CameraBridge.openNotificationPermission(),
          child: const Text('打开通知权限设置'),
        ),
      ],
    );
  }
}
