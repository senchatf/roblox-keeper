package com.example.robloxkeeper

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

//shoutout claude

class KeeperService : Service() {

    companion object {
        var isRunning = false
        val client = OkHttpClient()
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "keeper_channel"
        private const val TAG = "KeeperService"
        private const val TARGET_PACKAGE = "com.roblox.client"
        private const val DEEPLINK = "roblox://placeId=606849621"
        private const val CHECK_URL = "https://inventories.jailbreakchangelogs.com/bots/connected"
        private const val CHECK_INTERVAL = 10000L
        private const val HEARTBEAT_INTERVAL = 5 * 60 * 1000L // 5 minutes

        const val ACTION_LOG_UPDATE = "com.example.robloxkeeper.ACTION_LOG_UPDATE"
        const val EXTRA_LOG_TEXT = "extra_log_text"
        private const val PREFS_NAME = "keeper_prefs"
        private const val PREFS_KEY_LOGS = "service_logs"
        private const val MAX_LOG_LINES = 120

        fun getSavedLogs(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(PREFS_KEY_LOGS, "") ?: ""
        }

        fun clearSavedLogs(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(PREFS_KEY_LOGS).apply()
            context.sendBroadcast(
                Intent(ACTION_LOG_UPDATE)
                    .setPackage(context.packageName)
                    .putExtra(EXTRA_LOG_TEXT, "")
            )
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastNotForeground = false
    private var botId: String = "10811982647"

    private val timerTask = object : Runnable {
        override fun run() {
            val isRobloxForeground = isForegroundApp(TARGET_PACKAGE)
            Log.d(TAG, "Is Roblox in foreground? $isRobloxForeground")
            logEvent("Check: robloxForeground=$isRobloxForeground")

            if (isRobloxForeground) {
                lastNotForeground = false
            } else {
                if (lastNotForeground) {
                    openDeeplink()
                } else {
                    lastNotForeground = true
                }
            }

            handler.postDelayed(this, CHECK_INTERVAL)
        }
    }

    private val heartbeatTask = object : Runnable {
        override fun run() {
            // Run network call on a background thread
            Thread {
                checkHeartbeat()
            }.start()

            handler.postDelayed(this, HEARTBEAT_INTERVAL)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startForeground(NOTIFICATION_ID, createNotification())

        // Safely read the bot ID file here instead of in companion object
        botId = try {
            val file = File(
                Environment.getExternalStorageDirectory(),
                "Delta/Workspace/BotId.txt"
            )
            file.readText().trim()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read BotId.txt", e)
            logEvent("ERROR: Could not read BotId.txt — ${e.message}")
            ""
        }

        logEvent("Service started, bot ID: $botId")
        handler.post(timerTask)
        handler.post(heartbeatTask) // runs immediately, then every 5 mins
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(timerTask)
        handler.removeCallbacks(heartbeatTask)
        clearSavedLogs(this)
        Log.d(TAG, "Service stopped and logs cleared")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun killRoblox() {
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(TARGET_PACKAGE)
            Log.d(TAG, "Killed $TARGET_PACKAGE")
            logEvent("Killed $TARGET_PACKAGE")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to kill Roblox", e)
            logEvent("Failed to kill Roblox: ${e.message ?: "unknown"}")
        }
    }

    private fun checkHeartbeat() {
        if (botId.isBlank()) {
            logEvent("Heartbeat check skipped: no bot ID loaded")
            return
        }

        try {
            val request = Request.Builder()
                .url(CHECK_URL)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logEvent("Heartbeat check failed: HTTP ${response.code}")
                    return
                }

                val rawJson = response.body!!.string() // save it first
                val json = JSONObject(rawJson)         // then parse from the saved string
                val bots = json.getJSONArray("recent_heartbeats")

                var matchedBot: JSONObject? = null
                for (i in 0 until bots.length()) {
                    val bot = bots.getJSONObject(i)
                    if (bot.getString("id") == botId) {
                        matchedBot = bot
                        break
                    }
                }

                if (matchedBot != null) {
                    val lastHeartbeat = matchedBot.getLong("last_heartbeat")
                    val nowSeconds = System.currentTimeMillis() / 1000
                    val secondsAgo = nowSeconds - lastHeartbeat
                    val minutes = secondsAgo / 60
                    val seconds = secondsAgo % 60
                    val state = matchedBot.getString("client_state")

                    logEvent("Heartbeat: state=$state, last seen ${minutes}m ${seconds}s ago")

                    if (secondsAgo >= 150) {
                        logEvent("Heartbeat stale (${minutes}m ${seconds}s) — relaunching and killing Roblox")
                        handler.post { openDeeplink() }
                        handler.postDelayed({ killRoblox() }, 2000)
                    }
                } else {
                    logEvent("Heartbeat: bot ID $botId not found. Raw JSON: $rawJson")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat check error", e)
            logEvent("Heartbeat check error: ${e.message ?: "unknown"}")
        }
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Keeper Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Roblox Keeper")
            .setContentText("Monitoring Roblox status")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    @Suppress("DEPRECATION")
    private fun isForegroundApp(packageName: String): Boolean {
        return try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val processes = am.runningAppProcesses
            val foregroundApp = processes?.firstOrNull {
                it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            }
            val currentPackage = foregroundApp?.processName
            Log.d(TAG, "Foreground process: $currentPackage")
            currentPackage == packageName
        } catch (e: Exception) {
            Log.e(TAG, "Foreground check failed", e)
            false
        }
    }

    private fun openDeeplink() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, DEEPLINK.toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            Log.d(TAG, "Opened Roblox deeplink")
            logEvent("Relaunch triggered: $DEEPLINK")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open deeplink", e)
            logEvent("Relaunch failed: ${e.message ?: "unknown error"}")
        }
    }

    private fun logEvent(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val formatted = "[$timestamp] $message"

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(PREFS_KEY_LOGS, "") ?: ""
        val combined = if (existing.isBlank()) formatted else "$existing\n$formatted"
        val trimmed = combined
            .lineSequence()
            .filter { it.isNotBlank() }
            .toList()
            .takeLast(MAX_LOG_LINES)
            .joinToString("\n")
        prefs.edit().putString(PREFS_KEY_LOGS, trimmed).apply()

        sendBroadcast(
            Intent(ACTION_LOG_UPDATE)
                .setPackage(packageName)
                .putExtra(EXTRA_LOG_TEXT, formatted)
        )
        Log.d(TAG, formatted)
    }
}
