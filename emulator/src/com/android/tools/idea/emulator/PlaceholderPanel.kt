/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.idea.emulator.settings.EmulatorSettingsUi
import com.android.tools.idea.npw.assetstudio.roundToInt
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.components.htmlComponent
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.android.actions.RunAndroidAvdManagerAction
import java.awt.Color
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

/**
 * Placeholder panel that is shown in the Emulator tool window when no embedded Emulators are running.
 */
internal class PlaceholderPanel(project: Project): BorderLayoutPanel(), Disposable {

  val title
    get() = "No Running Emulators"

  private var emulatorLaunchesInToolWindow: Boolean
  private var hyperlinkListener: HyperlinkListener

  init {
    Disposer.register(project, this)

    emulatorLaunchesInToolWindow = EmulatorSettings.getInstance().launchInToolWindow
    hyperlinkListener = HyperlinkListener { event ->
      if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
        if (emulatorLaunchesInToolWindow) {
          val action = ActionManager.getInstance().getAction(RunAndroidAvdManagerAction.ID) as RunAndroidAvdManagerAction
          action.openAvdManager(project)
        }
        else {
          ShowSettingsUtil.getInstance().showSettingsDialog(project, EmulatorSettingsUi::class.java)
        }
      }
    }

    project.messageBus.connect(this).subscribe(EmulatorSettingsListener.TOPIC, EmulatorSettingsListener { settings ->
      emulatorLaunchesInToolWindow = settings.launchInToolWindow
      updateContent()
    })

    createContent()
  }

  private fun createContent() {
    // Use toned down colors.
    val textColor = interpolate(background, foreground, 0.7)
    val linkColor = interpolate(background, JBColor.BLUE, 0.7)
    val linkColorString = (linkColor.rgb and 0xFFFFFF).toString(16)
    val html = if (emulatorLaunchesInToolWindow) {
      """
      <center>
      There are no running Emulators.<br>
      To start one, use <font color = ${linkColorString}><a href=''>AVD Manager</a></font><br>
      or run the app.
      </center>
      """.trimIndent()
    }
    else {
      """
      <center>
      There are no running Emulators.<br>
      To make Emulators appear in this window,<br>
      enable the <i>Launch in a tool window</i><br>
      option in <font color = ${linkColorString}><a href=''>Emulator settings</a></font>.
      </center>
      """.trimIndent()
    }

    val text = htmlComponent(text = html,
                             lineWrap = true,
                             font = font.deriveFont(font.size * 1.1F),
                             foreground = textColor,
                             hyperlinkListener = hyperlinkListener).apply {
      isOpaque = false
      isFocusable = false
      border = JBUI.Borders.emptyTop(60)
    }
    add(text)
  }

  private fun updateContent() {
    removeAll()
    createContent()
    validate()
  }

  override fun updateUI() {
    super.updateUI()
    updateContent()
  }

  override fun dispose() {
  }

  companion object {
    /**
     * Interpolates between two colors.
     */
    @JvmStatic
    private fun interpolate(start: Color, end: Color, fraction: Double): Color {
      return Color(interpolate(start.red, end.red, fraction).coerceIn(0, 255),
                   interpolate(start.green, end.green, fraction).coerceIn(0, 255),
                   interpolate(start.blue, end.blue, fraction).coerceIn(0, 255),
                   interpolate(start.alpha, end.alpha, fraction).coerceIn(0, 255))
    }

    /**
     * Interpolates between two integers.
     */
    @JvmStatic
    private fun interpolate(start: Int, end: Int, fraction: Double): Int {
      return start + ((end - start) * fraction).roundToInt()
    }
  }
}