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

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import java.awt.Dimension

/** This label displays the [SceneView] model label. */
open class LabelPanel(var layoutData: LayoutData) : JBLabel() {
  init {
    maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
    foreground = labelDefaultColor
  }

  final override fun doLayout() {
    super.doLayout()
    // If there is a model name, we manually assign the content of the modelNameLabel and position
    // it here.
    // Once this panel gets more functionality, we will need the use of a layout manager. For now,
    // we just lay out the component manually.
    if (layoutData.modelName == null) {
      text = ""
      toolTipText = ""
      isVisible = false
    } else {
      text = layoutData.modelName
      // Use modelName for tooltip if none has been specified.
      toolTipText = layoutData.modelTooltip ?: layoutData.modelName
      isVisible = true
    }
  }

  companion object {
    val labelDefaultColor = JBColor(0x6c707e, 0xdfe1e5)
  }
}
