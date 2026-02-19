package com.lunasysman.launcher.deck

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lunasysman.launcher.core.model.DeckCard
import com.lunasysman.launcher.core.model.DeckWidget
import com.lunasysman.launcher.data.DeckRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Widget Deck feature.
 *
 * Manages deck state including cards, widgets, current page position,
 * and coordinates with [DeckWidgetHost] and [DeckBitmapCache].
 */
class DeckViewModel(
    private val repository: DeckRepository,
    val widgetHost: DeckWidgetHost,
    val bitmapCache: DeckBitmapCache,
) : ViewModel() {

    /** All cards with their widgets, observed reactively. */
    val cardsWithWidgets: StateFlow<List<Pair<DeckCard, List<DeckWidget>>>> =
        repository.observeCardsWithWidgets()
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Currently focused card index in the pager. */
    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage

    /** Last viewed card ID for reopening to same position. */
    private val _lastViewedCardId = MutableStateFlow<Long?>(null)

    fun setCurrentPage(page: Int) {
        _currentPage.value = page
        // Track last viewed card.
        val cards = cardsWithWidgets.value
        if (page in cards.indices) {
            _lastViewedCardId.value = cards[page].first.cardId
        }
    }

    /**
     * Determine the initial page to show when the deck opens.
     * Priority: starred card → last viewed card → 0
     */
    fun resolveInitialPage(): Int {
        val cards = cardsWithWidgets.value
        if (cards.isEmpty()) return 0

        // 1. Starred card
        val starredIdx = cards.indexOfFirst { it.first.starred }
        if (starredIdx >= 0) return starredIdx

        // 2. Last viewed card
        val lastId = _lastViewedCardId.value
        if (lastId != null) {
            val lastIdx = cards.indexOfFirst { it.first.cardId == lastId }
            if (lastIdx >= 0) return lastIdx
        }

        return 0
    }

    /** Create a new empty card. Returns the card ID or -1 on failure. */
    fun createCard(onResult: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.createCard()
            Log.d(TAG, "Created card: id=$id")
            onResult(id)
        }
    }

    /** Delete a card and clean up its widget IDs. */
    fun deleteCard(cardId: Long) {
        viewModelScope.launch {
            val widgets = repository.getWidgetsForCard(cardId)
            widgets.forEach { w ->
                widgetHost.deleteWidgetId(w.appWidgetId)
            }
            bitmapCache.evict(cardId)
            repository.deleteCard(cardId)
            Log.d(TAG, "Deleted card $cardId with ${widgets.size} widgets")
        }
    }

    /** Add a widget to a card. */
    fun addWidget(appWidgetId: Int, cardId: Long, provider: String, widthDp: Int, heightDp: Int) {
        viewModelScope.launch {
            repository.addWidget(appWidgetId, cardId, provider, widthDp, heightDp)
            Log.d(TAG, "Added widget $appWidgetId to card $cardId ($widthDp x $heightDp dp)")
        }
    }

    /** Remove a widget. Card is auto-deleted if it becomes empty. */
    fun removeWidget(appWidgetId: Int) {
        viewModelScope.launch {
            widgetHost.deleteWidgetId(appWidgetId)
            repository.removeWidget(appWidgetId)
            Log.d(TAG, "Removed widget $appWidgetId")
        }
    }

    /** Star/unstar a card. */
    fun toggleStar(cardId: Long, starred: Boolean) {
        viewModelScope.launch {
            if (starred) {
                repository.starCard(cardId)
            } else {
                repository.unstarAll()
            }
            Log.d(TAG, "Card $cardId starred=$starred")
        }
    }

    /**
     * Capture a bitmap snapshot of the given card's live widgets and cache it.
     * Call this when a card leaves the foreground (page swipe).
     */
    fun captureCard(cardId: Long) {
        val cards = cardsWithWidgets.value
        val widgets = cards.find { it.first.cardId == cardId }?.second ?: return
        if (widgets.isEmpty()) return

        // Collect live views that have a valid size.
        val views = widgets.mapNotNull { w ->
            widgetHost.getLiveView(w.appWidgetId)?.takeIf { it.width > 0 && it.height > 0 }
        }
        if (views.isEmpty()) {
            Log.w(TAG, "No live views to capture for card $cardId")
            return
        }

        try {
            val totalWidth = views.maxOf { it.width }
            val totalHeight = views.sumOf { it.height }
            val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            var yOffset = 0f
            for (view in views) {
                canvas.save()
                canvas.translate(0f, yOffset)
                view.draw(canvas)
                canvas.restore()
                yOffset += view.height
            }
            bitmapCache.put(cardId, bitmap)
            Log.d(TAG, "Captured bitmap for card $cardId: ${bitmap.width}x${bitmap.height}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture card $cardId", e)
        }
    }

    /** Called when deck is opened. */
    fun onDeckOpened() {
        widgetHost.startListening()
        Log.d(TAG, "Deck opened")
    }

    /** Called when deck is closed. */
    fun onDeckClosed() {
        widgetHost.stopListening()
        bitmapCache.clear()
        Log.d(TAG, "Deck closed")
    }

    override fun onCleared() {
        super.onCleared()
        widgetHost.stopListening()
        bitmapCache.clear()
    }

    companion object {
        private const val TAG = "DeckViewModel"

        fun factory(
            repository: DeckRepository,
            widgetHost: DeckWidgetHost,
            bitmapCache: DeckBitmapCache,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return DeckViewModel(repository, widgetHost, bitmapCache) as T
                }
            }
    }
}
