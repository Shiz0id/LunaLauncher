package com.lunasysman.launcher.data

import com.lunasysman.launcher.core.model.DeckCard
import com.lunasysman.launcher.core.model.DeckWidget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Repository for the Widget Deck feature.
 *
 * Manages deck cards and their widgets, providing reactive streams
 * and suspend functions for all CRUD operations.
 */
class DeckRepository(
    private val deckDao: DeckDao,
) {
    /** Observe all cards with their widgets, ordered by position. */
    fun observeCardsWithWidgets(): Flow<List<Pair<DeckCard, List<DeckWidget>>>> =
        combine(
            deckDao.observeCards().map { list -> list.map { it.toModel() } },
            deckDao.observeAllWidgets().map { list -> list.map { it.toModel() } },
        ) { cards, widgets ->
            val widgetsByCard = widgets.groupBy { it.cardId }
            cards.map { card ->
                card to (widgetsByCard[card.cardId] ?: emptyList())
            }
        }

    /** Observe just cards (no widgets). */
    fun observeCards(): Flow<List<DeckCard>> =
        deckDao.observeCards().map { list -> list.map { it.toModel() } }

    /** Get all cards snapshot. */
    suspend fun getCards(): List<DeckCard> =
        deckDao.getCards().map { it.toModel() }

    /** Get widgets for a specific card. */
    suspend fun getWidgetsForCard(cardId: Long): List<DeckWidget> =
        deckDao.getWidgetsForCard(cardId).map { it.toModel() }

    /** Get all widgets across all cards. */
    suspend fun getAllWidgets(): List<DeckWidget> =
        deckDao.getAllWidgets().map { it.toModel() }

    /** Create a new card at the end of the deck. Returns the new card ID. */
    suspend fun createCard(): Long {
        val count = deckDao.cardCount()
        if (count >= MAX_CARDS) return -1
        return deckDao.upsertCard(
            DeckCardEntity(
                position = count,
                starred = false,
                createdAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    /** Delete a card and all its widgets (CASCADE). */
    suspend fun deleteCard(cardId: Long) {
        deckDao.deleteCard(cardId)
        // Re-normalize positions after deletion.
        val remaining = deckDao.getCards()
        remaining.forEachIndexed { index, card ->
            if (card.position != index) {
                deckDao.updatePosition(card.cardId, index)
            }
        }
    }

    /** Star a card as the "deck home" card (unstars any previous). */
    suspend fun starCard(cardId: Long) {
        deckDao.unstarAll()
        deckDao.starCard(cardId)
    }

    /** Unstar all cards. */
    suspend fun unstarAll() {
        deckDao.unstarAll()
    }

    /** Add a widget to a card. */
    suspend fun addWidget(
        appWidgetId: Int,
        cardId: Long,
        provider: String,
        widthDp: Int,
        heightDp: Int,
    ) {
        val existingCount = deckDao.widgetCountForCard(cardId)
        deckDao.upsertWidget(
            DeckWidgetEntity(
                appWidgetId = appWidgetId,
                cardId = cardId,
                provider = provider,
                orderIndex = existingCount,
                widthDp = widthDp,
                heightDp = heightDp,
            ),
        )
    }

    /** Remove a widget. If it was the last on its card, delete the card too. */
    suspend fun removeWidget(appWidgetId: Int) {
        // Find the card this widget belongs to before deleting.
        val allWidgets = deckDao.getAllWidgets()
        val widget = allWidgets.find { it.appWidgetId == appWidgetId } ?: return
        val cardId = widget.cardId

        deckDao.deleteWidget(appWidgetId)

        // If the card is now empty, delete it.
        val remaining = deckDao.widgetCountForCard(cardId)
        if (remaining == 0) {
            deleteCard(cardId)
        }
    }

    /** How many cards exist. */
    suspend fun cardCount(): Int = deckDao.cardCount()

    companion object {
        /** Maximum number of cards in the deck. */
        const val MAX_CARDS = 7
    }
}

private fun DeckCardEntity.toModel(): DeckCard =
    DeckCard(
        cardId = cardId,
        position = position,
        starred = starred,
        createdAtEpochMs = createdAtEpochMs,
    )

private fun DeckWidgetEntity.toModel(): DeckWidget =
    DeckWidget(
        appWidgetId = appWidgetId,
        cardId = cardId,
        provider = provider,
        orderIndex = orderIndex,
        widthDp = widthDp,
        heightDp = heightDp,
    )
