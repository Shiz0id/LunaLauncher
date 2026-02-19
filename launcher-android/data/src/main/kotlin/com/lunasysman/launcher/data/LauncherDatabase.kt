package com.lunasysman.launcher.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LaunchPointEntity::class,
        DockEntryEntity::class,
        HomeSlotEntity::class,
        HomeIconEntity::class,
        JustTypeProviderEntity::class,
        DeckCardEntity::class,
        DeckWidgetEntity::class,
    ],
    version = 11,
    exportSchema = false,
)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun launchPointDao(): LaunchPointDao
    abstract fun dockDao(): DockDao
    abstract fun homeSlotsDao(): HomeSlotsDao
    abstract fun homeIconPositionsDao(): HomeIconPositionsDao
    abstract fun justTypeProviderDao(): JustTypeProviderDao
    abstract fun deckDao(): DeckDao

    companion object {
        fun create(context: Context): LauncherDatabase =
            Room.databaseBuilder(context, LauncherDatabase::class.java, "launcher.db")
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .addMigrations(MIGRATION_4_5)
                .addMigrations(MIGRATION_5_6)
                .addMigrations(MIGRATION_6_7)
                .addMigrations(MIGRATION_7_8)
                .addMigrations(MIGRATION_8_9)
                .addMigrations(MIGRATION_9_10)
                .addMigrations(MIGRATION_10_11)
                .build()

        private val MIGRATION_1_2: Migration =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS launch_points_new (
                          id TEXT NOT NULL PRIMARY KEY,
                          type TEXT NOT NULL,
                          displayTitle TEXT NOT NULL,
                          sortKey TEXT NOT NULL,
                          iconKey TEXT,
                          installSource TEXT,
                          badges INTEGER NOT NULL,
                          androidPackageName TEXT,
                          androidActivityName TEXT,
                          lastLaunchedAtEpochMs INTEGER,
                          pinned INTEGER NOT NULL,
                          hidden INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )

                    db.execSQL(
                        """
                        INSERT INTO launch_points_new (
                          id, type, displayTitle, sortKey, iconKey, installSource, badges,
                          androidPackageName, androidActivityName, lastLaunchedAtEpochMs, pinned, hidden
                        )
                        SELECT
                          id,
                          type,
                          COALESCE(title, ''),
                          LOWER(COALESCE(title, '')),
                          iconKey,
                          NULL,
                          0,
                          androidPackageName,
                          androidActivityName,
                          lastLaunchedAtEpochMs,
                          pinned,
                          hidden
                        FROM launch_points
                        """.trimIndent(),
                    )

                    db.execSQL("DROP TABLE launch_points")
                    db.execSQL("ALTER TABLE launch_points_new RENAME TO launch_points")
                }
            }

        private val MIGRATION_2_3: Migration =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS launch_points_new (
                          id TEXT NOT NULL PRIMARY KEY,
                          type TEXT NOT NULL,
                          title TEXT NOT NULL,
                          sortKey TEXT NOT NULL,
                          iconKey TEXT,
                          installSource TEXT,
                          badges INTEGER NOT NULL,
                          androidPackageName TEXT,
                          androidActivityName TEXT,
                          lastLaunchedAtEpochMs INTEGER,
                          pinned INTEGER NOT NULL,
                          pinnedRank INTEGER,
                          hidden INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )

                    db.execSQL(
                        """
                        INSERT INTO launch_points_new (
                          id, type, title, sortKey, iconKey, installSource, badges,
                          androidPackageName, androidActivityName, lastLaunchedAtEpochMs, pinned, pinnedRank, hidden
                        )
                        SELECT
                          id,
                          type,
                          COALESCE(displayTitle, ''),
                          COALESCE(sortKey, LOWER(COALESCE(displayTitle, ''))),
                          iconKey,
                          installSource,
                          COALESCE(badges, 0),
                          androidPackageName,
                          androidActivityName,
                          lastLaunchedAtEpochMs,
                          pinned,
                          CASE WHEN pinned = 1 THEN COALESCE(lastLaunchedAtEpochMs, 0) ELSE NULL END,
                          hidden
                        FROM launch_points
                        """.trimIndent(),
                    )

                    db.execSQL("DROP TABLE launch_points")
                    db.execSQL("ALTER TABLE launch_points_new RENAME TO launch_points")
                }
            }

        private val MIGRATION_3_4: Migration =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_launch_points_hidden_pinned_lastLaunchedAtEpochMs ON launch_points(hidden, pinned, lastLaunchedAtEpochMs)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_launch_points_type_hidden ON launch_points(type, hidden)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_launch_points_sortKey ON launch_points(sortKey)")
                }
            }

        private val MIGRATION_4_5: Migration =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS dock_entries (
                          position INTEGER NOT NULL PRIMARY KEY,
                          launchPointId TEXT NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_dock_entries_launchPointId ON dock_entries(launchPointId)")

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS home_slots (
                          slotIndex INTEGER NOT NULL PRIMARY KEY,
                          launchPointId TEXT NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_home_slots_launchPointId ON home_slots(launchPointId)")
                }
            }

        private val MIGRATION_5_6: Migration =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS just_type_provider (
                          id TEXT NOT NULL PRIMARY KEY,
                          category TEXT NOT NULL,
                          displayName TEXT,
                          enabled INTEGER NOT NULL,
                          orderIndex INTEGER NOT NULL,
                          version INTEGER NOT NULL,
                          source TEXT NOT NULL,
                          urlTemplate TEXT,
                          suggestUrlTemplate TEXT
                        )
                        """.trimIndent(),
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_just_type_provider_category_enabled_orderIndex ON just_type_provider(category, enabled, orderIndex)")
                }
            }

        private val MIGRATION_6_7: Migration =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE just_type_provider ADD COLUMN canPromotePrimaryResult INTEGER NOT NULL DEFAULT 0")
                }
            }

        private val MIGRATION_7_8: Migration =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS home_widgets (
                          appWidgetId INTEGER NOT NULL PRIMARY KEY,
                          provider TEXT NOT NULL,
                          cellX INTEGER NOT NULL,
                          cellY INTEGER NOT NULL,
                          spanX INTEGER NOT NULL,
                          spanY INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                }
            }

        private val MIGRATION_8_9: Migration =
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS home_icon_positions (
                          launchPointId TEXT NOT NULL PRIMARY KEY,
                          xNorm REAL NOT NULL,
                          yNorm REAL NOT NULL,
                          rotationDeg REAL NOT NULL,
                          zIndex INTEGER NOT NULL,
                          updatedAtEpochMs INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_home_icon_positions_updatedAtEpochMs ON home_icon_positions(updatedAtEpochMs)")

                    // Migrate existing grid placements into normalized positions (0..1) so they scale to any screen.
                    // slotIndex -> (col,row) in the existing 7x9 layout.
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO home_icon_positions (
                          launchPointId, xNorm, yNorm, rotationDeg, zIndex, updatedAtEpochMs
                        )
                        SELECT
                          launchPointId,
                          (CAST((slotIndex % 7) AS REAL) / 6.0),
                          (CAST((slotIndex / 7) AS REAL) / 8.0),
                          0.0,
                          slotIndex,
                          0
                        FROM home_slots
                        """.trimIndent(),
                    )
                }
            }

        private val MIGRATION_9_10: Migration =
            object : Migration(9, 10) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("DROP TABLE IF EXISTS home_widgets")
                }
            }

        private val MIGRATION_10_11: Migration =
            object : Migration(10, 11) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS deck_cards (
                          cardId INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                          position INTEGER NOT NULL,
                          starred INTEGER NOT NULL DEFAULT 0,
                          createdAtEpochMs INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS deck_widgets (
                          appWidgetId INTEGER NOT NULL PRIMARY KEY,
                          cardId INTEGER NOT NULL,
                          provider TEXT NOT NULL,
                          orderIndex INTEGER NOT NULL,
                          widthDp INTEGER NOT NULL,
                          heightDp INTEGER NOT NULL,
                          FOREIGN KEY (cardId) REFERENCES deck_cards(cardId) ON DELETE CASCADE
                        )
                        """.trimIndent(),
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_deck_widgets_cardId ON deck_widgets(cardId)")
                }
            }
    }
}
