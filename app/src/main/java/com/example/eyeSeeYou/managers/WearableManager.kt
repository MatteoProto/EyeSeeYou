package com.example.eyeSeeYou.managers

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.android.gms.wearable.Wearable

class WearableManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager) {

    companion object {
        private const val TAG = "WearableManager"
    }

    private var lastSentMessage: Long = 0
    private val messageDelay = 500L

    private val VIBRATE_MESSAGE_PATH = "/vibrate_request"


    fun sendMessageToWearables(message: String) {
        if (System.currentTimeMillis() - lastSentMessage < messageDelay || message.isEmpty() || !preferencesManager.isWatchVibrationActive()) {
            return
        }
        lastSentMessage = System.currentTimeMillis()

        Wearable.getNodeClient(context).connectedNodes.addOnCompleteListener { task ->
            if (task.isSuccessful && task.result != null) {
                val nodes = task.result
                if (nodes.isEmpty()) {
                    return@addOnCompleteListener
                }

                val data = message.toByteArray(Charsets.UTF_8)
                var successCount = 0
                var failureCount = 0

                nodes.forEach { node ->
                    Wearable.getMessageClient(context).sendMessage(
                        node.id,
                        VIBRATE_MESSAGE_PATH,
                        data
                    ).addOnSuccessListener {
                        successCount++
                        if (successCount + failureCount == nodes.size) {
                            showToastResult(successCount, failureCount)
                        }
                    }.addOnFailureListener { e ->
                        failureCount++
                        if (successCount + failureCount == nodes.size) {
                            showToastResult(successCount, failureCount)
                        }
                    }
                }
            } else {
                Log.e(TAG, "Errore nel recuperare i nodi connessi: ${task.exception}")
                Toast.makeText(context, "Errore nel trovare lo smartwatch", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showToastResult(successCount: Int, failureCount: Int) {
        when {
            successCount > 0 && failureCount == 0 -> {
                Toast.makeText(context, "Request sent smartwatch", Toast.LENGTH_SHORT).show()
            }
            successCount > 0 && failureCount > 0 -> {
                Toast.makeText(context, "Request sent to $successCount nodes, failed on $failureCount", Toast.LENGTH_SHORT).show()
            }
            failureCount > 0 -> {
                Toast.makeText(context, "Error sending request to nodes", Toast.LENGTH_SHORT).show()
            }
        }
    }
}