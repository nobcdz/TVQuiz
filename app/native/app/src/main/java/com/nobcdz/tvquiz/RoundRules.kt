package com.nobcdz.tvquiz

import java.text.Normalizer
import java.util.Locale
import kotlin.math.ceil
import org.json.JSONArray
import org.json.JSONObject

internal object RoundRules {
    private val curriculumKeys = listOf("curriculumGrade", "curriculumTerm", "chineseUnit", "mathUnit", "englishUnit")
    private val hidden = listOf("k", "answers", "correctIndices", "tip", "modelAnswer", "rubric")
    private val whitespace = Regex("""\s+""")

    fun needsNewQuestion(round: JSONObject, config: JSONObject): Boolean {
        val grade = config.getString("curriculumGrade")
        val term = config.getInt("curriculumTerm")
        return listOf(round.getJSONObject("rules"), round.getJSONObject("question")).let { (rules, question) ->
            rules.optString("curriculumGrade") != grade || rules.optInt("curriculumTerm") != term ||
                question.optString("level") != grade || question.optInt("term") != term
        }
    }

    fun updateCurriculum(round: JSONObject, config: JSONObject) {
        val rules = round.getJSONObject("rules")
        for (key in curriculumKeys) rules.put(key, config.get(key))
    }

    fun publicCopy(round: JSONObject): JSONObject = JSONObject(round.toString()).apply {
        val question = getJSONObject("question")
        hidden.forEach { question.remove(it) }
        remove("rules")
    }

    data class Mark(val correct: Boolean, val indices: JSONArray, val answerText: String)

    fun mark(question: JSONObject, input: JSONObject, verdict: Boolean?): Mark {
        val type = question.optString("type", "single")
        if (type == "text") {
            val accepted = question.getJSONArray("answers")
            val correct = verdict ?: run {
                val text = normalized(input.optString("text"))
                text.isNotEmpty() && (0 until accepted.length()).any { normalized(accepted.getString(it)) == text }
            }
            return Mark(correct, JSONArray(), accepted.getString(0))
        }
        val expected = if (type == "multiple") question.getJSONArray("correctIndices")
            else JSONArray().put(question.optInt("k"))
        val correct = verdict ?: run {
            val picked = input.getJSONArray("selections")
            val options = question.getJSONArray("a").length()
            val indices = indices(picked)
            require(indices.isNotEmpty() && indices.all { it in 0 until options }) { "请选择有效答案" }
            indices == indices(expected)
        }
        return Mark(correct, expected, "")
    }

    private fun normalized(text: String): String = Normalizer.normalize(text.trim(), Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(whitespace, " ")

    private fun indices(array: JSONArray): Set<Int> = (0 until array.length()).map(array::getInt).toSet()

    data class Progress(val done: Boolean, val passed: Boolean, val finished: Boolean)

    fun advance(round: JSONObject, correct: Boolean, extraLeft: Long): Progress {
        val rules = round.getJSONObject("rules")
        val extra = round.optBoolean("extra")
        val mode = round.getString("mode")
        val streak = mode == "streak"
        if (streak && !correct && !extra) round.put("earnedMs", 0)
        if (!extra || mode == "practice") {
            round.put("answered", round.getInt("answered") + 1)
            round.put("streak", if (correct) round.getInt("streak") + 1 else 0)
            if (correct) {
                round.put("correct", round.getInt("correct") + 1)
                if (rules.optBoolean("difficultyRewardEnabled")) {
                    val difficulty = round.getJSONObject("question").optInt("difficulty", 2)
                    val reward = rules.getJSONObject("difficultyRewards").getDouble(difficulty.toString())
                    round.put("earnedMs", round.getLong("earnedMs") + (reward * 60_000).toLong())
                }
            }
        }
        val target = round.getInt("target")
        val done = round.getInt(if (streak) "streak" else "answered") >= target
        val passed = streak || round.getInt("correct") >= ceil(target * rules.getDouble("passRatio")).toInt()
        return Progress(done, passed, mode != "practice" && done && (!passed || extraLeft == 0L))
    }
}
