package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val TAG = "Migration_32_33"

/**
 * v32 → v33 (后端会话隔离).
 *
 * v33 adds one column to `conversationentity`:
 * - `backend_session_path` — reasonix 等「服务端管会话」后端的 conversationId↔session 映射，
 *   用于会话隔离（同一对话多轮复用同一后端会话，不同对话互不串扰）。
 *   默认 "" 表示无映射。
 *
 * Additive only — no data migration. Hand-written (same pattern as Migration_31_32)
 * because the 32→33 auto-migration would need schema 32.json which was never exported.
 */
val Migration_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 32 to 33 (conversationentity add backend_session_path)")
        db.beginTransaction()
        try {
            db.execSQL("ALTER TABLE `conversationentity` ADD COLUMN `backend_session_path` TEXT NOT NULL DEFAULT ''")
            db.setTransactionSuccessful()
            Log.i(TAG, "migrate: migrate from 32 to 33 success")
        } finally {
            db.endTransaction()
        }
    }
}
