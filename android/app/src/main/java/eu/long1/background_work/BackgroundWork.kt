package eu.long1.background_work

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.FlutterCallbackInformation

class BackgroundWork : Service() {
  override fun onBind(intent: Intent): IBinder? {
    return null
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      "start" -> start(intent)
      "stop" -> stop()
      "save" -> save()
      "record" -> record()
    }

    return START_STICKY
  }

  @SuppressLint("WrongConstant")
  private fun start(intent: Intent) {
    if (initialized) {
      throw IllegalStateException("The service was already started.")
    }

    val rawHandler = intent.getLongExtra("callback", 0)
    val path = intent.getStringExtra("path")!!
    startEngine(rawHandler, path)
    saveRecordingState(RECORDING_STATE_INITIALIZED)

    createNotificationChannel()
    val notification = createBackgroundStartNotification()
    startForeground(1, notification)
  }

  private fun record() {
    saveRecordingState(RECORDING_STATE_RECORDING)
    postRecordNotification()
  }

  private fun save() {
    saveRecordingState(RECORDING_STATE_SAVING)
    postSaveNotification()
    stop()
  }

  private fun stop() {
    if (!initialized) {
      stopSelf()
      return
    }

    saveRecordingState(RECORDING_STATE_STOPPED)
    initialized = false
    try {
      flutterEngine.destroy()
    } catch (_: Exception) {
    }
    stopSelf()
  }

  private fun startEngine(rawHandler: Long, path: String) {
    // Step II
    // 1. lookup the callback
    // 2. get the path the the app bundle
    // 3. create the DartCallback
    // 4. create new instance of FlutterEngine
    // 5. use the new FlutterEngine to execute the DartCallback
    val info = FlutterCallbackInformation.lookupCallbackInformation(rawHandler)
    val pathToApp = FlutterInjector.instance().flutterLoader().findAppBundlePath()
    val callback = DartExecutor.DartCallback(assets, pathToApp, info)
    val engine = FlutterEngine(this, null)
    engine.dartExecutor.executeDartCallback(callback)


    // Step IV
    // 1. create the method channel to communicate with then background isolate
    // 2. send the path as the first argument
    // 3. mark the service as being initialized
    // 4. restore the saveRecordingState
    channel = MethodChannel(engine.dartExecutor, "background")
    channel.invokeMethod("path", path)
    initialized = true
  }

  private fun saveRecordingState(recordingState: String) {
    channel.invokeMethod("state", recordingState)
    preferences.edit()
      .putString(RECORDING_STATE_KEY, recordingState)
      .apply()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

      val channel =
        NotificationChannel(
          DEFAULT_CHANNEL_NAME,
          "default",
          NotificationManager.IMPORTANCE_HIGH
        )
      val manager = getSystemService(NotificationManager::class.java)
      manager.createNotificationChannel(channel)
    }
  }

  private val recordIntent: PendingIntent
    get() {
      val recordIntent = Intent(this, BackgroundWork::class.java)
      recordIntent.action = "record"
      return PendingIntent.getService(
        this,
        1,
        recordIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
      )
    }

  private val stopIntent: PendingIntent
    get() {
      val stopIntent = Intent(this, BackgroundWork::class.java)
      stopIntent.action = "stop"
      return PendingIntent.getService(
        this,
        1,
        stopIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
      )
    }

  private val saveIntent: PendingIntent
    get() {
      val saveIntent = Intent(this, BackgroundWork::class.java)
      saveIntent.action = "save"
      return PendingIntent.getService(
        this,
        1,
        saveIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
      )
    }

  private val contentIntent: PendingIntent
    get() {
      val contentIntent = Intent(this, MainActivity::class.java)
      return PendingIntent.getActivity(
        this,
        1,
        contentIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
      )
    }

  private fun postRecordNotification() {
    val manager = getSystemService(NotificationManager::class.java)
    val notification = NotificationCompat.Builder(this, DEFAULT_CHANNEL_NAME)
      .setWhen(0)
      .setContentTitle("Recording in progress")
      .setContentInfo("Press stop to save the recording.")
      .setContentIntent(contentIntent)
      .setPriority(NotificationManager.IMPORTANCE_HIGH)
      .setSmallIcon(R.drawable.ic_record)
      .addAction(R.drawable.ic_save, "Save", saveIntent)
      .addAction(R.drawable.ic_stop, "Stop", stopIntent)
      .setOngoing(true)
    manager.notify(1, notification.build())
  }

  private fun postSaveNotification() {
    val manager = getSystemService(NotificationManager::class.java)!!
    val notification = NotificationCompat.Builder(this, DEFAULT_CHANNEL_NAME)
      .setWhen(0)
      .setContentTitle("Recording saved")
      .setContentInfo("The recording was saved")
      .setContentIntent(contentIntent)
      .setPriority(NotificationManager.IMPORTANCE_HIGH)
      .setSmallIcon(R.drawable.ic_save)
    manager.notify(2, notification.build())
  }

  private fun createBackgroundStartNotification(): Notification {
    return NotificationCompat.Builder(this, DEFAULT_CHANNEL_NAME)
      .setWhen(0)
      .setContentTitle("Background work started")
      .setContentInfo("Press record to start recording")
      .setPriority(NotificationManager.IMPORTANCE_HIGH)
      .setContentIntent(contentIntent)
      .setSmallIcon(R.drawable.ic_record)
      .addAction(R.drawable.ic_record, "Record", recordIntent)
      .addAction(R.drawable.ic_stop, "Stop", stopIntent)
      .setOngoing(true)
      .build()
  }

  private val preferences: SharedPreferences
    get() {
      return getSharedPreferences(DEFAULT_PREFERENCES, MODE_PRIVATE)
    }

  companion object {
    const val DEFAULT_PREFERENCES = "DEFAULT_PREFERENCES"
    const val RECORDING_STATE_KEY = "state"
    const val RECORDING_STATE_STOPPED = "stopped"
    const val RECORDING_STATE_INITIALIZED = "initialized"
    const val RECORDING_STATE_RECORDING = "recording"
    const val RECORDING_STATE_SAVING = "saving"

    private const val DEFAULT_CHANNEL_NAME = "default"
    private lateinit var flutterEngine: FlutterEngine
    private lateinit var channel: MethodChannel
    private var initialized: Boolean = false
  }
}