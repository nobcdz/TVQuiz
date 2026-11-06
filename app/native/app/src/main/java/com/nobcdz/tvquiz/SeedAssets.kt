package com.nobcdz.tvquiz

import android.content.Context
import org.json.JSONArray

object SeedAssets {
    fun ensureQuestions(context: Context, store: LocalStore) {
        val version = "tvquiz-rjb-2026-03-5"
        if (store.get("builtin_bank_version") == version) return
        val questions = context.assets.open("questions.json").bufferedReader().use { JSONArray(it.readText()) }
        val db = store.writableDatabase
        db.beginTransaction()
        try {
            for (i in 0 until questions.length()) {
                val question = questions.getJSONObject(i)
                val id = question.getString("id")
                val exists = db.rawQuery("SELECT 1 FROM questions WHERE id=?", arrayOf(id)).use { it.moveToFirst() }
                // Stable IDs preserve parents' changes when later bank versions add questions.
                if (!exists && store.get("deleted_builtin_" + id) != "1") store.upsertQuestion(question)
            }
            store.set("builtin_bank_version", version)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
