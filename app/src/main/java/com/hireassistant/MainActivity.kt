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

        // AI 角色
        val spinnerAiRole = findViewById<Spinner>(R.id.spinnerAiRole)
        val etCustomRole = findViewById<EditText>(R.id.etCustomRole)
        spinnerAiRole.setSelection(storage.getInt("ai_role_index", 0))
        etCustomRole.setText(storage.get("ai_custom_role", ""))
        etCustomRole.visibility = if (spinnerAiRole.selectedItemPosition == 1) View.VISIBLE else View.GONE
        spinnerAiRole.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                storage.putInt("ai_role_index", pos)
                if (pos == 1) {
                    etCustomRole.visibility = View.VISIBLE
                    etCustomRole.hint = "输入AI职业身份，如：资深前端工程师"
                } else {
                    etCustomRole.visibility = View.GONE
                    etCustomRole.text.clear()
                    storage.put("ai_custom_role", "")
                    etPrompt.setText(DEFAULT_SYSTEM_PROMPT)
                    tvPromptStatus.text = ""
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 保存提示词
        findViewById<TextView>(R.id.btnSavePrompt).setOnClickListener {
            val text = etPrompt.text.toString().trim()
            if (text.length < 10) {
                tvPromptStatus.text = "❌ 提示词过短，至少10字"
                return@setOnClickListener
            }
            if (!text.contains("回复") && !text.contains("生成") && !text.contains("起草")) {
                tvPromptStatus.text = "⚠️ 提示词需包含回复/生成/起草等关键词"
                return@setOnClickListener
            }
            storage.put("system_prompt", text)
            val role = etCustomRole.text.toString().trim()
            storage.put("ai_custom_role", role)
            tvPromptStatus.text = if (role.isNotEmpty()) "✅ 已保存！AI将以「$role」身份回复"
                                 else "✅ 提示词已保存，已应用到AI"
        }

        // 恢复默认
        findViewById<TextView>(R.id.btnRestorePrompt).setOnClickListener {
            etPrompt.setText(DEFAULT_SYSTEM_PROMPT)
            tvPromptStatus.text = "🔄 已恢复默认，请点击保存"
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
}
