package ro.punguta.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ro.punguta.app.data.db.AppDatabase

/**
 * Validates the exported schemas. When a new version is added, extend this test to create the
 * previous version, run the migration with validation, and check the data survived.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun `version 1 schema can be created and seeded`() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            AppDatabase.seedDefaultCategories(db)
            db.query("SELECT COUNT(*) FROM categories").use { c ->
                c.moveToFirst()
                assertEquals(10, c.getInt(0))
            }
        }
    }

    @Test
    fun `migrations cover every version up to the current one`() {
        val covered = AppDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }.toSet()
        for (v in 1 until AppDatabase.VERSION) {
            assertTrue("Missing migration $v -> ${v + 1}", (v to v + 1) in covered)
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
