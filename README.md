# 点滴 — 本地记账 Android App

一款简洁、**完全离线**的个人记账应用：收支记录、标签与大类、时间筛选、攒钱目标、资金修正、统计图表、常驻通知快捷记账，以及从 GitHub Releases 检查更新。

**当前版本：1.0.5**（versionCode 6）  
仓库：[kers701/diandi](https://github.com/kers701/diandi)

## 功能一览

| 功能 | 说明 |
|------|------|
| **添加账单** | 右下角 +，可选「支出」或「收入」 |
| **时间字段** | 每笔账单记录年 / 月 / 日 |
| **大类选择** | 支出：餐饮、交通、购物、烟酒、娱乐…；收入：工资、奖金、兼职… |
| **自定义标签** | 添加时支持逗号分隔标签，支持模糊搜索 |
| **类型筛选** | 全部 / 支出 / 收入 |
| **时间筛选** | 全部 / 本年 / 本月 / 本日；另可按年/月/日与大类详细筛选 |
| **标签搜索** | 首页搜索框支持标签模糊匹配 |
| **攒钱目标** | 设置目标金额，记录存入进度；首页显示余额/目标进度 |
| **资金修正** | 初始余额为 0，通过修正（如 +800）设定起始金额 |
| **图表** | 消费折线（带数值）+ 大类扇形图与图例（可按年/月/日） |
| **常驻通知** | 独立进程 `:notify`（策略对齐 wallpaper_app：WakeLock / START_STICKY / 划掉自启）；点击打开独立记账页；**可在「软件说明」中开关** |
| **检查更新** | 读取 `kers701/diandi` 的 GitHub Releases，下载并安装 APK |
| **软件说明** | 应用内帮助页（顶栏「i」图标） |

## 技术栈

- **语言**：Kotlin  
- **UI**：Jetpack Compose + Material 3  
- **架构**：MVVM + Repository  
- **数据库**：Room（本地 SQLite，卸载即清除）  
- **网络**：OkHttp（仅用于检查更新 / 下载 APK）  
- **最低系统**：Android 8.0 (API 26)

## 使用说明（摘要）

1. 首次打开可授权通知；若关闭「常驻通知」，不会再启动通知服务。  
2. 余额 = 资金修正合计 + 收入 − 支出。  
3. 首页攒钱进度 = 余额 ÷ 所有攒钱目标总额。  
4. 从最近任务划掉主界面时，通知进程尽量保持（系统仍可能杀进程；关闭开关则彻底不启动）。

## 如何构建 APK

### 环境

- Android Studio 较新稳定版、JDK 17、Android SDK 35

### 本地

```bash
./gradlew assembleDebug
# 或签名 Release：配置 keystore.properties 后
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
| **1.0.5** | 修复说明入口；通知多进程保活与自启；独立记账页无返回动画 |
| **1.0.4** | 通知常驻策略对齐 wallpaper_app；点击通知独立记账页 |
| **1.0.3** | 折线数值标注；大类扇形图 |
| **1.0.2** | 软件说明页；常驻通知开关；README 更新 |
| **1.0.1** | 常驻通知、攒钱进度、检查更新、通知独立进程、年/月/日筛选 |
| **1.0.0** | 初版记账核心功能 |

## 项目结构

```
app/src/main/java/com/diandi/bookkeeping/
├── data/           # entity / dao / db / repository
├── service/        # KeepAliveService（:notify 进程）、BootReceiver
├── ui/screens/     # Home / Add / Charts / Goals / Correction / About
├── util/           # Categories、AppUpdateChecker、AppPrefs
└── viewmodel/
```

## 许可

自用项目；按需修改。数据仅存本机。
'''
)
PY
# zip changed files only
python3 << 'PY'
from pathlib import Path
import zipfile, subprocess
root = Path("/home/workdir/artifacts/DiandiApp")
files = [
    "README.md",
    "app/build.gradle.kts",
    "app/src/main/java/com/diandi/bookkeeping/util/AppPrefs.kt",
    "app/src/main/java/com/diandi/bookkeeping/ui/screens/AboutScreen.kt",
    "app/src/main/java/com/diandi/bookkeeping/ui/screens/HomeScreen.kt",
    "app/src/main/java/com/diandi/bookkeeping/ui/navigation/NavGraph.kt",
    "app/src/main/java/com/diandi/bookkeeping/MainActivity.kt",
    "app/src/main/java/com/diandi/bookkeeping/service/KeepAliveService.kt",
    "app/src/main/java/com/diandi/bookkeeping/service/BootReceiver.kt",
]
out = Path("/home/workdir/artifacts/DiandiApp-patch.zip")
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    for f in files:
        z.write(root / f, f)
print(subprocess.check_output(["unzip", "-l", str(out)], text=True))
PY
