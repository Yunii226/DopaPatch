package com.example.dopapatch.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [TaskEntity::class, CompletionEntity::class, DailyNoteEntity::class, NoteImageEntity::class],
    version = 1,
    exportSchema = false, // personal app; no migration history to preserve yet.
)
@TypeConverters(Converters::class)
abstract class DopaPatchDb : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun completionDao(): CompletionDao
    abstract fun dailyNoteDao(): DailyNoteDao
    abstract fun noteImageDao(): NoteImageDao

    companion object {
        fun build(context: Context): DopaPatchDb =
            Room.databaseBuilder(context, DopaPatchDb::class.java, "dopapatch.db").build()
    }
}
