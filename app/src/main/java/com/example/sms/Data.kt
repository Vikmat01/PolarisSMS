package com.example.sms

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.BlockedNumberContract
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.Telephony
import android.telephony.SmsManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

// ---------- Models ----------

data class Msg(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int,
    val read: Boolean
) {
    val outgoing get() = type != Telephony.Sms.MESSAGE_TYPE_INBOX
    val failed get() = type == Telephony.Sms.MESSAGE_TYPE_FAILED
    val pending get() = type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || type == Telephony.Sms.MESSAGE_TYPE_QUEUED
    val uri: Uri get() = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, id.toString())
}

data class Conversation(
    val threadId: Long,
    val address: String,
    val name: String?,
    val snippet: String,
    val date: Long,
    val unread: Int,
    val outgoing: Boolean
) {
    val title get() = name ?: address
}

data class ContactHit(val name: String, val number: String)

// ---------- SMS provider, sending, blacklist ----------

object Sms {
    private val names = ConcurrentHashMap<String, String>()

    fun isDefault(ctx: Context): Boolean {
        val role = runCatching {
            ctx.getSystemService(android.app.role.RoleManager::class.java)
                ?.isRoleHeld(android.app.role.RoleManager.ROLE_SMS) == true
        }.getOrDefault(false)
        return role || Telephony.Sms.getDefaultSmsPackage(ctx) == ctx.packageName
    }

    private fun hasContacts(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun name(ctx: Context, address: String): String? {
        names[address]?.let { return it.ifEmpty { null } }
        if (address.isBlank() || !hasContacts(ctx)) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
        val n = runCatching {
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
        names[address] = n ?: ""
        return n
    }

    private val photosByNumber = ConcurrentHashMap<String, String>()

    /** Contact photo for a phone number (thumbnail or full size), or null if none. */
    fun photoFor(ctx: Context, number: String, full: Boolean = false): String? {
        if (number.isBlank() || !hasContacts(ctx)) return null
        val key = (if (full) "F" else "T") + number
        photosByNumber[key]?.let { return it.ifEmpty { null } }
        val col = if (full) ContactsContract.PhoneLookup.PHOTO_URI else ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val p = runCatching {
            ctx.contentResolver.query(uri, arrayOf(col), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
        photosByNumber[key] = p ?: ""
        return p
    }

    fun clearNames() { names.clear(); photosByNumber.clear(); Photos.clear() }

    fun searchContacts(ctx: Context, q: String): List<ContactHit> {
        if (q.isBlank() || !hasContacts(ctx)) return emptyList()
        return runCatching {
            ctx.contentResolver.query(
                Phone.CONTENT_URI, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER),
                "${Phone.DISPLAY_NAME} LIKE ? OR ${Phone.NUMBER} LIKE ?", arrayOf("%$q%", "%$q%"),
                "${Phone.DISPLAY_NAME} ASC"
            )?.use { c ->
                val out = LinkedHashMap<String, ContactHit>()
                while (c.moveToNext() && out.size < 30) {
                    val num = c.getString(1) ?: continue
                    out.putIfAbsent(num.filter { it.isDigit() || it == '+' }, ContactHit(c.getString(0) ?: num, num))
                }
                out.values.toList()
            }
        }.getOrNull() ?: emptyList()
    }

    private val cols = arrayOf(
        Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
        Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.READ
    )

    private fun query(ctx: Context, sel: String?, args: Array<String>?, order: String): List<Msg> = runCatching {
        ctx.contentResolver.query(Telephony.Sms.CONTENT_URI, cols, sel, args, order)?.use { c ->
            val out = ArrayList<Msg>(c.count)
            while (c.moveToNext()) {
                out += Msg(
                    c.getLong(0), c.getLong(1), c.getString(2) ?: "", c.getString(3) ?: "",
                    c.getLong(4), c.getInt(5), c.getInt(6) != 0
                )
            }
            out
        }
    }.getOrNull() ?: emptyList()

    fun conversations(ctx: Context): List<Conversation> {
        val all = query(ctx, null, null, "${Telephony.Sms.DATE} DESC")
        val latest = LinkedHashMap<Long, Msg>()
        val unread = HashMap<Long, Int>()
        for (m in all) {
            if (m.address.isBlank()) continue
            latest.putIfAbsent(m.threadId, m)
            if (!m.outgoing && !m.read) unread[m.threadId] = (unread[m.threadId] ?: 0) + 1
        }
        return latest.values.map { m ->
            Conversation(m.threadId, m.address, name(ctx, m.address), m.body, m.date, unread[m.threadId] ?: 0, m.outgoing)
        }
    }

    fun threadId(ctx: Context, address: String): Long =
        runCatching { Telephony.Threads.getOrCreateThreadId(ctx, address) }.getOrDefault(-1L)

    fun messages(ctx: Context, address: String): List<Msg> {
        val t = threadId(ctx, address)
        if (t < 0) return emptyList()
        return query(ctx, "${Telephony.Sms.THREAD_ID}=?", arrayOf(t.toString()), "${Telephony.Sms.DATE} ASC")
    }

    fun markRead(ctx: Context, address: String) {
        val t = threadId(ctx, address)
        runCatching {
            ctx.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                ContentValues().apply { put(Telephony.Sms.READ, 1); put(Telephony.Sms.SEEN, 1) },
                "${Telephony.Sms.THREAD_ID}=? AND ${Telephony.Sms.READ}=0", arrayOf(t.toString())
            )
        }
        ctx.getSystemService(NotificationManager::class.java)?.cancel(address.hashCode())
    }

    fun send(ctx: Context, address: String, text: String) {
        val sm = ctx.getSystemService(SmsManager::class.java) ?: return
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, text)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
        }
        val uri = runCatching { ctx.contentResolver.insert(Telephony.Sms.CONTENT_URI, values) }.getOrNull()
        SmsEvents.changed()
        val parts = sm.divideMessage(text)
        val base = (uri?.lastPathSegment?.toIntOrNull() ?: (System.currentTimeMillis() % 100000).toInt()) * 100
        val sent = ArrayList<PendingIntent>()
        parts.forEachIndexed { i, _ ->
            val intent = Intent(ctx, SentReceiver::class.java)
                .setData(uri)
                .putExtra(SentReceiver.LAST, i == parts.lastIndex)
            sent += PendingIntent.getBroadcast(
                ctx, base + i, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        runCatching { sm.sendMultipartTextMessage(address, null, parts, sent, null) }
            .onFailure { uri?.let { setType(ctx, it, Telephony.Sms.MESSAGE_TYPE_FAILED) } }
    }

    fun setType(ctx: Context, uri: Uri, type: Int) {
        runCatching {
            ctx.contentResolver.update(uri, ContentValues().apply { put(Telephony.Sms.TYPE, type) }, null, null)
        }
    }

    fun deleteThread(ctx: Context, address: String) {
        val t = threadId(ctx, address)
        runCatching { ctx.contentResolver.delete(Telephony.Sms.CONTENT_URI, "${Telephony.Sms.THREAD_ID}=?", arrayOf(t.toString())) }
        SmsEvents.changed()
    }

    fun delete(ctx: Context, m: Msg) {
        runCatching { ctx.contentResolver.delete(m.uri, null, null) }
        SmsEvents.changed()
    }

    fun resend(ctx: Context, m: Msg) {
        runCatching { ctx.contentResolver.delete(m.uri, null, null) }
        send(ctx, m.address, m.body)
    }

    // Blacklist — the system list shared with the dialer. Works only while this app is the default SMS app.
    fun canBlock(ctx: Context) =
        runCatching { BlockedNumberContract.canCurrentUserBlockNumbers(ctx) }.getOrDefault(false)

    fun blocked(ctx: Context): List<String> = runCatching {
        ctx.contentResolver.query(
            BlockedNumberContract.BlockedNumbers.CONTENT_URI,
            arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER), null, null, null
        )?.use { c -> buildList { while (c.moveToNext()) c.getString(0)?.let { add(it) } } }
    }.getOrNull() ?: emptyList()

    fun isBlocked(ctx: Context, number: String) =
        runCatching { BlockedNumberContract.isBlocked(ctx, number) }.getOrDefault(false)

    fun block(ctx: Context, number: String) {
        runCatching {
            ctx.contentResolver.insert(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                ContentValues().apply { put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) }
            )
        }
    }

