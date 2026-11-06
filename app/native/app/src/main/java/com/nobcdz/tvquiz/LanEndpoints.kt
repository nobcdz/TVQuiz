package com.nobcdz.tvquiz

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import org.json.JSONObject
import java.io.IOException

internal class LanEndpoints(
    private val context: Context,
    private val bank: LocalStore,
    private val household: Household,
    private val assets: AssetManager,
    private val address: () -> Pair<String, Int>
) {
    private enum class Role { AUTHENTICATED, PARENT, TV }

    private class Call(val request: LanRequest, val child: LocalStore, val engine: LearningEngine, val tv: Boolean) {
        fun input() = JSONObject(request.body)
        fun intParam(name: String, fallback: Int) = request.param(name)?.toIntOrNull() ?: fallback
    }

    private class Route(val role: Role, val action: (Call) -> LanReply)

    private val routes = mapOf(
        "GET /api/state" to Route(Role.AUTHENTICATED, ::state),
        "PUT /api/config" to Route(Role.PARENT) {
            val changes = runCatching { it.input() }.getOrNull()
            if (changes == null) LanReply.json(JSONObject().put("error", "bad_json"))
            else LanReply.json(JSONObject().put("config", it.child.saveConfig(changes)))
        },
        "GET /api/household" to Route(Role.PARENT) { family() },
        "PUT /api/household" to Route(Role.PARENT, ::changeFamily),
        "POST /api/logout" to Route(Role.PARENT) {
            AdminAuth.logout(bearer(it.request))
            ok(true)
        },
        "GET /api/questions" to Route(Role.AUTHENTICATED) {
            list(bank.questions(it.request.param("subject"), it.request.param("level"), it.intParam("limit", 500)))
        },
        "POST /api/questions" to Route(Role.PARENT, ::saveQuestions),
        "GET /api/history" to Route(Role.AUTHENTICATED) { list(it.child.historyDays(it.intParam("days", 7))) },
        "GET /api/events" to Route(Role.AUTHENTICATED) { list(it.child.recentEvents(it.intParam("limit", 50))) },
        "GET /api/grading" to Route(Role.AUTHENTICATED) { LanReply.json(GradingService.settings(it.child)) },
        "PUT /api/grading" to Route(Role.PARENT) { LanReply.json(GradingService.save(it.child, it.input())) },
        "GET /api/reviews" to Route(Role.AUTHENTICATED) { list(it.child.reviews(it.request.param("state") ?: "pending")) },
        "POST /api/quiz/start" to Route(Role.TV) { LanReply.json(household.start()) },
        "POST /api/quiz/answer" to Route(Role.TV) { LanReply.json(household.answer(it.input())) },
        "POST /api/quiz/review" to Route(Role.TV) { LanReply.json(household.acknowledgeReview(it.input())) },
        "POST /api/event" to Route(Role.AUTHENTICATED, ::studyEvent)
    )

    fun respond(request: LanRequest): LanReply {
        if (request.method == "OPTIONS") return LanReply(204, null, byteArrayOf())
        return try {
            when {
                request.path == "/api/hello" && request.method == "GET" -> about()
                request.path == "/api/auth" && request.method == "POST" -> login(request)
                request.path.startsWith("/api/") -> dispatch(request)
                request.method == "GET" -> static(request)
                else -> LanReply.error(405, "method_not_allowed")
            }
        } catch (e: Exception) {
            LanReply.json(JSONObject().put("error", "bad_request")
                .put("message", e.message ?: "请求格式错误"), 400)
        }
    }

    private fun dispatch(request: LanRequest): LanReply {
        val tv = request.loopback && request.headers["x-device-token"] == AdminAuth.deviceToken
        val key = request.headers["x-admin-key"]
        val parent = AdminAuth.valid(bearer(request)) || (!tv && key != null && AdminAuth.verify(context, key))
        if (!tv && !parent) return LanReply.error(401, "unauthorized")
        val route = routes["${request.method} ${request.path}"] ?: prefixRoute(request)
            ?: return LanReply.error(404, "unknown_endpoint")
        if (route.role == Role.PARENT && !parent) return LanReply.error(403, "admin_only")
        if (route.role == Role.TV && !tv) return LanReply.error(403, "device_only")
        val index = if (parent && request.headers["x-child-profile"] == "2") 2 else 1
        return route.action(Call(request, household.store(index), household.engine(index), tv))
    }

    private fun bearer(request: LanRequest): String? =
        request.headers["authorization"]?.removePrefix("Bearer ") ?: request.headers["x-admin-token"]

    private fun prefixRoute(request: LanRequest): Route? = when {
        request.method == "DELETE" && request.path.startsWith("/api/questions/") ->
            Route(Role.PARENT) { ok(bank.deleteQuestion(Uri.decode(it.request.path.substringAfter("/api/questions/")))) }
        request.method == "POST" && request.path.startsWith("/api/reviews/") ->
            Route(Role.PARENT, ::review)
        else -> null
    }

    private fun login(request: LanRequest): LanReply {
        val password = runCatching { JSONObject(request.body).optString("passphrase") }.getOrDefault("")
        val token = AdminAuth.login(context, password)
            ?: return LanReply.error(401, "管理密码错误，连续错误 5 次后需等待一分钟")
        return LanReply.json(JSONObject().put("token", token).put("expiresIn", 43200))
    }

    private fun about(): LanReply {
        val (ip, port) = address()
        return LanReply.json(JSONObject().apply {
            put("app", context.applicationInfo.loadLabel(context.packageManager).toString())
            put("version", BuildConfig.VERSION_NAME)
            put("name", Prefs.deviceName(context))
            put("ip", ip)
            put("port", port)
            put("questions", bank.countQuestions())
            put("needsToken", true)
            put("passwordRequired", true)
        })
    }

    private fun family() = LanReply.json(JSONObject().apply {
        put("selection", household.selection())
        put("childCount", household.childCount())
        put("children", household.profiles())
    })

    private fun changeFamily(call: Call): LanReply {
        val input = call.input()
        when {
            input.has("names") -> {
                val names = input.getJSONObject("names")
                household.updateProfiles(input.getInt("childCount"), input.getString("selection"),
                    names.keys().asSequence().associate { key -> key.toInt() to names.getString(key) })
            }
            input.has("childCount") -> household.setChildCount(input.getInt("childCount"))
            input.optString("selection").isNotEmpty() -> household.select(input.getString("selection"))
            else -> household.rename(input.getInt("profileId"), input.getString("name"))
        }
        return family()
    }

    private fun state(call: Call): LanReply {
        val learning = if (call.tv) household.status() else call.engine.status()
        val child = if (call.tv) household.store(learning.getInt("childId")) else call.child
        val today = child.todayStats()
        return LanReply.json(JSONObject().apply {
            put("config", child.config())
            put("today", today)
            put("questions", bank.countQuestions())
            put("roundsDoneToday", today.optInt("rounds"))
            put("repetition", child.repetitionStats())
            put("learning", learning)
        })
    }

    private fun saveQuestions(call: Call): LanReply {
        val input = runCatching { call.input() }.getOrNull()
            ?: return LanReply.json(JSONObject().put("error", "bad_json"))
        if (input.has("items")) {
            val items = input.getJSONArray("items")
            val count = bank.importQuestions(items)
            return LanReply.json(JSONObject().put("ok", true).put("added", count)
                .put("skipped", items.length() - count))
        }
        return try {
            val id = bank.upsertQuestion(input)
            bank.logEvent("question_added", JSONObject().put("id", id))
            LanReply.json(JSONObject().put("ok", true).put("id", id))
        } catch (e: IllegalArgumentException) {
            LanReply.json(JSONObject().put("error", "bad_question").put("message", e.message.orEmpty()))
        }
    }

    private fun review(call: Call): LanReply {
        val path = call.request.path.substringAfter("/api/reviews/")
        val id = Uri.decode(path.substringBefore('/'))
        if (path.endsWith("/retry")) {
            require(call.child.review(id) != null) { "题目不存在" }
            GradingService.enqueue(call.child, call.engine, id)
            return ok()
        }
        val decision = call.input()
        return LanReply.json(call.engine.grade(id, decision.getDouble("score"),
            decision.optString("feedback"), "parent_or_agent"))
    }

    private fun studyEvent(call: Call): LanReply {
        val input = runCatching { call.input() }.getOrElse { JSONObject() }
        if (input.optString("type") != "study") return LanReply.error(403, "event_managed_by_device")
        bank.logEvent("study", input)
        return ok()
    }

    private fun list(items: org.json.JSONArray) = LanReply.json(JSONObject().put("items", items))
    private fun ok(value: Boolean = true) = LanReply.json(JSONObject().put("ok", value))

    private fun static(request: LanRequest): LanReply {
        val path = Uri.decode(request.path)
        if (path.contains('\\') || path.split('/').any { it == ".." || it == "." }) {
            return LanReply.error(404, "not_found")
        }
        val name = when {
            path.startsWith("/admin/assets/") -> path.substring(1)
            path.startsWith("/admin") -> "admin/index.html"
            path.startsWith("/assets/") -> "web$path"
            else -> "web/index.html"
        }
        return try {
            val data = assets.open(name).use { it.readBytes() }
            LanReply(200, mediaType(name), data)
        } catch (_: IOException) {
            LanReply.error(404, "not_found")
        }
    }

    private fun mediaType(name: String): String = when (name.substringAfterLast('.')) {
        "html" -> "text/html; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "js", "mjs" -> "application/javascript; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "jpg", "jpeg" -> "image/jpeg"
        "svg" -> "image/svg+xml"
        "woff2" -> "font/woff2"
        "woff" -> "font/woff"
        else -> "application/octet-stream"
    }
}
