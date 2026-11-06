package com.nobcdz.tvquiz

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

class LocalStore(context: Context, val childId: Int = 1, private val shared: LocalStore? = null) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, 1) {
    override fun getWritableDatabase(): SQLiteDatabase = shared?.writableDatabase ?: super.getWritableDatabase()
    override fun getReadableDatabase(): SQLiteDatabase = shared?.readableDatabase ?: super.getReadableDatabase()

    override fun onCreate(db: SQLiteDatabase) {
        val schema = listOf(
            """CREATE TABLE questions (
                id TEXT PRIMARY KEY, subject TEXT, level TEXT, term INTEGER DEFAULT 0,
                unit INTEGER DEFAULT 0, difficulty INTEGER, stem TEXT NOT NULL,
                options TEXT NOT NULL, answer INTEGER NOT NULL, tip TEXT,
                enabled INTEGER DEFAULT 1, details TEXT, created_at INTEGER
            )""",
            "CREATE TABLE kv (k TEXT PRIMARY KEY, v TEXT)",
            """CREATE TABLE events (
                id INTEGER PRIMARY KEY AUTOINCREMENT, child INTEGER NOT NULL,
                type TEXT, payload TEXT, ts INTEGER
            )""",
            "CREATE INDEX events_profile_time ON events(child, ts)",
            """CREATE TABLE reviews (
                id TEXT PRIMARY KEY, child INTEGER NOT NULL, nonce TEXT UNIQUE,
                state TEXT NOT NULL, payload TEXT NOT NULL, ts INTEGER NOT NULL
            )""",
            """CREATE TABLE question_progress (
                child INTEGER NOT NULL, qid TEXT NOT NULL, repetitions INTEGER NOT NULL,
                interval_days INTEGER NOT NULL, ease REAL NOT NULL, due_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL, PRIMARY KEY(child, qid)
            )""",
            "CREATE INDEX progress_profile_due ON question_progress(child, due_at)"
        )
        schema.forEach { db.execSQL(it) }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Missing TVQuiz database upgrade from $oldVersion to $newVersion")
    }

    internal companion object {
        const val DATABASE_NAME = "tvquiz.db"
    }

    private val records = LearningRecords(this)

    fun recordProgress(id: String, quality: Int) = records.recordProgress(id, quality)
    fun repetitionStats(): JSONObject = records.repetitionStats()
    fun saveReview(value: JSONObject) = records.saveReview(value)
    fun noteReview(value: JSONObject) = records.noteReview(value)
    fun review(id: String): JSONObject? = records.review(id)
    fun reviews(state: String): JSONArray = records.reviews(state)

    private fun scoped(key: String) = if (childId == 1) key else "child:$childId:$key"

    fun get(key: String, default: String = ""): String =
        readableDatabase.rawQuery("SELECT v FROM kv WHERE k=?", arrayOf(scoped(key))).use {
            if (it.moveToFirst()) it.getString(0) ?: default else default
        }

    fun set(key: String, value: String) {
        check(writableDatabase.insertWithOnConflict("kv", null, ContentValues().apply {
            put("k", scoped(key)); put("v", value)
        }, SQLiteDatabase.CONFLICT_REPLACE) != -1L) { "无法保存应用设置" }
    }

    fun config(): JSONObject = ViewingSettings.read(get("config", "{}"))

    @Synchronized
    fun saveConfig(input: JSONObject): JSONObject {
        val updated = ViewingSettings.apply(config(), input)
        set("config", updated.toString())
        return updated
    }

