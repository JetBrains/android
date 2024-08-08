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
package com.android.tools.idea.streaming.core

import com.android.SdkConstants
import com.android.annotations.concurrency.AnyThread
import com.android.repository.Revision
import com.android.repository.api.RepoManager.RepoLoadedListener
import com.android.repository.impl.meta.RepositoryPackages
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.stdui.StandardColors
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.streaming.DeviceMirroringSettings
import com.android.tools.idea.streaming.DeviceMirroringSettingsListener
import com.android.tools.idea.streaming.EmulatorSettings
import com.android.tools.idea.streaming.EmulatorSettingsListener
import com.android.tools.idea.streaming.device.settings.DeviceMirroringSettingsPage
import com.android.tools.idea.streaming.emulator.settings.EmulatorSettingsPage
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.IncorrectOperationException
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIllustrations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import javax.swing.SwingConstants
import javax.swing.event.HyperlinkEvent

private const val MIN_REQUIRED_EMULATOR_VERSION = "35.1.3"

// As recommended at https://jetbrains.github.io/ui/principles/empty_state/#21.
private const val TOP_MARGIN = 0.45
private const val SIDE_MARGIN = 0.15

/**
 * The panel that is shown in the Running Devices tool window when there are no running
 * embedded emulators and no mirrored devices.
 */
