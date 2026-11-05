package com.jm3.app.miuix.data

import android.content.Context
import androidx.room.Room
import com.jm3.app.miuix.data.history.HistoryDb
import com.jm3.app.miuix.data.history.MIGRATION_1_2
import com.jm3.app.miuix.data.history.RoomDownloadLedgerStore
import com.jm3.app.miuix.data.history.RoomHistoryRepository
import com.jm3.core.download.DownloadLedgerStore
import com.jm3.core.history.HistoryRepository

/**
 * 本地库域：一个 Room 库（jm3.db）承载阅读历史与下载台账。
 *
 * 两本台账同库是刻意的（v2 起含 downloads 表），所以库实例只能有一个：各自开库会拿到两份 WAL 连接。
 */
class LibraryDomain(context: Context) {

    private val appContext: Context = context.applicationContext

    /** 历史与下载共用一个 Room 库（jm3.db），v2 起含 downloads 表。 */
    private val db: HistoryDb by lazy {
        Room.databaseBuilder(appContext, HistoryDb::class.java, "jm3.db")
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    val history: HistoryRepository by lazy { RoomHistoryRepository(db.historyDao()) }

    internal val ledgerStore: DownloadLedgerStore by lazy { RoomDownloadLedgerStore(db.downloadDao()) }
}
