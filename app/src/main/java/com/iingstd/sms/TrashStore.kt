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
    fun restore(id: Long): Boolean {
        val record=entries().firstOrNull { it.id==id } ?: return false
        val values=ContentValues().apply {
            put(Telephony.Sms.ADDRESS,record.address); put(Telephony.Sms.BODY,record.body)
            put(Telephony.Sms.DATE,record.date); put(Telephony.Sms.TYPE,record.type)
            put(Telephony.Sms.READ,record.read); put(Telephony.Sms.SEEN,record.seen)
        }
        val inserted=context.contentResolver.insert(Telephony.Sms.CONTENT_URI,values) ?: return false
        if (writableDatabase.delete("trash","id=?",arrayOf(id.toString()))>0) return true
        // Keep the restored SMS and avoid pretending the trash entry was removed.
        return inserted.toString().isNotBlank() && false
    }
    fun permanentlyDelete(id:Long):Boolean=writableDatabase.delete("trash","id=?",arrayOf(id.toString()))>0
    fun empty():Int=writableDatabase.delete("trash",null,null)
}
