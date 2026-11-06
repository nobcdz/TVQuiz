package com.nobcdz.tvquiz

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject

class CurriculumActivity : Activity() {
    private val grades = listOf("一年级", "二年级", "三年级", "四年级", "五年级", "六年级", "七年级", "八年级", "九年级")
    private lateinit var config: JSONObject
    private var focusedRow = "grade"
    private val childId by lazy { intent.getIntExtra("child_id", 1).coerceIn(1, 2) }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        DeviceRuntime.ensure(this)
        config = DeviceRuntime.household.store(childId).config()
        render()
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

    private fun render() {
        val density = resources.displayMetrics.density
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((32 * density).toInt(), (24 * density).toInt(), (32 * density).toInt(), (24 * density).toInt())
            setBackgroundColor(0xFF0D192C.toInt())
        }
        content.addView(TextView(this).apply {
            text = DeviceRuntime.household.name(childId) + " · 题库年级与学习进度"
            textSize = 28f
            setTextColor(0xFFF0F7FF.toInt())
        })
        content.addView(TextView(this).apply {
            text = "自编人教版知识练习。各科只抽取选定学期、已学单元内的题目。修改从下一组生效。"
            textSize = 15f
            setTextColor(0xFFAEC5DF.toInt())
        })
        fun selector(key: String, label: String, choices: List<String>, current: Int, select: (Int) -> Unit) {
            val row = LinearLayout(this)
            fun button(title: String, click: () -> Unit): Button {
                val button = Button(this).apply {
                    text = title
                    learningStyle()
                    setOnClickListener { focusedRow = key; click() }
                }
                row.addView(button, LinearLayout.LayoutParams(0, -2, 1f))
                return button
            }
            button("−") { select((current - 1 + choices.size) % choices.size); render() }
            val center = button(label + " · " + choices[current]) { select((current + 1) % choices.size); render() }
            if (focusedRow == key) center.post { center.requestFocus() }
            button("+") { select((current + 1) % choices.size); render() }
            content.addView(row)
        }
        selector("grade", "年级", grades, grades.indexOf(config.getString("curriculumGrade")).coerceAtLeast(0)) {
            config.put("curriculumGrade", grades[it])
        }
        selector("term", "学期", listOf("上册", "下册"), config.getInt("curriculumTerm") - 1) {
            config.put("curriculumTerm", it + 1)
        }
        listOf("chineseUnit" to "语文", "mathUnit" to "数学", "englishUnit" to "英语").forEach { (key, subject) ->
            selector(key, subject, (1..12).map { "学到第 " + it + " 单元" }, config.getInt(key) - 1) {
                config.put(key, it + 1)
            }
        }
        content.addView(Button(this).apply {
            text = "保存学习进度"
            learningStyle(true)
            setOnClickListener {
                val changes = JSONObject()
                listOf("curriculumGrade", "curriculumTerm", "chineseUnit", "mathUnit", "englishUnit").forEach {
                    changes.put(it, config.get(it))
                }
                DeviceRuntime.household.store(childId).saveConfig(changes)
                finish()
            }
        })
        setContentView(ScrollView(this).apply { addView(content) })
    }
}
