/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.monitor.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.progress.util.ColorProgressBar
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.ui.InplaceButton
import com.intellij.ui.SimpleColoredComponent
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JPanel
import javax.swing.JProgressBar

/**
 * Panel displayed at the bottom of the Device Explorer tool window
 * used to track progress (status text and progress bar) of long running
 * operations, such as file transfers.
 */
class ProgressPanel : JPanel() {
  private val myProgressBar: JProgressBar
  private val myState: SimpleColoredComponent
  private var myCancelActionListener: ActionListener? = null

  init {
    // Create components
    myState = object : SimpleColoredComponent() {
      override fun getMinimumSize(): Dimension {
        // Override min size to (0, 0) to ensure the label does not grow past the
        // container width, as, by default, a SimpleColoredComponent returns a
        // min size == preferred size == size to display the text, however long.
        return Dimension(0, 0)
      }
    }
    myProgressBar = JProgressBar()
    myProgressBar.maximum = PROGRESS_STEPS
    val stopIcon = IconButton("Cancel", AllIcons.Process.Stop, AllIcons.Process.StopHovered)
    val cancelButton = InplaceButton(stopIcon) { e: ActionEvent ->
      myCancelActionListener?.actionPerformed(e)
    }
    setOkStatusColor()
    isVisible = false

    // Layout components:
    // +-----------------------------------------------+
    // + <status text>                                 +
    // +-----------------------------------------------+
    // + <progress bar>                | <cancel icon> +
    // +-----------------------------------------------+
    val layout = BorderLayout(0, 0)
    setLayout(layout)
    add(myState, BorderLayout.NORTH)
    add(myProgressBar, BorderLayout.CENTER)
    add(cancelButton, BorderLayout.EAST)
  }

  fun start() {
    clear()
    isVisible = true
  }

  fun stop() {
    isVisible = false
    clear()
  }

  private fun clear() {
    setProgress(0.0)
    setText("")
    setOkStatusColor()
  }

  fun setCancelActionListener(cancelActionListener: ActionListener?) {
    myCancelActionListener = cancelActionListener
  }

  private fun setOkStatusColor() {
    myProgressBar.foreground = ColorProgressBar.GREEN
  }

  fun setWarningStatusColor() {
    myProgressBar.foreground = ColorProgressBar.YELLOW
  }

  fun setErrorStatusColor() {
    myProgressBar.foreground = ColorProgressBar.RED
  }

  private fun setProgress(v: Double) {
    val fraction = (v * PROGRESS_STEPS).toInt()
    myProgressBar.value = fraction
  }

  fun setIndeterminate(indeterminate: Boolean) {
    myProgressBar.isIndeterminate = indeterminate
  }

  private fun setText(text: String) {
    myState.clear()
    myState.append(text)
  }

  companion object {
    private const val PROGRESS_STEPS = 1000
  }
}
