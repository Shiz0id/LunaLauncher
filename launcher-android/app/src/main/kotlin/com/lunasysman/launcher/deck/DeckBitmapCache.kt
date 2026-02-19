package com.lunasysman.launcher.deck

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.view.View

/**
 * Caches bitmap snapshots of background deck cards.
 *
 * When a card leaves the foreground, its live widget views are captured as a bitmap.
 * Background cards display this static bitmap instead of running live widgets,
 * eliminating CPU/battery drain from inactive widgets.
 *
 * Memory budget: max 7 cards × typical widget bitmap ≈ manageable footprint.
 */
class DeckBitmapCache {

    private val cache = mutableMapOf<Long, Bitmap>()

    /**
     * Capture a bitmap from a live Android View (the widget host view).
     *
     * @param cardId The card this bitmap belongs to.
     * @param view The live View to capture.
     * @return The captured bitmap, or null if capture fails.
     */
    fun capture(cardId: Long, view: View): Bitmap? {
        if (view.width <= 0 || view.height <= 0) {
            Log.w(TAG, "Cannot capture bitmap for card $cardId: view has zero size (${view.width}x${view.height})")
            return null
        }
        return try {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)

            // Recycle old bitmap if exists.
            cache[cardId]?.recycle()
            cache[cardId] = bitmap

            Log.d(TAG, "Captured bitmap for card $cardId: ${bitmap.width}x${bitmap.height} (${bitmap.byteCount / 1024}KB)")
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture bitmap for card $cardId", e)
            null
        }
    }

    /**
     * Store an externally-created bitmap for a card.
     */
    fun put(cardId: Long, bitmap: Bitmap) {
        cache[cardId]?.recycle()
        cache[cardId] = bitmap
    }

    /**
     * Retrieve the cached bitmap for a card.
     * Returns null if no bitmap is cached (card needs live rendering).
     */
    fun get(cardId: Long): Bitmap? = cache[cardId]

    /**
     * Check if a bitmap exists for the given card.
     */
    fun has(cardId: Long): Boolean = cache.containsKey(cardId)

    /**
     * Remove and recycle the bitmap for a specific card.
     */
    fun evict(cardId: Long) {
        cache.remove(cardId)?.recycle()
        Log.d(TAG, "Evicted bitmap for card $cardId")
    }

    /**
     * Clear all cached bitmaps and recycle memory.
     * Call when the deck is fully closed or on memory trim.
     */
    fun clear() {
        cache.values.forEach { it.recycle() }
        cache.clear()
        Log.d(TAG, "Cleared all cached bitmaps")
    }

    /**
     * Number of cached bitmaps.
     */
    val size: Int get() = cache.size

    companion object {
        private const val TAG = "DeckBitmapCache"
    }
}
