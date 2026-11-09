package com.example.sms

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Telephony

/** Bus that tells the open UI to reload when something in the SMS database changes. */
object SmsEvents {
    private val listeners = mutableSetOf<() -> Unit>()
    fun listen(l: () -> Unit) { synchronized(listeners) { listeners += l } }
    fun unlisten(l: () -> Unit) { synchronized(listeners) { listeners -= l } }
    fun changed() { synchronized(listeners) { listeners.toList() }.forEach { it() } }
}

/** Incoming SMS. Only the default SMS app receives SMS_DELIVER and must write it to the database itself. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        val address = parts[0].displayOriginatingAddress ?: parts[0].originatingAddress ?: return
        // Numbers in the system blacklist are normally filtered by Android, this is a safety net.
        if (Sms.isBlocked(ctx, address)) return
        val body = parts.joinToString("") { it.displayMessageBody ?: "" }
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.DATE_SENT, parts[0].timestampMillis)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        }
        runCatching { ctx.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) }
        if (MainActivity.openAddress != address) Notify.message(ctx, address, body)
        SmsEvents.changed()
    }
}

/** Required for the default-SMS role. MMS isn't supported by this app, the receiver just accepts the broadcast. */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = Unit
}

/** Result of sending one SMS part: marks the message as sent or failed. */
class SentReceiver : BroadcastReceiver() {
    companion object { const val LAST = "last" }

    override fun onReceive(ctx: Context, intent: Intent) {
        val uri = intent.data ?: return
        val ok = resultCode == Activity.RESULT_OK
        if (!ok) Sms.setType(ctx, uri, Telephony.Sms.MESSAGE_TYPE_FAILED)
        else if (intent.getBooleanExtra(LAST, true)) Sms.setType(ctx, uri, Telephony.Sms.MESSAGE_TYPE_SENT)
        SmsEvents.changed()
    }
}

/** "Reply with a message" when declining a call. Required for the default-SMS role. */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val to = intent?.data?.let { recipientFrom(it) }
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (to != null && !text.isNullOrBlank()) Sms.send(this, to, text)
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