    fun unblock(ctx: Context, number: String) {
        runCatching { BlockedNumberContract.unblock(ctx, number) }
    }
}

// ---------- Notifications ----------

object Notify {
    const val CHANNEL = "messages"
    const val EXTRA_ADDRESS = "address"

    fun ensureChannel(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Сообщения", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun message(ctx: Context, address: String, body: String) {
        if (ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(ctx)
        val open = Intent(ctx, MainActivity::class.java)
            .putExtra(EXTRA_ADDRESS, address)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            ctx, address.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(Sms.name(ctx, address) ?: address)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        ctx.getSystemService(NotificationManager::class.java)?.notify(address.hashCode(), n)
    }
}

// ---------- Helpers ----------

/** "sms:+7999...?body=..." → "+7999..." */
fun recipientFrom(uri: Uri): String? =
    uri.schemeSpecificPart?.substringBefore('?')?.split(',', ';')?.firstOrNull()
        ?.let { Uri.decode(it) }?.trim()?.ifEmpty { null }

private val ru: Locale = Locale.forLanguageTag("ru")

fun sameDay(a: Long, b: Long): Boolean {
    val x = Calendar.getInstance().apply { timeInMillis = a }
    val y = Calendar.getInstance().apply { timeInMillis = b }
    return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
}

fun dayKey(t: Long): Int =
    Calendar.getInstance().apply { timeInMillis = t }.let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }

fun fmtClock(t: Long): String = SimpleDateFormat("HH:mm", ru).format(Date(t))

fun fmtListTime(t: Long): String {
    val now = System.currentTimeMillis()
    val sameYear = Calendar.getInstance().get(Calendar.YEAR) ==
        Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.YEAR)
    val p = when {
        sameDay(t, now) -> "HH:mm"
        sameYear -> "d MMM"
        else -> "dd.MM.yy"
    }
    return SimpleDateFormat(p, ru).format(Date(t))
}

fun fmtDay(t: Long): String {
    val now = System.currentTimeMillis()
    return when {
        sameDay(t, now) -> "Сегодня"
        sameDay(t, now - 86_400_000L) -> "Вчера"
        else -> SimpleDateFormat("d MMMM", ru).format(Date(t))
    }
}
