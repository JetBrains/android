/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.properties

import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.ToggleAction

/**
 * Action for switching the units of dimension attribute values.
 */
@Suppress("ComponentNotRegistered")
object DimensionUnitAction: DefaultActionGroup("Units", listOf(

  object : ToggleAction("dp") {
    override fun isSelected(event: AnActionEvent): Boolean = PropertiesSettings.dimensionUnits == DimensionUnits.DP

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      if (state && PropertiesSettings.dimensionUnits != DimensionUnits.DP) {
        setUnits(event, DimensionUnits.DP)
      }
    }
  },

  object : ToggleAction("pixels") {
    override fun isSelected(event: AnActionEvent): Boolean = PropertiesSettings.dimensionUnits == DimensionUnits.PIXELS

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      if (state && PropertiesSettings.dimensionUnits != DimensionUnits.PIXELS) {
        setUnits(event, DimensionUnits.PIXELS)
      }
    }
  }
)) {
  override fun update(event: AnActionEvent) {
    val model = LayoutInspector.get(event)?.inspectorModel
    event.presentation.isEnabled = model?.resourceLookup?.dpi != null
  }

  init {
    isPopup = true
  }
}

private fun setUnits(event: AnActionEvent, units: DimensionUnits) {
  PropertiesSettings.dimensionUnits = units
  ToolContent.getToolContent(event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT))?.component?.repaint()
}
