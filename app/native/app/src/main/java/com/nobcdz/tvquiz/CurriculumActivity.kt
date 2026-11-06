package com.nobcdz.tvquiz

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.text.NumberFormat

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
        val formatter = NumberFormat.getIntegerInstance()
        val totalCount = formatter.format(counts.sumOf { it.getInt("count") })
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, pad / 2)
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFF173A46.toInt(), 0xFF21294D.toInt())).apply { cornerRadius = pad.toFloat() }
            addView(label("TVQuiz  /  题库总览", 17f))
            addView(label("$totalCount 道题", 38f).apply {
                setTextColor(0xFFD0FF91.toInt())
                setTypeface(typeface, Typeface.BOLD)
            })
        }
        content.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = pad / 2 })
        val hint = childId?.let { "为 ${household.name(it)} 选择出题年级" } ?: "按方向键浏览，按确定选择出题年级"
        content.addView(label(hint, 18f))
        val subjects = listOf("语文", "数学", "英语", "常识")
        val displayGrades = (ViewingSettings.levels + counts.map { it.getString("level") }).distinct()
        val colors = mapOf("语文" to 0xFFFFD89A.toInt(), "数学" to 0xFFA8DEFF.toInt(),
            "英语" to 0xFFDAC6FF.toInt(), "常识" to 0xFFC5F2A0.toInt())
        var shelf = LinearLayout(this)
        for ((index, grade) in displayGrades.withIndex()) {
            if (index % 2 == 0) {
                shelf = LinearLayout(this)
                content.addView(shelf, LinearLayout.LayoutParams(-1, -2))
            }
            val entries = counts.filter { it.getString("level") == grade }
            val total = entries.sumOf { it.getInt("count") }
            val bySubject = entries.associate { it.getString("subject") to it.getInt("count") }
            val title = grade.ifBlank { "未标注年级" }
            val selected = grade == current
            val text = SpannableStringBuilder("$title${if (selected) "（当前）" else ""}  ·  ${formatter.format(total)} 道\n")
            text.setSpan(StyleSpan(Typeface.BOLD), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            for (subject in (subjects + bySubject.keys).distinct()) {
                val start = text.length
                text.append("${subject.ifBlank { "未标注学科" }} ${formatter.format(bySubject[subject] ?: 0)}  ")
                text.setSpan(ForegroundColorSpan(colors[subject] ?: 0xFFEAF4FF.toInt()),
                    start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val selectable = grade in ViewingSettings.levels && total > 0
            val row = Button(this).apply {
                this.text = text
                isAllCaps = false
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                textSize = 16f
                setLineSpacing((pad / 6).toFloat(), 1f)
                setTextColor(0xFFEAF4FF.toInt())
                setPadding(pad / 2, pad / 4, pad / 2, pad / 4)
                stateListAnimator = null
                fun card(fill: Int, edge: Int, width: Int) = GradientDrawable().apply {
                    setColor(fill)
                    cornerRadius = pad / 2f
                    setStroke(width.coerceAtLeast(1), edge)
                }
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused),
                        card(0xFF294343.toInt(), 0xFFD0FF91.toInt(), pad / 8))
                    val fill = if (index % 2 == 0) 0xFF1A3048.toInt() else 0xFF292845.toInt()
                    addState(intArrayOf(), card(fill,
                        if (selected) 0xFFD0FF91.toInt() else 0xFF3C5071.toInt(), pad / 16))
                }
                isEnabled = selectable
                setOnClickListener { chooseGrade(grade, childId) }
            }
            shelf.addView(row, LinearLayout.LayoutParams(0, -2, 1f).apply {
                topMargin = pad / 4
                if (index % 2 == 1) marginStart = pad / 3
            })
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
                .setItems(names) { _, index -> chooseTerm(index + 1, grade) }
        } else {
            val target = childId ?: 1
            dialog.setTitle("设置出题年级")
                .setMessage("将 ${household.name(target)} 的出题年级设为$grade？")
                .setPositiveButton("确定") { _, _ -> chooseTerm(target, grade) }
        }
        dialog.show()
    }

    private fun chooseTerm(childId: Int, grade: String) {
        if (grade == "幼儿园") {
            saveGrade(childId, grade, 0)
            return
        }
        AlertDialog.Builder(this).setTitle("$grade · 选择出题学期")
            .setItems(arrayOf("全年", "上学期", "下学期")) { _, term -> saveGrade(childId, grade, term) }
            .setNegativeButton("取消", null).show()
    }

    private fun saveGrade(childId: Int, grade: String, term: Int) {
        val household = DeviceRuntime.household
        household.store(childId).saveConfig(JSONObject().put("curriculumGrade", grade).put("questionTerm", term))
        val period = listOf("全年", "上学期", "下学期")[term]
        Toast.makeText(this, "${household.name(childId)} · $grade · $period", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onStop() {
        super.onStop()
        finish()
    }
}
