# 点滴 — 本地记账 Android App

一款简洁、**完全离线**的个人记账应用：收支记录、标签与大类、时间筛选、攒钱目标、资金修正、统计图表、常驻通知快捷记账、通知自动记账（应用白名单），以及从 GitHub Releases 检查更新。

**当前版本：1.2.5**（versionCode 25）  
仓库：[kers701/diandi](https://github.com/kers701/diandi)

## 功能一览

| 功能 | 说明 |
|------|------|
| **添加账单** | 右下角 +，可选「支出」或「收入」；支持大类、标签、备注与日期 |
| **时间字段** | 每笔账单记录年 / 月 / 日 |
| **大类选择** | 支出：餐饮、交通、购物、烟酒、娱乐…；收入：工资、奖金、兼职…；筛选时大类列表跟随收支类型 |
| **自定义标签** | 添加时支持标签，首页支持模糊搜索 |
| **类型 / 时间筛选** | 全部 / 支出 / 收入；全部 / 本年 / 本月 / 本日；另可按日期范围与大类详细筛选 |
| **攒钱目标** | 设置目标金额；首页与通知显示余额/目标进度 |
| **资金修正** | 初始余额为 0，通过修正（如 +800）设定起始金额 |
| **图表** | 消费折线（带数值）+ 大类扇形图（可按年/月/日） |
| **常驻通知** | 独立进程 `:notify`（对齐 wallpaper_app：WakeLock / START_STICKY / 划掉自启）；点击打开独立记账页；可在「软件说明」中开关 |
| **通知自动记账** | 读取已授权的通知，识别金额后自动生成账单；**必须配置应用白名单**；选择列表中**已加入白名单的应用置顶** |
| **应用列表** | 与 wallpaper_app 相同逻辑读取可启动应用（含系统预装；Manifest `queries`） |
| **检查更新** | 读取 `kers701/diandi` 的 GitHub Releases，下载并安装 APK |
| **软件说明** | 应用内帮助页（顶栏「i」图标）：通知开关、自动记账、白名单、关键词等 |

## 技术栈

- **语言**：Kotlin  
- **UI**：Jetpack Compose + Material 3  
- **架构**：MVVM + Repository  
- **数据库**：Room（本地 SQLite，卸载即清除）  
- **网络**：OkHttp（仅用于检查更新 / 下载 APK）  
- **最低系统**：Android 8.0 (API 26)

## 使用说明（摘要）

1. 首次打开可授权通知；若关闭「常驻通知」，不会再启动通知服务。  
2. 开启「通知自动记账」后，需授予通知使用权，并在白名单中勾选支付/短信等应用；**未选白名单则不会记账**。  
3. 白名单选择列表：已勾选应用固定排在列表顶部，便于查看与取消。  
4. 余额 = 资金修正合计 + 收入 − 支出。  
5. 首页攒钱进度 = 余额 ÷ 所有攒钱目标总额（有目标时）。  

## 如何构建 APK

### 环境

- Android Studio、JDK 17、Android SDK 35

### 本地

```bash
./gradlew assembleDebug
# 签名 Release：配置 keystore.properties 后
./gradlew assembleRelease
```

### GitHub Actions

| 工作流 | 触发 | 产物 |
|--------|------|------|
| **Build APK** | push/PR、手动 | Debug Artifact `diandi-debug-apk` |
| **Build Release APK** | 提交含「发布」等、`v*` tag、手动 | 签名 APK；可创建 GitHub Release |

**Secrets：** `RELEASE_KEYSTORE_BASE64`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`

## 版本记录

| 版本 | 说明 |
|------|------|
| **1.2.5** | 白名单列表已选应用置顶；更新 README 与软件说明 |
| **1.2.4** | 补全 InstalledAppsHelper 修复编译 |
| **1.2.3** | 应用列表对齐 wallpaper；筛选大类随收支 |
| **1.2.2** | 构建修复 |
| **1.0.x** | 初版核心与通知/图表等迭代 |

## 项目结构

```
app/src/main/java/com/diandi/bookkeeping/
├── data/           # entity / dao / db / repository
├── service/        # KeepAlive / 自动记账通知 / Boot / Restart
├── ui/             # screens / components / theme / navigation
├── util/           # Categories / AppPrefs / InstalledAppsHelper / 更新检测
└── viewmodel/
```

## 许可

个人使用；请遵守各系统厂商对后台与通知的限制说明。
