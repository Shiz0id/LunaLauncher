package com.lunasysman.launcher.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deck_cards")
data class DeckCardEntity(
    @PrimaryKey(autoGenerate = true) val cardId: Long = 0,
    val position: Int,
    val starred: Boolean = false,
    val createdAtEpochMs: Long,
)
