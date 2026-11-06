package com.nobcdz.tvquiz

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Household(context: Context, first: LocalStore) {
    private val stores = listOf(first, LocalStore(context, 2, first))
    private val engines = stores.map(::LearningEngine)
    private var selection = first.get("household_viewers", "1")
    private var count = first.get("household_child_count").toIntOrNull()?.coerceIn(1, 2)
        ?: if (selection in listOf("2", "both") || store(2).get("display_name").isNotEmpty() ||
            store(2).config().optInt("version") > 1) 2 else 1

    @Synchronized fun childCount(): Int = count

    @Synchronized fun setChildCount(value: Int) {
        updateProfiles(value, if (value == 1) "1" else selection, emptyMap())
    }

    @Synchronized fun updateProfiles(value: Int, viewer: String, names: Map<Int, String>) {
        require(value in 1..2) { "孩子档案数量须为 1 或 2" }
        require(viewer == "1" || (value == 2 && viewer in listOf("2", "both"))) { "观看档案未启用" }
        val normalized = names.mapValues { (id, name) ->
            require(id in 1..value && name.trim().length in 1..16) { "请输入 1–16 字的档案名称" }
            name.trim()
        }
        val db = stores[0].writableDatabase
        db.beginTransaction()
        try {
            stores[0].set("household_child_count", value.toString())
            stores[0].set("household_viewers", viewer)
            normalized.forEach { (id, name) -> store(id).set("display_name", name) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        count = value
        selection = viewer
    }

    @Synchronized fun profiles(): JSONArray = JSONArray((1..count).map { id ->
        val config = store(id).config()
        JSONObject().put("id", id).put("name", name(id))
            .put("grade", config.getString("curriculumGrade")).put("term", config.getInt("curriculumTerm"))
    })

    @Synchronized fun chooseChild(id: Int): Boolean {
        if (id !in 1..count) return false
        select(id.toString())
        return true
    }

    fun store(id: Int): LocalStore = stores[id - 1]
    fun engine(id: Int): LearningEngine = engines[id - 1]
    fun name(id: Int): String = store(id).get("display_name", "孩子 $id")

    fun rename(id: Int, name: String) {
        require(id in 1..2 && name.trim().length in 1..16) { "请输入 1–16 字的档案名称" }
        store(id).set("display_name", name.trim())
    }

    @Synchronized fun selection(): String = selection

    @Synchronized fun select(value: String) {
        require(value == "1" || (count == 2 && value in listOf("2", "both"))) { "观看档案未启用" }
        stores[0].set("household_viewers", value)
        selection = value
    }

    private fun viewers(): List<Int> = when (selection) {
        "2" -> listOf(2)
        "both" -> listOf(1, 2)
        else -> listOf(1)
    }

    private fun target(): Int {
        val ids = viewers()
        val states = ids.associateWith { engine(it).status() }
        return ids.firstOrNull { states.getValue(it).getString("reason") in listOf("quota", "schedule", "rounds") }
            ?: ids.firstOrNull { store(it).get("review_feedback").isNotEmpty() }
            ?: ids.firstOrNull { states.getValue(it).getString("reason").isNotEmpty() }
            ?: ids.first()
    }

    @Synchronized fun status(): JSONObject {
        val id = target()
        val status = engine(id).status()
        if (selection == "both") {
            val remaining = viewers().map { engine(it).usableMillis() }.minOrNull() ?: 0L
            val dailyRemaining = viewers().minOf { engine(it).status().getLong("dailyRemainingSeconds") }
            status.put("remainingSeconds", (remaining + 999) / 1000)
            status.put("dailyRemainingSeconds", dailyRemaining)
            if (status.getString("reason").isEmpty() && remaining == 0L) {
                val blocked = viewers().map { engine(it).status() }.firstOrNull { it.getString("reason").isNotEmpty() }
                if (blocked != null) status.put("reason", blocked.getString("reason"))
                    .put("nextAvailableAt", blocked.getLong("nextAvailableAt"))
            }
        }
        return status.put("childId", id).put("childName", name(id))
            .put("viewingTogether", selection == "both").put("viewerSelection", selection)
    }

    @Synchronized fun hasFeedback(): Boolean = viewers().any { store(it).get("review_feedback").isNotEmpty() }

    @Synchronized fun consume(milliseconds: Long, expectedSelection: String = selection) {
        if (selection != expectedSelection) return
        if (selection != "both") {
            engine(viewers().first()).consume(milliseconds)
            return
        }
        synchronized(engines[0]) {
            synchronized(engines[1]) {
                val db = stores[0].writableDatabase
                db.beginTransaction()
                try {
                    val amount = minOf(milliseconds.coerceIn(0, 5000), engines[0].usableMillis(), engines[1].usableMillis())
                    if (amount > 0) engines.forEach { it.consume(amount) }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }
    }

    @Synchronized fun start(): JSONObject {
        val id = target()
        return decorate(engine(id).start(), id)
    }

    @Synchronized fun answer(input: JSONObject): JSONObject {
        val id = input.optInt("childId", target())
        require(id in viewers()) { "观看档案已切换，请重新打开题目" }
        val result = engine(id).answer(input)
        if (result.optBoolean("pending")) GradingService.enqueue(store(id), engine(id), result.getString("reviewId"))
        return decorate(result, id)
    }

    @Synchronized fun acknowledgeReview(input: JSONObject): JSONObject {
        val id = input.optInt("childId", target())
        require(id in viewers()) { "观看档案已切换，请重新打开题目" }
        return decorate(engine(id).acknowledgeReview(input.getString("reviewId")), id)
    }

    private fun decorate(result: JSONObject, id: Int): JSONObject {
        if (result.has("error")) return result
        val combined = status()
        result.put("childId", id).put("childName", name(id)).put("status", combined)
        result.optJSONObject("next")?.put("childId", id)?.put("childName", name(id))?.put("status", combined)
        return result
    }
}
