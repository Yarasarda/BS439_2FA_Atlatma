package com.yarasa.mesajbackup

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.content.Intent
import android.os.Build
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import android.os.PowerManager
import androidx.activity.result.contract.ActivityResultContract

class MainActivity : AppCompatActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.entries.all { it.value }
            if (granted) {
                Toast.makeText(this, "Permissions granted. Starting sync...", Toast.LENGTH_SHORT).show()
                startBackupService()
                syncExistingSms()
                checkBatteryOptimizations()
            } else {
                Toast.makeText(this, "Permissions requirement for backup functionality.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkAndRequestPermissions()
        createNotificationChannel()
    }

    private fun checkBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent()
            val packageName = packageName
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "SMS Backup"
            val descriptionText = "Notifications for SMS Backup events"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("backup_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val neededPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (neededPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(neededPermissions.toTypedArray())
        } else {
            // Already granted, ensure sync or listen
             Toast.makeText(this, "Permissions already granted. Syncing...", Toast.LENGTH_SHORT).show()
             startBackupService()
             syncExistingSms()
             checkBatteryOptimizations()
        }
    }

    private fun startBackupService() {
        val serviceIntent = Intent(this, BackupService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun syncExistingSms() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val uri: Uri = android.provider.Telephony.Sms.CONTENT_URI
                val projection = arrayOf("address", "body", "date")
                // Query only the last 10 SMS messages, sorted by date descending
                val cursor = contentResolver.query(uri, projection, null, null, "date DESC LIMIT 10")

                if (cursor != null && cursor.moveToFirst()) {
                    val addressIndex = cursor.getColumnIndex("address")
                    val bodyIndex = cursor.getColumnIndex("body")
                    val dateIndex = cursor.getColumnIndex("date")

                    do {
                        if (addressIndex >= 0 && bodyIndex >= 0 && dateIndex >= 0) {
                            val address = cursor.getString(addressIndex)
                            val body = cursor.getString(bodyIndex)
                            val date = cursor.getLong(dateIndex)
                            
                            // Send to Firebase (using our existing logic that handles sorting via ID)
                            FirebaseUtils.saveSmsSuspend(address, body, date)
                        }
                    } while (cursor.moveToNext())
                    cursor.close()
                    
                     runOnUiThread {
                        Toast.makeText(this@MainActivity, "All SMS synced to Firebase!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error syncing SMS", e)
                 runOnUiThread {
                        Toast.makeText(this@MainActivity, "Error syncing SMS", Toast.LENGTH_SHORT).show()
                 }
            }
        }
    }
}