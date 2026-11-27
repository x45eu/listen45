// SPDX-License-Identifier: GPL-3.0-only
package eu.x45.listen45.recognition

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class ReferenceTrack(val id: Long, val title: String, val artist: String)

internal class ReferenceCatalog private constructor(context: Context) :
    SQLiteOpenHelper(context, "reference-catalog.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE tracks (" +
                "id INTEGER PRIMARY KEY, title TEXT NOT NULL, artist TEXT NOT NULL, " +
                "fingerprint BLOB NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("No catalog migration from $oldVersion to $newVersion")
    }

    fun hasTracks(): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM tracks LIMIT 1", null).use { it.moveToFirst() }

    fun tracks(): List<ReferenceTrack> =
        readableDatabase.rawQuery("SELECT id, title, artist FROM tracks ORDER BY title COLLATE NOCASE, id", null)
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(ReferenceTrack(cursor.getLong(0), cursor.getString(1), cursor.getString(2)))
                    }
                }
            }

    fun add(title: String, artist: String, fingerprint: LongArray): Boolean {
        val bytes = ByteBuffer.allocate(fingerprint.size * Long.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        bytes.asLongBuffer().put(fingerprint)
        val values = ContentValues().apply {
            put("title", title)
            put("artist", artist)
            put("fingerprint", bytes.array())
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val count = db.rawQuery("SELECT COUNT(*) FROM tracks", null).use {
                it.moveToFirst()
                it.getInt(0)
            }
            if (count >= MAX_TRACKS) return false
            db.insertOrThrow("tracks", null, values)
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    fun delete(id: Long) {
        writableDatabase.delete("tracks", "id = ?", arrayOf(id.toString()))
    }

    fun forEachFingerprint(action: (ReferenceTrack, LongArray) -> Unit) {
        // Query one blob at a time to stay within Android's cursor window even for long tracks.
        for (track in tracks()) {
            readableDatabase.query("tracks", arrayOf("fingerprint"), "id = ?", arrayOf(track.id.toString()),
                null, null, null).use { cursor ->
                if (!cursor.moveToFirst()) return@use
                val bytes = cursor.getBlob(0)
                val landmarks = LongArray(bytes.size / Long.SIZE_BYTES)
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().get(landmarks)
                action(track, landmarks)
            }
        }
    }

    companion object {
        const val MAX_TRACKS = 100

        @Volatile
        private var instance: ReferenceCatalog? = null

        fun get(context: Context): ReferenceCatalog = instance ?: synchronized(this) {
            instance ?: ReferenceCatalog(context.applicationContext).also { instance = it }
        }
    }
}
