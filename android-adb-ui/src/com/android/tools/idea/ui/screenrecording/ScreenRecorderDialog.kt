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
package com.android.tools.idea.ui.screenrecording

import com.android.tools.idea.ui.AndroidAdbUiBundle
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapperDialog
import com.intellij.ui.PopupBorder
import com.intellij.ui.TitlePanel
import com.intellij.ui.WindowMoveListener
import com.intellij.ui.WindowRoundedCornersManager
import com.intellij.ui.components.DialogPanel
import com.intellij.ui.dsl.builder.components.DslLabel
import com.intellij.ui.dsl.builder.components.DslLabelType
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.util.Locale
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.SwingUtilities
import javax.swing.border.Border
import javax.swing.text.JTextComponent

/** Modeless dialog shown during device screen recording. */
internal class ScreenRecorderDialog(
  private val dialogTitle: String,
  project: Project,
  private val maxRecordingDurationMillis: Int,
  private val onStop: Runnable,
) : DialogWrapper(project, false, IdeModalityType.MODELESS) {

  private val alarm: Alarm = Alarm(disposable)
  private var recordingStartTime: Long = 0L
  private val panel: DialogPanel = createPanel()

  private var recordingTimeMillis: Long = 0
    set(value) {
      field = value
      recordingLabelText = recordingTimeText(value)
    }

  private var recordingLabelText = recordingTimeText(recordingTimeMillis)
    set(value) {
      field = value
      recordingLabel.text = value
    }

  private lateinit var recordingLabel: JTextComponent
  private lateinit var stopButton: JButton

  init {
    init()
  }

  override fun init() {
    super.init()
    setUndecorated(true)
    rootPane.windowDecorationStyle = JRootPane.NONE
    rootPane.border = JBUI.Borders.empty()
    panel.border = PopupBorder.Factory.create(true, true)
    WindowRoundedCornersManager.configure(this)
    pack()
  }

  override fun show() {
    super.show()
    recordingStartTime = System.currentTimeMillis()
    alarm.addRequest(::stopRecordingTimer, maxRecordingDurationMillis, ModalityState.any())
    alarm.addRequest(::updateRecordingTime, 1000, ModalityState.any())
  }

  override fun createCenterPanel(): JComponent {
    return panel
  }

  override fun createSouthPanel(): JComponent? {
    return null
  }

  override fun createContentPaneBorder(): Border? {
    return null
  }

  private fun stopRecordingTimer() {
    alarm.cancelAllRequests()
    onStop.run()
    recordingLabelText = AndroidAdbUiBundle.message("screenrecord.action.stopping")
    stopButton.isEnabled = false
  }

  @Nls
  private fun recordingTimeText(timeMillis: Long): String {
    val seconds = (timeMillis / 1000).toInt()
    return AndroidAdbUiBundle.message("screenrecord.dialog.progress",
                                      String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60))
  }

  private fun updateRecordingTime() {
    recordingTimeMillis = System.currentTimeMillis() - recordingStartTime
    alarm.addRequest(::updateRecordingTime, millisUntilNextSecondTick(), ModalityState.any())
  }

  private fun millisUntilNextSecondTick(): Long {
    return 1000 - recordingTimeMillis % 1000
  }

  /** Creates contents of the dialog. */
  private fun createPanel(): DialogPanel {
    val dialogPanel = DialogPanel(dialogTitle)
    val titlePanel = TitlePanel()
    titlePanel.setText(dialogTitle)
    titlePanel.setActive(true)
    // WindowMoveListener allows the window to be moved by dragging the title panel.
    val moveListener: WindowMoveListener = object : WindowMoveListener(titlePanel) {
      override fun getView(component: Component): Component {
        return SwingUtilities.getAncestorOfClass(DialogWrapperDialog::class.java, component)
      }
    }
    moveListener.installTo(titlePanel)
    dialogPanel.add(titlePanel, BorderLayout.NORTH)

    val centerPanel = BorderLayoutPanel()
    centerPanel.border = JBUI.Borders.empty(15, 10)
    @Suppress("UnstableApiUsage")
    recordingLabel = DslLabel(DslLabelType.LABEL).apply { text = recordingTimeText(recordingTimeMillis) }
    centerPanel.addToLeft(recordingLabel)
    centerPanel.addToCenter(Box.createRigidArea(Dimension(JBUIScale.scale(20), 0)))
    stopButton = JButton(AndroidAdbUiBundle.message("screenrecord.dialog.stop.recording"))
    stopButton.addActionListener {
      stopRecordingTimer()
    }
    centerPanel.addToRight(stopButton)
    dialogPanel.add(centerPanel, BorderLayout.CENTER)
    return dialogPanel
  }
}
