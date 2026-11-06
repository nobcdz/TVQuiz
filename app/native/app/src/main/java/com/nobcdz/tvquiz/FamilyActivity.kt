package com.nobcdz.tvquiz

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class FamilyActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        DeviceRuntime.ensure(this)
        render()
    }

    private fun render() {
        val household = DeviceRuntime.household
        val pad = (24 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(0xFF101A32.toInt())
        }
        fun label(value: String, size: Float) {
            content.addView(TextView(this).apply {
                text = value
                textSize = size
                setTextColor(0xFFEAF4FF.toInt())
            })
        }
        fun button(row: LinearLayout, title: String, selected: Boolean = false, action: () -> Unit) {
            row.addView(Button(this).apply {
                text = title
                learningStyle(selected)
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, pad / 3, pad / 3, pad / 3) })
        }
        label("孩子档案", 28f)
        label("孩子开机后在电视上选择自己的档案。", 16f)
        val counts = LinearLayout(this)
        for (count in 1..2) {
            button(counts, "$count 个孩子", household.childCount() == count) {
                household.setChildCount(count)
                render()
            }
        }
        content.addView(counts)
        for (id in 1..household.childCount()) {
            val config = household.store(id).config()
            val row = LinearLayout(this)
            button(row, "${household.name(id)} · ${config.getString("curriculumGrade")}") {
                startActivity(Intent(this, CurriculumActivity::class.java).putExtra("child_id", id))
            }
            content.addView(row)
        }
        label("名称、每次解锁时长和每日上限可在手机家长端分别设置。", 16f)
        val footer = LinearLayout(this)
        button(footer, "完成") { finish() }
        content.addView(footer)
        setContentView(ScrollView(this).apply { addView(content) })
    }

    override fun onStop() {
        super.onStop()
        finish()
    }
}
