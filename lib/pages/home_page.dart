import 'dart:async';

import 'package:flutter/material.dart';

import '../main.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  bool _serviceRunning = false;
  String _lastPhotoUri = '';
  String _error = '';
  StreamSubscription<bool>? _serviceSub;
  StreamSubscription<String>? _photoSub;
  StreamSubscription<String>? _errorSub;

  @override
  void initState() {
    super.initState();
    CameraBridge.init();
    _serviceSub = CameraBridge.serviceStream.listen((running) {
      if (!mounted) return;
      setState(() => _serviceRunning = running);
    });
    _photoSub = CameraBridge.photoSavedStream.listen((uri) {
      if (!mounted) return;
      setState(() => _lastPhotoUri = uri);
    });
    _errorSub = CameraBridge.errorStream.listen((message) {
      if (!mounted) return;
      setState(() => _error = message);
    });
  }

  @override
  void dispose() {
    _serviceSub?.cancel();
    _photoSub?.cancel();
    _errorSub?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        ListTile(
          title: const Text('ForegroundService 状态'),
          subtitle: Text(_serviceRunning ? '运行中' : '未运行'),
          trailing: Switch(
            value: _serviceRunning,
            onChanged: (value) async {
              if (value) {
                await CameraBridge.startService();
              } else {
                await CameraBridge.stopService();
              }
            },
          ),
        ),
        const SizedBox(height: 12),
        FilledButton.icon(
          onPressed: () => CameraBridge.toggleFloatingBall(),
          icon: const Icon(Icons.circle),
          label: const Text('显示/隐藏悬浮球'),
        ),
        const SizedBox(height: 12),
        FilledButton.icon(
          onPressed: () => CameraBridge.takePhoto(),
          icon: const Icon(Icons.camera_alt),
          label: const Text('立即拍照'),
        ),
        const SizedBox(height: 12),
        FilledButton.icon(
          onPressed: () => CameraBridge.switchCamera(),
          icon: const Icon(Icons.flip_camera_android),
          label: const Text('切换前后摄像头'),
        ),
        if (_lastPhotoUri.isNotEmpty) ...[
          const SizedBox(height: 16),
          Text('最近照片: $_lastPhotoUri'),
        ],
        if (_error.isNotEmpty) ...[
          const SizedBox(height: 16),
          Text('错误: $_error', style: TextStyle(color: Theme.of(context).colorScheme.error)),
        ],
      ],
    );
  }
}
