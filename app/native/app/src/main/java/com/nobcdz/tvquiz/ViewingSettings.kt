package com.nobcdz.tvquiz

import org.json.JSONObject

internal object ViewingSettings {
    private val clock = Regex("""(?:[01][0-9]|2[0-3]):[0-5][0-9]""")
    val levels = listOf("幼儿园") + "一二三四五六七八九".map { "${it}年级" }
    private val modes = setOf("round", "streak", "practice")
    private val numbers = linkedMapOf(
        "dailyMinutes" to 480, "sessionMinutes" to 120, "questionsPerRound" to 100,
        "roundsPerDay" to 100, "wrongThreshold" to 100, "extraQuestions" to 100,
        "curriculumTerm" to 2, "chineseUnit" to 12, "mathUnit" to 12, "englishUnit" to 12
    )
    private val switches = listOf("allDay", "extraEnabled", "spacedRepetitionEnabled")
    private val times = mapOf("windowStart" to "18:30", "windowEnd" to "20:00")

    private fun defaults() = JSONObject("""
        {"dailyMinutes":60,"sessionMinutes":10,"questionsPerRound":5,"roundsPerDay":6,"passRatio":1,
         "windowStart":"18:30","windowEnd":"20:00","allDay":true,"spacedRepetitionEnabled":false,
         "playMode":"streak","extraEnabled":true,"wrongThreshold":1,"extraQuestions":1,
         "curriculumGrade":"七年级","curriculumTerm":1,"chineseUnit":1,"mathUnit":1,"englishUnit":1,"version":1}
    """.trimIndent())

    fun read(raw: String): JSONObject {
        val saved = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        val merged = defaults()
        for (key in saved.keys()) merged.put(key, saved.get(key))
        for ((key, fallback) in times) {
            if (!clock.matches(merged.optString(key))) merged.put(key, fallback)
        }
        val priorStart = saved.optString("windowStart")
        if (!saved.has("allDay") && clock.matches(priorStart) && priorStart == saved.optString("windowEnd")) {
            merged.put("allDay", true)
        }
        return merged
    }

    fun apply(current: JSONObject, patch: JSONObject): JSONObject {
        for ((key, ceiling) in numbers) {
            if (!patch.has(key)) continue
            val value = patch.getDouble(key)
            val integer = value.toInt()
            require(value == integer.toDouble() && integer in 1..ceiling) { "$key 超出范围" }
            current.put(key, integer)
        }
        for (key in switches) {
            if (patch.has(key)) current.put(key, patch.getBoolean(key))
        }
        val unrestricted = current.getBoolean("allDay")
        for (key in times.keys) {
            if (!patch.has(key)) continue
            val value = patch.optString(key)
            if (clock.matches(value)) current.put(key, value)
            else require(unrestricted) { "请输入有效的开始和结束时间" }
        }
        if (patch.has("curriculumGrade")) {
            val grade = patch.getString("curriculumGrade")
            require(grade in levels)
            current.put("curriculumGrade", grade)
        }
        if (patch.has("playMode")) {
            val mode = patch.getString("playMode")
            require(mode in modes) { "未知玩法" }
            current.put("playMode", mode)
        }
        if (patch.has("passRatio")) {
            val ratio = patch.getDouble("passRatio")
            require(ratio > 0.0 && ratio <= 1.0) { "答对比例需要大于 0 且不超过 100%" }
            current.put("passRatio", ratio)
        }
        require(unrestricted || current.getString("windowStart") != current.getString("windowEnd")) {
            "开始与结束时间不能相同；全天请开启全天可用"
        }
        return current.put("version", current.optInt("version", 1) + 1)
    }
}
