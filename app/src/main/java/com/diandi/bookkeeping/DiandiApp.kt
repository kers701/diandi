package com.diandi.bookkeeping

import android.app.Application
import android.app.ActivityManager
import android.os.Build
import android.os.Process
import com.diandi.bookkeeping.data.db.AppDatabase
import com.diandi.bookkeeping.data.repository.BookkeepingRepository
import com.diandi.bookkeeping.service.AiAnalysisScheduler

class DiandiApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy { BookkeepingRepository(database.transactionDao()) }

    override fun onCreate() {
        super.onCreate()
        // KeepAliveService and RemoteWorkerService run in :notify and :ai. The default
        // WorkManager instance is initialized only in the main process.
        if (isMainProcess()) {
            AiAnalysisScheduler.ensureScheduled(this)
        }
    }

    private fun isMainProcess(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName() == packageName
        }
        val manager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val pid = Process.myPid()
        return manager.runningAppProcesses
            ?.any { it.pid == pid && it.processName == packageName }
            ?: false
    }
}
