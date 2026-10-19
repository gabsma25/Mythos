package com.mythos.reader.data.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mythos.reader.model.Book
import com.mythos.reader.model.ExcludedSource

@Database(
    entities = [Book::class, ExcludedSource::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class MythosDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        fun create(context: Context): MythosDatabase =
            Room.databaseBuilder(context, MythosDatabase::class.java, "mythos.db").build()
    }
}