    fun questions(subject: String? = null, level: String? = null, limit: Int = 200,
                  spaced: Boolean = false, forLearning: Boolean = false, curriculum: JSONObject? = null): JSONArray {
        val filters = mutableListOf("q.enabled = 1")
        val values = mutableListOf<String>()
        if (!subject.isNullOrEmpty()) {
            filters.add("q.subject = ?")
            values.add(subject)
        }
        if (!level.isNullOrEmpty()) {
            filters.add("q.level = ?")
            values.add(level)
        }
        if (forLearning) {
            val plan = curriculum ?: config()
            filters.add("q.level = ? AND q.term = ?")
            values.add(plan.getString("curriculumGrade"))
            values.add(plan.getInt("curriculumTerm").toString())
            filters.add("q.unit <= CASE q.subject WHEN '语文' THEN ? WHEN '数学' THEN ? WHEN '英语' THEN ? ELSE 12 END")
            for (key in listOf("chineseUnit", "mathUnit", "englishUnit")) values.add(plan.getInt(key).toString())
        }

        val withProgress = forLearning || spaced
        val order = mutableListOf<String>()
        val join = if (withProgress) {
            values.add(0, childId.toString())
            order.add("p.qid IS NOT NULL")
            if (spaced) {
                order.add("CASE WHEN p.due_at <= ? THEN 0 ELSE 1 END")
                values.add(System.currentTimeMillis().toString())
            }
            order.add("p.updated_at")
            "LEFT JOIN question_progress p ON p.qid = q.id AND p.child = ?"
        } else ""
        order.add("RANDOM()")
        values.add(limit.coerceIn(1, 5000).toString())
        val sql = """
            SELECT q.* FROM questions q $join
            WHERE ${filters.joinToString(" AND ")}
            ORDER BY ${order.joinToString(", ")} LIMIT ?
        """.trimIndent()
        val items = JSONArray()
        readableDatabase.rawQuery(sql, values.toTypedArray()).use { cursor ->
            while (cursor.moveToNext()) items.put(readQuestion(cursor))
        }
        return items
    }

    private fun readQuestion(row: android.database.Cursor): JSONObject {
        fun text(name: String): String? = row.getString(row.getColumnIndexOrThrow(name))
        fun number(name: String): Int = row.getInt(row.getColumnIndexOrThrow(name))
        val details = text("details")
        val item = if (details.isNullOrEmpty()) JSONObject() else JSONObject(details)
        if (!item.has("type")) item.put("type", "single")
        return item.put("id", text("id")).put("subject", text("subject").orEmpty())
            .put("level", text("level").orEmpty()).put("difficulty", number("difficulty"))
            .put("term", number("term")).put("unit", number("unit")).put("q", text("stem"))
            .put("a", JSONArray(text("options"))).put("k", number("answer")).put("tip", text("tip").orEmpty())
    }

    fun countQuestions(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM questions WHERE enabled=1", null).use {
        if (it.moveToFirst()) it.getInt(0) else 0
    }

