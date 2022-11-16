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

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.tree.isActionActive
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import icons.StudioIcons
import kotlin.reflect.KMutableProperty1

const val HIGHLIGHT_COLOR_RED = 0xFF0000
const val HIGHLIGHT_COLOR_BLUE = 0x4F9EE3
const val HIGHLIGHT_COLOR_GREEN = 0x479345
const val HIGHLIGHT_COLOR_YELLOW = 0xFFC66D
const val HIGHLIGHT_COLOR_PURPLE = 0x871094
const val HIGHLIGHT_COLOR_ORANGE = 0xE1A336

const val HIGHLIGHT_DEFAULT_COLOR = HIGHLIGHT_COLOR_BLUE

object ViewMenuAction : DropDownAction(null, "View Options", StudioIcons.Common.VISIBILITY_INLINE) {
  class SettingsAction(name: String, val property: KMutableProperty1<RenderSettings, Boolean>) : ToggleAction(name) {
    override fun isSelected(event: AnActionEvent) =
      event.getData(DEVICE_VIEW_SETTINGS_KEY)?.let { settings -> return property.get(settings) } ?: false

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      event.getData(DEVICE_VIEW_SETTINGS_KEY)?.let { settings -> property.set(settings, state) }
    }
  }

  init {
    add(SettingsAction("Show Borders", RenderSettings::drawBorders))
    add(SettingsAction("Show Layout Bounds", RenderSettings::drawUntransformedBounds))
    add(SettingsAction("Show View Label", RenderSettings::drawLabel))
    add(SettingsAction("Show Fold Hinge and Angle", RenderSettings::drawFold))
    add(HighlightColorAction)
  }

  override fun update(e: AnActionEvent) {
    val enabled = e.getData(DEVICE_VIEW_MODEL_KEY)?.isActive ?: return
    e.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY)?.isEnabled = enabled
  }

  override fun canBePerformed(context: DataContext) = context.getData(DEVICE_VIEW_MODEL_KEY)?.isActive == true
}

object HighlightColorAction : DefaultActionGroup("Recomposition Highlight Color", true) {

  override fun update(event: AnActionEvent) {
    super.update(event)
    val layoutInspector = LayoutInspector.get(event)
    val isConnected = layoutInspector?.currentClient?.isConnected ?: false
    event.presentation.isVisible = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_ENABLE_RECOMPOSITION_HIGHLIGHTS.get() &&
                                   StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_ENABLE_RECOMPOSITION_COUNTS.get() &&
                                   StudioFlags.USE_COMPONENT_TREE_TABLE.get() &&
                                   layoutInspector?.treeSettings?.showRecompositions ?: false &&
                                   (!isConnected || isActionActive(event, Capability.SUPPORTS_COMPOSE_RECOMPOSITION_COUNTS))
    event.presentation.isEnabled = isConnected
  }

  class ColorSettingAction(private val color: Int, name: String): CheckboxAction(name, null, null) {
    override fun isSelected(event: AnActionEvent): Boolean =
      event.getData(DEVICE_VIEW_SETTINGS_KEY)?.highlightColor == color

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      event.getData(DEVICE_VIEW_SETTINGS_KEY)?.highlightColor = color
      LayoutInspector.get(event)?.currentClient?.stats?.recompositionHighlightColor = color
    }
  }

  init {
    add(ColorSettingAction(HIGHLIGHT_COLOR_RED, "Red"))
    add(ColorSettingAction(HIGHLIGHT_COLOR_BLUE, "Blue"))
    add(ColorSettingAction(HIGHLIGHT_COLOR_GREEN, "Green"))
    add(ColorSettingAction(HIGHLIGHT_COLOR_YELLOW, "Yellow"))
    add(ColorSettingAction(HIGHLIGHT_COLOR_PURPLE, "Purple"))
    add(ColorSettingAction(HIGHLIGHT_COLOR_ORANGE, "Orange"))
  }
}
