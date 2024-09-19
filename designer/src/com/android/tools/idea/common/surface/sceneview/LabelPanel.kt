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
package com.android.tools.idea.common.surface.sceneview

import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.DisplaySettings
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.scopeDisposable
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * This label displays the [SceneView] model label.
 *
 * If [partOfOrganizationGroup] then label will display [DisplaySettings.parameterName] (or
 * modelDisplayName if [DisplaySettings.parameterName] is null). If [partOfOrganizationGroup] is not
 * enabled then [DisplaySettings.modelDisplayName] is displayed.
 */
open class LabelPanel(
  private val displaySettings: DisplaySettings,
  protected val scope: CoroutineScope,
  private val partOfOrganizationGroup: StateFlow<Boolean>,
) : JBLabel() {

  init {
    maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
    foreground = AdtUiUtils.HEADER_COLOR
    font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)

    fun updateUi() {
      val parameter = displaySettings.parameterName.value
      val display = displaySettings.modelDisplayName.value
      val name = parameter?.takeIf { partOfOrganizationGroup.value } ?: display ?: ""
      text = name
      toolTipText = displaySettings.tooltip.value ?: name
      isVisible = text.isNotBlank()
    }

    updateUi()

    scope.launch(uiThread) {
      merge(
          displaySettings.modelDisplayName,
          displaySettings.parameterName,
          partOfOrganizationGroup,
          displaySettings.tooltip,
        )
        .conflate()
        .collect {
          updateUi()
          invalidate()
        }
    }

    val messageBusConnection =
      ApplicationManager.getApplication().messageBus.connect(scope.scopeDisposable())
    messageBusConnection.subscribe(
      UISettingsListener.TOPIC,
      UISettingsListener { font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL) },
    )
  }

  companion object {
    /** Minimum allowed width for the [LabelPanel]. */
    @SwingCoordinate const val MIN_WIDTH = 20
  }
}