internal class EmptyStatePanel(
  private val project: Project,
  disposableParent: Disposable
): JBPanel<EmptyStatePanel>(GridBagLayout()), Disposable {

  private val emulatorLaunchesInToolWindow: Boolean
    get()= EmulatorSettings.getInstance().launchInToolWindow
  private val activateOnConnection: Boolean
    get() = DeviceMirroringSettings.getInstance().activateOnConnection
  private var emulatorVersionIsInsufficient: Boolean

  init {
    Disposer.register(disposableParent, this)

    isOpaque = true
    background = StandardColors.BACKGROUND_COLOR
    border = JBUI.Borders.empty()
    // Allow the panel to receive focus so that the framework considers the tool window active (b/157181475).
    isFocusable = true

    emulatorVersionIsInsufficient = false

    addMouseListener(object : MouseAdapter() {
      override fun mousePressed(event: MouseEvent) {
        requestFocusInWindow()
      }
    })

    val messageBusConnection = project.messageBus.connect(this)
    messageBusConnection.subscribe(EmulatorSettingsListener.TOPIC, EmulatorSettingsListener { updateContent() })
    messageBusConnection.subscribe(DeviceMirroringSettingsListener.TOPIC, DeviceMirroringSettingsListener { updateContent() })

    val progress = StudioLoggerProgressIndicator(EmptyStatePanel::class.java)
    Disposer.register(this) {
      progress.cancel()
    }
    val scope = createCoroutineScope(Dispatchers.IO)
    scope.launch {
      asyncActivityCount?.incrementAndGet() // Keep track of asynchronous activities for tests.
      try {
        val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
        val sdkManager = sdkHandler.getSdkManager(progress)
        val listener = RepoLoadedListener { packages -> localPackagesUpdated(packages) }
        try {
          Disposer.register(this@EmptyStatePanel) { sdkManager.removeLocalChangeListener(listener) }
          sdkManager.addLocalChangeListener(listener)
          localPackagesUpdated(sdkManager.packages)
        }
        catch (_: IncorrectOperationException) {
          // Disposed already.
        }
      }
      finally {
        asyncActivityCount?.decrementAndGet()
      }
    }

    updateContent()
  }

  @AnyThread
  private fun localPackagesUpdated(packages: RepositoryPackages) {
    val emulatorPackage = packages.localPackages[SdkConstants.FD_EMULATOR] ?: return
    UIUtil.invokeLaterIfNeeded { // This is safe because this code doesn't touch PSI or VFS.
      val insufficient = emulatorPackage.version < Revision.parseRevision(MIN_REQUIRED_EMULATOR_VERSION)
      if (emulatorVersionIsInsufficient != insufficient) {
        emulatorVersionIsInsufficient = insufficient
        updateContent()
      }
    }
  }

  private fun createContent() {
    val linkColorString = (JBUI.CurrentTheme.Link.Foreground.ENABLED.rgb and 0xFFFFFF).toString(16)
    val titleColorString = (AdtUiUtils.TITLE_COLOR.rgb and 0xFFFFFF).toString(16)
    val plusSign = "<font color = $titleColorString size=\"+1\"><b>&#xFF0B;</b></font>"
    val virtualFragment: String = when {
      emulatorVersionIsInsufficient ->
        "To launch virtual devices in this window, install Android Emulator $MIN_REQUIRED_EMULATOR_VERSION or higher. " +
        "Please <font color = $linkColorString><a href='CheckForUpdate'>check for&nbsp;updates</a></font> and install " +
        "the&nbsp;latest version of the&nbsp;Android&nbsp;Emulator."

      emulatorLaunchesInToolWindow ->
        "To launch a&nbsp;virtual device, click $plusSign and select the device from the list, or use the&nbsp;" +
        "<font color = $linkColorString><a href='DeviceManager'>Device&nbsp;Manager</a></font>."

      else ->
        "To launch a&nbsp;virtual device, click $plusSign and select a virtual device, or select " +
        "the&nbsp;<b>Launch in the&nbsp;Running&nbsp;Devices tool window</b> option in&nbsp;the&nbsp;" +
        "<font color = $linkColorString><a href='EmulatorSettings'>Emulator&nbsp;settings</a></font> " +
        "and use the&nbsp;<font color = $linkColorString><a href='DeviceManager'>Device&nbsp;Manager</a></font>."
    }
    val physicalFragment: String = when {
      activateOnConnection ->
        "To mirror a&nbsp;physical device, connect it via USB cable or over WiFi."

      else ->
        "To mirror a&nbsp;physical device, connect it via USB cable or over WiFi, click $plusSign and select the&nbsp;device from " +
        "the&nbsp;list. You may also select the&nbsp;<b>Activate mirroring when a&nbsp;new physical device is connected</b> option " +
        "in&nbsp;the&nbsp;<font color = $linkColorString><a href='DeviceMirroringSettings'>Device&nbsp;Mirroring&nbsp;settings</a></font>."
    }
    val html =
      """
      <center>
      <p>$physicalFragment</p>
      <p/>
      <p>$virtualFragment</p>
      </center>
      """.trimIndent()

    val hyperlinkAction = HyperlinkEventAction { event ->
      if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
        when (event.description) {
          "DeviceManager" -> {
            // Action id is from com.android.tools.idea.devicemanager.DeviceManagerAction.
            val action = ActionManager.getInstance().getAction(
              if (StudioFlags.UNIFIED_DEVICE_MANAGER_ENABLED.get()) "Android.DeviceManager2" else "Android.DeviceManager")
            ActionUtil.invokeAction(action, SimpleDataContext.getProjectContext(project), ActionPlaces.UNKNOWN, null, null)
          }
          "CheckForUpdate" -> {
            val action = ActionManager.getInstance().getAction("CheckForUpdate")
            ActionUtil.invokeAction(action, SimpleDataContext.getProjectContext(project), ActionPlaces.UNKNOWN, null, null)
          }
          "EmulatorSettings" -> {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, EmulatorSettingsPage::class.java)
          }
          "DeviceMirroringSettings" -> {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, DeviceMirroringSettingsPage::class.java)
          }
        }
      }
    }

    var textComponent: JComponent? = null
    panel {
      row {
        text(text = html, action = hyperlinkAction).applyToComponent {
          font = AdtUiUtils.EMPTY_TOOL_WINDOW_FONT
          foreground = StandardColors.PLACEHOLDER_TEXT_COLOR
          textComponent = this
        }
      }
    }

    val c = GridBagConstraints().apply {
      fill = GridBagConstraints.BOTH
      gridx = 1
      gridy = 0
      weightx = 1 - SIDE_MARGIN * 2
      weighty = TOP_MARGIN
    }
    val icon = JBLabel(StudioIllustrations.Common.DEVICES_LINEUP).apply {
      horizontalAlignment = SwingConstants.CENTER
      verticalAlignment = SwingConstants.BOTTOM
      border = JBUI.Borders.emptyBottom(16)
    }
    add(icon, c)

    c.apply {
      gridx = 1
      gridy = 1
      weighty = 1 - TOP_MARGIN
    }
    add(textComponent!!, c)

    c.apply {
      gridx = 0
      weightx = SIDE_MARGIN
    }
    add(createSpacer(), c)

    c.apply {
      gridx = 2
    }
    add(createSpacer(), c)
  }

  private fun createSpacer(): JBPanel<*> {
    return JBPanel<JBPanel<*>>()
      .withBorder(JBUI.Borders.empty())
      .withMinimumWidth(0)
      .withMinimumHeight(0)
      .withPreferredSize(JBUI.scale(24), 0)
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

  companion object {
    @TestOnly
    internal val asyncActivityCount: AtomicInteger? = if (ApplicationManager.getApplication().isUnitTestMode) AtomicInteger() else null
  }
}