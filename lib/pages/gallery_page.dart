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
  final Set<String> _selectedUris = <String>{};
  final Map<String, Future<Uint8List?>> _thumbnailFutures = {};

  bool get _selectionMode => _selectedUris.isNotEmpty;

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
      _selectedUris.removeWhere((uri) => !urisSet.contains(uri));
      _thumbnailFutures.removeWhere((uri, _) => !urisSet.contains(uri));
      _loading = false;
    });
  }

  Future<Uint8List?> _cachedPhotoBytes(String uri) {
    return _thumbnailFutures.putIfAbsent(uri, () => CameraBridge.loadPhotoBytes(uri));
  }

  void _toggleSelection(String uri) {
    setState(() {
      if (!_selectedUris.add(uri)) {
        _selectedUris.remove(uri);
      }
    });
  }

  void _selectAll() {
    setState(() {
      if (_selectedUris.length == _uris.length) {
        _selectedUris.clear();
      } else {
        _selectedUris
          ..clear()
          ..addAll(_uris);
      }
    });
  }

  Future<void> _openPreview(int initialIndex) {
    return showDialog<void>(
      context: context,
      builder: (context) => Dialog(
        insetPadding: const EdgeInsets.all(16),
        child: SizedBox(
          width: double.maxFinite,
          height: MediaQuery.sizeOf(context).height * 0.78,
          child: PageView.builder(
            controller: PageController(initialPage: initialIndex),
            itemCount: _uris.length,
            itemBuilder: (context, index) {
              final uri = _uris[index];
              return FutureBuilder<Uint8List?>(
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
                          Icon(Icons.broken_image_outlined, size: 48),
                          SizedBox(height: 8),
                          Text('图片加载失败'),
                        ],
                      ),
                    );
                  }
                  return InteractiveViewer(
                    child: Center(
                      child: Image.memory(snapshot.data!, fit: BoxFit.contain),
                    ),
                  );
                },
              );
            },
          ),
        ),
      ),
    );
  }

  Future<void> _deleteSelected() async {
    final count = _selectedUris.length;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('删除图片'),
        content: Text('确定删除已选择的 $count 张图片吗？'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('删除'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;

    final uris = _selectedUris.toList();
    final deletedCount = await CameraBridge.deletePhotos(uris);
    if (!mounted) return;
    setState(() {
      _uris = _uris.where((uri) => !uris.contains(uri)).toList();
      for (final uri in uris) {
        _thumbnailFutures.remove(uri);
      }
      _selectedUris.clear();
    });
    await _load();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('已删除 $deletedCount 张图片')),
    );
  }

  Future<void> _copySelectedToFolder() async {
    final controller = TextEditingController(text: 'CameraVSelected');
    final folder = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('复制到文件夹'),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: const InputDecoration(
            labelText: 'Pictures 下的文件夹名称',
            hintText: '例如 CameraVSelected',
          ),
          textInputAction: TextInputAction.done,
          onSubmitted: (value) => Navigator.of(context).pop(value),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(controller.text),
            child: const Text('复制'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (folder == null || folder.trim().isEmpty) return;

    final copiedCount = await CameraBridge.copyPhotosToFolder(
      _selectedUris.toList(),
      folder.trim(),
    );
    if (!mounted) return;
    setState(_selectedUris.clear);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('已复制 $copiedCount 张图片到 Pictures/${folder.trim()}')),
    );
  }

  Widget _buildSelectionBar() {
    return Material(
      color: Theme.of(context).colorScheme.surfaceContainerHighest,
      child: SafeArea(
        bottom: false,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
          child: Row(
            children: [
              IconButton(
                tooltip: '取消选择',
                onPressed: () => setState(_selectedUris.clear),
                icon: const Icon(Icons.close),
              ),
              Expanded(child: Text('已选择 ${_selectedUris.length} 张')),
              TextButton(
                onPressed: _selectAll,
                child: Text(_selectedUris.length == _uris.length ? '取消全选' : '全选'),
              ),
              IconButton(
                tooltip: '复制到文件夹',
                onPressed: _copySelectedToFolder,
                icon: const Icon(Icons.drive_folder_upload_outlined),
              ),
              IconButton(
                tooltip: '删除',
                onPressed: _deleteSelected,
                icon: const Icon(Icons.delete_outline),
              ),
            ],
          ),
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
    return Column(
      children: [
        if (_selectionMode) _buildSelectionBar(),
        Expanded(
          child: RefreshIndicator(
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
                final selected = _selectedUris.contains(uri);
                return Card(
                  clipBehavior: Clip.antiAlias,
                  child: InkWell(
                    onTap: () => _selectionMode ? _toggleSelection(uri) : _openPreview(index),
                    onLongPress: () => _toggleSelection(uri),
                    child: Stack(
                      fit: StackFit.expand,
                      children: [
                        Column(
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
                                    _selectionMode ? '点击选择/取消' : '点击预览，长按选择',
                                    style: Theme.of(context).textTheme.bodySmall,
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                        if (selected)
                          Positioned.fill(
                            child: ColoredBox(
                              color: Theme.of(context).colorScheme.primary.withAlpha(46),
                            ),
                          ),
                        Positioned(
                          top: 8,
                          right: 8,
                          child: AnimatedOpacity(
                            opacity: _selectionMode ? 1 : 0,
                            duration: const Duration(milliseconds: 150),
                            child: IgnorePointer(
                              ignoring: !_selectionMode,
                              child: Checkbox(
                                value: selected,
                                onChanged: (_) => _toggleSelection(uri),
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
          ),
        ),
      ],
    );
  }
}
