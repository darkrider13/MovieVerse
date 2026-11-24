package com.trackerxx.movieverse.ui.library

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize

/**
 * State of one drag-to-reorder (cards or tabs): long press picks an item up, a copy follows the finger, the list
 * re-orders live as the finger passes over other items, letting go saves the new order.
 * Positions are in the coordinates of the list that is being dragged in.
 */
@Stable
class DragState {
    /** id of the picked-up item (null = nothing is being dragged) */
    var key by mutableStateOf<String?>(null)

    /** ids in the order they are shown right now */
    var order by mutableStateOf<List<String>>(emptyList())

    /** finger position */
    var pos by mutableStateOf(Offset.Zero)

    /** finger position minus the top-left of the picked-up item (dx / dy) */
    var grab by mutableStateOf(Offset.Zero)

    /** size of the picked-up item in px */
    var size by mutableStateOf(IntSize.Zero)

    /** time a drag ended: a tap right after a drag is ignored for 400 ms (noClick / noClickC) */
    var noClickAt = 0L
    var lastSwap = 0L

    val active: Boolean get() = key != null

    /** the picked-up item takes the place of [target] (b.after / b.before in the web version) */
    fun swapWith(target: String) {
        val k = key ?: return
        val cur = order.toMutableList()
        val from = cur.indexOf(k)
        val to = cur.indexOf(target)
        if (from < 0 || to < 0 || from == to) return
        cur.removeAt(from)
        cur.add(to, k)
        order = cur
        lastSwap = System.currentTimeMillis()
    }

    /** a tap within 400 ms after a drag must not open / select anything */
    fun recentlyDragged(): Boolean = System.currentTimeMillis() - noClickAt < 400
}

private fun swapAllowed(d: DragState) = System.currentTimeMillis() - d.lastSwap >= 130

/** the card under the finger takes the dragged card's place (at most every 130 ms) */
fun swapAtGrid(d: DragState, grid: LazyGridState) {
    if (!d.active || !swapAllowed(d)) return
    val p = d.pos
    val t = grid.layoutInfo.visibleItemsInfo.firstOrNull { it2 ->
        val k = it2.key
        val h = (it2.size.width * 1.5f).toInt()
        k is String && k != d.key && d.order.contains(k) &&
            p.x >= it2.offset.x && p.x < it2.offset.x + it2.size.width &&
            p.y >= it2.offset.y && p.y < it2.offset.y + h
    } ?: return
    d.swapWith(t.key as String)
}

fun swapAtRow(d: DragState, list: LazyListState) {
    if (!d.active || !swapAllowed(d)) return
    val x = d.pos.x
    val t = list.layoutInfo.visibleItemsInfo.firstOrNull { it2 ->
        val k = it2.key
        k is String && k != d.key && d.order.contains(k) && x >= it2.offset && x < it2.offset + it2.size
    } ?: return
    d.swapWith(t.key as String)
}

/**
 * Long press on a card (only where "my order" can be changed: ids() != null) picks it up.
 * onDrop gets the ids in their new order.
 */
@Composable
fun Modifier.cardReorder(
    drag: DragState,
    grid: LazyGridState,
    ids: () -> List<String>?,
    haptic: () -> Unit,
    onDrop: (List<String>) -> Unit
): Modifier {
    val idsNow by rememberUpdatedState(ids)
    val dropNow by rememberUpdatedState(onDrop)
    val hapticNow by rememberUpdatedState(haptic)
    return this.pointerInput(drag, grid) {
        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                val order = idsNow()
                if (order != null) {
                    val hit = grid.layoutInfo.visibleItemsInfo.firstOrNull { it2 ->
                        val k = it2.key
                        val h = (it2.size.width * 1.5f).toInt()
                        k is String && order.contains(k) &&
                            offset.x >= it2.offset.x && offset.x < it2.offset.x + it2.size.width &&
                            offset.y >= it2.offset.y && offset.y < it2.offset.y + h
                    }
                    if (hit != null) {
                        drag.order = order
                        drag.grab = Offset(offset.x - hit.offset.x, offset.y - hit.offset.y)
                        drag.pos = offset
                        drag.size = IntSize(hit.size.width, (hit.size.width * 1.5f).toInt())
                        drag.lastSwap = 0L
                        drag.key = hit.key as String
                        hapticNow()
                    }
                }
            },
            onDrag = { change, amount ->
                if (drag.active) {
                    change.consume()
                    drag.pos = drag.pos + amount
                    swapAtGrid(drag, grid)
                }
            },
            onDragEnd = {
                if (drag.active) {
                    val order = drag.order
                    drag.noClickAt = System.currentTimeMillis()
                    drag.key = null
                    dropNow(order)
                }
            },
            onDragCancel = {
                if (drag.active) {
                    val order = drag.order
                    drag.noClickAt = System.currentTimeMillis()
                    drag.key = null
                    dropNow(order)
                }
            }
        )
    }
}

/** Long press on a status tab picks it up (the All tab and the + tab stay where they are). */
@Composable
fun Modifier.tabReorder(
    drag: DragState,
    list: LazyListState,
    ids: () -> List<String>,
    pillTop: () -> Float,
    pillHeight: () -> Int,
    haptic: () -> Unit,
    onDrop: (List<String>) -> Unit,
    canPick: (String) -> Boolean = { true }
): Modifier {
    val idsNow by rememberUpdatedState(ids)
    val dropNow by rememberUpdatedState(onDrop)
    val hapticNow by rememberUpdatedState(haptic)
    val canPickNow by rememberUpdatedState(canPick)
    val topNow by rememberUpdatedState(pillTop)
    val heightNow by rememberUpdatedState(pillHeight)
    return this.pointerInput(drag, list) {
        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                val order = idsNow()
                val hit = list.layoutInfo.visibleItemsInfo.firstOrNull { it2 ->
                    val k = it2.key
                    k is String && order.contains(k) && canPickNow(k) && offset.x >= it2.offset && offset.x < it2.offset + it2.size
                }
                if (hit != null) {
                    drag.order = order
                    drag.grab = Offset(offset.x - hit.offset, offset.y - topNow())
                    drag.pos = offset
                    drag.size = IntSize(hit.size, heightNow())
                    drag.lastSwap = 0L
                    drag.key = hit.key as String
                    hapticNow()
                }
            },
            onDrag = { change, amount ->
                if (drag.active) {
                    change.consume()
                    drag.pos = drag.pos + amount
                    swapAtRow(drag, list)
                }
            },
            onDragEnd = {
                if (drag.active) {
                    val order = drag.order
                    drag.noClickAt = System.currentTimeMillis()
                    drag.key = null
                    dropNow(order)
                }
            },
            onDragCancel = {
                if (drag.active) {
                    val order = drag.order
                    drag.noClickAt = System.currentTimeMillis()
                    drag.key = null
                    dropNow(order)
                }
            }
        )
    }
}
