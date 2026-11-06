package com.nobcdz.tvquiz.tests

import android.app.Instrumentation
import android.os.Bundle
import com.nobcdz.tvquiz.LearningEngine
import com.nobcdz.tvquiz.Household
import com.nobcdz.tvquiz.LocalStore
import com.nobcdz.tvquiz.SeedAssets
import org.json.JSONArray
import org.json.JSONObject
import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import java.io.File

class RegressionInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val output = Bundle()
        try {
            check(context.packageName == "com.nobcdz.tvquiz.tests") { "Tests require their isolated APK context" }
            val directory = targetContext.getDir("regression", 0)
            val isolated = object : ContextWrapper(targetContext) {
                override fun getDatabasePath(name: String) = File(directory, name)
                override fun deleteDatabase(name: String) = SQLiteDatabase.deleteDatabase(getDatabasePath(name))
                override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
                    SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
                override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, handler: DatabaseErrorHandler?): SQLiteDatabase =
                    SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory, handler)
            }
            isolated.deleteDatabase(LocalStore.DATABASE_NAME)
            val store = LocalStore(isolated)
            checkOptionalServices(store)
            checkViewingGuardPolicy()
            checkViewingAllowance(store)
            checkRoundRules()
            checkLearningRecords(isolated, store)
            fun item(id: String, grade: String, term: Int, unit: Int) = JSONObject()
                .put("id", id).put("level", grade).put("term", term).put("unit", unit)
                .put("subject", "数学").put("q", id).put("a", JSONArray().put("正确").put("错误").put("干扰一").put("干扰二")).put("k", 0)
            store.importQuestions(JSONArray().put(item("eligible1", "七年级", 1, 1))
                .put(item("eligible2", "七年级", 1, 1))
                .put(item("wrong-grade", "八年级", 1, 1))
                .put(item("wrong-term", "七年级", 2, 1))
                .put(item("future-unit", "七年级", 1, 2)))
            store.saveConfig(JSONObject().put("allDay", true).put("dailyMinutes", 480)
                .put("questionsPerRound", 1).put("sessionMinutes", 10).put("playMode", "streak")
                .put("wrongThreshold", 1).put("extraQuestions", 1).put("mathUnit", 1))
            check(store.questions(limit = 20, forLearning = true).length() == 4) { "Grade filtering excluded another term or unit" }
            val duplicate = item("eligible-copy", "七年级", 1, 1).put("q", "eligible1")
            check(store.importQuestions(JSONArray().put(duplicate)) == 0) { "Import duplicated an existing stem" }
            check(store.importQuestions(JSONArray().put(item("hard-import", "九年级", 2, 1).put("difficulty", "难"))) == 1)
            check(store.questions(level = "九年级").getJSONObject(0).getInt("difficulty") == 5)
            var engine = LearningEngine(store)
            val round = engine.start()
            check(round.getJSONObject("status").getLong("dailyRemainingSeconds") == 28800L)
            check(round.getJSONObject("question").getString("level") == "七年级")
            val nonce = round.getString("nonce")
            check(LearningEngine(LocalStore(isolated)).start().getString("nonce") == nonce) { "Task changed on reopening" }
            fun answer(task: JSONObject, correct: Boolean): JSONObject = engine.answer(JSONObject()
                .put("roundId", task.getString("id")).put("nonce", task.getString("nonce"))
                .put("selections", JSONArray().put(if (correct) 0 else 1)))
            val wrong = answer(round, false)
            val nextUnseen = wrong.getJSONObject("next").getJSONObject("question").getString("id")
            check(nextUnseen != round.getJSONObject("question").getString("id"))
            check(store.questions(limit = 1, forLearning = true, spaced = true).getJSONObject(0).getString("id") == nextUnseen)
            check(wrong.getJSONObject("status").getInt("extraPool") == 1) { "Wrong answer must add one practice question" }
            answer(round, false)
            check(engine.status().getInt("extraPool") == 1) { "Duplicate submission counted twice" }
            engine = LearningEngine(LocalStore(isolated))
            check(engine.status().getInt("extraPool") == 1) { "Extra pool not persistent" }
            val afterWrong = engine.start()
            check(afterWrong.getString("nonce") == wrong.getJSONObject("next").getString("nonce"))
            store.saveConfig(JSONObject().put("sessionMinutes", 20))
            val firstCorrect = answer(afterWrong, true)
            check(!firstCorrect.getBoolean("finished")) { "Base answer bypassed extra practice" }
            val task = firstCorrect.getJSONObject("next")
            val complete = answer(task, true)
            check(complete.getBoolean("finished") && complete.getBoolean("passed"))
            check(complete.getDouble("rewardMinutes") == 10.0) { "Rules changed during active task" }
            check(engine.status().getInt("extraPool") == 0)
            val balance = engine.status().getLong("remainingSeconds")
            answer(task, true)
            check(engine.status().getLong("remainingSeconds") == balance) { "Reward granted twice" }
            check(LearningEngine(LocalStore(isolated)).status().getLong("remainingSeconds") == 600L) { "Balance not persistent" }
            engine.consume(5000)
            check(engine.status().getLong("remainingSeconds") == 595L)
            repeat(119) { engine.consume(5000) }
            check(engine.status().getString("reason") == "timeup") { "Quota did not expire" }
            check(engine.start().has("question")) { "No new task after time limit" }
            val family = Household(isolated, store)
            check(family.profiles().length() == 1 && !family.chooseChild(2))
            family.setChildCount(2)
            val second = family.store(2)
            check(family.engine(2).status().getLong("remainingSeconds") == 0L)
            second.saveConfig(JSONObject().put("curriculumGrade", "八年级").put("dailyMinutes", 30))
            check(store.config().getString("curriculumGrade") == "七年级")
            store.set("credit_ms", "7000")
            second.set("credit_ms", "2500")
            family.select("both")
            family.consume(5000, "1")
            check(store.get("credit_ms") == "7000") { "A stale timer charged the new viewers" }
            family.consume(5000)
            check(store.get("credit_ms") == "4500" && second.get("credit_ms") == "0") { "Shared watch must stop at the smaller balance" }
            check(second.todayStats().getDouble("usedMinutes") * 60000 in 2499.9..2500.1)
            check(family.status().getString("reason") == "timeup")
            val nextChild = family.start()
            check(nextChild.getInt("childId") == 2 && nextChild.getJSONObject("question").getString("id") == "wrong-grade")
            family.answer(JSONObject().put("childId", 2).put("roundId", nextChild.getString("id"))
                .put("nonce", nextChild.getString("nonce")).put("selections", JSONArray().put(1)))
            check(family.engine(2).status().getInt("extraPool") == 1 && engine.status().getInt("extraPool") == 0)
            family.select("1")
            check(family.status().getLong("remainingSeconds") == 5L)
            check(Household(isolated, store).selection() == "1")
            check(family.chooseChild(2))
            family.setChildCount(1)
            check(family.selection() == "1" && Household(isolated, store).childCount() == 1)
            family.setChildCount(2)
            check(family.chooseChild(2) && family.profiles().getJSONObject(1).getString("grade") == "八年级")
            second.saveConfig(JSONObject().put("curriculumGrade", "七年级").put("curriculumTerm", 2))
            check(family.start().getJSONObject("question").getString("level") == "七年级")
            second.saveConfig(JSONObject().put("playMode", "practice"))
            second.set("used_ms", "3600000")
            check(family.start().getString("blocked") == "quota") { "Daily quota must lock instead of serving practice questions" }
            check(family.status().getLong("dailyRemainingSeconds") == 0L)
            check(family.engine(2).answer(JSONObject()).getString("blocked") == "quota")
            check(com.nobcdz.tvquiz.GateService.isMiTvPlayback("com.mitv.tvhome.DetailsActivity"))
            check(!com.nobcdz.tvquiz.GateService.isMiTvPlayback("com.mitv.tvhome.MainActivityUserMode"))
            checkReviewSettlement(second)
            store.close()
            isolated.deleteDatabase(LocalStore.DATABASE_NAME)
            val fresh = LocalStore(isolated)
            check(fresh.countQuestions() == 0)
            SeedAssets.ensureQuestions(targetContext, fresh)
            check(fresh.countQuestions() == 5004)
            val counts = fresh.questionCounts()
            check((0 until counts.length()).sumOf { counts.getJSONObject(it).getInt("count") } == 5004)
            check((0 until counts.length()).sumOf {
                val row = counts.getJSONObject(it)
                if (row.getString("level") == "幼儿园") row.getInt("count") else 0
            } == 79)
            val preschool = JSONObject().put("curriculumGrade", "幼儿园")
            check(fresh.questions(limit = 100, forLearning = true, curriculum = preschool).length() == 79)
            val sample = fresh.questions(limit = 1, level = "一年级").getJSONObject(0)
            fresh.deleteQuestion(sample.getString("id"))
            fresh.set("builtin_bank_version", "previous")
            SeedAssets.ensureQuestions(targetContext, fresh)
            check(fresh.countQuestions() == 5003) { "Deleted builtin question reappeared" }
            checkLanContract(targetContext, fresh)
            fresh.close()
            checkParentCredentials()
            val audioResult = checkGateAudio()
            output.putString("stream", "PASS — learning records, round rules, viewing allowance, viewing guard policy, LAN contract, learning flows, fresh database, profile isolation, shared debit, active child routing, stale timer protection, persistent selection\n$audioResult\n")
            finish(android.app.Activity.RESULT_OK, output)
        } catch (error: Throwable) {
            output.putString("stream", "FAIL — " + android.util.Log.getStackTraceString(error))
            finish(android.app.Activity.RESULT_CANCELED, output)
        }
    }

    private fun checkReviewSettlement(store: LocalStore) {
        store.set("used_ms", "0")
        store.set("active_round", "")
        store.saveConfig(JSONObject().put("playMode", "practice")
            .put("curriculumGrade", "九年级").put("curriculumTerm", 1))
        store.upsertQuestion(JSONObject().put("id", "fixture-subjective")
            .put("subject", "语文").put("level", "九年级").put("term", 1).put("unit", 1)
            .put("type", "subjective").put("q", "写下你的答案"))
        val engine = LearningEngine(store)
        val round = engine.start()
        val submitted = engine.answer(JSONObject().put("roundId", round.getString("id"))
            .put("nonce", round.getString("nonce")).put("text", "测试答案"))
        val id = submitted.getString("reviewId")
        check(submitted.getBoolean("pending"))
        val graded = engine.grade(id, 86.0, "通过", "fixture")
        check(graded.getJSONObject("result").getBoolean("correct"))
        check(engine.grade(id, 10.0, "", "fixture").getDouble("score") == 86.0)
        check(engine.start().getBoolean("reviewed"))
        engine.acknowledgeReview(id)
        check(!engine.start().optBoolean("reviewed"))
    }

    private fun checkLearningRecords(context: Context, store: LocalStore) {
        val child = LocalStore(context, 2, store)
        val pending = JSONObject().put("id", "fixture-review").put("ts", 1L)
            .put("input", JSONObject().put("nonce", "fixture-review-nonce"))
        child.saveReview(pending)
        check(store.review("fixture-review") == null)
        check(child.reviews("pending").length() == 1)
        child.noteReview(pending.put("feedback", "pending note"))
        check(child.review("fixture-review")?.getString("feedback") == "pending note")
        child.saveReview(pending.put("state", "graded"))
        child.noteReview(pending.put("feedback", "stale note"))
        check(child.reviews("pending").length() == 0)
        check(child.reviews("graded").getJSONObject(0).getString("feedback") == "pending note")

        repeat(2) { child.recordProgress("fixture-progress", 4) }
        store.readableDatabase.query("question_progress", arrayOf("repetitions", "interval_days"),
            "child=? AND qid=?", arrayOf("2", "fixture-progress"), null, null, null).use { row ->
            check(row.moveToFirst() && row.getInt(0) == 2 && row.getInt(1) == 6)
        }
    }

    private fun checkRoundRules() {
        val rules = com.nobcdz.tvquiz.RoundRules
        val text = JSONObject().put("type", "text").put("answers", JSONArray().put("AB CD"))
        check(rules.mark(text, JSONObject().put("text", "ＡＢ  cd"), null).correct)
        val multiple = JSONObject().put("type", "multiple")
            .put("a", JSONArray().put("A").put("B").put("C"))
            .put("correctIndices", JSONArray().put(0).put(2))
        check(rules.mark(multiple, JSONObject().put("selections", JSONArray().put(2).put(0)), null).correct)
        check(runCatching {
            rules.mark(multiple, JSONObject().put("selections", JSONArray().put(3)), null)
        }.isFailure)
        val round = JSONObject().put("question", multiple.put("tip", "private"))
            .put("rules", JSONObject().put("passRatio", 1.0))
            .put("mode", "practice").put("target", 1).put("answered", 0).put("streak", 0).put("correct", 0)
        val public = rules.publicCopy(round)
        check(!public.has("rules") && !public.getJSONObject("question").has("correctIndices"))
        check(!public.getJSONObject("question").has("tip") && multiple.has("correctIndices"))
        check(!rules.advance(round, correct = true, extraLeft = 0).finished)
    }

    private fun checkViewingAllowance(store: LocalStore) {
        val engine = LearningEngine(store)
        store.set("usage_day", (store.startOfToday() - 1).toString())
        store.set("credit_ms", "120000")
        store.set("used_ms", "60000")
        val rolled = engine.status()
        check(rolled.getLong("remainingSeconds") == 0L && rolled.getDouble("usedMinutes") == 0.0)
        check(store.get("usage_day") == store.startOfToday().toString())

        store.set("credit_ms", "10000")
        store.set("used_ms", (store.config().getLong("dailyMinutes") * 60_000 - 2_000).toString())
        engine.consume(9000)
        check(store.get("credit_ms") == "8000")
        check(engine.status().getString("reason") == "quota" && engine.usableMillis() == 0L)
        store.set("credit_ms", "0")
        store.set("used_ms", "0")

        val schedule = com.nobcdz.tvquiz.ViewingSchedule(JSONObject()
            .put("windowStart", "21:30").put("windowEnd", "06:30"))
        val time = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2026, java.util.Calendar.NOVEMBER, 5, 6, 29)
        }
        check(schedule.allows(time))
        time.add(java.util.Calendar.MINUTE, 1)
        check(!schedule.allows(time))
        val next = time.clone() as java.util.Calendar
        next.set(java.util.Calendar.HOUR_OF_DAY, 21)
        check(schedule.nextStart(time, tomorrow = false) == next.timeInMillis)
    }

    private fun checkViewingGuardPolicy() {
        fun decision(
            pkg: String?, launcher: Boolean = false, exempt: Boolean = false,
            music: Boolean = false, activity: String? = null, awake: Boolean = true,
            sdk: Int = 23, overlay: Boolean = true, accessibility: Boolean = false
        ) = com.nobcdz.tvquiz.ViewingGuardPolicy.evaluate(
            awake, pkg, overlay || accessibility, overlay, accessibility, music, activity, sdk, exempt, launcher
        )

        val home = decision("com.android.tv.launcher", launcher = true)
        check(!home.count && home.protect) { "Home must pause viewing time without bypassing the gate" }
        val app = decision("com.example.player")
        check(app.count && app.protect)
        val settings = decision("com.android.settings", exempt = true)
        check(!settings.count && !settings.protect)
        check(!decision("com.mitv.tvhome", launcher = true).count)
        check(decision("com.mitv.tvhome", launcher = true, activity = "com.mitv.tvhome.PlayerActivity").count)
        check(decision("com.mitv.tvhome", launcher = true, music = true).count)
        val sleeping = decision("com.example.player", awake = false)
        check(!sleeping.count && !sleeping.protect)
        check(decision(null, overlay = false).protect)
        check(!decision(null, sdk = 34, overlay = false).protect)
        check(decision(null, sdk = 34, overlay = false, accessibility = true).protect)
    }

    private fun checkLanContract(context: Context, store: LocalStore) {
        val family = Household(context, store)
        val server = com.nobcdz.tvquiz.LanServer(context, store, context.assets, 0, family)
        val auth = com.nobcdz.tvquiz.AdminAuth
        auth.setPassword(context, "local-fixture-password")
        server.start()
        try {
            val host = "127.0.0.1:${server.boundPort}"
            fun exchange(method: String, path: String, extra: String = "", body: String = ""): String {
                val bytes = body.toByteArray(Charsets.UTF_8)
                return java.net.Socket("127.0.0.1", server.boundPort).use { socket ->
                    socket.soTimeout = 5000
                    socket.getOutputStream().write(
                        "$method $path HTTP/1.1\r\nHost: $host\r\nContent-Length: ${bytes.size}\r\n$extra\r\n"
                            .toByteArray(Charsets.UTF_8))
                    socket.getOutputStream().write(bytes)
                    val source = socket.getInputStream().buffered()
                    fun readLine(): String? {
                        val bytes = java.io.ByteArrayOutputStream()
                        while (true) {
                            val next = source.read()
                            if (next < 0) return null
                            if (next == 10) return bytes.toString("UTF-8").removeSuffix("\r")
                            bytes.write(next)
                        }
                    }
                    val status = readLine()
                    val headers = generateSequence { readLine() }.takeWhile { it.isNotEmpty() }.toList()
                    val length = headers.first { it.startsWith("Content-Length:", ignoreCase = true) }
                        .substringAfter(':').trim().toInt()
                    val payload = ByteArray(length)
                    var offset = 0
                    while (offset < length) {
                        val count = source.read(payload, offset, length - offset)
                        check(count > 0) { "Truncated HTTP test response" }
                        offset += count
                    }
                    (listOf(status) + headers).joinToString("\r\n") + "\r\n\r\n" + String(payload, Charsets.UTF_8)
                }
            }
            val body = """{"passphrase":"local-fixture-password"}"""
            check(exchange("GET", "/api/hello").startsWith("HTTP/1.1 200"))
            check(exchange("GET", "/api/state").startsWith("HTTP/1.1 401"))
            check(exchange("POST", "/api/auth", "Origin: https://example.invalid\r\n", body).startsWith("HTTP/1.1 403"))
            check(exchange("POST", "/api/auth", "Content-Length: 2\r\n").startsWith("HTTP/1.1 400"))
            val login = exchange("POST", "/api/auth", "Origin: http://$host\r\n", body)
            check(login.startsWith("HTTP/1.1 200") && !login.contains("Access-Control-Allow-Origin"))
            val token = JSONObject(login.substringAfter("\r\n\r\n")).getString("token")
            val parent = "Authorization: Bearer $token\r\n"
            val device = "X-Device-Token: ${auth.deviceToken}\r\n"
            check(exchange("GET", "/api/household", parent).startsWith("HTTP/1.1 200"))
            val before = exchange("GET", "/api/household", parent).substringAfter("\r\n\r\n")
            val rejected = """{"childCount":2,"selection":"both","names":{"1":"New name","2":" "}}"""
            check(exchange("PUT", "/api/household", parent, rejected).startsWith("HTTP/1.1 400"))
            check(exchange("GET", "/api/household", parent).substringAfter("\r\n\r\n") == before)
            val accepted = """{"childCount":2,"selection":"both","names":{"1":"Child A","2":"Child B"}}"""
            check(exchange("PUT", "/api/household", parent, accepted).startsWith("HTTP/1.1 200"))
            check(family.childCount() == 2 && family.selection() == "both" && family.name(2) == "Child B")
            check(exchange("POST", "/api/quiz/start", parent, "{}").startsWith("HTTP/1.1 403"))
            check(exchange("PUT", "/api/config", device, "{}").startsWith("HTTP/1.1 403"))
            check(exchange("GET", "/api/state", device).startsWith("HTTP/1.1 200"))
            check(exchange("GET", "/admin/assets/%2e%2e/index.html").startsWith("HTTP/1.1 404"))
            check(exchange("POST", "/api/logout", parent).startsWith("HTTP/1.1 200"))
            check(exchange("GET", "/api/household", parent).startsWith("HTTP/1.1 401"))
        } finally {
            server.stop()
            context.getSharedPreferences("parent_access", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun checkOptionalServices(store: LocalStore) {
        val update = com.nobcdz.tvquiz.Updater
        val release = JSONObject().put("tag_name", "v1.2.0").put("body", "[force]")
            .put("assets", JSONArray()
                .put(JSONObject().put("name", "LegacyApp-v1.2.0.apk").put("browser_download_url", "https://example.invalid/legacy"))
                .put(JSONObject().put("name", "TVQuiz-1.2.0-debug.apk").put("browser_download_url", "https://example.invalid/debug"))
                .put(JSONObject().put("name", "TVQuiz-1.2.0.apk").put("browser_download_url", "https://example.invalid/tv")))
        val info = requireNotNull(update.decodeRelease(release, "1.1.9"))
        check(info.force && info.url.endsWith("/tv") && !update.isNewer("1.2.0", "1.2"))
        check(update.decodeRelease(release, "1.2.0")?.force == false)
        release.put("prerelease", true)
        check(update.decodeRelease(release, "1.1.9") == null)
        release.put("prerelease", false)
        release.getJSONArray("assets").remove(2)
        check(update.decodeRelease(release, "1.1.9") == null)

        val grading = com.nobcdz.tvquiz.GradingService
        val config = grading.save(store, JSONObject().put("mode", "ai").put("apiKey", "test-key"))
        check(config.getBoolean("hasKey") && !config.has("apiKey"))
        val payload = grading.requestBody(config, JSONObject().put("q", "test"), "answer", "data:image/png;base64,AA==")
        check(payload.getJSONArray("messages").getJSONObject(1).getJSONArray("content").length() == 2)
        val raw = JSONObject().put("choices", JSONArray().put(JSONObject().put("message",
            JSONObject().put("content", """```json
{"score":85}
```"""))))
        check(grading.decodeResult(raw.toString()).getInt("score") == 85)
        store.set("grading_settings", "{}")
    }

    private fun checkParentCredentials() {
        val testPassword = "legacy-test-password"
        val salt = ByteArray(16) { it.toByte() }
        val spec = javax.crypto.spec.PBEKeySpec(testPassword.toCharArray(), salt, 60_000, 256)
        val digest = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        val preferences = targetContext.getSharedPreferences("parent_access", Context.MODE_PRIVATE)
        val flags = android.util.Base64.NO_WRAP
        preferences.edit().clear()
            .putString("salt", android.util.Base64.encodeToString(salt, flags))
            .putString("hash", android.util.Base64.encodeToString(digest, flags)).commit()
        try {
            val auth = com.nobcdz.tvquiz.AdminAuth
            val token = requireNotNull(auth.login(targetContext, testPassword))
            check(auth.valid(token))
            auth.setPassword(targetContext, "new-test-password")
            check(!auth.valid(token) && auth.verify(targetContext, "new-test-password"))
        } finally {
            preferences.edit().clear().commit()
        }
    }

    private fun checkGateAudio(): String {
        if (android.os.Build.VERSION.SDK_INT < 23) return "Skipped media mute state check on API <23"
        val audio = targetContext.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        if (audio.isVolumeFixed) return "Skipped media mute state check on fixed-volume device"
        val stream = android.media.AudioManager.STREAM_MUSIC
        val initiallyMuted = audio.isStreamMute(stream)
        val gate = com.nobcdz.tvquiz.GateAudio(targetContext)
        try {
            val volume = audio.getStreamVolume(stream)
            gate.update(true)
            check(audio.isStreamMute(stream)) { "Gate did not mute media audio" }
            gate.update(true)
            gate.update(false)
            check(audio.isStreamMute(stream) == initiallyMuted && audio.getStreamVolume(stream) == volume) {
                "Gate did not restore prior media volume and mute state"
            }
            if (!initiallyMuted) {
                audio.adjustStreamVolume(stream, android.media.AudioManager.ADJUST_MUTE, 0)
                gate.update(true)
                gate.update(false)
                check(audio.isStreamMute(stream)) { "Gate removed the user's existing mute" }
            }
            return "PASS — gate media mute, volume restoration and existing mute preservation"
        } finally {
            gate.update(false)
            val adjustment = if (initiallyMuted) android.media.AudioManager.ADJUST_MUTE
                else android.media.AudioManager.ADJUST_UNMUTE
            audio.adjustStreamVolume(stream, adjustment, 0)
        }
    }
}
