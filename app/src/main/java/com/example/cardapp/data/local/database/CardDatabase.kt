package com.example.cardapp.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.cardapp.data.local.dao.BatchCardDao
import com.example.cardapp.data.local.dao.VerifiedCardDao
import com.example.cardapp.data.local.entity.BatchCard
import com.example.cardapp.data.local.entity.VerifiedCard

@Database(
    entities = [BatchCard::class, VerifiedCard::class],
    version = 1,
    exportSchema = false
)
abstract class CardDatabase : RoomDatabase() {
    abstract fun batchCardDao(): BatchCardDao
    abstract fun verifiedCardDao(): VerifiedCardDao

    companion object {
        @Volatile
        private var INSTANCE: CardDatabase? = null

        fun getDatabase(context: Context): CardDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CardDatabase::class.java,
                    "card_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}