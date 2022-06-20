import 'dart:async';
import 'dart:io';
import 'dart:isolate';
import 'dart:ui';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:record/record.dart';

Future<void> _background() async {
  // Step III
  // 1. inform the foreground we are up using the registered SendPort
  SendPort? foreground = IsolateNameServer.lookupPortByName('foreground');
  foreground?.send(null);

  // Step V
  // 1. call ensureInitialized on this isolate
  // 2. create the method channel
  // 3. save the path
  // 4. send the recording state to foreground
  // 5. implement the recording plugin
  WidgetsFlutterBinding.ensureInitialized();
  const MethodChannel channel = MethodChannel('eu.long1/background_work/background');

  late String path;
  final Record record = Record();
  channel.setMethodCallHandler((MethodCall call) async {
    if (call.method == 'path') {
      path = call.arguments;
      return;
    }
    foreground?.send(call.arguments);

    if (call.arguments == 'recording') {
      record.start(path: path);
    } else if (call.arguments == 'stopped') {
      record.stop();
      File(path).deleteSync();
    } else if (call.arguments == 'saving') {
      record.stop();
    }
  });
}

class RecorderService extends ValueNotifier<RecordingState> {
  RecorderService({MethodChannel? channel})
      : _port = ReceivePort(),
        _channel = channel ?? const MethodChannel('eu.long1/background_work'),
        super(RecordingState.stopped) {
    _port.listen(_onBackgroundMessage);
  }

  final MethodChannel _channel;
  final ReceivePort _port;
  Completer<void>? _completer;
  bool _registered = false;

  Future<RecordingState> getState() async {
    final dynamic state = await _channel.invokeMethod('state');
    value = RecordingState.valueOf(state);
    if (value != RecordingState.stopped) {
      _registered = true;
    }
    return value;
  }

  Future<void> register(String path) async {
    if (_registered) {
      return;
    }

    if (_completer != null) {
      throw StateError("You can only register one function.");
    }
    _completer = Completer<void>();

    // Step I
    // 1. register the background function
    // 2. register the SendPort
    // 3. start the service
    // 4. await for completer
    final CallbackHandle handle = PluginUtilities.getCallbackHandle(_background)!;
    IsolateNameServer.registerPortWithName(_port.sendPort, 'foreground');

    await _channel.invokeMethod('start', <dynamic>[handle.toRawHandle(), path]);
    await _completer!.future;
    _completer = null;
  }

  Future<void> stop() async {
    IsolateNameServer.removePortNameMapping('foreground');
    await _channel.invokeMethod('stop');
    _registered = false;
  }

  Future<void> save() async {
    _checkRegistered();
    await _channel.invokeMethod('save');
  }

  Future<void> record() async {
    _checkRegistered();
    await _channel.invokeMethod('record');
  }

  Future<void> resetState() async {
    await _channel.invokeMethod('reset_state');
    await getState();
    await stop();
  }

  void _onBackgroundMessage(dynamic message) {
    if (message == null) {
      _registered = true;
      _completer!.complete();
      value = RecordingState.initialized;
    } else {
      value = RecordingState.valueOf(message);
      if (value == RecordingState.stopped) {
        _registered = false;
      }
    }
  }

  void _checkRegistered() {
    if (!_registered) {
      throw StateError('You need to register first by calling the register method.');
    }
  }
}

enum RecordingState {
  stopped,
  initialized,
  recording,
  saving;

  static RecordingState valueOf(String value) {
    return RecordingState.values.firstWhere((state) => state.name == value);
  }
}
