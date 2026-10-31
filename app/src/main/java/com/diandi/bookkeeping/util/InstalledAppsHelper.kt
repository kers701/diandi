package com.diandi.bookkeeping.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/** 与 wallpaper_app ForegroundAppHelper.listLaunchableApps 对齐 */
data class LauncherAppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean
)

object InstalledAppsHelper {

    /**
     * 可启动应用列表（桌面图标能打开的全部应用，含系统预装）。
     * 需 Manifest `<queries>` 声明 MAIN/LAUNCHER，否则 Android 11+ 结果不完整。
     */
    fun listLaunchableApps(context: Context): List<LauncherAppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolves = try {
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        } catch (_: Exception) {
            emptyList()
        }
        val self = context.packageName
        val map = linkedMapOf<String, LauncherAppInfo>()
        for (ri in resolves) {
            val pkg = ri.activityInfo?.packageName ?: continue
            if (pkg == self) continue
            if (pkg in map) continue
            val label = try {
                ri.loadLabel(pm)?.toString() ?: pkg
            } catch (_: Exception) {
                pkg
            }
            val isSystem = try {
                val ai = pm.getApplicationInfo(pkg, 0)
                (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            } catch (_: Exception) {
                false
            }
            map[pkg] = LauncherAppInfo(pkg, label, isSystem)
        }
        return map.values.sortedBy { it.label.lowercase() }
    }
}
