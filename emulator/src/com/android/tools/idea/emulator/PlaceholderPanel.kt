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

import com.android.SdkConstants
import com.android.annotations.concurrency.AnyThread
import com.android.repository.Revision
import com.android.repository.api.ProgressIndicator
import com.android.repository.api.RepoManager.RepoLoadedListener
import com.android.repository.impl.meta.RepositoryPackages
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.emulator.settings.EmulatorSettingsUi
import com.android.tools.idea.npw.assetstudio.roundToInt
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.components.htmlComponent
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.android.actions.RunAndroidAvdManagerAction
import java.awt.Color
import java.awt.event.MouseEvent
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

/**
 * Placeholder panel that is shown in the Emulator tool window when no embedded Emulators are running.
 */
internal class PlaceholderPanel(project: Project): BorderLayoutPanel(), Disposable {

  val title
    get() = "No Running Emulators"

  private var emulatorLaunchesInToolWindow: Boolean
  private var emulatorVersionIsSufficient: Boolean
  private var hyperlinkListener: HyperlinkListener

  init {
    Disposer.register(project, this)

    emulatorLaunchesInToolWindow = EmulatorSettings.getInstance().launchInToolWindow
    emulatorVersionIsSufficient = true

    hyperlinkListener = HyperlinkListener { event ->
      if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
        if (emulatorLaunchesInToolWindow) {
          if (emulatorVersionIsSufficient) {
            val action = ActionManager.getInstance().getAction(RunAndroidAvdManagerAction.ID) as RunAndroidAvdManagerAction
            action.openAvdManager(project)
          }
          else {
            val actionManager = ActionManager.getInstance()
            val mouseEvent = MouseEvent(this, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 0, 0, 1, false)
            actionManager.tryToExecute(actionManager.getAction("CheckForUpdate"), mouseEvent, null, null, false)
          }
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

    ApplicationManager.getApplication().executeOnPooledThread {
      val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
      val progress: ProgressIndicator = StudioLoggerProgressIndicator(AvdManagerConnection::class.java)
      val sdkManager = sdkHandler.getSdkManager(progress)
      val listener = RepoLoadedListener { packages -> localPackagesUpdated(packages) }
      sdkManager.addLocalChangeListener(listener)
      Disposer.register(this, Disposable { sdkManager.removeLocalChangeListener(listener) })

      localPackagesUpdated(sdkManager.packages)
    }

    createContent()
  }

  @AnyThread
  private fun localPackagesUpdated(packages: RepositoryPackages) {
    val emulatorPackage = packages.localPackages.get(SdkConstants.FD_EMULATOR)
    if (emulatorPackage != null) {
      invokeLater {
        val sufficient = emulatorPackage.version >= Revision.parseRevision(MIN_REQUIRED_EMULATOR_VERSION)
        if (emulatorVersionIsSufficient != sufficient) {
          emulatorVersionIsSufficient = sufficient
          updateContent()
        }
      }
    }
  }

  private fun createContent() {
    // Use toned down colors.
    val textColor = interpolate(background, foreground, 0.7)
    val linkColor = interpolate(background, JBColor.BLUE, 0.7)
    val linkColorString = (linkColor.rgb and 0xFFFFFF).toString(16)
    val html = if (emulatorLaunchesInToolWindow) {
      if (emulatorVersionIsSufficient) {
        """
        <center>
        No emulators are currently running.<br>
        To launch an emulator, use the <font color = ${linkColorString}><a href=''>AVD Manager</a></font><br>
        or run your app while targeting a virtual device.
        </center>
        """.trimIndent()
      }
      else {
        """
        <center>
        To use the Android Emulator in this<br>
        window, install version ${MIN_REQUIRED_EMULATOR_VERSION} or higher.<br>
        Please <font color = ${linkColorString}><a href=''>check for updates</a></font> and install<br>
        the latest version of the Android Emulator.
        </center>
        """.trimIndent()
      }
    }
    else {
      """
      <center>
      The Android Emulator is currently configured<br>
      to run as a standalone application. To make<br>
      Android Emulator launch in this window<br>
      instead, select the <i>Launch in a tool window</i><br>
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
    private const val MIN_REQUIRED_EMULATOR_VERSION = "30.0.9"

    /**
     * Interpolates between two colors.
     */
    @JvmStatic
    private fun interpolate(start: Color, end: Color, @Suppress("SameParameterValue") fraction: Double): Color {
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