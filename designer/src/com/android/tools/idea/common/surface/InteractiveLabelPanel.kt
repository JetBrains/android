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
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** This label displays the [SceneView] model label. */
class InteractiveLabelPanel(
  displayName: StateFlow<String?>,
  tooltip: StateFlow<String?>,
  scope: CoroutineScope,
  private val onLabelClicked: (suspend () -> Boolean),
) : LabelPanel(displayName, tooltip, scope) {

  init {
    addMouseListener(
      object : MouseAdapter() {
        override fun mouseEntered(e: MouseEvent?) {
          foreground = AdtUiUtils.HEADER_HOVER_COLOR
        }

        override fun mouseExited(e: MouseEvent?) {
          foreground = AdtUiUtils.HEADER_COLOR
        }

        override fun mouseClicked(e: MouseEvent?) {
          scope.launch { onLabelClicked() }
        }
      }
    )
  }
}
