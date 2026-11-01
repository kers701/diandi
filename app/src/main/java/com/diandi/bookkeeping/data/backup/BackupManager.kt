package com.diandi.bookkeeping.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.diandi.bookkeeping.BuildConfig
import com.diandi.bookkeeping.data.db.AppDatabase
import com.diandi.bookkeeping.data.entity.FundCorrection
import com.diandi.bookkeeping.data.entity.SavingsGoal
import com.diandi.bookkeeping.data.entity.Transaction
import com.diandi.bookkeeping.util.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.LocalDate

/** 用户可移植的数据备份；系统级授权不会包含在文件中。 */
object BackupManager {
    private const val FORMAT = "com.diandi.bookkeeping.backup"
    private const val VERSION = 1
    private const val MAX_BACKUP_BYTES = 50 * 1024 * 1024
    private const val MAX_ROWS_PER_TABLE = 500_000

    data class Snapshot(
        val transactions: List<Transaction>,
        val goals: List<SavingsGoal>,
        val corrections: List<FundCorrection>,
        val expenseCategories: List<String>,
        val incomeCategories: List<String>
    )

    suspend fun export(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val db = AppDatabase.getInstance(appContext)
        val dao = db.transactionDao()
        val snapshot = db.withTransaction {
            Snapshot(
                transactions = dao.getTransactionsForBackup(),
                goals = dao.getGoalsForBackup(),
                corrections = dao.getCorrectionsForBackup(),
                expenseCategories = AppPrefs.getExpenseCategories(appContext),
                incomeCategories = AppPrefs.getIncomeCategories(appContext)
            )
        }
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("createdAt", System.currentTimeMillis())
            .put("transactions", JSONArray().apply {
                snapshot.transactions.forEach { put(it.toJson()) }
            })
            .put("savingsGoals", JSONArray().apply {
                snapshot.goals.forEach { put(it.toJson()) }
            })
            .put("fundCorrections", JSONArray().apply {
                snapshot.corrections.forEach { put(it.toJson()) }
            })
            .put("categories", JSONObject()
                .put("expense", JSONArray(snapshot.expenseCategories))
                .put("income", JSONArray(snapshot.incomeCategories))
            )
        val jsonText = root.toString(2)
        require(jsonText.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) {
            "备份文件超过 50 MB，请减少数据后再导出。"
        }

        val output = appContext.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("无法创建备份文件。")
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(jsonText)
        }
    }

    /** 先在内存中完整解析与验证，只有成功返回的 Snapshot 才能进入恢复确认流程。 */
    fun read(context: Context, uri: Uri): Snapshot {
        val input = context.applicationContext.contentResolver.openInputStream(uri)
            ?: throw IOException("无法读取所选文件。")
        val bytes = input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_BACKUP_BYTES) { "备份文件过大，无法导入。" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }

        return try {
            parse(String(bytes, Charsets.UTF_8).removePrefix("\uFEFF"))
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("文件不是有效的点滴备份，或文件内容已损坏。", e)
        }
    }

    suspend fun restore(context: Context, snapshot: Snapshot) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val db = AppDatabase.getInstance(appContext)
        val dao = db.transactionDao()
        db.withTransaction {
            dao.deleteAllTransactions()
            dao.deleteAllGoals()
            dao.deleteAllCorrections()
            if (snapshot.transactions.isNotEmpty()) dao.insertTransactions(snapshot.transactions)
            if (snapshot.goals.isNotEmpty()) dao.insertGoals(snapshot.goals)
            if (snapshot.corrections.isNotEmpty()) dao.insertCorrections(snapshot.corrections)
        }
        AppPrefs.setExpenseCategories(appContext, snapshot.expenseCategories)
        AppPrefs.setIncomeCategories(appContext, snapshot.incomeCategories)
    }

    private fun parse(text: String): Snapshot {
        val root = JSONObject(text)
        require(root.getString("format") == FORMAT) { "这不是点滴记账导出的备份文件。" }
        val version = root.getInt("version")
        require(version == VERSION) {
            if (version > VERSION) "备份文件版本较新，请升级点滴后再恢复。"
            else "备份文件版本不受支持。"
        }

        val transactions = root.getJSONArray("transactions").checkedSize("账单").let { array ->
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val id = item.getLong("id").also { require(it > 0) { "账单编号无效。" } }
                val amount = item.getDouble("amount").also {
                    require(it.isFinite() && it > 0.0) { "账单金额无效。" }
                }
                val type = item.getString("type")
                require(type == "EXPENSE" || type == "INCOME") { "账单收支类型无效。" }
                val category = item.getString("category").also {
                    require(it.isNotBlank()) { "账单类目不能为空。" }
                }
                val year = item.getInt("year")
                val month = item.getInt("month")
                val day = item.getInt("day")
                LocalDate.of(year, month, day)
                Transaction(
                    id = id,
                    amount = amount,
                    type = type,
                    category = category,
                    tags = item.getString("tags"),
                    note = item.getString("note"),
                    year = year,
                    month = month,
                    day = day,
                    createdAt = item.getLong("createdAt").also { require(it >= 0) }
                )
            }
        }

        val goals = root.getJSONArray("savingsGoals").checkedSize("攒钱目标").let { array ->
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val id = item.getLong("id").also { require(it > 0) { "目标编号无效。" } }
                val title = item.getString("title").also {
                    require(it.isNotBlank()) { "目标名称不能为空。" }
                }
                val target = item.getDouble("targetAmount").also {
                    require(it.isFinite() && it >= 0.0) { "目标金额无效。" }
                }
                val current = item.getDouble("currentAmount").also {
                    require(it.isFinite() && it >= 0.0) { "目标进度金额无效。" }
                }
                SavingsGoal(
                    id = id,
                    title = title,
                    targetAmount = target,
                    currentAmount = current,
                    deadline = item.getString("deadline"),
                    createdAt = item.getLong("createdAt").also { require(it >= 0) }
                )
            }
        }

        val corrections = root.getJSONArray("fundCorrections").checkedSize("资金修正").let { array ->
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val id = item.getLong("id").also { require(it > 0) { "资金修正编号无效。" } }
                val amount = item.getDouble("amount").also {
                    require(it.isFinite()) { "资金修正金额无效。" }
                }
                val year = item.getInt("year")
                val month = item.getInt("month")
                val day = item.getInt("day")
                LocalDate.of(year, month, day)
                FundCorrection(
                    id = id,
                    amount = amount,
                    note = item.getString("note"),
                    year = year,
                    month = month,
                    day = day,
                    createdAt = item.getLong("createdAt").also { require(it >= 0) }
                )
            }
        }

        val categories = root.getJSONObject("categories")
        val expenseCategories = categories.getJSONArray("expense").toStringList("支出类目")
        val incomeCategories = categories.getJSONArray("income").toStringList("收入类目")
        require(transactions.map { it.id }.distinct().size == transactions.size) { "账单编号重复。" }
        require(goals.map { it.id }.distinct().size == goals.size) { "目标编号重复。" }
        require(corrections.map { it.id }.distinct().size == corrections.size) { "资金修正编号重复。" }

        return Snapshot(
            transactions = transactions,
            goals = goals,
            corrections = corrections,
            expenseCategories = expenseCategories,
            incomeCategories = incomeCategories
        )
    }

    private fun JSONArray.checkedSize(label: String): JSONArray {
        require(length() <= MAX_ROWS_PER_TABLE) { "${label}数量超出允许范围。" }
        return this
    }

    private fun JSONArray.toStringList(label: String): List<String> {
        require(length() in 1..10_000) { "${label}列表无效。" }
        val result = List(length()) { index ->
            getString(index).trim().also { require(it.isNotBlank()) { "${label}中存在空项。" } }
        }
        require(result.distinct().size == result.size) { "${label}中存在重复项。" }
        return result
    }

    private fun Transaction.toJson() = JSONObject()
        .put("id", id)
        .put("amount", amount)
        .put("type", type)
        .put("category", category)
        .put("tags", tags)
        .put("note", note)
        .put("year", year)
        .put("month", month)
        .put("day", day)
        .put("createdAt", createdAt)

    private fun SavingsGoal.toJson() = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("targetAmount", targetAmount)
        .put("currentAmount", currentAmount)
        .put("deadline", deadline)
        .put("createdAt", createdAt)

    private fun FundCorrection.toJson() = JSONObject()
        .put("id", id)
        .put("amount", amount)
        .put("note", note)
        .put("year", year)
        .put("month", month)
        .put("day", day)
        .put("createdAt", createdAt)
}
