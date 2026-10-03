package com.rk.taskmanager.widget

import androidx.annotation.ColorRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.glance.LocalContext
import androidx.glance.unit.ColorProvider
import com.rk.taskmanager.R

/**
 * Resolves a colour resource into a Glance [ColorProvider].
 *
 * Glance's `ColorProvider(resId)` factory and `ResourceColorProvider` are both annotated
 * `@RestrictTo(LIBRARY_GROUP)` and fail lint from an app module, while the only public factory
 * takes a single fixed `Color`. So the day/night selection is done here instead: the resource is
 * resolved through the render `Context`, which applies the `values-night/` variant automatically,
 * and the result is handed to Glance as a fixed colour. Reading `LocalContext` also means the
 * widget follows the system when it flips between light and dark.
 */
@Composable
internal fun widgetColor(@ColorRes resId: Int): ColorProvider {
    val context = LocalContext.current
    val color = remember(context, resId) { Color(ContextCompat.getColor(context, resId)) }
    return remember(color) { ColorProvider(color) }
}

/** Colour slots used by the widgets, defined in `values/` and `values-night/`. */
internal object WidgetPalette {
    @ColorRes val background = R.color.widget_background
    @ColorRes val textPrimary = R.color.widget_text_primary
    @ColorRes val textSecondary = R.color.widget_text_secondary
    @ColorRes val textDisabled = R.color.widget_text_disabled
    @ColorRes val track = R.color.widget_track
    @ColorRes val fillCpu = R.color.widget_fill_cpu
    @ColorRes val fillRam = R.color.widget_fill_ram
    @ColorRes val fillGpu = R.color.widget_fill_gpu
}