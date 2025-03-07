/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.layoutinspector.ui

import com.android.tools.idea.layoutinspector.ui.toolbar.actions.RECOMPOSITION_COLOR_BLUE
import com.intellij.ide.util.PropertiesComponent
import kotlin.properties.Delegates

private const val DRAW_BORDERS_KEY = "live.layout.inspector.draw.borders"
private const val SHOW_LAYOUT_BOUNDS_KEY = "live.layout.inspector.draw.layout"
private const val DRAW_LABEL_KEY = "live.layout.inspector.draw.label"
private const val DRAW_FOLD_KEY = "live.layout.inspector.draw.fold"
private const val RECOMPOSITION_COLOR_KEY = "live.layout.inspector.highlight.color"

const val RECOMPOSITION_DEFAULT_COLOR = RECOMPOSITION_COLOR_BLUE

interface RenderSettings {
  data class State(
    val scalePercent: Int,
    val drawBorders: Boolean,
    val drawUntransformedBounds: Boolean,
    val drawLabel: Boolean,
    val drawFold: Boolean,
    val recompositionColor: Int,
  )

  fun interface Listener {
    fun onChange(state: State)
  }

  val modificationListeners: MutableList<Listener>

  /** Scale of the view in percentage. Used to scale borders thickness, labels size etc. */
  var scalePercent: Int

  /** Scale of the view as a fraction: 1 = 100% */
  val scaleFraction: Double
    get() = scalePercent / 100.0

  var drawBorders: Boolean

  var drawUntransformedBounds: Boolean

  var drawLabel: Boolean

  var drawFold: Boolean

  /** The color used for recomposition highlights */
  var recompositionColor: Int

  fun toState(): State {
    return State(
      scalePercent = scalePercent,
      drawBorders = drawBorders,
      drawUntransformedBounds = drawUntransformedBounds,
      drawLabel = drawLabel,
      drawFold = drawFold,
      recompositionColor = recompositionColor,
    )
  }

  fun invokeListeners() {
    val state = toState()
    modificationListeners.forEach { it.onChange(state) }
  }
}

class EditorRenderSettings(scalePercent: Int = 100) : RenderSettings {
  override val modificationListeners = mutableListOf<RenderSettings.Listener>()
  override var scalePercent: Int by
    Delegates.observable(scalePercent) { _, _, _ -> invokeListeners() }

  override var drawBorders: Boolean by Delegates.observable(true) { _, _, _ -> invokeListeners() }

  override var drawUntransformedBounds: Boolean by
    Delegates.observable(false) { _, _, _ -> invokeListeners() }

  override var drawLabel by Delegates.observable(true) { _, _, _ -> invokeListeners() }

  override var drawFold by Delegates.observable(true) { _, _, _ -> invokeListeners() }

  override var recompositionColor: Int
    get() = 0xFF0000
    set(_) {}
}

class InspectorRenderSettings(scalePercent: Int = 100) : RenderSettings {
  override val modificationListeners = mutableListOf<RenderSettings.Listener>()

  /** Scale of the view in percentage: 100 = 100% */
  override var scalePercent: Int by
    Delegates.observable(scalePercent) { _, _, _ -> invokeListeners() }

  override var drawBorders: Boolean
    get() = PropertiesComponent.getInstance().getBoolean(DRAW_BORDERS_KEY, true)
    set(value) {
      PropertiesComponent.getInstance().setValue(DRAW_BORDERS_KEY, value, true)
      invokeListeners()
    }

  override var drawUntransformedBounds: Boolean
    get() = PropertiesComponent.getInstance().getBoolean(SHOW_LAYOUT_BOUNDS_KEY, false)
    set(value) {
      PropertiesComponent.getInstance().setValue(SHOW_LAYOUT_BOUNDS_KEY, value, false)
      invokeListeners()
    }

  override var drawLabel: Boolean
    get() = PropertiesComponent.getInstance().getBoolean(DRAW_LABEL_KEY, true)
    set(value) {
      PropertiesComponent.getInstance().setValue(DRAW_LABEL_KEY, value, true)
      invokeListeners()
    }

  override var drawFold: Boolean
    get() = PropertiesComponent.getInstance().getBoolean(DRAW_FOLD_KEY, true)
    set(value) {
      PropertiesComponent.getInstance().setValue(DRAW_FOLD_KEY, value, true)
      invokeListeners()
    }

  override var recompositionColor: Int
    get() =
      PropertiesComponent.getInstance().getInt(RECOMPOSITION_COLOR_KEY, RECOMPOSITION_DEFAULT_COLOR)
    set(value) {
      val actual = value.and(0xFFFFFF)
      val old =
        PropertiesComponent.getInstance()
          .getInt(RECOMPOSITION_COLOR_KEY, RECOMPOSITION_DEFAULT_COLOR)
      if (old != actual) {
        PropertiesComponent.getInstance()
          .setValue(RECOMPOSITION_COLOR_KEY, actual, RECOMPOSITION_DEFAULT_COLOR)
        invokeListeners()
      }
    }
}
