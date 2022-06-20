import 'dart:io';

import 'package:background_work/recorder.dart';
import 'package:flutter/material.dart';
import 'package:path/path.dart';
import 'package:path_provider/path_provider.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Background work',
      theme: ThemeData(
        primarySwatch: Colors.green,
      ),
      home: const MyHomePage(),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({Key? key}) : super(key: key);

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  final RecorderService _service = RecorderService();
  RecordingState _state = RecordingState.stopped;

  @override
  void initState() {
    super.initState();

    WidgetsBinding.instance.addPostFrameCallback((timeStamp) {
      _service.addListener(_onValue);
      _service.getState();
    });
  }

  void _onValue() {
    print(_service.value);
    setState(() => _state = _service.value);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        actions: <Widget>[
          IconButton(
            icon: const Icon(Icons.lock_reset),
            onPressed: () async {
              await _service.resetState();
            },
          ),
        ],
      ),
      body: Center(
        child: Center(
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (_state == RecordingState.stopped)
                TextButton.icon(
                  icon: const Icon(Icons.fiber_manual_record),
                  label: const Text('Start'),
                  onPressed: () async {
                    final Directory dir = await getApplicationDocumentsDirectory();
                    await _service.register(join(dir.path, 'file.mp4'));
                  },
                )
              else ...<Widget>[
                if (_state == RecordingState.recording)
                  TextButton.icon(
                    icon: const Icon(Icons.save_alt),
                    label: const Text('Save'),
                    onPressed: () {
                      _service.save();
                    },
                  )
                else
                  TextButton.icon(
                    icon: const Icon(Icons.fiber_manual_record),
                    label: const Text('Record'),
                    onPressed: () {
                      _service.record();
                    },
                  ),
                TextButton.icon(
                  icon: const Icon(Icons.stop),
                  label: const Text('Stop'),
                  onPressed: () {
                    _service.stop();
                  },
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
