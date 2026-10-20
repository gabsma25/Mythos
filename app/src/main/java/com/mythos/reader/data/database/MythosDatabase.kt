package com.mythos.reader.data.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mythos.reader.model.Book
import com.mythos.reader.model.ExcludedSource
import com.mythos.reader.model.Highlight

@Database(
    entities = [Book::class, ExcludedSource::class, Highlight::class],
    version = 3,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class MythosDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun highlightDao(): HighlightDao

    companion object {
        fun create(context: Context): MythosDatabase =
            Room.databaseBuilder(context, MythosDatabase::class.java, "mythos.db").build()
    }
}
