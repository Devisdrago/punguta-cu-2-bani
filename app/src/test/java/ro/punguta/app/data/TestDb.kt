package ro.punguta.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ro.punguta.app.data.db.AppDatabase

/** In-memory database with the same migrations and seed callback as production. */
fun testDb(): AppDatabase =
    AppDatabase.configure(
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries(),
    ).build()
