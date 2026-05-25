import 'dart:typed_data';

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
  final Map<String, Future<Uint8List?>> _thumbnailFutures = {};

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final uris = await CameraBridge.loadGallery();
    final urisSet = uris.toSet();
    if (!mounted) return;
    setState(() {
      _uris = uris;
      _thumbnailFutures.removeWhere((uri, _) => !urisSet.contains(uri));
      _loading = false;
    });
  }

  Future<Uint8List?> _cachedPhotoBytes(String uri) {
    return _thumbnailFutures.putIfAbsent(uri, () => CameraBridge.loadPhotoBytes(uri));
  }

  Future<void> _openPreview(String uri) {
    return showDialog<void>(
      context: context,
      builder: (context) => Dialog(
        child: FutureBuilder<Uint8List?>(
          future: _cachedPhotoBytes(uri),
          builder: (context, snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return const Padding(
                padding: EdgeInsets.all(24),
                child: Center(child: CircularProgressIndicator()),
              );
            }
            if (snapshot.hasError || !snapshot.hasData) {
              return const Padding(
                padding: EdgeInsets.all(24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.broken_image_outlined, size: 48),
                    SizedBox(height: 8),
                    Text('图片加载失败'),
                  ],
                ),
              );
            }
            return Padding(
              padding: const EdgeInsets.all(8),
              child: Image.memory(snapshot.data!),
            );
          },
        ),
      ),
    );
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
      child: GridView.builder(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(12),
        itemCount: _uris.length,
        gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
          maxCrossAxisExtent: 220,
          mainAxisSpacing: 12,
          crossAxisSpacing: 12,
          childAspectRatio: 0.78,
        ),
        itemBuilder: (context, index) {
          final uri = _uris[index];
          final fileName = uri.split('/').last;
          return Card(
            clipBehavior: Clip.antiAlias,
            child: InkWell(
              onTap: () => _openPreview(uri),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Expanded(
                    child: FutureBuilder<Uint8List?>(
                      future: _cachedPhotoBytes(uri),
                      builder: (context, snapshot) {
                        if (snapshot.connectionState == ConnectionState.waiting) {
                          return const Center(child: CircularProgressIndicator());
                        }
                        if (snapshot.hasError || !snapshot.hasData) {
                          return const Center(
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(Icons.broken_image_outlined, size: 40),
                                SizedBox(height: 4),
                                Text('加载失败'),
                              ],
                            ),
                          );
                        }
                        return Image.memory(
                          snapshot.data!,
                          fit: BoxFit.cover,
                          width: double.infinity,
                        );
                      },
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.all(8),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          fileName,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        const SizedBox(height: 4),
                        Text(
                          '点击预览',
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}
