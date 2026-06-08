# Word助手 测试版

一个 Android 悬浮窗 AI 助手，支持 DeepSeek 和 MiMo 2.5，在招聘软件上辅助求职。也支持自定义角色进行自由对话。

## ✨ 功能

- **📸 JD 抓取** — 无障碍服务读取当前页面，AI 提取岗位/公司/薪资/职责
- **🤖 AI 对话** — 结合 JD + 简历 + 历史记录生成回复，支持粘贴或输入框打字
- **📤 填入输入框** — 无障碍服务将 AI 回复直接填入招聘 App 聊天框
- **📂 历史记录** — 岗位记录支持置顶、搜索、删除，也支持新建空对话
- **👤 自定义角色** — 可创建多个 AI 角色（如"心理咨询师"），各自独立提示词
- **🎨 可调透明度** — 悬浮窗透明度 10%-100%
- **📄 简历解析** — 粘贴简历文本，AI 提取技能/经验/学历
- **🔧 提示词编辑** — 支持自定义系统提示词，保存/恢复/校验

## 📱 安装

1. 下载 APK
2. 手机设置 → 安全 → 允许安装未知来源
3. 打开 App → 开启**无障碍权限**和**悬浮窗权限**
4. ⚙️ 设置 → 填写 API Key → 保存 → 开启悬浮窗

## ⚙️ 构建

```bash
# 需要 JDK 17+, Android SDK (platforms;android-34, build-tools;34.0.0)

git clone https://github.com/ConsoleSun/Word-Assistant.git
cd Word-Assistant
echo "sdk.dir=D:/android-sdk" > local.properties
./gradlew assembleDebug
```

## ⚠️ 免责声明

本软件仅供学习和技术研究使用。使用者应遵守相关平台的服务协议，自行承担使用过程中产生的一切后果。

## 📄 License

MIT
