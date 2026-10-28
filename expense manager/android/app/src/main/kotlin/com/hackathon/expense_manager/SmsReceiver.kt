package com.hackathon.expense_manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject

class SmsReceiver : BroadcastReceiver() {
    companion object {
        const val PREFS = "paisatrack_pending"
        const val KEY = "queue"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val queue = try {
            JSONArray(prefs.getString(KEY, "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }

        val bodies = LinkedHashMap<String, StringBuilder>()
        val stamps = LinkedHashMap<String, Long>()
        for (message in messages) {
            val sender = message.originatingAddress ?: ""
            bodies.getOrPut(sender) { StringBuilder() }
                .append(message.messageBody ?: "")
            stamps[sender] = message.timestampMillis
        }

        for ((sender, body) in bodies) {
            val obj = JSONObject()
            obj.put("id", -System.currentTimeMillis() - queue.length())
            obj.put("address", sender)
            obj.put("body", body.toString())
            obj.put("date", stamps[sender] ?: System.currentTimeMillis())
            queue.put(obj)
        }

        prefs.edit().putString(KEY, queue.toString()).apply()
    }
}
