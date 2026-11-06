package com.nobcdz.tvquiz

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.widget.Button
import android.widget.EditText

private fun Context.surface(fill: Int, outline: Int, cornerDp: Int): GradientDrawable {
    val scale = resources.displayMetrics.density
    return GradientDrawable().apply {
        setColor(fill)
        cornerRadius = cornerDp * scale
        setStroke((scale * 2).toInt().coerceAtLeast(1), outline)
    }
}

fun Button.learningStyle(primary: Boolean = false) {
    val fill = if (primary) 0xFF235B60.toInt() else 0xFF1B304A.toInt()
    backgroundTintList = null
    stateListAnimator = null
    background = StateListDrawable().also { states ->
        states.addState(intArrayOf(android.R.attr.state_focused),
            context.surface(0xFF28516B.toInt(), 0xFF8CFFE7.toInt(), 14))
        states.addState(intArrayOf(), context.surface(fill, 0xFF365571.toInt(), 14))
    }
    isAllCaps = false
    textSize = 16f
    setTextColor(0xFFF0F6FF.toInt())
    val padding = (resources.displayMetrics.density * 12).toInt()
    setPadding(padding, padding, padding, padding)
}

fun EditText.learningField() {
    background = context.surface(0xFFFCFDF9.toInt(), 0xFFE4EBDD.toInt(), 12)
    textSize = 17f
    setTextColor(0xFF303B31.toInt())
    setHintTextColor(0xFF687362.toInt())
    val padding = (14 * resources.displayMetrics.density).toInt()
    setPadding(padding, padding, padding, padding)
}
