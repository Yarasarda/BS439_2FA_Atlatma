package com.yarasa.mesajbackup

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import android.util.Log

data class SmsMessage(
    val sender: String = "",
    val message: String = "",
    val timestamp: Long = 0,
    val date: String = "" // Human readable date for convenience
)

object FirebaseUtils {
    private const val TAG = "FirebaseUtils"
    private val db = FirebaseFirestore.getInstance()

    fun saveSms(sender: String, message: String, timestamp: Long) {
        val sms = SmsMessage(
            sender = sender,
            message = message,
            timestamp = timestamp,
            date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
        )

        // Invert timestamp to ensure newest messages appear at the top in Firebase Console (which sorts by ID ascending)
        val documentId = "${Long.MAX_VALUE - timestamp}_${sender.take(5)}"
        
        db.collection("sms_backups")
            .document(documentId)
            .set(sms)
            .addOnSuccessListener { 
                Log.d(TAG, "DocumentSnapshot added with ID: $documentId")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error adding document", e)
            }
    }
    
    // Suspend version for coroutines if needed
    // Suspend version for coroutines if needed
    suspend fun saveSmsSuspend(sender: String, message: String, timestamp: Long): Boolean {
         val sms = SmsMessage(
            sender = sender,
            message = message,
            timestamp = timestamp,
            date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
        )
        return try {
            val documentId = "${Long.MAX_VALUE - timestamp}_${sender.take(5)}"
            db.collection("sms_backups").document(documentId).set(sms).await()
            Log.d(TAG, "SMS saved successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving SMS", e)
            false
        }
    }
}
