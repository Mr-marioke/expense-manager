package com.hackathon.expense_manager

import android.content.Context
import android.provider.Telephony
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import org.json.JSONArray

class MainActivity : FlutterActivity() {
    private val channelName = "expense_manager/sms"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "readInbox" -> {
                        val since = (call.argument<Number>("since") ?: 0).toLong()
                        try {
                            result.success(readInbox(since))
                        } catch (e: Exception) {
                            result.error("sms_read_failed", e.message, null)
                        }
                    }
                    "consumePending" -> {
                        try {
                            result.success(consumePending())
                        } catch (e: Exception) {
                            result.error("pending_failed", e.message, null)
                        }
                    }
                    else -> result.notImplemented()
                }
            }
    }

    private fun readInbox(since: Long): List<Map<String, Any>> {
        val out = mutableListOf<Map<String, Any>>()
        val uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        val selection = "${Telephony.Sms.DATE} > ?"
        contentResolver.query(
            uri,
            projection,
            selection,
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
            val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
            while (cursor.moveToNext() && out.size < 4000) {
                val body = if (bodyIdx >= 0) cursor.getString(bodyIdx) ?: "" else ""
                if (body.isEmpty()) continue
                out.add(
                    mapOf(
                        "id" to (if (idIdx >= 0) cursor.getLong(idIdx) else 0L),
                        "address" to (if (addressIdx >= 0) cursor.getString(addressIdx) ?: "" else ""),
                        "body" to body,
                        "date" to (if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L),
                    )
                )
            }
        }
        return out
    }

    private fun consumePending(): List<Map<String, Any>> {
        val prefs = getSharedPreferences(SmsReceiver.PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(SmsReceiver.KEY, "[]") ?: "[]"
        prefs.edit().putString(SmsReceiver.KEY, "[]").apply()
        val arr = try {
            JSONArray(raw)
        } catch (e: Exception) {
            JSONArray()
        }
        val out = mutableListOf<Map<String, Any>>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            out.add(
                mapOf(
                    "id" to obj.optLong("id"),
                    "address" to obj.optString("address"),
                    "body" to obj.optString("body"),
                    "date" to obj.optLong("date"),
                )
            )
        }
        return out
    }
}
