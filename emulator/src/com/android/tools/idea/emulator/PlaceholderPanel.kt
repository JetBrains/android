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
import com.android.tools.adtui.stdui.StandardColors
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.emulator.settings.EmulatorSettingsUi
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.htmlComponent
import com.intellij.util.ui.JBUI
import org.jetbrains.android.actions.RunAndroidAvdManagerAction
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.MouseEvent
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

private const val MIN_REQUIRED_EMULATOR_VERSION = "30.2.5"

// As recommended at https://jetbrains.github.io/ui/principles/empty_state/#21.
private const val TOP_MARGIN = 0.45
private const val SIDE_MARGIN = 0.15

/**
 * Placeholder panel that is shown in the Emulator tool window when no embedded Emulators are running.
 */
internal class PlaceholderPanel(project: Project): JBPanel<PlaceholderPanel>(GridBagLayout()), Disposable {

  val title
    get() = "No Running Emulators"

  private var emulatorLaunchesInToolWindow: Boolean
  private var emulatorVersionIsSufficient: Boolean
  private var hyperlinkListener: HyperlinkListener

  init {
    Disposer.register(project.earlyDisposable, this)

    isOpaque = true
    background = StandardColors.BACKGROUND_COLOR
    border = JBUI.Borders.empty()
    // Allow the panel to receive focus so that the framework considers the tool window active (b/157181475).
    isFocusable = true

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

    executeOnPooledThread {
      val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
      val progress: ProgressIndicator = StudioLoggerProgressIndicator(AvdManagerConnection::class.java)
      val sdkManager = sdkHandler.getSdkManager(progress)
      val listener = RepoLoadedListener { packages -> localPackagesUpdated(packages) }
      sdkManager.addLocalChangeListener(listener)
      Disposer.register(this) { sdkManager.removeLocalChangeListener(listener) }

      localPackagesUpdated(sdkManager.packages)
    }

    updateContent()
  }

  @AnyThread
  private fun localPackagesUpdated(packages: RepositoryPackages) {
    val emulatorPackage = packages.localPackages[SdkConstants.FD_EMULATOR] ?: return
    invokeLaterInAnyModalityState {
      val sufficient = emulatorPackage.version >= Revision.parseRevision(MIN_REQUIRED_EMULATOR_VERSION)
      if (emulatorVersionIsSufficient != sufficient) {
        emulatorVersionIsSufficient = sufficient
        updateContent()
      }
    }
  }

  private fun createContent() {
    val linkColorString = (JBUI.CurrentTheme.Link.linkColor().rgb and 0xFFFFFF).toString(16)
    val html = if (emulatorLaunchesInToolWindow) {
      if (emulatorVersionIsSufficient) {
        """
        <center>
        No emulators are currently running.
        To&nbsp;launch an&nbsp;emulator, use the&nbsp;<font color = $linkColorString><a href=''>AVD&nbsp;Manager</a></font>
        or run your app while targeting a&nbsp;virtual device.
        </center>
        """.trimIndent()
      }
      else {
        """
        <center>
        To use the Android Emulator in this
        window, install version $MIN_REQUIRED_EMULATOR_VERSION or higher.
        Please <font color = $linkColorString><a href=''>check for&nbsp;updates</a></font> and install
        the&nbsp;latest version of the&nbsp;Android&nbsp;Emulator.
        </center>
        """.trimIndent()
      }
    }
    else {
      """
      <center>
      The&nbsp;Android Emulator is currently configured
      to run as a&nbsp;standalone application. To&nbsp;make
      the&nbsp;Android Emulator launch in this window
      instead, select the&nbsp;<i>Launch in a&nbsp;tool window</i>
      option in the&nbsp;<font color = $linkColorString><a href=''>Emulator&nbsp;settings</a></font>.
      </center>
      """.trimIndent()
    }

    val text = htmlComponent(text = html,
                             lineWrap = true,
                             font = JBUI.Fonts.label(13f),
                             foreground = StandardColors.PLACEHOLDER_TEXT_COLOR,
                             hyperlinkListener = hyperlinkListener).apply {
      isOpaque = false
      isFocusable = false
      border = JBUI.Borders.empty()
    }

    val c = GridBagConstraints().apply {
      fill = GridBagConstraints.BOTH
      gridx = 1
      gridy = 0
      weightx = 1 - SIDE_MARGIN * 2
      weighty = TOP_MARGIN
    }
    add(createSpacer(), c)

    c.apply {
      gridx = 0
      gridy = 1
      weightx = SIDE_MARGIN
      weighty = 1 - TOP_MARGIN
    }
    add(createSpacer(), c)

    c.apply {
      gridx = 2
    }
    add(createSpacer(), c)

    c.apply {
      gridx = 1
    }
    add(text, c)
  }

  private fun createSpacer(): JBPanel<*> {
    return JBPanel<JBPanel<*>>()
      .withBorder(JBUI.Borders.empty())
      .withMinimumWidth(0)
      .withMinimumHeight(0)
      .withPreferredSize(0, 0)
      .andTransparent()
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
}