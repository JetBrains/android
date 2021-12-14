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
package com.android.tools.idea.logcat.settings

import com.android.tools.idea.logcat.LogcatBundle
import com.android.tools.idea.logcat.LogcatToolWindowFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.util.io.FileUtilRt.LARGE_FOR_CONTENT_LOADING
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.GridBag
import org.jetbrains.annotations.VisibleForTesting
import java.awt.GridBagConstraints.NORTHWEST
import java.awt.GridBagConstraints.WEST
import java.awt.GridBagLayout
import javax.swing.Box
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

private const val MAX_BUFFER_SIZE_MB = 100
private const val MAX_BUFFER_SIZE_KB = 1024 * MAX_BUFFER_SIZE_MB

internal class LogcatApplicationSettingsConfigurable(private val logcatSettings: LogcatSettings) : Configurable, Configurable.NoScroll {
  @VisibleForTesting
  internal val cycleBufferSizeTextField = JTextField(10).apply {
    text = (logcatSettings.bufferSize / 1024).toString()
  }

  @VisibleForTesting
  internal val cyclicBufferSizeWarningLabel = JLabel()

  private val component = JPanel(GridBagLayout()).apply {
    cyclicBufferSizeWarningLabel.foreground = JBColor.red
    cycleBufferSizeTextField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        updateWarningLabel()
      }
    })
    val gridBag = GridBag().anchor(NORTHWEST)
    add(JLabel(LogcatBundle.message("logcat.settings.buffer.size")), gridBag.nextLine().next())
    add(Box.createHorizontalStrut(JBUIScale.scale(20)), gridBag.next())
    add(cycleBufferSizeTextField, gridBag.next())
    add(JLabel(LogcatBundle.message("logcat.settings.buffer.kb")), gridBag.next().weightx(1.0).anchor(WEST))
    add(cyclicBufferSizeWarningLabel, gridBag.nextLine().next().coverLine().weighty(1.0).anchor(NORTHWEST))
  }

  override fun createComponent() = component

  private fun updateWarningLabel() {
    val value = getBufferSizeKb()
    cyclicBufferSizeWarningLabel.text = when {
      value == null || !isValidBufferSize(value) ->
        LogcatBundle.message("logcat.settings.buffer.warning.invalid", MAX_BUFFER_SIZE_KB.toString(), MAX_BUFFER_SIZE_MB.toString())
      value > LARGE_FOR_CONTENT_LOADING / 1024 -> LogcatBundle.message("logcat.settings.buffer.warning.tooLarge")
      else -> ""
    }
  }

  override fun getDisplayName(): String {
    return LogcatBundle.message("logcat.settings.title")
  }

  override fun isModified(): Boolean {
    val bufferSizeKb = getBufferSizeKb()
    return bufferSizeKb != null && isValidBufferSize(bufferSizeKb) && bufferSizeKb != logcatSettings.bufferSize / 1024
  }

  override fun apply() {
    logcatSettings.bufferSize = getBufferSizeKb()?.times(1024) ?: return

    LogcatToolWindowFactory.logcatPresenters.forEach {
      it.applyLogcatSettings(logcatSettings)
    }
  }

  private fun getBufferSizeKb() = try {
    cycleBufferSizeTextField.text.toInt()
  }
  catch (e: NumberFormatException) {
    null
  }
}

private fun isValidBufferSize(value: Int) = value in (1..MAX_BUFFER_SIZE_KB)
