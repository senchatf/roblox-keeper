package com.example.robloxkeeper

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var toggleSwitch: Switch
    private lateinit var logsView: TextView
    private lateinit var copyLogsButton: Button
    private lateinit var clearLogsButton: Button
    private val logsReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == KeeperService.ACTION_LOG_UPDATE) {
                renderLogs()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toggleSwitch = findViewById(R.id.toggle_switch)
        logsView = findViewById(R.id.logs_view)
        copyLogsButton = findViewById(R.id.copy_logs_button)
        clearLogsButton = findViewById(R.id.clear_logs_button)

        toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!hasUsageStatsPermission()) {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } else {
                    startService(Intent(this, KeeperService::class.java))
                }
            } else {
                stopService(Intent(this, KeeperService::class.java))
            }
        }

        copyLogsButton.setOnClickListener {
            val logs = KeeperService.getSavedLogs(this)
            val textToCopy = if (logs.isBlank()) getString(R.string.no_logs) else logs
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Keeper Logs", textToCopy))
            Toast.makeText(this, R.string.logs_copied, Toast.LENGTH_SHORT).show()
        }

        clearLogsButton.setOnClickListener {
            KeeperService.clearSavedLogs(this)
            renderLogs()
            Toast.makeText(this, R.string.logs_cleared, Toast.LENGTH_SHORT).show()
        }

        toggleSwitch.isChecked = KeeperService.isRunning
        renderLogs()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(KeeperService.ACTION_LOG_UPDATE)
        ContextCompat.registerReceiver(this, logsReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(logsReceiver)
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun renderLogs() {
        val logs = KeeperService.getSavedLogs(this)
        logsView.text = if (logs.isBlank()) getString(R.string.no_logs) else logs
    }
}
