# 点滴 — 本地记账 Android App

一款简洁、完全离线的个人记账应用，支持收支记录、自定义标签、时间与类别筛选、攒钱目标、资金修正以及消费趋势与大类分布图。

## 功能一览

| 功能 | 说明 |
|------|------|
| **添加账单** | 点击右下角 +，可选择「支出」或「收入」 |
| **时间字段** | 每笔账单记录年 / 月 / 日 |
| **大类选择** | 支出：餐饮、交通、购物、烟酒、娱乐…；收入：工资、奖金、兼职… |
| **自定义标签** | 添加时支持逗号分隔标签，支持模糊搜索 |
| **筛选** | 按年 / 月 / 日、按支出/收入类型、按大类筛选 |
| **标签搜索** | 首页搜索框支持标签模糊匹配 |
| **攒钱目标** | 设置目标金额，随时存入进度 |
| **资金修正** | 初始余额为 0，通过修正（如 +800）设定起始金额 |
| **图表** | 消费折线图（按月汇总）+ 大类分布进度条（可按年/月/日维度） |

## 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose + Material 3
- **架构**：MVVM + Repository
- **数据库**：Room（本地 SQLite）
- **图表**：Vico（纯 Compose）
- **最低系统**：Android 8.0 (API 26)

## 如何构建 APK

### 1. 环境要求
- Android Studio **Ladybug** 或更新版本（推荐最新稳定版）
- JDK 17
- Android SDK 35

### 2. 打开项目
1. 解压或直接打开本项目的 `DiandiApp` 文件夹
2. Android Studio → **Open** → 选择 `DiandiApp` 目录
3. 等待 Gradle Sync 完成（首次会下载依赖；会生成 `gradlew`）

### 3. 运行 / 打包
- **直接运行**：连接真机或启动模拟器，点击 Run ▶
- **生成 Debug APK**：
  ```
  Build → Build Bundle(s) / APK(s) → Build APK(s)
  ```
  生成路径：`app/build/outputs/apk/debug/app-debug.apk`
- **本机签名 Release**：复制 `keystore.properties.example` 为 `keystore.properties` 并填写路径与密码，再执行 `./gradlew assembleRelease`

### 4. GitHub Actions（与 wallpaper_app 同款）

已内置两个工作流（参考 [kers701/wallpaper_app](https://github.com/kers701/wallpaper_app)）：

| 工作流 | 触发 | 产物 |
|--------|------|------|
| **Build APK** | push/PR 到 main、手动 | Debug APK → Artifact `diandi-debug-apk` |
| **Build Release APK** | 提交含「发布/正式/release/re…」、tag `v*`、手动 | 签名 Release APK → `diandi-release-apk`；含「发布」或 `v*` tag 时创建 GitHub Release |

**仓库 Secrets（签名必填）：**

| Secret | 说明 |
|--------|------|
| `RELEASE_KEYSTORE_BASE64` | keystore 文件 Base64（`base64 -w0 your.keystore`） |
| `RELEASE_STORE_PASSWORD` | 仓库密码 |
| `RELEASE_KEY_ALIAS` | 密钥别名 |
| `RELEASE_KEY_PASSWORD` | 密钥密码 |

推送到 GitHub 后，在 **Settings → Secrets and variables → Actions** 中添加上述 Secrets。

### 5. 安装
将生成的 APK 传到手机，开启「允许安装未知来源应用」后安装即可。

## 项目结构

```
app/src/main/java/com/diandi/bookkeeping/
├── data/
│   ├── entity/          # Transaction / SavingsGoal / FundCorrection
│   ├── dao/             # Room DAO + 统计查询
│   ├── db/              # AppDatabase
│   └── repository/      # BookkeepingRepository
├── ui/
│   ├── screens/         # Home / Add / Charts / Goals / Correction
│   ├── components/      # 通用组件
│   ├── navigation/      # 路由
│   └── theme/           # Material 3 主题
├── viewmodel/           # MainViewModel
├── util/                # 预置大类
├── DiandiApp.kt         # Application
└── MainActivity.kt
```

## 余额计算逻辑

```
当前余额 = 资金修正总和 + 总收入 - 总支出
```

例如：初始为 0，添加资金修正 +800，再记一笔支出 50，则余额显示 750。

## 后续可扩展

- 多账本 / 多账户
- 周期账单
- 预算管理
- 数据导出（CSV / JSON）
- 桌面小组件
- 密码保护 / 生物识别

---

应用名：**点滴**  
包名：`com.diandi.bookkeeping`  
版本：1.0.0
