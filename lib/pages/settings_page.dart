import 'package:flutter/material.dart';

import '../main.dart';

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  static const resolutions = ['640x480', '1280x720', '1920x1080'];

  String _resolution = resolutions.last;
  bool _flashEnabled = false;
  bool _autoFocus = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final current = await CameraBridge.getSettings();
    if (!mounted) return;
    setState(() {
      _resolution = (current['resolution'] as String?) ?? _resolution;
      _flashEnabled = (current['flashEnabled'] as bool?) ?? _flashEnabled;
      _autoFocus = (current['autoFocus'] as bool?) ?? _autoFocus;
    });
  }

  Future<void> _save() async {
    await CameraBridge.updateSettings(
      resolution: _resolution,
      flashEnabled: _flashEnabled,
      autoFocus: _autoFocus,
    );
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Semantics(
          liveRegion: true,
          child: Text('设置已保存'),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        DropdownButtonFormField<String>(
          value: _resolution,
          items: resolutions.map((e) => DropdownMenuItem(value: e, child: Text(e))).toList(),
          decoration: const InputDecoration(labelText: '分辨率'),
          onChanged: (value) {
            if (value == null) return;
            setState(() => _resolution = value);
          },
        ),
        const SizedBox(height: 12),
        SwitchListTile(
          value: _flashEnabled,
          onChanged: (value) => setState(() => _flashEnabled = value),
          title: const Text('启用闪光灯'),
        ),
        SwitchListTile(
          value: _autoFocus,
          onChanged: (value) => setState(() => _autoFocus = value),
          title: const Text('启用自动对焦'),
        ),
        const SizedBox(height: 12),
        FilledButton(onPressed: _save, child: const Text('保存')),
      ],
    );
  }
}
