package com.nobcdz.tvquiz

import java.util.Calendar
import org.json.JSONObject

internal class ViewingAllowance(private val store: LocalStore) {
    data class Balance(val credit: Long, val used: Long, val dailyLeft: Long)
    data class State(val balance: Balance, val rounds: Int, val reason: String, val availableAt: Long)

    fun state(): State {
        rollDay()
        val config = store.config()
        val balance = balance(config)
        val rounds = store.roundsDoneToday()
        val schedule = ViewingSchedule(config)
        val now = Calendar.getInstance()
        val reason = when {
            balance.dailyLeft == 0L -> "quota"
            balance.credit <= 0 && rounds >= config.getInt("roundsPerDay") -> "rounds"
            !schedule.allows(now) -> "schedule"
            balance.credit > 0 -> ""
            else -> "timeup"
        }
        val availableAt = when (reason) {
            "quota", "rounds" -> schedule.nextStart(now, tomorrow = true)
            "schedule" -> schedule.nextStart(now, tomorrow = false)
            else -> 0L
        }
        return State(balance, rounds, reason, availableAt)
    }

    fun debit(milliseconds: Long) {
        val db = store.writableDatabase
        db.beginTransaction()
        try {
            rollDay()
            val balance = balance(store.config())
            val debit = minOf(milliseconds.coerceIn(0, 5_000), balance.credit, balance.dailyLeft)
            if (debit > 0) {
                write("credit_ms", balance.credit - debit)
                write("used_ms", balance.used + debit)
                store.logEvent("usage", JSONObject().put("milliseconds", debit))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // Called inside the answer transaction, alongside the round and answer log.
    fun grant(requested: Long): Long {
        val balance = balance(store.config())
        val granted = minOf(requested, (balance.dailyLeft - balance.credit).coerceAtLeast(0))
        write("credit_ms", balance.credit + granted)
        return granted
    }

    private fun balance(config: JSONObject): Balance {
        val used = read("used_ms")
        return Balance(read("credit_ms"), used, (config.getLong("dailyMinutes") * 60_000 - used).coerceAtLeast(0))
    }

    private fun rollDay() {
        if (read("usage_day") == store.startOfToday()) return
        val db = store.writableDatabase
        db.beginTransaction()
        try {
            val today = store.startOfToday()
            if (read("usage_day") != today) {
                write("credit_ms", 0)
                write("used_ms", 0)
                write("usage_day", today)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun read(key: String): Long = store.get(key, "0").toLongOrNull() ?: 0L
    private fun write(key: String, value: Long) = store.set(key, value.toString())
}

internal class ViewingSchedule(config: JSONObject) {
    private val allDay = config.optBoolean("allDay")
    private val start = minuteOfDay(config.getString("windowStart"))
    private val end = minuteOfDay(config.getString("windowEnd"))

    fun allows(time: Calendar): Boolean {
        if (allDay) return true
        val minute = minuteOfDay(time)
        return when {
            start < end -> minute >= start && minute < end
            start > end -> minute >= start || minute < end
            else -> false
        }
    }

    fun nextStart(now: Calendar, tomorrow: Boolean): Long {
        val time = now.clone() as Calendar
        if (tomorrow) {
            time.add(Calendar.DAY_OF_YEAR, 1)
            time.set(Calendar.HOUR_OF_DAY, 0)
            time.set(Calendar.MINUTE, 0)
        }
        time.set(Calendar.SECOND, 0)
        time.set(Calendar.MILLISECOND, 0)
        if (!allows(time)) {
            if (minuteOfDay(time) >= start) time.add(Calendar.DAY_OF_YEAR, 1)
            time.set(Calendar.HOUR_OF_DAY, start / 60)
            time.set(Calendar.MINUTE, start % 60)
        }
        return time.timeInMillis
    }

    private fun minuteOfDay(time: Calendar): Int = time.get(Calendar.HOUR_OF_DAY) * 60 + time.get(Calendar.MINUTE)
    private fun minuteOfDay(value: String): Int =
        value.substringBefore(':').toInt() * 60 + value.substringAfter(':').toInt()
}
