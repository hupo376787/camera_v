import 'package:flutter/material.dart';

import '../main.dart';

class GalleryPage extends StatefulWidget {
  const GalleryPage({super.key});

  @override
  State<GalleryPage> createState() => _GalleryPageState();
}

class _GalleryPageState extends State<GalleryPage> {
  bool _loading = true;
  List<String> _uris = const [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final uris = await CameraBridge.loadGallery();
    if (!mounted) return;
    setState(() {
      _uris = uris;
      _loading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_uris.isEmpty) {
      return const Center(child: Text('暂无拍照记录'));
    }
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView.builder(
        itemCount: _uris.length,
        itemBuilder: (context, index) {
          final uri = _uris[index];
          return ListTile(
            leading: const Icon(Icons.image),
            title: Text(uri, maxLines: 1, overflow: TextOverflow.ellipsis),
            subtitle: const Text('点击预览'),
            onTap: () => showDialog<void>(
              context: context,
              builder: (context) => Dialog(
                child: FutureBuilder(
                  future: CameraBridge.loadPhotoBytes(uri),
                  builder: (context, snapshot) {
                    if (!snapshot.hasData) {
                      return const Padding(
                        padding: EdgeInsets.all(24),
                        child: Center(child: CircularProgressIndicator()),
                      );
                    }
                    return Padding(
                      padding: const EdgeInsets.all(8),
                      child: Image.memory(snapshot.data!),
                    );
                  },
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}
