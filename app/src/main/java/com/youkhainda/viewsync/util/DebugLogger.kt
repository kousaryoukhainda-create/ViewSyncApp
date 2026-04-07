package com.youkhainda.viewsync.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Centralized debug logging utility for ViewSyncApp
 * 
 * Features:
 * - Logs to both Android Logcat and in-memory buffer
 * - In-memory buffer can be displayed in-app via DebugOverlay
 * - Timestamped entries with severity levels
 * - Configurable max log size to prevent memory issues
 */
object DebugLogger {
    
    private const val TAG = "ViewSyncDebug"
    private const val MAX_LOG_SIZE = 500 // Maximum number of log entries to keep
    
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    // In-memory log buffer for in-app display
    private val _logBuffer = MutableStateFlow<List<LogEntry>>(emptyList())
    val logBuffer: StateFlow<List<LogEntry>> = _logBuffer.asStateFlow()
    
    // Enable/disable debug logging
    var isEnabled: Boolean = true
    
    data class LogEntry(
        val timestamp: String,
        val level: LogLevel,
        val tag: String,
        val message: String,
    ) {
        override fun toString(): String = "[$timestamp] [$level] [$tag] $message"
    }
    
    enum class LogLevel {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }
    
    /**
     * Log a verbose message
     */
    fun v(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.VERBOSE, tag, message, throwable)
    }
    
    /**
     * Log a debug message
     */
    fun d(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.DEBUG, tag, message, throwable)
    }
    
    /**
     * Log an info message
     */
    fun i(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.INFO, tag, message, throwable)
    }
    
    /**
     * Log a warning message
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.WARN, tag, message, throwable)
    }
    
    /**
     * Log an error message
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, tag, message, throwable)
    }
    
    /**
     * Log a step in a process with automatic success/failure tracking
     */
    fun step(tag: String, stepName: String, stepNumber: Int, totalSteps: Int): String {
        val message = "Step $stepNumber/$totalSteps: $stepName"
        i(tag, message)
        return message
    }
    
    /**
     * Log successful completion of a step
     */
    fun stepSuccess(tag: String, stepName: String, detail: String = "") {
        val message = if (detail.isNotEmpty()) "✓ $stepName - $detail" else "✓ $stepName"
        i(tag, message)
    }
    
    /**
     * Log failure of a step
     */
    fun stepFailed(tag: String, stepName: String, error: String, throwable: Throwable? = null) {
        val message = "✗ $stepName - $error"
        e(tag, message, throwable)
    }
    
    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        if (!isEnabled) return
        
        val timestamp = dateFormat.format(Date())
        val fullMessage = if (throwable != null) {
            "$message | ${throwable.message ?: throwable.toString()}"
        } else {
            message
        }
        
        // Log to Android Logcat
        when (level) {
            LogLevel.VERBOSE -> Log.v(TAG, "[$tag] $fullMessage")
            LogLevel.DEBUG -> Log.d(TAG, "[$tag] $fullMessage")
            LogLevel.INFO -> Log.i(TAG, "[$tag] $fullMessage")
            LogLevel.WARN -> Log.w(TAG, "[$tag] $fullMessage")
            LogLevel.ERROR -> Log.e(TAG, "[$tag] $fullMessage", throwable)
        }
        
        // Add to in-memory buffer
        val entry = LogEntry(timestamp, level, tag, fullMessage)
        val currentBuffer = _logBuffer.value.toMutableList()
        currentBuffer.add(entry)
        
        // Trim buffer if it exceeds max size
        if (currentBuffer.size > MAX_LOG_SIZE) {
            currentBuffer.removeAt(0)
        }
        
        _logBuffer.value = currentBuffer
    }
    
    /**
     * Clear the log buffer
     */
    fun clear() {
        _logBuffer.value = emptyList()
    }
    
    /**
     * Export logs as a string for sharing/saving
     */
    fun exportLogs(): String {
        return _logBuffer.value.joinToString("\n") { it.toString() }
    }
}
