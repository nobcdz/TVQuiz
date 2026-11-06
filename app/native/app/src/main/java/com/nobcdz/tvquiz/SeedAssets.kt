package com.nobcdz.tvquiz

import android.content.Context
import android.content.ContentValues
import org.json.JSONArray

object SeedAssets {
    fun ensureQuestions(context: Context, store: LocalStore) {
        val version = "tvquiz-rjb-2026-03-13"
        if (store.get("builtin_bank_version") == version) return
        val questions = context.assets.open("questions.json").bufferedReader().use { JSONArray(it.readText()) }
        val db = store.writableDatabase
        db.beginTransaction()
        try {
            for (i in 0 until questions.length()) {
                val question = questions.getJSONObject(i)
                val retired = question.optJSONObject("retiredQuestion")
                question.remove("retiredQuestion")
                if (retired != null) {
                    val options = retired.getJSONArray("a").toString()
                    val balanced = retired.optJSONArray("balancedOptions")?.toString() ?: options
                    db.delete("questions",
                        "id=? AND level=? AND subject=? AND stem=? AND tip=? AND answer=? AND options IN (?, ?) AND term IN (0, ?, ?)",
                        arrayOf(retired.getString("id"), question.getString("level"), question.getString("subject"),
                            retired.getString("q"), retired.getString("tip"), retired.getInt("k").toString(),
                            options, balanced, retired.getInt("term").toString(), retired.getInt("previousTerm").toString()))
                }
                val id = question.getString("id")
                val exists = db.rawQuery("SELECT 1 FROM questions WHERE id=?", arrayOf(id)).use { it.moveToFirst() }
                // Stable IDs preserve parents' changes when later bank versions add questions.
                val previousOptions = question.optJSONArray("previousOptions")
                question.remove("previousOptions")
                if (!exists && store.get("deleted_builtin_" + id) != "1") store.upsertQuestion(question)
                if (exists && previousOptions != null) {
                    val replacement = ContentValues().apply { put("options", question.getJSONArray("a").toString()) }
                    db.update("questions", replacement,
                        "id=? AND options=? AND answer=? AND level=? AND subject=? AND stem=?",
                        arrayOf(id, previousOptions.toString(), question.getInt("k").toString(),
                            question.getString("level"), question.getString("subject"), question.getString("q")))
                }
                if (exists && question.optInt("term") in 1..2) {
                    val term = ContentValues().apply { put("term", question.getInt("term")) }
                    db.update("questions", term, "id=? AND term IN (0, ?) AND level=? AND subject=? AND stem=?",
                        arrayOf(id, question.optInt("previousTerm").toString(), question.getString("level"),
                            question.getString("subject"), question.getString("q")))
                }
            }
            store.set("builtin_bank_version", version)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
