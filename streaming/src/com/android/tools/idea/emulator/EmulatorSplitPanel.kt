/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.emulator

import com.android.tools.adtui.common.primaryPanelBackground
import com.intellij.ui.OnePixelSplitter
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * A panel containing two subpanels separated by a [OnePixelSplitter].
 */
class EmulatorSplitPanel(splitType: SplitType, proportion: Double) : JPanel(BorderLayout()) {
  private val splitter = OnePixelSplitter(splitType == SplitType.VERTICAL, proportion.toFloat())

  var splitType: SplitType
    get() = if (splitter.orientation) SplitType.VERTICAL else SplitType.HORIZONTAL
    set(value) { splitter.orientation = value == SplitType.VERTICAL }
  var proportion: Double
    get() = splitter.proportion.toDouble()
    set(value) { splitter.proportion = value.toFloat() }
  var firstComponent: JComponent
    get() = splitter.firstComponent
    set(value) { splitter.firstComponent = value }
  var secondComponent: JComponent
    get() = splitter.secondComponent
    set(value) { splitter.secondComponent = value }

  constructor(layoutNode: SplitNode) : this(layoutNode.splitType, layoutNode.splitRatio)

  init {
    background = primaryPanelBackground
    add(splitter, BorderLayout.CENTER)
  }

  fun getState(): EmulatorPanelState {
    return EmulatorPanelState(splitType, proportion, getChildState(firstComponent), getChildState(secondComponent))
  }

  private fun getChildState(child: JComponent): EmulatorPanelState {
    return when (child) {
      is EmulatorDisplayPanel -> {
        EmulatorPanelState(child.displayId)
      }
      is EmulatorSplitPanel -> {
        child.getState()
      }
      else -> {
        throw IllegalArgumentException()
      }
    }
  }
}

enum class SplitType {
  /** Panels are side by side. */
  HORIZONTAL,
  /** One panel is above the other. */
  VERTICAL
}
