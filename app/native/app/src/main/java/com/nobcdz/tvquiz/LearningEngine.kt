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
            .put("awaitingReview", active()?.has("pendingReview") == true)
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

        val feedback = store.get("review_feedback").takeIf(String::isNotEmpty)?.let(store::review)
        if (feedback?.optString("state") == "graded") {
            return reply.put("reviewed", true).put("reviewId", feedback.getString("id"))
                .put("score", feedback.getDouble("score")).put("feedback", feedback.optString("feedback"))
                .put("result", feedback.getJSONObject("result"))
        }

        val config = store.config()
        val mode = config.getString("playMode")
        if (mode != "practice") {
            if (blocked == "rounds" || blocked == "schedule") return reply.put("blocked", blocked)
            if (viewing.optLong("remainingSeconds") > 0L) return reply.put("watching", true)
        }

        val saved = active()
        if (saved?.has("pendingReview") == true) {
            return reply.put("pending", true).put("reviewId", saved.getString("pendingReview"))
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
        for (key in listOf("answered", "correct", "streak")) put(key, 0)
    }

    @Synchronized
    fun acknowledgeReview(id: String): JSONObject {
        if (store.get("review_feedback") == id) store.set("review_feedback", "")
        return JSONObject().put("ok", true).put("status", status())
    }

    @Synchronized
    fun grade(reviewId: String, score: Double, feedback: String, source: String): JSONObject {
        require(score in 0.0..100.0) { "分数需要在 0–100 之间" }
        val entry = requireNotNull(store.review(reviewId)) { "待批题目不存在" }
        if (entry.optString("state") == "graded") return entry
        val passScore = GradingService.settings(store).optDouble("passScore", 60.0)
        val passed = score >= passScore
        val quality = if (!passed) 1 else if (score >= 90) 5 else if (score >= 75) 4 else 3
        return atomic {
            val result = answer(entry.getJSONObject("input"), passed, quality)
            require(!result.has("error") && !result.has("blocked")) { "当前任务暂不可结算，请在可用时段重试" }
            entry.put("result", result)
            entry.put("source", source)
            entry.put("feedback", feedback.take(5_000))
            entry.put("score", score)
            entry.put("gradedAt", System.currentTimeMillis())
            entry.put("state", "graded")
            store.saveReview(entry)
            store.set("review_feedback", reviewId)
            store.logEvent("review_graded", JSONObject().put("score", score).put("source", source).put("id", reviewId))
            entry
        }
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
    fun answer(input: JSONObject, verdict: Boolean? = null, quality: Int? = null): JSONObject {
        val current = status()
        val blocked = current.getString("reason")
        val student = verdict == null
        if (student && blocked == "quota") return JSONObject().put("blocked", blocked)
        if (student) {
            val previous = store.get("last_answer").takeIf(String::isNotEmpty)?.let { JSONObject(it) }
            if (previous != null && previous.optString("nonce") == input.optString("nonce")) {
                return previous.getJSONObject("result")
            }
        }

        val round = active() ?: return JSONObject().put("error", "round_missing")
        if (student && round.has("pendingReview")) {
            return JSONObject().put("pending", true).put("status", current)
                .put("reviewId", round.getString("pendingReview"))
        }
        if (student) {
            require(!RoundRules.needsNewQuestion(round, store.config())) { "年级或学期已更新，请重新加载题目" }
        }
        val sameRound = input.getString("roundId") == round.getString("id")
        val sameQuestion = input.getString("nonce") == round.getString("nonce")
        require(sameRound && sameQuestion) { "题目已更新，请重新加载" }
        if (student && round.getString("mode") != "practice" && (blocked == "schedule" || blocked == "rounds")) {
            return JSONObject().put("blocked", blocked)
        }
        val question = round.getJSONObject("question")
        val type = question.optString("type", "single")
        if (verdict == null && (type == "subjective" || input.optString("drawing").isNotEmpty())) {
            return submitReview(round, question, input)
        }
        val mark = RoundRules.mark(question, input, verdict)
        val correct = mark.correct
        return atomic {
            round.remove("pendingReview")
            val rules = round.getJSONObject("rules")
            val wasExtra = round.optBoolean("extra")
            val mode = round.getString("mode")
            val extraLeft = updatePractice(rules, correct, wasExtra)
            val progress = RoundRules.advance(round, correct, extraLeft)
            val questionId = question.getString("id")
            val progressScore = quality ?: if (correct) 4 else 1
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
                put("answerText", if (question.optString("type") == "subjective")
                    question.optString("modelAnswer") else mark.answerText)
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

    private fun submitReview(round: JSONObject, question: JSONObject, input: JSONObject): JSONObject {
        val image = input.optString("drawing")
        require(image.isNotEmpty() || input.optString("text").isNotBlank()) { "请先作答" }
        if (image.isNotEmpty()) {
            require(image.length <= 2_000_000 && image.startsWith("data:image/png;base64,")) {
                "手写图片格式不正确或过大"
            }
        }
        val ticket = UUID.randomUUID().toString()
        atomic {
            val record = JSONObject().apply {
                put("id", ticket)
                put("ts", System.currentTimeMillis())
                put("state", "pending")
                put("input", input)
                put("question", question)
                put("feedback", "等待批改")
            }
            store.saveReview(record)
            saveRound(round.put("pendingReview", ticket))
            store.logEvent("review_submitted", JSONObject().put("id", ticket))
        }
        return JSONObject().put("status", status()).put("reviewId", ticket).put("pending", true)
    }
}
