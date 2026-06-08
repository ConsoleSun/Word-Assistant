package com.hireassistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.content.ClipData
import android.content.ClipboardManager
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var storage: StorageManager
    private lateinit var etResume: EditText
    private lateinit var tvResumeStatus: TextView

    private fun switchTab(active: View, vararg inactive: View) {
        active.setBackgroundResource(R.drawable.tab_active)
        (active as TextView).setTextColor(0xFFe6edf3.toInt())
        inactive.forEach {
            it.setBackgroundResource(R.drawable.tab_inactive)
            (it as TextView).setTextColor(0xFF484f58.toInt())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        storage = StorageManager(this)

        // 标签页切换
        val pageSettings = findViewById<View>(R.id.pageSettings)
        val pageAppearance = findViewById<View>(R.id.pageAppearance)
        val pageResume = findViewById<View>(R.id.pageResume)
        val pageAdvanced = findViewById<View>(R.id.pageAdvanced)
        val pageData = findViewById<View>(R.id.pageData)

        fun showPage(vararg views: View) {
            listOf(pageSettings, pageAppearance, pageResume, pageAdvanced, pageData).forEach {
                it.visibility = if (views.contains(it)) View.VISIBLE else View.GONE
            }
        }

        findViewById<TextView>(R.id.tabSettings).setOnClickListener {
            showPage(pageSettings)
            switchTab(it, findViewById(R.id.tabAppearance), findViewById(R.id.tabResume), findViewById(R.id.tabAdvanced), findViewById(R.id.tabData))
        }
        findViewById<TextView>(R.id.tabAppearance).setOnClickListener {
            showPage(pageAppearance)
            switchTab(it, findViewById(R.id.tabSettings), findViewById(R.id.tabResume), findViewById(R.id.tabAdvanced), findViewById(R.id.tabData))
        }
        findViewById<TextView>(R.id.tabResume).setOnClickListener {
            showPage(pageResume)
            switchTab(it, findViewById(R.id.tabSettings), findViewById(R.id.tabAppearance), findViewById(R.id.tabAdvanced), findViewById(R.id.tabData))
        }
        findViewById<TextView>(R.id.tabAdvanced).setOnClickListener {
            showPage(pageAdvanced)
            switchTab(it, findViewById(R.id.tabSettings), findViewById(R.id.tabAppearance), findViewById(R.id.tabResume), findViewById(R.id.tabData))
        }
        findViewById<TextView>(R.id.tabData).setOnClickListener {
            showPage(pageData)
            switchTab(it, findViewById(R.id.tabSettings), findViewById(R.id.tabAppearance), findViewById(R.id.tabResume), findViewById(R.id.tabAdvanced))
        }

        // 创建通知渠道（Android 8+ 前台服务必须）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("hire", "求职助手",
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "悬浮窗服务通知"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        // 无障碍权限
        findViewById<TextView>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "找到「求职助手」并开启", Toast.LENGTH_LONG).show()
        }

        // 悬浮窗开关
        findViewById<TextView>(R.id.btnToggleFloat).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            } else {
                try {
                    val intent = Intent(this, FloatingService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    Toast.makeText(this, "悬浮窗已开启", Toast.LENGTH_SHORT).show()
                    finish()
                } catch (e: Exception) {
                    Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // AI 设置
        val spinnerModel = findViewById<Spinner>(R.id.spinnerModel)
        val etKey = findViewById<EditText>(R.id.etApiKey)
        val spinnerMode = findViewById<Spinner>(R.id.spinnerMode)
        val btnSave = findViewById<TextView>(R.id.btnSave)
        // 加载已保存的设置
        etKey.setText(storage.get("api_key", ""))
        spinnerModel.setSelection(storage.get("ai_provider", "deepseek").let {
            if (it == "mimo") 1 else 0
        })
        spinnerMode.setSelection(storage.get("reply_mode", "professional").let {
            if (it == "human") 1 else 0
        })

        findViewById<TextView>(R.id.btnClearJobs).setOnClickListener {
            if (android.app.AlertDialog.Builder(this)
                .setTitle("确认清空")
                .setMessage("删除所有岗位数据？")
                .setPositiveButton("确定") { _, _ ->
                    storage.put("saved_jobs", "[]")
                    Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show() != null) {}
        }

        // 默认系统提示词
        val DEFAULT_SYSTEM_PROMPT = "你是用户的求职写作助手。用户正在找工作，需要帮忙起草回复消息。\n\n" +
            "要求：\n- 简洁专业，80-200字\n- 根据职位描述和对话历史生成回复\n- 直接返回内容，不要加引号或解释"
        val etPrompt = findViewById<EditText>(R.id.etSystemPrompt)
        val tvPromptStatus = findViewById<TextView>(R.id.tvPromptStatus)
        val savedPrompt = storage.get("system_prompt", "")
        etPrompt.setText(if (savedPrompt.isNotEmpty()) savedPrompt else DEFAULT_SYSTEM_PROMPT)

        // AI 角色 — 动态管理
        val spinnerAiRole = findViewById<Spinner>(R.id.spinnerAiRole)
        val etCustomRole = findViewById<EditText>(R.id.etCustomRole)
        var customRoles = loadCustomRoles()

        fun refreshRoleSpinner(selectLast: Boolean = false) {
            val items = mutableListOf("👤 求职人")
            items.addAll(customRoles.keys)
            items.add("+ 新增自定义")
            val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, items)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerAiRole.adapter = adapter
            if (selectLast && items.size > 2) spinnerAiRole.setSelection(items.size - 2)
        }

        fun loadRole(roleName: String) {
            if (roleName == "👤 求职人" || roleName.startsWith("+")) {
                etCustomRole.visibility = View.GONE
                etPrompt.setText(DEFAULT_SYSTEM_PROMPT)
                storage.put("ai_custom_role", "")
            } else {
                etCustomRole.visibility = View.GONE
                etPrompt.setText(customRoles[roleName] ?: DEFAULT_SYSTEM_PROMPT)
                storage.put("ai_custom_role", roleName)
            }
        }

        refreshRoleSpinner()
        val savedRole = storage.get("ai_custom_role", "")
        if (savedRole.isNotEmpty() && customRoles.containsKey(savedRole)) {
            val idx = (listOf("👤 求职人") + customRoles.keys.toList()).indexOf(savedRole)
            if (idx > 0) spinnerAiRole.setSelection(idx)
        }
        etCustomRole.visibility = View.GONE

        spinnerAiRole.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val items = (spinnerAiRole.adapter as ArrayAdapter<String>)
                val selected = items.getItem(pos) ?: return
                if (selected == "+ 新增自定义") {
                    etCustomRole.visibility = View.VISIBLE
                    etCustomRole.hint = "输入角色名称，如：资深前端工程师"
                    etCustomRole.text.clear()
                    etPrompt.setText(DEFAULT_SYSTEM_PROMPT)
                    tvPromptStatus.text = ""
                } else {
                    loadRole(selected)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 删除当前选中的自定义角色
        findViewById<TextView>(R.id.btnRestorePrompt).setOnClickListener {
            val items = spinnerAiRole.adapter as ArrayAdapter<String>
            val selected = items.getItem(spinnerAiRole.selectedItemPosition) ?: ""
            if (selected != "👤 求职人" && !selected.startsWith("+")) {
                customRoles.remove(selected)
                saveCustomRoles(customRoles)
                refreshRoleSpinner()
                spinnerAiRole.setSelection(0)
                loadRole("👤 求职人")
                tvPromptStatus.text = "已删除「$selected」"
            } else {
                etPrompt.setText(DEFAULT_SYSTEM_PROMPT)
                tvPromptStatus.text = "🔄 已恢复默认"
            }
        }
        (findViewById<TextView>(R.id.btnRestorePrompt)).text = "🗑 删除/恢复"

        // 保存提示词（含自定义角色）
        findViewById<TextView>(R.id.btnSavePrompt).setOnClickListener {
            val text = etPrompt.text.toString().trim()
            if (text.length < 10) {
                tvPromptStatus.text = "❌ 提示词过短，至少10字"
                return@setOnClickListener
            }
            val roleName = etCustomRole.text.toString().trim()
            if (etCustomRole.visibility == View.VISIBLE && roleName.isNotEmpty()) {
                // 保存新自定义角色
                customRoles[roleName] = text
                saveCustomRoles(customRoles)
                storage.put("system_prompt", text)
                storage.put("ai_custom_role", roleName)
                refreshRoleSpinner(true)
                etCustomRole.visibility = View.GONE
                etCustomRole.text.clear()
                tvPromptStatus.text = "✅ 已保存角色「$roleName」"
            } else {
                storage.put("system_prompt", text)
                tvPromptStatus.text = "✅ 提示词已保存，已应用到AI"
            }
        }

        // 透明度
        val seekOpacity = findViewById<SeekBar>(R.id.seekbarOpacity)
        val tvOpacity = findViewById<TextView>(R.id.tvOpacityValue)
        seekOpacity.progress = storage.getInt("panel_opacity", 55)
        tvOpacity.text = "${seekOpacity.progress}%"
        seekOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                tvOpacity.text = "${p}%"
                storage.putInt("panel_opacity", p)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        btnSave.setOnClickListener {
            val provider = if (spinnerModel.selectedItemPosition == 1) "mimo" else "deepseek"
            val mode = if (spinnerMode.selectedItemPosition == 1) "human" else "professional"
            storage.put("api_key", etKey.text.toString())
            storage.put("ai_provider", provider)
            storage.put("reply_mode", mode)
            storage.put("ai_custom_role", etCustomRole.text.toString())
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
        }

        // 简历
        etResume = findViewById<EditText>(R.id.etResumeText)
        tvResumeStatus = findViewById<TextView>(R.id.tvResumeStatus)
        etResume.setText(storage.get("resume_raw", ""))

        // 显示已解析结果
        val parsed = storage.get("resume_parsed", "")
        if (parsed.isNotEmpty()) tvResumeStatus.text = "✅ 简历已解析"

        findViewById<TextView>(R.id.btnPickPdf).setOnClickListener {
            // 从剪贴板粘贴（比PDF解析更可靠）
            val cb = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cb.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            if (clip.length > 20) {
                etResume.setText(clip)
                tvResumeStatus.text = "✅ 已粘贴 ${clip.length} 字，可编辑后点AI解析"
            } else {
                tvResumeStatus.text = "⚠️ 剪贴板无文本，请先复制简历内容"
            }
        }

        findViewById<TextView>(R.id.btnParseResume).setOnClickListener {
            val text = etResume.text.toString().trim()
            if (text.length < 20) {
                Toast.makeText(this, "请先粘贴简历内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            tvResumeStatus.text = "⏳ AI解析中..."
            storage.put("resume_raw", text)

            val provider = storage.get("ai_provider", "deepseek")
            val apiKey = if (provider == "mimo") storage.get("mimo_api_key", storage.get("api_key", "")) else storage.get("api_key", "")
            if (apiKey.isEmpty()) {
                tvResumeStatus.text = "❌ 请先保存API Key"
                return@setOnClickListener
            }

            // 直接用AIHelper解析
            val tempAi = AIHelper(storage)
            tempAi.parseResume(text) { result ->
                try {
                    val json = org.json.JSONObject(result.substringAfter("{").substringBeforeLast("}").let { "{$it}" })
                    storage.put("resume_parsed", json.toString())
                    tvResumeStatus.text = "✅ 解析完成: ${json.optString("name","?")} | ${json.optString("skills","")}"
                    Toast.makeText(this@MainActivity, "简历已保存", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    tvResumeStatus.text = "❌ 解析失败: ${e.message}"
                }
            }
        }
    }

    /** 从PDF文件中提取文本 */
    private fun extractPdfText(uri: Uri) {
        tvResumeStatus.text = "⏳ 解析PDF..."
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                // 简单提取：找PDF流中的可见文本
                val raw = String(bytes, Charsets.ISO_8859_1)
                val sb = StringBuilder()
                // 匹配 BT...ET 块中的文本
                val textRegex = Regex("\\(([^)]+)\\)\\s*Tj")
                textRegex.findAll(raw).forEach { match ->
                    sb.append(match.groupValues[1]).append(" ")
                }
                val extracted = sb.toString().trim()
                if (extracted.length > 20) {
                    etResume.setText(extracted)
                    tvResumeStatus.text = "✅ 提取${extracted.length}字，可编辑后点AI解析"
                } else {
                    tvResumeStatus.text = "⚠️ 未能提取文字，请手动复制粘贴"
                }
            }
        } catch (e: Exception) {
            tvResumeStatus.text = "❌ 读取失败: ${e.message}"
        }
    }

    private fun loadCustomRoles(): MutableMap<String, String> {
        val json = storage.get("custom_roles", "{}")
        return try {
            val obj = org.json.JSONObject(json)
            val map = mutableMapOf<String, String>()
            obj.keys().forEach { map[it] = obj.getString(it) }
            map
        } catch (e: Exception) { mutableMapOf() }
    }

    private fun saveCustomRoles(roles: Map<String, String>) {
        val obj = org.json.JSONObject()
        roles.forEach { (k, v) -> obj.put(k, v) }
        storage.put("custom_roles", obj.toString())
    }
}
