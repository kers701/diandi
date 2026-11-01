package com.diandi.bookkeeping

import android.app.Application
import com.diandi.bookkeeping.data.ai.AiAnalysisPrefs
import com.diandi.bookkeeping.data.db.AppDatabase
import com.diandi.bookkeeping.data.repository.BookkeepingRepository
import com.diandi.bookkeeping.service.AiAnalysisScheduler

class DiandiApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy { BookkeepingRepository(database.transactionDao()) }

    override fun onCreate() {
        super.onCreate()
        if (AiAnalysisPrefs.isEnabled(this)) AiAnalysisScheduler.ensureScheduled(this)
    }
}
