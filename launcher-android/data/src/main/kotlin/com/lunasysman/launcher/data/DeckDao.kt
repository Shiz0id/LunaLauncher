package com.lunasysman.launcher.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {

    // ── Cards ──────────────────────────────────────────────────

    @Query("SELECT * FROM deck_cards ORDER BY position ASC")
    fun observeCards(): Flow<List<DeckCardEntity>>

    @Query("SELECT * FROM deck_cards ORDER BY position ASC")
    suspend fun getCards(): List<DeckCardEntity>

    @Query("SELECT * FROM deck_cards WHERE cardId = :cardId")
    suspend fun getCard(cardId: Long): DeckCardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCard(card: DeckCardEntity): Long

    @Query("DELETE FROM deck_cards WHERE cardId = :cardId")
    suspend fun deleteCard(cardId: Long)

    @Query("SELECT COUNT(*) FROM deck_cards")
    suspend fun cardCount(): Int

    @Query("UPDATE deck_cards SET starred = 0")
    suspend fun unstarAll()

    @Query("UPDATE deck_cards SET starred = 1 WHERE cardId = :cardId")
    suspend fun starCard(cardId: Long)

    @Query("UPDATE deck_cards SET position = :position WHERE cardId = :cardId")
    suspend fun updatePosition(cardId: Long, position: Int)

    // ── Widgets ────────────────────────────────────────────────

    @Query("SELECT * FROM deck_widgets WHERE cardId = :cardId ORDER BY orderIndex ASC")
    fun observeWidgetsForCard(cardId: Long): Flow<List<DeckWidgetEntity>>

    @Query("SELECT * FROM deck_widgets ORDER BY orderIndex ASC")
    fun observeAllWidgets(): Flow<List<DeckWidgetEntity>>

    @Query("SELECT * FROM deck_widgets WHERE cardId = :cardId ORDER BY orderIndex ASC")
    suspend fun getWidgetsForCard(cardId: Long): List<DeckWidgetEntity>

    @Query("SELECT * FROM deck_widgets ORDER BY orderIndex ASC")
    suspend fun getAllWidgets(): List<DeckWidgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWidget(widget: DeckWidgetEntity)

    @Query("DELETE FROM deck_widgets WHERE appWidgetId = :appWidgetId")
    suspend fun deleteWidget(appWidgetId: Int)

    @Query("SELECT COUNT(*) FROM deck_widgets WHERE cardId = :cardId")
    suspend fun widgetCountForCard(cardId: Long): Int
}
