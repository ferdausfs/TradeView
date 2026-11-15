package com.ferdausfs.tradeview.domain.model

import kotlinx.serialization.Serializable

/**
 * Chart drawings (spec P6). Anchors are stored in *data* space (time + price), never pixels, so a
 * drawing survives pan, zoom, timeframe changes and replay. chart.js re-projects them on render.
 */
@Serializable
data class Anchor(val time: Long, val price: Double)

@Serializable
enum class DrawKind(val label: String) {
    Cursor("Cursor"),
    Crosshair("Crosshair"),
    TrendLine("Trend line"),
    Horizontal("Horizontal line"),
    Rectangle("Box"),
}

@Serializable
data class Drawing(
    val id: String,
    val kind: DrawKind,
    val points: List<Anchor>,
    val color: String = "#2962FF",
    val width: Int = 2,
    val dashed: Boolean = false,
    val filled: Boolean = false,
    val selected: Boolean = false,
) {
    /** Horizontal lines need one anchor; everything else two. */
    val isComplete: Boolean get() = points.size >= if (kind == DrawKind.Horizontal) 1 else 2
}

/** Serialises the drawing list for chart.js (JSON, camelCase keys match the JS side). */
fun List<Drawing>.toJson(): String = joinToString(prefix = "[", postfix = "]", separator = ",") { d ->
    val pts = d.points.joinToString(prefix = "[", postfix = "]") { a ->
        """{"time":${a.time},"price":${String.format(java.util.Locale.US, "%.8f", a.price)}}"""
    }
    """{"id":"${d.id}","kind":"${d.kind.name}","color":"${d.color}","width":${d.width},""" +
        """"dashed":${d.dashed},"filled":${d.filled},"selected":${d.selected},"points":$pts}"""
}
