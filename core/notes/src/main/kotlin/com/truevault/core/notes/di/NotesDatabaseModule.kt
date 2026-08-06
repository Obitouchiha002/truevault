package com.truevault.core.notes.di

import android.content.Context
import androidx.room.Room
import com.truevault.core.notes.db.NoteDao
import com.truevault.core.notes.db.NOTES_MIGRATIONS
import com.truevault.core.notes.db.NotesDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NotesDatabaseModule {

    /**
     * The notes database, on its own file and its own connection.
     *
     * Nothing in this graph can see the vault database. That is the separation: not a rule someone
     * has to remember, but two objects that have no way to reach each other.
     *
     * Not encrypted, and the UI says so. Notes are the visible half of the app; a user who wants
     * something protected moves it into the vault, which is a different act with a different
     * guarantee.
     */
    @Provides
    @Singleton
    fun provideNotesDatabase(@ApplicationContext context: Context): NotesDatabase =
        Room.databaseBuilder(context, NotesDatabase::class.java, NotesDatabase.NAME)
            .addMigrations(*NOTES_MIGRATIONS)
            // No fallbackToDestructiveMigration. A migration that wipes notes on upgrade is a data
            // loss bug that only shows up in production, on other people's devices.
            .build()

    @Provides
    fun provideNoteDao(database: NotesDatabase): NoteDao = database.noteDao()
}
