/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.ui.toolbar.actions

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.tree.isActionActive
import com.android.tools.idea.layoutinspector.ui.RenderModel
import com.android.tools.idea.layoutinspector.ui.RenderSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import icons.StudioIcons
import javax.swing.JComponent
import kotlin.reflect.KMutableProperty1
import org.jetbrains.annotations.VisibleForTesting

const val RECOMPOSITION_COLOR_RED_ARGB = 0xFFFF0000.toInt()
const val RECOMPOSITION_COLOR_BLUE_ARGB = 0xFF4F9EE3.toInt()
const val RECOMPOSITION_COLOR_GREEN_ARGB = 0xFF479345.toInt()
const val RECOMPOSITION_COLOR_YELLOW_ARGB = 0xFFFFC66D.toInt()
const val RECOMPOSITION_COLOR_PURPLE_ARGB = 0xFF871094.toInt()
const val RECOMPOSITION_COLOR_ORANGE_ARGB = 0xFFE1A336.toInt()

/** Action shown in Layout Inspector toolbar, used to control Layout Inspector [RenderSettings]. */
class RenderSettingsAction(
  private val renderModelProvider: () -> RenderModel,
  renderSettingsProvider: () -> RenderSettings,
) : DropDownAction(null, "View Options", StudioIcons.Common.VISIBILITY_INLINE) {

  init {
    add(
      ToggleRenderSettingsAction(
        "Show Borders",
        renderSettingsProvider,
        RenderSettings::drawBorders,
      )
    )
    add(
      ToggleRenderSettingsAction(
        "Show Layout Bounds",
        renderSettingsProvider,
        RenderSettings::drawUntransformedBounds,
      )
    )
    add(
      ToggleRenderSettingsAction(
        "Show View Label",
        renderSettingsProvider,
        RenderSettings::drawLabel,
      )
    )
    add(
      ToggleRenderSettingsAction(
        "Show Fold Hinge and Angle",
        renderSettingsProvider,
        RenderSettings::drawFold,
      )
    )
    add(HighlightColorAction(renderSettingsProvider))
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val enabled = renderModelProvider().isActive
    e.presentation.isEnabled = enabled
    e.presentation.isPerformGroup = enabled
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    component.isEnabled = presentation.isEnabled
  }
}

private class ToggleRenderSettingsAction(
  actionName: String,
  private val renderSettingsProvider: () -> RenderSettings,
  private val property: KMutableProperty1<RenderSettings, Boolean>,
) : ToggleAction(actionName) {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun isSelected(event: AnActionEvent): Boolean {
    return property.get(renderSettingsProvider())
  }

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    return property.set(renderSettingsProvider(), state)
  }
}

@VisibleForTesting
class HighlightColorAction(renderSettingsProvider: () -> RenderSettings) :
  DefaultActionGroup("Recomposition Highlight Color", true) {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    super.update(event)
    val layoutInspector = LayoutInspector.get(event)
    val isConnected = layoutInspector?.currentClient?.isConnected ?: false
    event.presentation.isVisible =
      layoutInspector?.treeSettings?.showRecompositions ?: false &&
        (!isConnected || isActionActive(event, Capability.SUPPORTS_COMPOSE_RECOMPOSITION_COUNTS))
    event.presentation.isEnabled = isConnected
  }

  init {
    add(ColorSettingAction("Red", RECOMPOSITION_COLOR_RED_ARGB, renderSettingsProvider))
    add(ColorSettingAction("Blue", RECOMPOSITION_COLOR_BLUE_ARGB, renderSettingsProvider))
    add(ColorSettingAction("Green", RECOMPOSITION_COLOR_GREEN_ARGB, renderSettingsProvider))
    add(ColorSettingAction("Yellow", RECOMPOSITION_COLOR_YELLOW_ARGB, renderSettingsProvider))
    add(ColorSettingAction("Purple", RECOMPOSITION_COLOR_PURPLE_ARGB, renderSettingsProvider))
    add(ColorSettingAction("Orange", RECOMPOSITION_COLOR_ORANGE_ARGB, renderSettingsProvider))
  }
}

private class ColorSettingAction(
  actionName: String,
  private val color: Int,
  private val renderSettingsProvider: () -> RenderSettings,
) : CheckboxAction(actionName, null, null) {
  override fun isSelected(event: AnActionEvent): Boolean =
    renderSettingsProvider().recompositionColor == color

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    renderSettingsProvider().recompositionColor = color
    LayoutInspector.get(event)?.currentClient?.stats?.recompositionHighlightColor = color
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
