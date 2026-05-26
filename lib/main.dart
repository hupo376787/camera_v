import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'pages/gallery_page.dart';
import 'pages/home_page.dart';
import 'pages/permission_page.dart';
import 'pages/settings_page.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const CameraVApp());
}

class CameraVApp extends StatefulWidget {
  const CameraVApp({super.key});

  @override
  State<CameraVApp> createState() => _CameraVAppState();
}

class _CameraVAppState extends State<CameraVApp> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Floating Camera',
      theme: ThemeData(colorSchemeSeed: Colors.teal, useMaterial3: true),
      home: Scaffold(
        appBar: AppBar(title: const Text('悬浮球静默拍照系统')),
        body: IndexedStack(
          index: _index,
          children: const [
            HomePage(),
            PermissionPage(),
            GalleryPage(),
            SettingsPage(),
          ],
        ),
        bottomNavigationBar: NavigationBar(
          selectedIndex: _index,
          destinations: const [
            NavigationDestination(icon: Icon(Icons.home), label: 'Home'),
            NavigationDestination(icon: Icon(Icons.security), label: 'Permission'),
            NavigationDestination(icon: Icon(Icons.photo_library), label: 'Gallery'),
            NavigationDestination(icon: Icon(Icons.settings), label: 'Settings'),
          ],
          onDestinationSelected: (value) => setState(() => _index = value),
        ),
      ),
    );
  }
}

class CameraBridge {
  CameraBridge._();

  static const _channel = MethodChannel('floating_camera_channel');
  static final StreamController<String> _photos = StreamController.broadcast();
  static final StreamController<String> _errors = StreamController.broadcast();
  static final StreamController<bool> _service = StreamController.broadcast();
  static bool _initialized = false;

  static Stream<String> get photoSavedStream => _photos.stream;
  static Stream<String> get errorStream => _errors.stream;
  static Stream<bool> get serviceStream => _service.stream;

  static Future<void> init() async {
    if (_initialized) return;
    _initialized = true;
    _channel.setMethodCallHandler((call) async {
      switch (call.method) {
        case 'onPhotoSaved':
          if (call.arguments is String) _photos.add(call.arguments as String);
          break;
        case 'onServiceStatusChanged':
          _service.add(call.arguments == true);
          break;
        case 'onError':
          if (call.arguments is String) _errors.add(call.arguments as String);
          break;
      }
    });
  }

  static Future<void> startService() => _channel.invokeMethod('startService');

  static Future<void> stopService() => _channel.invokeMethod('stopService');

  static Future<bool> isServiceRunning() async {
    final result = await _channel.invokeMethod('isServiceRunning');
    return result == true;
  }

  static Future<void> requestServiceStatus() => _channel.invokeMethod('requestServiceStatus');

  static Future<void> takePhoto() => _channel.invokeMethod('takePhoto');

  static Future<void> toggleFloatingBall() => _channel.invokeMethod('toggleFloatingBall');

  static Future<void> switchCamera() => _channel.invokeMethod('switchCamera');

  static Future<void> updateSettings({
    String resolution = 'max',
    bool flashEnabled = false,
    bool autoFocus = true,
  }) {
    return _channel.invokeMethod('updateSettings', {
      'resolution': resolution,
      'flashEnabled': flashEnabled,
      'autoFocus': autoFocus,
    });
  }

  static Future<Map<String, dynamic>> getSettings() async {
    final result = await _channel.invokeMethod('getSettings');
    if (result is Map) {
      return result.map((key, value) => MapEntry('$key', value));
    }
    return <String, dynamic>{};
  }

  static Future<List<String>> loadGallery() async {
    final result = await _channel.invokeMethod('getGalleryPhotos');
    if (result is List) {
      return result.map((e) => '$e').toList();
    }
    return [];
  }

  static Future<Uint8List?> loadPhotoBytes(String uri) async {
    final result = await _channel.invokeMethod('getPhotoBytes', {'uri': uri});
    return result is Uint8List ? result : null;
  }

  static Future<int> deletePhotos(List<String> uris) async {
    final result = await _channel.invokeMethod('deletePhotos', {'uris': uris});
    return result is int ? result : 0;
  }

  static Future<int> copyPhotosToFolder(List<String> uris, String folder) async {
    final result = await _channel.invokeMethod('copyPhotosToFolder', {
      'uris': uris,
      'folder': folder,
    });
    return result is int ? result : 0;
  }

  static Future<void> openOverlayPermission() => _channel.invokeMethod('openOverlayPermission');

  static Future<void> openAccessibilityPermission() =>
      _channel.invokeMethod('openAccessibilityPermission');

  static Future<void> openNotificationPermission() =>
      _channel.invokeMethod('openNotificationPermission');

  static Future<void> openCameraPermissionPage() =>
      _channel.invokeMethod('openCameraPermissionPage');
}
