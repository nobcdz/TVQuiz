package com.nobcdz.tvquiz

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

class CurriculumActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        DeviceRuntime.ensure(this)
        val household = DeviceRuntime.household
        val childId = intent.getIntExtra("child_id", 0).takeIf { it in 1..household.childCount() }
        val current = childId?.let { household.store(it).config().getString("curriculumGrade") }
        val rows = household.store(1).questionCounts()
        val counts = (0 until rows.length()).map(rows::getJSONObject)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(0xFF101A32.toInt())
        }
        fun label(text: String, size: Float) = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(0xFFEAF4FF.toInt())
        }
        content.addView(label("题库总览 · 共 ${counts.sumOf { it.getInt("count") }} 道", 28f))
        val hint = childId?.let { "为 ${household.name(it)} 选择出题年级" } ?: "按方向键浏览，按确定选择出题年级"
        content.addView(label(hint, 16f))
        val subjects = listOf("语文", "数学", "英语", "常识")
        val displayGrades = (ViewingSettings.levels + counts.map { it.getString("level") }).distinct()
        for (grade in displayGrades) {
            val entries = counts.filter { it.getString("level") == grade }
            val total = entries.sumOf { it.getInt("count") }
            val bySubject = entries.associate { it.getString("subject") to it.getInt("count") }
            val cells = (subjects + bySubject.keys).distinct().map {
                "${it.ifBlank { "未标注学科" }} ${bySubject[it] ?: 0}"
            }
            val title = grade.ifBlank { "未标注年级" }
            val selected = grade == current
            val text = "$title${if (selected) "（当前）" else ""} · $total 道\n${cells.joinToString("    ")}"
            val selectable = grade in ViewingSettings.levels && total > 0
            val row = Button(this).apply {
                this.text = text
                isAllCaps = false
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                learningStyle(selected)
                isEnabled = selectable
                setOnClickListener { chooseGrade(grade, childId) }
            }
            content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = pad / 3 })
            if (selected) row.post { row.requestFocus() }
        }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        })
    }

    private fun chooseGrade(grade: String, childId: Int?) {
        val household = DeviceRuntime.household
        val dialog = AlertDialog.Builder(this).setNegativeButton("取消", null)
        if (childId == null && household.childCount() > 1) {
            val names = (1..household.childCount()).map(household::name).toTypedArray()
            dialog.setTitle("将${grade}设为谁的出题年级？")
                .setItems(names) { _, index -> saveGrade(index + 1, grade) }
        } else {
            val target = childId ?: 1
            dialog.setTitle("设置出题年级")
                .setMessage("将 ${household.name(target)} 的出题年级设为$grade？")
                .setPositiveButton("确定") { _, _ -> saveGrade(target, grade) }
        }
        dialog.show()
    }

    private fun saveGrade(childId: Int, grade: String) {
        val household = DeviceRuntime.household
        household.store(childId).saveConfig(JSONObject().put("curriculumGrade", grade))
        Toast.makeText(this, "${household.name(childId)} 的出题年级已设为$grade", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onStop() {
        super.onStop()
        finish()
    }
}
