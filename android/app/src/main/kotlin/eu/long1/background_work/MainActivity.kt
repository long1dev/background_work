package eu.long1.background_work

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity(), MethodChannel.MethodCallHandler {
  private val preferences: SharedPreferences
    get() {
      return getSharedPreferences(BackgroundWork.DEFAULT_PREFERENCES, MODE_PRIVATE)
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val channel = MethodChannel(flutterEngine!!.dartExecutor, "eu.long1/background_work")
    channel.setMethodCallHandler(this)
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    when (call.method) {
      "start" -> {
        val args = call.arguments as List<Any>
        val intent = Intent(this, BackgroundWork::class.java)
        intent.action = "start"
        intent.putExtra("callback", args[0] as Long)
        intent.putExtra("path", args[1] as String)
        startService(intent)
        result.success(null)
      }
      "stop" -> {
        val intent = Intent(this, BackgroundWork::class.java)
        intent.action = "stop"
        startService(intent)
        result.success(null)
      }
      "save" -> {
        val intent = Intent(this, BackgroundWork::class.java)
        intent.action = "save"
        startService(intent)
        result.success(null)
      }
      "record" -> {
        val intent = Intent(this, BackgroundWork::class.java)
        intent.action = "record"
        startService(intent)
        result.success(null)
      }
      "reset_state" -> {
        preferences.edit().remove(BackgroundWork.RECORDING_STATE_KEY).apply()
        result.success(null)
      }
      "state" -> {
        val state =
          preferences.getString(BackgroundWork.RECORDING_STATE_KEY,
            BackgroundWork.RECORDING_STATE_STOPPED)
        result.success(state)
      }
      else -> result.notImplemented()
    }
  }
}
