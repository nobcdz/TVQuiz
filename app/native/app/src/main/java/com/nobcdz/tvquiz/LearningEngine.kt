package com.nobcdz.tvquiz

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class LearningEngine(private val store: LocalStore) {
    private fun number(key: String) = store.get(key, "0").toLongOrNull() ?: 0L
    private fun put(key: String, value: Long) = store.set(key, value.toString())

    private val allowance = ViewingAllowance(store)

    @Synchronized
    fun status(): JSONObject {
        val state = allowance.state()
        val balance = state.balance
        return JSONObject()
            .put("remainingSeconds", (minOf(balance.credit, balance.dailyLeft) + 999) / 1000)
            .put("usedMinutes", balance.used / 60000.0)
            .put("dailyRemainingSeconds", (balance.dailyLeft + 999) / 1000)
            .put("extraPool", number("extra_pool"))
            .put("wrongProgress", number("wrong_progress"))
            .put("nextAvailableAt", state.availableAt)
            .put("roundsDoneToday", state.rounds)
            .put("reason", state.reason)
    }

    @Synchronized
    fun usableMillis(): Long {
        val state = allowance.state()
        return if (state.reason.isEmpty()) minOf(state.balance.credit, state.balance.dailyLeft) else 0L
    }

    @Synchronized
    fun consume(milliseconds: Long) = allowance.debit(milliseconds)

    private fun active(): JSONObject? = store.get("active_round").takeIf { it.isNotEmpty() }?.let { JSONObject(it) }

    private fun draw(round: JSONObject, isExtra: Boolean) {
        val candidates = store.questions(
            limit = 2, spaced = store.config().optBoolean("spacedRepetitionEnabled"),
            forLearning = true, curriculum = round.getJSONObject("rules")
        )
        require(candidates.length() != 0) { "题库为空，请由家长添加题目" }
        val previous = round.optJSONObject("question")?.optString("id")
        val selected = (0 until candidates.length()).asSequence().map(candidates::getJSONObject)
            .firstOrNull { it.optString("id") != previous } ?: candidates.getJSONObject(0)
        round.put("question", selected).put("nonce", UUID.randomUUID().toString()).put("extra", isExtra)
    }

    private fun saveRound(round: JSONObject?) = store.set("active_round", round?.toString().orEmpty())

    private fun publicRound(round: JSONObject): JSONObject = RoundRules.publicCopy(round)
        .put("extraPool", number("extra_pool")).put("status", status())

    private fun defaultExtra(round: JSONObject): Boolean = if (round.getString("mode") == "practice") {
        number("extra_pool") > 0
    } else {
        round.optInt("answered") >= round.getInt("target")
    }

    @Synchronized
    fun start(): JSONObject {
        val viewing = status()
        val reply = JSONObject().put("status", viewing)
        val blocked = viewing.getString("reason")
        if (blocked == "quota") return reply.put("blocked", blocked)

        val config = store.config()
        val mode = config.getString("playMode")
        if (mode != "practice") {
            if (blocked == "rounds" || blocked == "schedule") return reply.put("blocked", blocked)
            if (viewing.optLong("remainingSeconds") > 0L) return reply.put("watching", true)
        }

        val saved = active()?.takeUnless {
            it.has("pendingReview") || it.optJSONObject("question")?.optString("type") == "subjective"
        }
        val newRules = saved != null && saved.optInt("answered") == 0 &&
            saved.getJSONObject("rules").optInt("version") != config.optInt("version")
        val task = if (saved == null || newRules || saved.optString("mode") != mode) {
            createRound(config)
        } else saved
        if (task !== saved || RoundRules.needsNewQuestion(task, config)) {
            if (task === saved) RoundRules.updateCurriculum(task, config)
            draw(task, defaultExtra(task))
            saveRound(task)
        }
        return publicRound(task)
    }

    private fun createRound(config: JSONObject): JSONObject = JSONObject().apply {
        put("id", UUID.randomUUID().toString())
        put("rules", config)
        put("mode", config.getString("playMode"))
        put("target", config.getInt("questionsPerRound"))
        for (key in listOf("answered", "correct")) put(key, 0)
    }

    private inline fun <T> atomic(work: () -> T): T {
        val database = store.writableDatabase
        database.beginTransaction()
        try {
            val result = work()
            database.setTransactionSuccessful()
            return result
        } finally {
            database.endTransaction()
        }
    }

    @Synchronized
    fun answer(input: JSONObject): JSONObject {
        val current = status()
        val blocked = current.getString("reason")
        if (blocked == "quota") return JSONObject().put("blocked", blocked)
        val previous = store.get("last_answer").takeIf(String::isNotEmpty)?.let { JSONObject(it) }
        if (previous != null && previous.optString("nonce") == input.optString("nonce")) {
            return previous.getJSONObject("result")
        }

        val round = active() ?: return JSONObject().put("error", "round_missing")
        require(!round.has("pendingReview")) { "题目已更新，请重新加载" }
        require(!RoundRules.needsNewQuestion(round, store.config())) { "年级或学期已更新，请重新加载题目" }
        val sameRound = input.getString("roundId") == round.getString("id")
        val sameQuestion = input.getString("nonce") == round.getString("nonce")
        require(sameRound && sameQuestion) { "题目已更新，请重新加载" }
        if (round.getString("mode") != "practice" && (blocked == "schedule" || blocked == "rounds")) {
            return JSONObject().put("blocked", blocked)
        }
        val question = round.getJSONObject("question")
        val type = question.optString("type", "single")
        require(type != "subjective") { "题目已更新，请重新加载" }
        val mark = RoundRules.mark(question, input)
        val correct = mark.correct
        return atomic {
            val rules = round.getJSONObject("rules")
            val wasExtra = round.optBoolean("extra")
            val mode = round.getString("mode")
            val extraLeft = updatePractice(rules, correct, wasExtra)
            val progress = RoundRules.advance(round, correct, extraLeft)
            val questionId = question.getString("id")
            val progressScore = if (correct) 4 else 1
            store.recordProgress(questionId, progressScore)
            val answerEvent = JSONObject().apply {
                put("qid", questionId)
                put("correct", correct)
                put("difficulty", question.optInt("difficulty", 2))
                put("extra", wasExtra)
                put("mode", mode)
            }
            store.logEvent("answer", answerEvent)
            val result = JSONObject().apply {
                put("correct", correct)
                put("correctIndices", mark.indices)
                put("answerText", mark.answerText)
                put("tip", question.optString("tip"))
                put("finished", progress.finished)
                put("passed", progress.finished && progress.passed)
            }
            if (progress.finished) {
                result.put("rewardMinutes", closeRound(round, progress.passed))
            } else {
                draw(round, defaultExtra(round) && (mode != "streak" || progress.done))
                saveRound(round)
                result.put("next", publicRound(round))
            }
            result.put("status", status())
            store.set("last_answer", JSONObject().put("nonce", input.getString("nonce")).put("result", result).toString())
            result
        }
    }

    private fun closeRound(round: JSONObject, passed: Boolean): Double {
        var minutes = 0.0
        if (passed) {
            val rules = round.getJSONObject("rules")
            val reward = rules.getLong("sessionMinutes") * 60_000L
            minutes = allowance.grant(reward) / 60_000.0
            val event = JSONObject().put("minutes", minutes).put("roundId", round.getString("id"))
            store.logEvent("quiz_pass", event)
        }
        val summary = JSONObject().put("passed", passed)
            .put("correct", round.getInt("correct")).put("total", round.getInt("answered")).put("native", true)
        store.logEvent("quiz", summary)
        saveRound(null)
        return minutes
    }

    private fun updatePractice(rules: JSONObject, correct: Boolean, wasExtra: Boolean): Long {
        val pool = number("extra_pool")
        if (correct && wasExtra && pool > 0) {
            put("extra_pool", pool - 1)
            return pool - 1
        }
        if (correct || !rules.optBoolean("extraEnabled")) return pool
        val wrong = number("wrong_progress") + 1
        val threshold = rules.getInt("wrongThreshold")
        if (wrong < threshold) {
            put("wrong_progress", wrong)
            return pool
        }
        val added = rules.getLong("extraQuestions")
        put("extra_pool", pool + added)
        put("wrong_progress", wrong - threshold)
        store.logEvent("extra_added", JSONObject().put("count", added))
        return pool + added
    }


}
