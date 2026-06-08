package com.hireassistant

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.view.KeyEvent
import android.widget.*

class FloatingService : Service() {

    private lateinit var wm: WindowManager
    private var floatBtn: View? = null
    private var currentPanel: View? = null
    private var open = false
    private lateinit var store: StorageManager
    private lateinit var ai: AIHelper
    private var selectedJob: org.json.JSONObject? = null
    private var lastReply: String = ""
    private var lastUserInput: String = ""
    private var pastedText: String = ""

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        store = StorageManager(this)
        ai = AIHelper(store)
        createBtn()
    }

    // ==================== 悬浮按钮 ====================
    private fun createBtn() {
        // 紫色渐变圆形按钮
        val v = TextView(this).apply {
            text = "W"
            textSize = 18f; setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER
            // 紫色渐变圆形背景
            val gd = android.graphics.drawable.GradientDrawable().apply {
                setShape(android.graphics.drawable.GradientDrawable.OVAL)
                colors = intArrayOf(0xFF7c3aed.toInt(), 0xFF6366f1.toInt())
                setGradientType(android.graphics.drawable.GradientDrawable.LINEAR_GRADIENT)
                orientation = android.graphics.drawable.GradientDrawable.Orientation.TL_BR
            }
            background = gd
        }
        val size = (48 * resources.displayMetrics.density).toInt()
        val p = WindowManager.LayoutParams(size, size,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END; x = 20; y = 300
            alpha = 0.55f
        }
        var ix=0;var iy=0;var tx=0f;var ty=0f
        v.setOnTouchListener { _, e ->
            when(e.action){
                MotionEvent.ACTION_DOWN->{ix=p.x;iy=p.y;tx=e.rawX;ty=e.rawY;true}
                MotionEvent.ACTION_MOVE->{p.x=ix+(tx-e.rawX).toInt();p.y=iy+(e.rawY-ty).toInt();wm.updateViewLayout(v,p);true}
                MotionEvent.ACTION_UP->{if(Math.abs(e.rawX-tx)<10&&Math.abs(e.rawY-ty)<10)toggle();true}
                else->false
            }
        }
        wm.addView(v, p); floatBtn = v
    }

    private fun toggle() { if(open) closePanel() else showJobList() }

    // ==================== 面板管理 ====================
    private fun showJobList() {
        removePanel()
        val panel = LayoutInflater.from(this).inflate(R.layout.panel_job_list, null)
        open = true; selectedJob = null

        panel.findViewById<TextView>(R.id.btnClose).setOnClickListener { closePanel() }

        // 抓JD按钮
        panel.findViewById<TextView>(R.id.btnCapture).setOnClickListener { captureJD(panel) }

        // 新建空记录
        panel.findViewById<TextView>(R.id.btnNewChat).setOnClickListener {
            val input = android.widget.EditText(this).apply {
                hint = "输入对话名称"; setTextColor(0xFFFFFFFF.toInt())
                setHintTextColor(0xFF888888.toInt())
            }
            // 直接在面板顶部显示输入框
            val container = panel.findViewById<LinearLayout>(R.id.jobListContainer)
            val status = panel.findViewById<TextView>(R.id.tvJobStatus)
            status?.visibility = View.GONE
            container?.removeAllViews()
            val wrapper = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(8,8,8,8)
                addView(input)
                val btnRow = LinearLayout(this@FloatingService).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0,8,0,0)
                }
                val btnCreate = Button(this@FloatingService).apply {
                    text = "创建"; setTextColor(0xFFFFFFFF.toInt())
                    setBackgroundColor(0xFF238636.toInt())
                    setOnClickListener {
                        val name = input.text.toString().trim()
                        if (name.isEmpty()) { Toast.makeText(this@FloatingService,"请输入名称",Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                        val json = org.json.JSONObject().apply {
                            put("position", name); put("company", ""); put("salary", "")
                            put("location", ""); put("description", ""); put("requirements", "")
                            put("pinned", false); put("savedAt", System.currentTimeMillis())
                            put("isChatOnly", true)
                        }
                        val jobs = loadJobs().toMutableList()
                        jobs.add(0, json)
                        saveJobs(jobs)
                        refreshJobList(panel)
                        Toast.makeText(this@FloatingService, "已创建: $name", Toast.LENGTH_SHORT).show()
                    }
                }
                val btnCancel = Button(this@FloatingService).apply {
                    text = "取消"; setTextColor(0xFFCCCCCC.toInt())
                    setBackgroundColor(0xFF333333.toInt())
                    setOnClickListener { refreshJobList(panel) }
                }
                btnRow.addView(btnCreate)
                btnRow.addView(btnCancel)
                addView(btnRow)
            }
            container?.addView(wrapper)
        }

        refreshJobList(panel)
        addPanel(panel)
    }

    private fun showChatPanel(job: org.json.JSONObject) {
        removePanel()
        selectedJob = job
        val panel = LayoutInflater.from(this).inflate(R.layout.panel_chat, null)
        open = true

        val out = panel.findViewById<TextView>(R.id.tvOutput)
        val title = panel.findViewById<TextView>(R.id.tvChatTitle)
        out.setTextIsSelectable(true)

        title.text = job.optString("position", "AI 助手").take(15)

        // 区分空记录和岗位记录
        if (job.optBoolean("isChatOnly", false)) {
            out.text = "💬 ${job.optString("position")}\n（自由对话模式）"
        } else {
            val jdText = "<b>📋 ${job.optString("position")}</b><br>" +
                "🏢 ${job.optString("company")} | 💰 ${job.optString("salary")} | 📍 ${job.optString("location")}<br>" +
                "<br><i>📝 职责:</i><br><small>${job.optString("description","")}</small><br>" +
                "<br><i>✅ 要求:</i><br><small>${job.optString("requirements","")}</small>"
            out.text = android.text.Html.fromHtml(jdText, android.text.Html.FROM_HTML_MODE_LEGACY)
        }

        // 历史对话
        val history = store.get("chat_${job.optString("position")}_${job.optString("company")}", "")
        if (history.isNotEmpty()) {
            // 解析历史中的标记
            val formatted = history
                .replace(Regex("📥.*"), "<br><b><font color='#1890ff'>$0</font></b>")
                .replace(Regex("🤖.*"), "<br><font color='#52c41a'>$0</font>")
            out.text = android.text.Html.fromHtml(formatted, android.text.Html.FROM_HTML_MODE_LEGACY)
        }

        panel.findViewById<TextView>(R.id.btnBack).setOnClickListener { showJobList() }
        // 最小化 = 彻底移除窗口，释放键盘给BOSS
        panel.findViewById<TextView>(R.id.btnMinimize).setOnClickListener { closePanel() }
        panel.findViewById<TextView>(R.id.btnClose).setOnClickListener { closePanel() }

        val scrollView = panel.findViewById<ScrollView>(R.id.scrollOutput)

        val sendBtn = panel.findViewById<TextView>(R.id.btnSend)
        val regenBtn = panel.findViewById<TextView>(R.id.btnRegen)

        val doSend = { userMsg: String ->
            lastUserInput = userMsg
            sendBtn.isEnabled = false; regenBtn.isEnabled = false; sendBtn.text = "⏳..."
            out.append("\n\n📥 $userMsg\n\n💭 AI思考中...")
            val history = store.get("chat_${job.optString("position")}_${job.optString("company")}", "")
            val resume = store.get("resume_parsed", "")
            var context = "岗位:${job.optString("position")}@${job.optString("company")}\n" +
                "JD:${job.optString("description")}\n" + "要求:${job.optString("requirements")}\n"
            if (resume.isNotEmpty()) context += "我的简历:${resume.take(500)}\n"
            else context += "(用户未上传简历，若HR问的问题超出岗位JD范围，可通用回答)\n"
            context += "历史对话:\n${history.takeLast(1500)}"
            ai.chatWithContext(userMsg, context) { reply ->
                out.post {
                    // 用 Html.toHtml 保留现有格式
                    val oldHtml = android.text.Html.toHtml(out.text as? android.text.Spanned ?: android.text.SpannableString(""))
                    val newHtml = "$oldHtml<br><br><b><font color='#1890ff'>📥 $userMsg</font></b><br><font color='#52c41a'>🤖 $reply</font><br>"
                    out.text = android.text.Html.fromHtml(newHtml, android.text.Html.FROM_HTML_MODE_LEGACY)
                    lastReply = reply
                    saveChatHistory(out.text.toString())
                    scrollView.postDelayed({ scrollView.fullScroll(View.FOCUS_DOWN) }, 200)
                    sendBtn.isEnabled = true; regenBtn.isEnabled = true; sendBtn.text = "发送"
                }
            }
        }

        // 粘贴按钮：从剪贴板读取并显示预览
        panel.findViewById<TextView>(R.id.btnPaste).setOnClickListener {
            val cb = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val t = cb.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
            if (t.isNotEmpty()) {
                pastedText = t
                val preview = t.take(100) + if (t.length > 100) "..." else ""
                out.append("\n📋 已粘贴: $preview")
            } else {
                Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show()
            }
        }

        sendBtn.setOnClickListener {
            if(pastedText.isNotEmpty()) doSend(pastedText)
            else {
                val cb = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val t = cb.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
                if(t.isNotEmpty()) doSend(t)
            }
        }

        regenBtn.setOnClickListener {
            if (lastUserInput.isNotEmpty()) doSend(lastUserInput)
        }
        panel.findViewById<TextView>(R.id.btnGreet).setOnClickListener {
            out.append("\n⏳ 生成招呼语...")
            val resume = store.get("resume_parsed", "")
            var jd = "岗位:${job.optString("position")}\n描述:${job.optString("description")}"
            if (resume.isNotEmpty()) jd += "\n我的简历:$resume"
            ai.greet(jd) { reply ->
                out.post {
                    val oldHtml = android.text.Html.toHtml(out.text as? android.text.Spanned ?: android.text.SpannableString(""))
                    val newHtml = "$oldHtml<br><br><font color='#52c41a'>🤖 $reply</font><br>"
                    out.text = android.text.Html.fromHtml(newHtml, android.text.Html.FROM_HTML_MODE_LEGACY)
                    lastReply = reply
                    saveChatHistory(out.text.toString())
                }
            }
        }
        panel.findViewById<TextView>(R.id.btnCopy).setOnClickListener {
            val cb = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val text = if (lastReply.isNotEmpty()) lastReply
                       else out.text.toString().replace(Regex("<[^>]*>"), "").trim()
            cb.setPrimaryClip(ClipData.newPlainText("r", text))
            Toast.makeText(this,"已复制(${text.length}字)",Toast.LENGTH_SHORT).show()
        }
        // 填入BOSS输入框
        panel.findViewById<TextView>(R.id.btnFill).setOnClickListener {
            val text = if (lastReply.isNotEmpty()) lastReply
                       else out.text.toString().replace(Regex("<[^>]*>"), "").trim()
            val sr = ScreenReaderService.instance
            if (sr != null && sr.fillToInput(text)) {
                Toast.makeText(this, "✅ 已填入BOSS", Toast.LENGTH_SHORT).show()
                closePanel()  // 填入后关闭面板，释放键盘
            } else {
                val cb = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("r", text))
                Toast.makeText(this, "已复制，请手动粘贴", Toast.LENGTH_SHORT).show()
            }
        }

        addPanel(panel)
    }

    // ==================== JD 抓取 ====================
    private fun captureJD(listPanel: View) {
        val sr = ScreenReaderService.instance
        val status = listPanel.findViewById<TextView>(R.id.tvJobStatus)
        if (sr == null) { status?.text = "请先开启无障碍权限"; status?.visibility = View.VISIBLE; return }
        status?.text = "📸 正在读取屏幕内容..."
        status?.visibility = View.VISIBLE
        sr.captureAndParse { raw ->
            listPanel.post {
                status?.text = "⏳ AI解析中..."
                ai.parseJD(raw) { result ->
                    listPanel.post {
                        status?.text = ""
                        try {
                            val json = org.json.JSONObject(result.substringAfter("{").substringBeforeLast("}").let { "{$it}" })
                            // 去重保存
                            val oldJobs = loadJobs()
                            val jobs = oldJobs.toMutableList()
                            // 保留旧记录的置顶状态
                            val old = oldJobs.find { it.optString("position")==json.optString("position") && it.optString("company")==json.optString("company") }
                            if (old != null) json.put("pinned", old.optBoolean("pinned", false))
                            else json.put("pinned", false)
                            jobs.removeAll { it.optString("position")==json.optString("position") && it.optString("company")==json.optString("company") }
                            json.put("savedAt", System.currentTimeMillis())
                            jobs.add(0, json)
                            saveJobs(jobs)
                            status?.text = "✅ 已保存: ${json.optString("position")}"
                            Toast.makeText(this, "已保存: ${json.optString("position")}", Toast.LENGTH_SHORT).show()
                            refreshJobList(listPanel)
                        } catch (e: Exception) {
                            status?.text = "解析失败: ${e.message}"
                        }
                    }
                }
            }
        }
    }

    private fun refreshJobList(panel: View) {
        val container = panel.findViewById<LinearLayout>(R.id.jobListContainer)
        val status = panel.findViewById<TextView>(R.id.tvJobStatus)
        var jobs = loadJobs()
        container?.removeAllViews()
        if (jobs.isEmpty()) {
            status?.visibility = View.VISIBLE
            return
        }
        status?.visibility = View.GONE
        // 置顶排前面
        jobs = jobs.sortedByDescending { it.optBoolean("pinned", false) }.take(30)
        jobs.forEach { job ->
            val pinned = job.optBoolean("pinned", false)
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(10, 10, 10, 10)
                background = if (pinned) resources.getDrawable(R.drawable.job_card_pinned, theme)
                             else resources.getDrawable(R.drawable.job_card_bg, theme)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 }
            }
            val textView = TextView(this).apply {
                text = "${if(pinned)"📌 " else ""}${job.optString("position","?")}\n${job.optString("company","?")} · ${job.optString("salary","")} · ${job.optString("location","")}"
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                setOnClickListener { showChatPanel(job) }
            }
            val pinBtn = Button(this).apply {
                text = if (pinned) "📌" else "📍"
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setOnClickListener {
                    val jobs2 = loadJobs().toMutableList()
                    val idx = jobs2.indexOfFirst {
                        it.optString("position")==job.optString("position") && it.optString("company")==job.optString("company")
                    }
                    if (idx >= 0) {
                        jobs2[idx].put("pinned", !jobs2[idx].optBoolean("pinned", false))
                        saveJobs(jobs2)
                        refreshJobList(panel)
                    }
                }
            }
            val delBtn = Button(this).apply {
                text = "🗑"; setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setOnClickListener {
                    val jobs2 = loadJobs().toMutableList()
                    jobs2.removeAll { it.optString("position")==job.optString("position") && it.optString("company")==job.optString("company") }
                    saveJobs(jobs2)
                    refreshJobList(panel)
                    Toast.makeText(this@FloatingService, "已删除: ${job.optString("position")}", Toast.LENGTH_SHORT).show()
                }
            }
            item.addView(textView); item.addView(pinBtn); item.addView(delBtn)
            container?.addView(item)
        }
    }

    // ==================== 存储 ====================
    private fun loadJobs(): List<org.json.JSONObject> {
        val raw = store.get("saved_jobs", "[]")
        return try {
            (0 until org.json.JSONArray(raw).length()).map { org.json.JSONArray(raw).getJSONObject(it) }
        } catch (e: Exception) { emptyList() }
    }
    private fun saveJobs(jobs: List<org.json.JSONObject>) {
        val pinned = jobs.filter { it.optBoolean("pinned", false) }
        val unpinned = jobs.filter { !it.optBoolean("pinned", false) }
        val arr = org.json.JSONArray(); (pinned + unpinned.take(100)).forEach { arr.put(it) }
        store.put("saved_jobs", arr.toString())
    }
    private fun saveChatHistory(text: String) {
        val job = selectedJob ?: return
        store.put("chat_${job.optString("position")}_${job.optString("company")}", text.takeLast(3000))
    }

    // ==================== 面板增删 ====================
    private fun addPanel(panel: View) {
        val density = resources.displayMetrics.density
        val pp = WindowManager.LayoutParams((290 * density).toInt(), -2,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            val pct = store.getInt("panel_opacity", 55) / 100f
            alpha = pct.coerceIn(0.1f, 1.0f)
        }
        val h = panel.findViewById<View>(R.id.panelHeader)
        h?.let { addDrag(it, panel, pp) }
        // 返回键关闭面板
        panel.isFocusableInTouchMode = true
        panel.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                closePanel(); true
            } else false
        }
        wm.addView(panel, pp); currentPanel = panel
        panel.requestFocus()
    }
    private fun addDrag(header: View, panel: View, pp: WindowManager.LayoutParams) {
        var ix=0;var iy=0;var tx=0f;var ty=0f
        header.setOnTouchListener { _, e ->
            when(e.action){
                MotionEvent.ACTION_DOWN->{ix=pp.x;iy=pp.y;tx=e.rawX;ty=e.rawY;true}
                MotionEvent.ACTION_MOVE->{pp.x=ix+(e.rawX-tx).toInt();pp.y=iy+(e.rawY-ty).toInt();wm.updateViewLayout(panel,pp);true}
                else->false
            }
        }
    }
    private fun removePanel() { currentPanel?.let{wm.removeView(it)}; currentPanel=null }
    private fun closePanel() { removePanel(); open=false }

    // ==================== 服务生命周期 ====================
    override fun onStartCommand(i:Intent?,f:Int,s:Int):Int{
        try {
            val n = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this,"hire")
            else Notification.Builder(this)
            n.setContentTitle("Word助手").setContentText("悬浮窗运行中").setSmallIcon(android.R.drawable.ic_menu_edit)
            startForeground(1, n.build())
        } catch (_: Exception) {}
        return START_STICKY
    }
    override fun onBind(i:Intent?):IBinder?=null
    override fun onDestroy() { floatBtn?.let{wm.removeView(it)}; removePanel(); super.onDestroy() }
}
