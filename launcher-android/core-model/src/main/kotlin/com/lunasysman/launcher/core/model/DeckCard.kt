package com.lunasysman.launcher.core.model

/**
 * Represents a single card in the Widget Deck.
 *
 * Each card contains one or more widgets arranged in a flexible layout.
 * Cards are ordered by [position] in the horizontal carousel and can be
 * starred as the "deck home" card.
 *
 * @property cardId Auto-generated unique identifier.
 * @property position Ordering index in the deck (0-based, left to right).
 * @property starred Whether this card is the "deck home" starting card.
 * @property createdAtEpochMs Timestamp of card creation for default ordering.
 */
data class DeckCard(
    val cardId: Long,
    val position: Int,
    val starred: Boolean,
    val createdAtEpochMs: Long,
)
