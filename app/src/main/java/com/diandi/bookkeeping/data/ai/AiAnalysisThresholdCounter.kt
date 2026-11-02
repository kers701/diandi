package com.diandi.bookkeeping.data.ai

import android.content.Context
import android.content.Intent
import android.util.AtomicFile
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** 以 app 私有文件作为跨进程账单触发计数的唯一数据源。 */
object AiAnalysisThresholdCounter {
    const val DEFAULT_THRESHOLD = 5
    const val MIN_THRESHOLD = 1
    const val MAX_THRESHOLD = 999

    const val ACTION_STATE_CHANGED = "com.diandi.bookkeeping.action.AI_ANALYSIS_THRESHOLD_CHANGED"

    private const val STATE_DIRECTORY = "ai_analysis_counter"
    private const val STATE_FILE_NAME = "threshold.state"
    private const val LOCK_FILE_NAME = "threshold.lock"
    private val processLock = ReentrantLock()

    data class State(val threshold: Int, val count: Int)

    data class IncrementResult(val state: State, val reachedThreshold: Boolean)

    fun readState(context: Context): State = withFileLock(context) { atomicFile ->
        readStateLocked(atomicFile)
    }

    fun setThreshold(context: Context, threshold: Int): State {
        require(threshold in MIN_THRESHOLD..MAX_THRESHOLD)
        val state = withFileLock(context) { atomicFile ->
            val previous = readStateLocked(atomicFile)
            val updated = State(
                threshold = threshold,
                count = previous.count.coerceIn(0, threshold - 1)
            )
            writeStateLocked(atomicFile, updated)
            updated
        }
        notifyStateChanged(context)
        return state
    }

    /** 新账单成功入库后调用；达到阈值时在同一把文件锁中归零，最多触发一次。 */
    fun recordNewBill(context: Context): IncrementResult {
        val result = withFileLock(context) { atomicFile ->
            val previous = readStateLocked(atomicFile)
            val nextCount = previous.count + 1
            val reached = nextCount >= previous.threshold
            val updated = State(
                threshold = previous.threshold,
                count = if (reached) 0 else nextCount
            )
            writeStateLocked(atomicFile, updated)
            IncrementResult(updated, reached)
        }
        notifyStateChanged(context)
        return result
    }

    private fun readStateLocked(file: AtomicFile): State {
        return try {
            file.openRead().bufferedReader(StandardCharsets.UTF_8).use { reader ->
                val threshold = reader.readLine()?.toIntOrNull()
                    ?.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
                    ?: DEFAULT_THRESHOLD
                val count = reader.readLine()?.toIntOrNull()
                    ?.coerceIn(0, threshold - 1)
                    ?: 0
                State(threshold, count)
            }
        } catch (_: Exception) {
            State(DEFAULT_THRESHOLD, 0)
        }
    }

    private fun writeStateLocked(file: AtomicFile, state: State) {
        val output = file.startWrite()
        try {
            output.write("${state.threshold}\n${state.count}\n".toByteArray(StandardCharsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    private inline fun <T> withFileLock(context: Context, action: (AtomicFile) -> T): T {
        val appContext = context.applicationContext
        return processLock.withLock {
            val directory = File(appContext.filesDir, STATE_DIRECTORY)
            if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory) {
                throw IllegalStateException("无法创建 AI 分析计数文件目录")
            }
            val lockFile = File(directory, LOCK_FILE_NAME)
            RandomAccessFile(lockFile, "rw").use { lockHandle ->
                val fileLock = lockHandle.channel.lock()
                try {
                    action(AtomicFile(File(directory, STATE_FILE_NAME)))
                } finally {
                    fileLock.release()
                }
            }
        }
    }

    private fun notifyStateChanged(context: Context) {
        context.applicationContext.sendBroadcast(
            Intent(ACTION_STATE_CHANGED).setPackage(context.packageName)
        )
    }
}
