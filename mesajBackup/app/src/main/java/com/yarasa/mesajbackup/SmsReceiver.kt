package com.yarasa.mesajbackup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.launch
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.os.PowerManager
import android.widget.Toast
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("SmsReceiver", "onReceive triggered. Action: ${intent.action}")
        
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val pendingResult = goAsync()
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MesajBackup::SmsReceiverWakelock")
            wakeLock.acquire(10*1000L) // 10 seconds timeout

            val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
            
            scope.launch {
                try {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "New SMS Received!", Toast.LENGTH_LONG).show() // Visual feedback
                    }
                    
                    val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                    if (messages != null) {
                        for (message in messages) {
                            val sender = message.displayOriginatingAddress ?: "Unknown"
                            val messageBody = message.displayMessageBody ?: message.messageBody ?: ""
                            val timestamp = message.timestampMillis

                            Log.d("SmsReceiver", "SMS received from: $sender")
                            
                            // Save to Firebase using suspend function
                            val success = FirebaseUtils.saveSmsSuspend(sender, messageBody, timestamp)
                            
                            withContext(Dispatchers.Main) {
                                if (success) {
                                    showNotification(context, sender, "SMS Backed Up")
                                } else {
                                    showNotification(context, sender, "Backup Failed!")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SmsReceiver", "Error processing SMS", e)
                } finally {
                    if (wakeLock.isHeld) {
                        wakeLock.release()
                    }
                    pendingResult.finish()
                }
            }
        }
    }

    private fun showNotification(context: Context, sender: String, title: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val builder = NotificationCompat.Builder(context, "backup_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("Message from $sender")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }
}
