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

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.intellij.openapi.Disposable
import com.intellij.ui.JBColor
import kotlinx.coroutines.launch
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

/** This label displays the [SceneView] model label. */
class InteractiveLabelPanel(
  layoutData: LayoutData,
  disposable: Disposable,
  private val onLabelClicked: (suspend () -> Boolean)
) : LabelPanel(layoutData) {

  private val scope = AndroidCoroutineScope(disposable)

  init {
    addMouseListener(
      object : MouseAdapter() {
        override fun mouseEntered(e: MouseEvent?) {
          foreground = labelHoverColor
        }

        override fun mouseExited(e: MouseEvent?) {
          foreground = labelDefaultColor
        }

        override fun mouseClicked(e: MouseEvent?) {
          scope.launch { onLabelClicked() }
        }
      },
    )
  }

  companion object {
    val labelHoverColor = JBColor(0x5a5d6b, 0xf0f1f2)
  }
}