    fun upsertQuestion(item: JSONObject): String {
        val prompt = item.optString("q").trim()
        require(prompt.length in 1..5000) { "请填写题干" }
        val type = item.optString("type", "single")
        val options = when (type) {
            "boolean" -> JSONArray().put("正确").put("错误")
            "single", "multiple", "text", "subjective" -> item.optJSONArray("a") ?: JSONArray()
            else -> throw IllegalArgumentException("不支持此题型")
        }
        val answer = when (type) {
            "subjective" -> 0
            "text" -> {
                val acceptable = item.optJSONArray("answers") ?: JSONArray()
                require(acceptable.length() > 0) { "填空题需要至少一个可接受的答案" }
                require((0 until acceptable.length()).all { acceptable.getString(it).isNotBlank() }) { "答案不能为空" }
                0
            }
            else -> {
                require(options.length() in 2..8) { "需要 2–8 个选项" }
                require((0 until options.length()).all { options.getString(it).isNotBlank() }) { "选项不能为空" }
                val keys = if (type == "multiple") item.optJSONArray("correctIndices") ?: JSONArray()
                    else JSONArray().put(item.optInt("k", -1))
                require(keys.length() != 0) { "请选择正确答案" }
                require((0 until keys.length()).all { keys.getInt(it) in 0 until options.length() }) { "答案序号超出选项范围" }
                keys.getInt(0)
            }
        }
        val difficulty = when (item.optString("difficulty", "2").trim()) {
            "易" -> 1
            "中" -> 3
            "难" -> 5
            else -> item.optString("difficulty", "2").trim().toIntOrNull() ?: item.optInt("difficulty", -1)
        }
        require(difficulty in 1..5) { "难度需要为易、中、难，或 1–5 的数值" }
        val suppliedId = item.optString("id")
        val id = if (suppliedId.isEmpty()) "q_" + UUID.randomUUID() else suppliedId
        val row = ContentValues().apply {
            put("id", id)
            put("stem", prompt)
            put("options", options.toString())
            put("answer", answer)
            put("subject", item.optString("subject", "常识"))
            put("level", item.optString("level"))
            put("difficulty", difficulty)
            put("term", item.optInt("term"))
            put("unit", item.optInt("unit"))
            put("tip", item.optString("tip"))
            put("enabled", 1)
            put("details", JSONObject(item.toString()).put("type", type).toString())
        }
        check(writableDatabase.insertWithOnConflict("questions", null, row, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
        return id
    }

    fun importQuestions(items: JSONArray): Int {
        require(items.length() in 1..5000) { "每次导入 1–5000 道题" }
        val db = writableDatabase
        var added = 0
        db.beginTransaction()
        try {
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val matches = db.rawQuery(
                    "SELECT 1 FROM questions WHERE subject=? AND level=? AND term=? AND stem=? AND id<>? LIMIT 1",
                    arrayOf(item.optString("subject", "常识"), item.optString("level"), item.optInt("term", 0).toString(),
                        item.optString("q").trim(), item.optString("id"))
                ).use { it.moveToFirst() }
                if (matches) continue
                upsertQuestion(item)
                added++
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return added
    }

    fun deleteQuestion(id: String): Boolean {
        if (id.startsWith("rjb-")) set("deleted_builtin_" + id, "1")
        return writableDatabase.delete("questions", "id=?", arrayOf(id)) > 0
    }

    fun logEvent(type: String, payload: JSONObject) {
        val event = ContentValues().apply {
            put("child", childId)
            put("type", type)
            put("payload", payload.toString())
            put("ts", System.currentTimeMillis())
        }
        writableDatabase.insertOrThrow("events", null, event)
    }

    fun recentEvents(limit: Int = 50): JSONArray {
        val output = JSONArray()
        readableDatabase.query(
            "events", arrayOf("ts", "type", "payload"), "child=? AND type<>?",
            arrayOf(childId.toString(), "usage"), null, null, "id DESC", limit.coerceIn(1, 500).toString()
        ).use { rows ->
            while (rows.moveToNext()) {
                output.put(JSONObject().put("ts", rows.getLong(0)).put("type", rows.getString(1))
                    .put("payload", JSONObject(rows.getString(2))))
            }
        }
        return output
    }

    fun stats(start: Long, end: Long): JSONObject {
        val counts = EventTotals()
        readableDatabase.query(
            "events", arrayOf("type", "payload"), "child=? AND ts>=? AND ts<?",
            arrayOf(childId.toString(), start.toString(), end.toString()), null, null, null
        ).use { events ->
            while (events.moveToNext()) {
                val payload = runCatching { JSONObject(events.getString(1)) }.getOrElse { JSONObject() }
                counts.add(events.getString(0), payload)
            }
        }
        return counts.asJson()
    }

    private class EventTotals {
        private var milliseconds = 0L
        private var attempts = 0
        private var hits = 0
        private var passed = 0

        fun add(kind: String, value: JSONObject) {
            when (kind) {
                "usage" -> milliseconds += value.optLong("milliseconds")
                "answer" -> {
                    attempts += 1
                    if (value.optBoolean("correct")) hits += 1
                }
                "quiz" -> if (!value.optBoolean("native")) {
                    attempts += value.optInt("total")
                    hits += value.optInt("correct")
                }
                "quiz_pass" -> passed += 1
            }
        }

        fun asJson(): JSONObject {
            val minutes = milliseconds / 60_000.0
            val percent = if (attempts == 0) 0 else hits * 100 / attempts
            return JSONObject().put("watch", minutes).put("usedMinutes", minutes)
                .put("rounds", passed).put("correct", hits).put("answered", attempts).put("accuracy", percent)
        }
    }

    fun todayStats(): JSONObject = stats(startOfToday(), Long.MAX_VALUE)
    fun roundsDoneToday(): Int = DatabaseUtils.longForQuery(
        readableDatabase, "SELECT COUNT(*) FROM events WHERE child=? AND ts>=? AND ts<? AND type=?",
        arrayOf(childId.toString(), startOfToday().toString(), Long.MAX_VALUE.toString(), "quiz_pass")
    ).toInt()

    fun historyDays(days: Int = 7): JSONArray {
        val history = JSONArray()
        val boundary = Calendar.getInstance().apply {
            timeInMillis = startOfToday()
            add(Calendar.DAY_OF_YEAR, 1)
        }
        repeat(days.coerceIn(1, 90)) { offset ->
            val end = boundary.timeInMillis
            boundary.add(Calendar.DAY_OF_YEAR, -1)
            val label = when (offset) {
                0 -> "今天"
                1 -> "昨天"
                else -> (boundary.get(Calendar.MONTH) + 1).toString() + "/" + boundary.get(Calendar.DAY_OF_MONTH)
            }
            history.put(stats(boundary.timeInMillis, end).put("label", label))
        }
        return history
    }

    fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}


