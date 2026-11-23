package com.iingstd.sms

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.provider.Telephony

/** Local, app-private recovery copy. Only SMS deleted through this app can be recovered. */
class TrashStore(private val context: Context) : SQLiteOpenHelper(context, "sms_trash.db", null, 1) {
    data class Entry(val id: Long, val address: String, val body: String, val date: Long,
                     val type: Int, val read: Int, val seen: Int, val deletedAt: Long)
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE trash (id INTEGER PRIMARY KEY AUTOINCREMENT, address TEXT NOT NULL, body TEXT NOT NULL, msg_date INTEGER NOT NULL, msg_type INTEGER NOT NULL, msg_read INTEGER NOT NULL, msg_seen INTEGER NOT NULL, deleted_at INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun purgeExpired(): Int = writableDatabase.delete("trash", "deleted_at < ?", arrayOf((System.currentTimeMillis()-30L*24*60*60*1000).toString()))
    fun entries(): List<Entry> {
        purgeExpired()
        val out=mutableListOf<Entry>()
        readableDatabase.query("trash",null,null,null,null,null,"deleted_at DESC").use { c ->
            while(c.moveToNext()) out.add(Entry(c.getLong(c.getColumnIndexOrThrow("id")),
                c.getString(c.getColumnIndexOrThrow("address")),
                c.getString(c.getColumnIndexOrThrow("body")),
                c.getLong(c.getColumnIndexOrThrow("msg_date")),
                c.getInt(c.getColumnIndexOrThrow("msg_type")),
                c.getInt(c.getColumnIndexOrThrow("msg_read")),
                c.getInt(c.getColumnIndexOrThrow("msg_seen")),
                c.getLong(c.getColumnIndexOrThrow("deleted_at"))))
        }
        return out
    }
    fun moveSmsToTrash(smsId: Long): Boolean {
        val uri=Uri.withAppendedPath(Telephony.Sms.CONTENT_URI,smsId.toString())
        val record=context.contentResolver.query(uri,
            arrayOf("address","body","date","type","read","seen"),null,null,null)?.use { c ->
            if (!c.moveToFirst()) null else Entry(0,c.getString(0).orEmpty(),c.getString(1).orEmpty(),
                c.getLong(2),c.getInt(3),c.getInt(4),c.getInt(5),System.currentTimeMillis())
        } ?: return false
        val values=ContentValues().apply {
            put("address",record.address); put("body",record.body); put("msg_date",record.date)
            put("msg_type",record.type); put("msg_read",record.read); put("msg_seen",record.seen)
            put("deleted_at",record.deletedAt)
        }
        val backupId=writableDatabase.insertOrThrow("trash",null,values)
        if (context.contentResolver.delete(uri,null,null)>0) return true
        writableDatabase.delete("trash","id=?",arrayOf(backupId.toString()))
        return false
    }
    /**
     * Batch delete: one SQLite transaction instead of a disk commit per SMS.
     * The backup is inserted before each provider delete; failures remove that backup.
     * Commit successful backups even if a later SMS fails.
     */
    fun moveSmsToTrashBatch(ids: List<Long>, progress: (Int, Int) -> Unit = { _, _ -> }): Pair<Int, Int> {
        val db = writableDatabase
        var moved = 0
        var failed = 0
        db.beginTransaction()
        try {
            ids.forEachIndexed { index, smsId ->
                try {
                    val uri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, smsId.toString())
                    val record = context.contentResolver.query(uri,
                        arrayOf("address", "body", "date", "type", "read", "seen"), null, null, null)?.use { c ->
                        if (!c.moveToFirst()) null else Entry(0, c.getString(0).orEmpty(),
                            c.getString(1).orEmpty(), c.getLong(2), c.getInt(3),
                            c.getInt(4), c.getInt(5), System.currentTimeMillis())
                    }
                    if (record == null) {
                        failed++
                    } else {
                        val values = ContentValues().apply {
                            put("address", record.address); put("body", record.body)
                            put("msg_date", record.date); put("msg_type", record.type)
                            put("msg_read", record.read); put("msg_seen", record.seen)
                            put("deleted_at", record.deletedAt)
                        }
                        val backupId = db.insertOrThrow("trash", null, values)
                        if (context.contentResolver.delete(uri, null, null) > 0) moved++
                        else {
                            db.delete("trash", "id=?", arrayOf(backupId.toString()))
                            failed++
                        }
                    }
                } catch (_: Exception) { failed++ }
                if ((index + 1) % 10 == 0 || index + 1 == ids.size) progress(index + 1, ids.size)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return moved to failed
    }
    /**
     * Restore into the correct Android SMS box and verify the row is readable
     * before removing the recovery copy. A failed verification retains the backup.
     */
    fun restore(id: Long): Boolean {
        val record = entries().firstOrNull { it.id == id } ?: return false
        val destination = when (record.type) {
            Telephony.Sms.MESSAGE_TYPE_INBOX -> Telephony.Sms.Inbox.CONTENT_URI
            Telephony.Sms.MESSAGE_TYPE_SENT -> Telephony.Sms.Sent.CONTENT_URI
            Telephony.Sms.MESSAGE_TYPE_DRAFT -> Telephony.Sms.Draft.CONTENT_URI
            Telephony.Sms.MESSAGE_TYPE_OUTBOX -> Telephony.Sms.Outbox.CONTENT_URI
            Telephony.Sms.MESSAGE_TYPE_FAILED -> Telephony.Sms.CONTENT_URI
            Telephony.Sms.MESSAGE_TYPE_QUEUED -> Telephony.Sms.CONTENT_URI
            else -> Telephony.Sms.CONTENT_URI
        }
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, record.address)
            put(Telephony.Sms.BODY, record.body)
            put(Telephony.Sms.DATE, record.date)
            put(Telephony.Sms.TYPE, record.type)
            put(Telephony.Sms.READ, record.read)
            put(Telephony.Sms.SEEN, record.seen)
        }
        val inserted = context.contentResolver.insert(destination, values) ?: return false
        val verified = try {
            context.contentResolver.query(inserted,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.TYPE),
                null, null, null)?.use { c ->
                c.moveToFirst() && c.getString(0).orEmpty() == record.address &&
                    c.getString(1).orEmpty() == record.body && c.getInt(2) == record.type
            } == true
        } catch (_: Exception) { false }
        if (!verified) return false // Never discard the only recovery copy.
        return writableDatabase.delete("trash", "id=?", arrayOf(id.toString())) > 0
    }
    fun permanentlyDelete(id:Long):Boolean=writableDatabase.delete("trash","id=?",arrayOf(id.toString()))>0
    fun empty():Int=writableDatabase.delete("trash",null,null)
}
