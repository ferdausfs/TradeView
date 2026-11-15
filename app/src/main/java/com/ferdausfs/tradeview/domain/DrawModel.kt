package com.ferdausfs.tradeview.domain

import com.ferdausfs.tradeview.domain.model.Anchor
import com.ferdausfs.tradeview.domain.model.DrawKind
import com.ferdausfs.tradeview.domain.model.Drawing

/**
 * Pure drawing state machine: tap points become drawings, and hit-testing decides what the
 * cursor tool selects or deletes. Kept free of Android/Compose so it is unit-testable.
 */
class DrawModel {

    private var items = listOf<Drawing>()

    /** Anchors collected for the shape currently being drawn (empty when idle). */
    var pending: List<Anchor> = emptyList()
        private set

    var selectedId: String? = null
        private set

    fun all(): List<Drawing> = items.map { it.copy(selected = it.id == selectedId) }

    fun replace(next: List<Drawing>) {
        items = next
        pending = emptyList()
        selectedId = null
    }

    fun select(id: String?) {
        selectedId = id
    }

    fun hasTool(tool: DrawKind): Boolean =
        tool != DrawKind.Cursor && tool != DrawKind.Crosshair

    /**
     * Adds an anchor for the active tool. Returns "created" when the shape is finished,
     * "armed" when it still needs another point, "idle" when no draw tool is active.
     */
    fun addPoint(tool: DrawKind, anchor: Anchor): String {
        if (!hasTool(tool)) return "idle"
        val need = if (tool == DrawKind.Horizontal) 1 else 2
        pending = pending + anchor
        if (pending.size < need) return "armed"
        items = items + Drawing(
            id = "${tool.name}-${System.nanoTime()}",
            kind = tool,
            points = pending,
        )
        pending = emptyList()
        return "created"
    }

    fun cancelPending() {
        pending = emptyList()
    }

    fun removeSelected() {
        val id = selectedId ?: return
        items = items.filterNot { it.id == id }
        selectedId = null
    }

    fun clearAll() {
        items = emptyList()
        pending = emptyList()
        selectedId = null
    }

    /**
     * Selects the drawing nearest to [anchor]. [priceSpan] is the visible price range so the
     * tolerance scales with zoom — 8% of the view, roughly a finger-width on a phone.
     */
    fun hitTest(anchor: Anchor, priceSpan: Double): String? {
        if (items.isEmpty()) return null
        val tolerance = (priceSpan * 0.08).coerceAtLeast(1e-6)
        var best: String? = null
        var bestDistance = Double.MAX_VALUE
        for (d in items) {
            val distance = when {
                d.kind == DrawKind.Horizontal -> kotlin.math.abs(d.points.first().price - anchor.price)
                d.points.size >= 2 -> distanceToSegment(anchor, d.points[0], d.points[1])
                else -> Double.MAX_VALUE
            }
            if (distance <= tolerance && distance < bestDistance) {
                bestDistance = distance
                best = d.id
            }
        }
        selectedId = best
        return best
    }

    private fun distanceToSegment(p: Anchor, a: Anchor, b: Anchor): Double {
        // Times and prices live on wildly different scales, so project the tap onto the line's
        // price at the tapped time and measure vertically.
        val span = (b.time - a.time).coerceAtLeast(1L)
        val slope = (b.price - a.price) / span
        val projected = a.price + (p.time - a.time) * slope
        return kotlin.math.abs(projected - p.price)
    }
}
