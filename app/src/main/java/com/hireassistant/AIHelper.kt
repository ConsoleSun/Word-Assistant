package com.hireassistant

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class AIHelper(private val store: StorageManager) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private fun endpoint() = if (store.get("ai_provider","deepseek") == "mimo")
        "https://api.xiaomimimo.com/v1/chat/completions" else "https://api.deepseek.com/chat/completions"

    private fun model() = if (store.get("ai_provider","deepseek") == "mimo") "mimo-v2.5-pro" else "deepseek-chat"

    private fun key() = store.get("api_key","")

    private fun systemPrompt(): String {
        // 优先使用用户自定义的提示词
        val customPrompt = store.get("system_prompt", "")
        if (customPrompt.isNotEmpty() && customPrompt.length >= 10) {
            val mode = store.get("reply_mode","professional")
            val style = if (mode == "human") " 风格：口语化自然。" else " 风格：简洁专业。"
            return customPrompt + style
        }
        // 默认
        val mode = store.get("reply_mode","professional")
        val roleIdx = store.getInt("ai_role_index", 0)
        val roleDesc = when (roleIdx) {
            1 -> store.get("ai_custom_role", "").take(100)
            else -> "求职人"
        }
        val roleText = if (roleDesc.isNotEmpty() && roleDesc != "求职人")
            "用户身份是：$roleDesc。" else ""
        val prefix = "你是用户的写作助手。$roleText 需要帮忙起草回复。"
        return if (mode == "human") "$prefix 口语化自然。直接返回内容。" else "$prefix 简洁专业。直接返回内容。"
    }

    /** AI 对话回复 */
    fun chat(userMsg: String, callback: (String) -> Unit) {
        chatWithContext(userMsg, "", callback)
    }

    /** 带上下文的AI对话（包含JD+历史记录） */
    fun chatWithContext(userMsg: String, context: String, callback: (String) -> Unit) {
        val userPrompt = if (context.isNotEmpty()) "$context\n\n招聘者最新消息：$userMsg\n请根据以上信息生成回复：" else userMsg
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resp = call(systemPrompt(), userPrompt, 400)
                withContext(Dispatchers.Main) { callback(resp) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { callback("错误: ${e.message}") }
            }
        }
    }

    /** 生成招呼语 */
    fun greet(description: String, callback: (String) -> Unit) {
        val prompt = if (description.length > 20)
            "根据以下职位描述生成一条打招呼语：\n$description" else "请生成一条通用的打招呼语"
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resp = call(systemPrompt(), prompt)
                withContext(Dispatchers.Main) { callback(resp) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { callback("错误: ${e.message}") }
            }
        }
    }

    /** 解析简历文本 */
    fun parseResume(rawText: String, callback: (String) -> Unit) {
        val sysPrompt = """从简历文本中提取信息，返回JSON：
{"name":"姓名","phone":"电话","email":"邮箱","skills":"核心技能","experience":"工作经历摘要","education":"学历","summary":"个人优势(100字)","expectedSalary":""}
所有字段用字符串。找不到填空。只返回JSON。"""
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var resp = call(sysPrompt, rawText.take(4000), 600)
                val s = resp.indexOf('{'); val e = resp.lastIndexOf('}')
                if (s>=0 && e>s) resp = resp.substring(s, e+1)
                withContext(Dispatchers.Main) { callback(resp) }
            } catch (ex: Exception) {
                withContext(Dispatchers.Main) { callback("{\"error\":\"${ex.message}\"}") }
            }
        }
    }

    /** 从屏幕抓取的原始文本中解析JD */
    fun parseJD(rawText: String, callback: (String) -> Unit) {
        val sysPrompt = """从招聘页面文字提取6个字段。每个字段都必须尽量找到，宁可模糊也不要留空。

- position: 页面最顶部的岗位标题
- company: 公司名称。线索："公司"二字旁边、页面顶部职位名下方、底部"版权所有"处、任何带"有限公司""科技""网络"的文字。如果页面没有明确公司名，看看是否有"代招""外包"等字样
- salary: 数字+K或数字-数字格式。如 10K-15K、8千-1.2万、15-25K·13薪。可能在职位名同一行或下方紧挨着
- location: 城市名+区名，如 郑州、郑州市、北京朝阳。通常在薪资附近或职位名下方
- requirements: "任职要求""岗位要求""资格"下方的要点列表
- description: "岗位职责""工作内容"下方的要点列表

只返回JSON：{"position":"","company":"","salary":"","location":"","requirements":"","description":""}"""
        // 清理文字：去掉多余空白，保留换行结构
        val cleaned = rawText.replace(Regex("\\s+"), " ").replace(Regex("【|】"), "\n").trim().take(4000)
        val user = "页面文字：\n$cleaned\n\n请提取职位信息JSON："
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var resp = call(sysPrompt, user, 800)
                // 提取JSON部分
                val start = resp.indexOf('{')
                val end = resp.lastIndexOf('}')
                if (start >= 0 && end > start) resp = resp.substring(start, end+1)
                withContext(Dispatchers.Main) { callback(resp) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { callback("解析失败: ${e.message}") }
            }
        }
    }

    private fun call(system: String, user: String, maxTokens: Int = 400): String {
        val body = JSONObject().apply {
            put("model", model())
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", system) })
                put(JSONObject().apply { put("role", "user"); put("content", user) })
            })
            put("temperature", 0.7)
            put("max_tokens", maxTokens)
        }

        val req = Request.Builder().url(endpoint())
            .header("Authorization", "Bearer ${key()}")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON)).build()

        val r = client.newCall(req).execute()
        if (!r.isSuccessful) throw IOException("API ${r.code}")
        val content = JSONObject(r.body?.string()?:"").getJSONArray("choices")
            .getJSONObject(0).getJSONObject("message").getString("content")
        return content.trim()
    }
}
