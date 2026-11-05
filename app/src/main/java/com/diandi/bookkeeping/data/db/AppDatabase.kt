package com.diandi.bookkeeping.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.diandi.bookkeeping.data.dao.TransactionDao
import com.diandi.bookkeeping.data.entity.Budget
import com.diandi.bookkeeping.data.entity.FundCorrection
import com.diandi.bookkeeping.data.entity.SavingsGoal
import com.diandi.bookkeeping.data.entity.Transaction

@Database(
    entities = [Transaction::class, SavingsGoal::class, FundCorrection::class, Budget::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "diandi_db"
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5
                ).build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE savings_goals ADD COLUMN allocationWeight REAL NOT NULL DEFAULT 1.0"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE savings_goals ADD COLUMN lastManualDeposit REAL NOT NULL DEFAULT 0.0"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE fund_corrections ADD COLUMN source TEXT NOT NULL DEFAULT '手动'"
                )
            }
        }

        /**
         * 4 → 5：
         * 1. 金额从 REAL（元）改为 INTEGER（分），三张表都要重建（SQLite 不能改列类型）；
         *    转换用 ROUND(x * 100)，负数（资金修正）同样适用。
         * 2. 新增预算表 budgets。
         *
         * 注意：Room 会校验迁移后的表结构与实体声明完全一致，列名沿用旧名
         * （amount / targetAmount / currentAmount / lastManualDeposit），只改类型。
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // ---- transactions ----
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transactions_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `amount` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `tags` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `year` INTEGER NOT NULL,
                        `month` INTEGER NOT NULL,
                        `day` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO `transactions_new`
                        (`id`, `amount`, `type`, `category`, `tags`, `note`, `year`, `month`, `day`, `createdAt`)
                    SELECT
                        `id`,
                        CAST(ROUND(`amount` * 100) AS INTEGER),
                        `type`, `category`, `tags`, `note`, `year`, `month`, `day`, `createdAt`
                    FROM `transactions`
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE `transactions`")
                database.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")

                // ---- savings_goals ----
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `savings_goals_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `targetAmount` INTEGER NOT NULL,
                        `currentAmount` INTEGER NOT NULL,
                        `deadline` TEXT NOT NULL,
                        `allocationWeight` REAL NOT NULL,
                        `lastManualDeposit` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO `savings_goals_new`
                        (`id`, `title`, `targetAmount`, `currentAmount`, `deadline`,
                         `allocationWeight`, `lastManualDeposit`, `createdAt`)
                    SELECT
                        `id`, `title`,
                        CAST(ROUND(`targetAmount` * 100) AS INTEGER),
                        CAST(ROUND(`currentAmount` * 100) AS INTEGER),
                        `deadline`,
                        `allocationWeight`,
                        CAST(ROUND(`lastManualDeposit` * 100) AS INTEGER),
                        `createdAt`
                    FROM `savings_goals`
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE `savings_goals`")
                database.execSQL("ALTER TABLE `savings_goals_new` RENAME TO `savings_goals`")

                // ---- fund_corrections ----
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `fund_corrections_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `amount` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `year` INTEGER NOT NULL,
                        `month` INTEGER NOT NULL,
                        `day` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO `fund_corrections_new`
                        (`id`, `amount`, `source`, `note`, `year`, `month`, `day`, `createdAt`)
                    SELECT
                        `id`,
                        CAST(ROUND(`amount` * 100) AS INTEGER),
                        `source`, `note`, `year`, `month`, `day`, `createdAt`
                    FROM `fund_corrections`
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE `fund_corrections`")
                database.execSQL("ALTER TABLE `fund_corrections_new` RENAME TO `fund_corrections`")

                // ---- budgets（新表） ----
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `budgets` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `scope` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `monthlyAmount` INTEGER NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
