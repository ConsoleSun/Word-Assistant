# Word助手 测试版

一个 Android 悬浮窗 App，在招聘软件上提供 AI 辅助功能。支持 BOSS 直聘、智联招聘等主流平台。

## ✨ 功能

- **📸 JD 抓取** — 一键读取当前页面职位信息，AI 结构化提取岗位、公司、薪资、职责
- **🤖 AI 对话** — 粘贴 HR 消息，AI 结合 JD + 简历 + 历史生成回复
- **📤 填入输入框** — 通过无障碍服务直接将 AI 回复填入招聘 App 聊天框
- **📂 历史记录** — 自动保存每个岗位，支持置顶、搜索、删除
- **🎨 可调透明度** — 悬浮窗透明度 10%-100% 自由调节
- **📄 简历解析** — 粘贴简历文本，AI 提取技能、经验等结构化信息
- **🔧 高级设置** — 自定义系统提示词、AI 职业角色
- **🤖 双模型** — 支持 DeepSeek 和 MiMo 2.5

## 📱 安装

1. 下载最新的 APK 文件
2. 手机设置 → 安全 → 允许安装未知来源应用
3. 打开 APK 进行安装
4. 首次启动按提示开启**无障碍权限**和**悬浮窗权限**

## ⚙️ 配置

1. 打开 App → ⚙️ 设置 → 选择 AI 模型 → 填入 API Key → 保存
2. 点击「开启悬浮窗」
3. 打开招聘 App → 点击屏幕右侧紫色圆形按钮

## 🏗 构建

```bash
# 安装 Android SDK (platforms;android-34, build-tools;34.0.0)
# 需要 JDK 17+

git clone https://github.com/ConsoleSun/Word-Assistant.git
cd Word-Assistant

# 创建 local.properties
echo "sdk.dir=D:/android-sdk" > local.properties

./gradlew assembleDebug
# APK 输出在 app/build/outputs/apk/debug/
```

## 📁 项目结构

```
hireassistant/
├── app/
│   ├── build.gradle.kts          # 依赖：OkHttp, Coroutines, AppCompat
│   └── src/main/
│       ├── AndroidManifest.xml   # 权限：悬浮窗、无障碍、网络、前台服务
│       ├── java/com/hireassistant/
│       │   ├── MainActivity.kt        # 设置页（5 标签页）
│       │   ├── FloatingService.kt     # 悬浮窗 + 双面板逻辑
│       │   ├── AIHelper.kt            # DeepSeek/MiMo API 调用
│       │   ├── ScreenReaderService.kt # 无障碍服务（读屏+填字）
│       │   └── StorageManager.kt      # SharedPreferences 存储
│       ├── res/
│       │   ├── layout/           # 界面布局
│       │   ├── drawable/         # 按钮、面板背景
│       │   ├── values/           # 字符串、样式
│       │   └── xml/              # 无障碍配置
│       └── mipmap-anydpi-v26/    # 自适应图标
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/
```

## 📊 数据存储

所有数据存储在应用私有目录（SharedPreferences），不上传任何服务器。

## ⚠️ 免责声明

本软件仅供学习和技术研究使用。使用者应遵守相关平台的服务协议，自行承担使用过程中产生的一切后果。开发者不对用户的任何行为负责。

## 📄 License

MIT
