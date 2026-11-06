package com.nobcdz.tvquiz

import java.text.Normalizer
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal object RoundRules {
    private val hidden = listOf("k", "answers", "correctIndices", "tip", "modelAnswer", "rubric")
    private val whitespace = Regex("""\s+""")

    fun needsNewQuestion(round: JSONObject, config: JSONObject): Boolean {
        val grade = config.getString("curriculumGrade")
        val term = if (grade == "幼儿园") 0 else config.optInt("questionTerm")
        return listOf(round.getJSONObject("rules"), round.getJSONObject("question")).let { (rules, question) ->
            rules.optString("curriculumGrade") != grade || question.optString("level") != grade ||
                (grade != "幼儿园" && rules.optInt("questionTerm") != term) ||
                (term != 0 && question.optInt("term") !in listOf(0, term))
        }
    }

    fun updateCurriculum(round: JSONObject, config: JSONObject) {
        round.getJSONObject("rules").put("curriculumGrade", config.getString("curriculumGrade"))
            .put("questionTerm", config.optInt("questionTerm"))
    }

    fun publicCopy(round: JSONObject): JSONObject = JSONObject(round.toString()).apply {
        val question = getJSONObject("question")
        hidden.forEach { question.remove(it) }
        remove("rules")
    }

    data class Mark(val correct: Boolean, val indices: JSONArray, val answerText: String)

    fun mark(question: JSONObject, input: JSONObject): Mark {
        val type = question.optString("type", "single")
        if (type == "text") {
            val accepted = question.getJSONArray("answers")
            val correct = run {
                val text = normalized(input.optString("text"))
                text.isNotEmpty() && (0 until accepted.length()).any { normalized(accepted.getString(it)) == text }
            }
            return Mark(correct, JSONArray(), accepted.getString(0))
        }
        val expected = if (type == "multiple") question.getJSONArray("correctIndices")
            else JSONArray().put(question.optInt("k"))
        val correct = run {
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
        // Keep the stored mode key so existing profiles and active rounds carry over.
        val cumulative = mode == "streak"
        if (!extra || mode == "practice") {
            round.put("answered", round.getInt("answered") + 1)
            if (correct) {
                round.put("correct", round.getInt("correct") + 1)
            }
        }
        val target = round.getInt("target")
        val done = round.getInt(if (cumulative) "correct" else "answered") >= target
        val passed = cumulative || round.getInt("correct").toDouble() / target >= rules.getDouble("passRatio")
        return Progress(done, passed, mode != "practice" && done && (!passed || extraLeft == 0L))
    }
}
