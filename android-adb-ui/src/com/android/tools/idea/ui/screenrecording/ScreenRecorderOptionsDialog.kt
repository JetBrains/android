/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.help.AndroidWebHelpProvider.Companion.HELP_PREFIX
import com.android.tools.idea.ui.AndroidAdbUiBundle.message
import com.android.tools.idea.ui.save.SaveConfigurationResolver
import com.android.tools.idea.ui.screenrecording.ScreenRecorderAction.Companion.MAX_RECORDING_DURATION_MINUTES
import com.android.tools.idea.ui.screenrecording.ScreenRecorderAction.Companion.MAX_RECORDING_DURATION_MINUTES_LEGACY
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JEditorPane
import kotlin.math.roundToInt

/** A dialog for setting the options for a screen recording. */
internal class ScreenRecorderOptionsDialog(
  private val project: Project,
  private val emulatorRecordingAvailable: Boolean,
  private val apiLevel: Int,
) : DialogWrapper(project) {

  private val settings = DeviceScreenRecordingSettings.getInstance()
  private val saveConfig = settings.saveConfig
  private lateinit var recordingLengthField: JEditorPane
  private val saveConfigResolver = project.service<SaveConfigurationResolver>()
  private val saveLocation: String
    get() = saveConfigResolver.expandSaveLocation (saveConfig.saveLocation)
  private lateinit var saveLocationText: JEditorPane
  private var fileExtension: String = "mp4"

  init {
    title = message("screenrecord.options.title")
    init()
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row {
        text(getMaxRecordingLengthText(emulatorRecordingAvailable && settings.useEmulatorRecordingWhenAvailable))
          .applyToComponent { recordingLengthField = this }
      }
      row(message("screenrecord.options.bit.rate")) {
        intTextField(1..32)
          .widthGroup("text_boxes")
          .align(AlignX.LEFT)
          .bindIntText(settings::bitRateMbps)
      }
      row(message("screenrecord.options.resolution")) {
        comboBox(listOf(100, 75, 50, 37, 25))
          .widthGroup("text_boxes")
          .align(AlignX.LEFT)
          .bindItem({ (settings.scale * 100).roundToInt() }, { settings.scale = (it ?: 100) / 100.0 })
      }
      row {
        checkBox(message("screenrecord.options.show.taps"))
          .bindSelected(settings::showTaps)
      }.contextHelp(message("screenrecord.options.show.taps.tooltip"))

      if (emulatorRecordingAvailable) {
        row {
          checkBox(message("screenrecord.options.use.emulator.recording"))
            .bindSelected(settings::useEmulatorRecordingWhenAvailable)
            .onChanged {
              fileExtension = if (it.isSelected) "webm" else "mp4"
              recordingLengthField.text = getMaxRecordingLengthText(it.isSelected)
            }
        }.contextHelp(message("screenrecord.options.use.emulator.recording.tooltip"))
      }

      if (StudioFlags.SCREENSHOT_STREAMLINED_SAVING.get()) {
        row {
          text(message("screenrecord.options.save.directory"))
          text(saveLocation)
            .applyToComponent { saveLocationText = this }
          link(message("configure.save.link.text")) { configureSave() }
            .align(AlignX.RIGHT)
        }
      }
    }
  }

  override fun getDimensionServiceKey(): String =
      SCREEN_RECORDER_DIMENSIONS_KEY

  override fun getHelpId(): String =
      "${HELP_PREFIX}r/studio-ui/am-video.html"

  override fun createDefaultActions() {
    super.createDefaultActions()
    okAction.putValue(Action.NAME, message("screenrecord.options.ok.button.text"))
  }

  private fun configureSave() {
    ShowSettingsUtil.getInstance().showSettingsDialog(project, DeviceScreenRecordingSettingsPage::class.java) {
      it.fileExtension = fileExtension
    }
    saveLocationText.text = saveLocation
    pack()
  }

  private fun getMaxRecordingLengthText(forEmulator: Boolean): @Nls String {
    val maxLength = if (forEmulator || apiLevel >= 34) MAX_RECORDING_DURATION_MINUTES else MAX_RECORDING_DURATION_MINUTES_LEGACY
    return message("screenrecord.options.info", maxLength)
  }

  companion object {
    private const val SCREEN_RECORDER_DIMENSIONS_KEY: @NonNls String = "ScreenRecorderOptionsDialog"
  }
}
