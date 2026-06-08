package com.hireassistant

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject

/**
 * 无障碍服务 — 读取招聘App屏幕上的职位信息
 */
class ScreenReaderService : AccessibilityService() {

    companion object {
        var instance: ScreenReaderService? = null
        var pendingCallback: ((String) -> Unit)? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    /** 抓取招聘App屏幕文字（自动过滤自己） */
    fun captureScreen(): String {
        val myWindows = windows ?: return ""
        val sb = StringBuilder()
        for (i in 0 until myWindows.size) {
            val w = myWindows[i] ?: continue
            val root = w.root ?: continue
            // 跳过自己的窗口：读取根节点的包名
            val rootPn = root.packageName?.toString() ?: ""
            if (rootPn.contains("hireassistant")) continue
            collectText(root, sb, 0)
            root.recycle()
        }
        return sb.toString()
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (depth > 80) return
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val hint = node.hintText?.toString()?.trim()

        // 每段文字单独一行，保留结构
        if (!text.isNullOrEmpty() && text.length > 1) {
            sb.append(text).append("\n")
        }
        if (!desc.isNullOrEmpty() && desc != text && desc.length > 2) {
            sb.append(desc).append("\n")
        }
        if (!hint.isNullOrEmpty() && hint.length > 1) {
            sb.append(hint).append("\n")
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectText(it, sb, depth+1); it.recycle() }
        }
    }

    /** 发送JD到AI解析，返回结构化JSON */
    fun captureAndParse(callback: (String) -> Unit) {
        val raw = captureScreen()
        if (raw.length < 20) {
            callback("{\"error\":\"未能读取屏幕内容，请确保已开启无障碍权限\"}")
            return
        }
        // 返回原始文本，由 FloatingService 调用 AI 解析
        callback(raw)
    }

    /** 将文字填入招聘App的输入框（自动跳过自己的窗口） */
    fun fillToInput(text: String): Boolean {
        val allWindows = windows ?: return false
        for (i in 0 until allWindows.size) {
            val w = allWindows[i] ?: continue
            val root = w.root ?: continue
            // 跳过自己的窗口
            val pn = root.packageName?.toString() ?: ""
            if (pn.contains("hireassistant")) { root.recycle(); continue }

            val inputs = findEditableNodes(root)
            root.recycle()
            for (node in inputs) {
                try {
                    val args = android.os.Bundle()
                    args.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        text
                    )
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    node.recycle()
                    return true
                } catch (_: Exception) {
                    node.recycle()
                }
            }
        }
        return false
    }

    private fun findEditableNodes(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        findEditable(node, results)
        return results
    }

    private fun findEditable(node: AccessibilityNodeInfo, results: MutableList<AccessibilityNodeInfo>) {
        if (node.isEditable && node.isEnabled) {
            results.add(android.view.accessibility.AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { findEditable(it, results); it.recycle() }
        }
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
