package com.lunasysman.launcher.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "deck_widgets",
    foreignKeys = [
        ForeignKey(
            entity = DeckCardEntity::class,
            parentColumns = ["cardId"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["cardId"]),
    ],
)
data class DeckWidgetEntity(
    @PrimaryKey val appWidgetId: Int,
    val cardId: Long,
    val provider: String,
    val orderIndex: Int,
    val widthDp: Int,
    val heightDp: Int,
)
