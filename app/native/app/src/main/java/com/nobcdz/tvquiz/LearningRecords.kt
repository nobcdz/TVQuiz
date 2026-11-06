package com.nobcdz.tvquiz

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.round

internal class LearningRecords(private val store: LocalStore) {
    private val child get() = store.childId.toString()

    fun recordProgress(id: String, quality: Int) {
        val db = store.writableDatabase
        db.beginTransaction()
        try {
            val previous = db.query("question_progress", arrayOf("repetitions", "interval_days", "ease"),
                "child=? AND qid=?", arrayOf(child, id), null, null, null).use { row ->
                if (row.moveToFirst()) Progress(row.getInt(0), row.getLong(1), row.getDouble(2))
                else Progress(0, 0, 2.5)
            }
            val score = quality.coerceIn(0, 5)
            val repetitions = if (score < 3) 0 else previous.repetitions + 1
            val interval = when (repetitions) {
                0 -> 0L
                1 -> 1L
                2 -> 6L
                else -> round(previous.interval * previous.ease).toLong().coerceIn(1L, 36_500L)
            }
            val missed = 5 - score
            val ease = (previous.ease + 0.1 - missed * (0.08 + missed * 0.02)).coerceAtLeast(1.3)
            val now = System.currentTimeMillis()
            val delay = if (repetitions == 0) 600_000L else interval * 86_400_000L
            val values = ContentValues().apply {
                put("child", store.childId)
                put("qid", id)
                put("repetitions", repetitions)
                put("interval_days", interval)
                put("ease", ease)
                put("due_at", now + delay)
                put("updated_at", now)
            }
            db.insertWithOnConflict("question_progress", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun repetitionStats(): JSONObject {
        val sql = """
            SELECT COUNT(*), SUM(CASE WHEN p.due_at <= ? THEN 1 ELSE 0 END)
            FROM question_progress p
            JOIN questions q ON q.id = p.qid
            WHERE p.child = ? AND q.enabled = 1
        """.trimIndent()
        return store.readableDatabase.rawQuery(sql, arrayOf(System.currentTimeMillis().toString(), child)).use {
            it.moveToFirst()
            JSONObject().put("studied", it.getInt(0)).put("due", it.getInt(1))
        }
    }

    fun saveReview(value: JSONObject) {
        val row = ContentValues().apply {
            put("id", value.getString("id"))
            put("nonce", value.getJSONObject("input").getString("nonce"))
            put("state", value.optString("state", "pending"))
            put("payload", value.toString())
            put("ts", value.getLong("ts"))
            put("child", store.childId)
        }
        store.writableDatabase.insertWithOnConflict("reviews", null, row, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun noteReview(value: JSONObject) {
        val payload = ContentValues().apply { put("payload", value.toString()) }
        store.writableDatabase.update("reviews", payload, "child=? AND id=? AND state=?",
            arrayOf(child, value.getString("id"), "pending"))
    }

    fun review(id: String): JSONObject? =
        reviewRows("child=? AND id=?", arrayOf(child, id), 1).optJSONObject(0)

    fun reviews(state: String): JSONArray = if (state == "all") {
        reviewRows("child=?", arrayOf(child), 200)
    } else {
        reviewRows("child=? AND state=?", arrayOf(child, state), 200)
    }

    private fun reviewRows(filter: String, args: Array<String>, limit: Int): JSONArray {
        val rows = JSONArray()
        store.readableDatabase.query("reviews", arrayOf("payload"), filter, args,
            null, null, "ts DESC", limit.toString()).use { cursor ->
            while (cursor.moveToNext()) rows.put(JSONObject(cursor.getString(0)))
        }
        return rows
    }

    private data class Progress(val repetitions: Int, val interval: Long, val ease: Double)
}
