package com.diandi.bookkeeping

import android.app.Application
import com.diandi.bookkeeping.data.db.AppDatabase
import com.diandi.bookkeeping.data.repository.BookkeepingRepository

class DiandiApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy { BookkeepingRepository(database.transactionDao()) }
}
