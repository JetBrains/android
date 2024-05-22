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
package com.android.tools.idea.common.surface

import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** This label displays the [SceneView] model label. */
open class LabelPanel(
  private val displayName: StateFlow<String?>,
  private val tooltip: StateFlow<String?>,
  protected val scope: CoroutineScope,
) : JBLabel() {

  init {
    maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
    foreground = AdtUiUtils.HEADER_COLOR
    font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
    text = displayName.value
    toolTipText = tooltip.value ?: displayName.value ?: ""
    scope.launch(uiThread) {
      displayName.collect {
        text = it ?: ""
        isVisible = text.isNotBlank()
        invalidate()
      }
    }
    scope.launch(uiThread) {
      tooltip.collect {
        toolTipText = it ?: text ?: ""
        invalidate()
      }
    }
  }

  companion object {
    /** Minimum allowed width for the [LabelPanel]. */
    @SwingCoordinate const val MIN_WIDTH = 20
  }
}
