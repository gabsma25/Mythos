package com.mythos.reader.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mythos.reader.model.Book

@Database(entities = [Book::class], version = 1)
abstract class MythosDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        fun create(context: Context): MythosDatabase =
            Room.databaseBuilder(context, MythosDatabase::class.java, "mythos.db").build()
    }
}
