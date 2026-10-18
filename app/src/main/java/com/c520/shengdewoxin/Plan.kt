package com.c520.shengdewoxin

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** 一個動作：t 是種類（alarm、call…），p 是參數。 */
class Act(val t: String, val p: JSONObject)

/** 一句話要做的事：say 是要念的話，acts 是要執行的動作（可以沒有，例如單純問答）。 */
class Plan(val say: String, val acts: List<Act>) {
    fun toJson(): String = JSONObject()
        .put("say", say)
        .put("actions", JSONArray().apply { acts.forEach { put(JSONObject(it.p.toString()).put("t", it.t)) } })
        .toString()

    companion object {
        fun of(say: String, t: String, p: JSONObject = JSONObject()) = Plan(say, listOf(Act(t, p)))

        /** 解析 AI 回的 JSON；不是 JSON 時把整段當成回答念出來。 */
        fun parse(text: String): Plan {
            val s = text.indexOf('{')
            val e = text.lastIndexOf('}')
            if (s >= 0 && e > s) {
                try {
                    val j = JSONObject(text.substring(s, e + 1))
                    val arr = j.optJSONArray("actions") ?: JSONArray()
                    val acts = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { Act(it.optString("t"), it) }
                    return Plan(j.optString("say"), acts)
                } catch (ex: JSONException) {
                    // 不是合法 JSON，往下當純文字
                }
            }
            return Plan(text.trim().take(300), emptyList())
        }
    }
}
