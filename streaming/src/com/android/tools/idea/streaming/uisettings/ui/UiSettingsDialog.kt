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
package com.android.tools.idea.streaming.uisettings.ui

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.PopupBorder
import com.intellij.ui.WindowMoveListener
import com.intellij.ui.WindowRoundedCornersManager
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Container
import java.awt.KeyboardFocusManager
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HORIZONTAL_MARGIN = 20
private const val VERTICAL_MARGIN = 8
private const val SEPARATOR_MARGIN = 4

private var instanceCounter = 0

/**
 * Display the UiSettingsDialog
 */
internal fun showUiSettingsDialog(
  project: Project,
  model: UiSettingsModel,
  deviceType: DeviceType,
  parentDisposable: Disposable
): UiSettingsDialog {
  val dialog = UiSettingsDialog(project, model, deviceType)
  dialog.logAround("show") {
    dialog.show()
  }
  dialog.logAround("handleCleanUp") {
    dialog.handleCleanUp(parentDisposable)
  }
  if (StudioFlags.UI_SETTINGS_B475894230_LOGGING.get()) {
    dialog.delayedOpenCheck()
  }
  return dialog
}

/**
 * A dialog for displaying setting shortcuts.
 */
internal class UiSettingsDialog(
  project: Project,
  model: UiSettingsModel,
  deviceType: DeviceType
) : DialogWrapper(project, null, false, IdeModalityType.MODELESS, false) {
  private val header = UiSettingsHeader(model)
  private val panel = UiSettingsPanel(model, deviceType)
  private val instance = ++instanceCounter

  init {
    logAround("init") {
      init()
    }
  }

  override fun init() {
    super.init()
    setUndecorated(true)
    rootPane.windowDecorationStyle = JRootPane.NONE
    rootPane.border = PopupBorder.Factory.create(true, true)
    header.border = JBUI.Borders.compound(
      JBUI.Borders.customLineBottom(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
      JBUI.Borders.empty(VERTICAL_MARGIN, HORIZONTAL_MARGIN, SEPARATOR_MARGIN, HORIZONTAL_MARGIN)
    )
    panel.border = JBUI.Borders.empty(SEPARATOR_MARGIN, HORIZONTAL_MARGIN, VERTICAL_MARGIN, HORIZONTAL_MARGIN)
    with(contentPanel) {
      isFocusCycleRoot = true
      isFocusTraversalPolicyProvider = true
      focusTraversalPolicy = object : LayoutFocusTraversalPolicy() {
        override fun getFirstComponent(container: Container): Component? {
          val first = super.getFirstComponent(container) ?: return null
          val from = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
          val fromOutside = from == null || !SwingUtilities.isDescendingFrom(from, container)
          return if (first.name == RESET_TITLE && fromOutside) super.getComponentAfter(container, first) else first
        }
      }
    }
    WindowRoundedCornersManager.configure(this)

    // WindowMoveListener allows the window to be moved by dragging the panel.
    val moveListener = WindowMoveListener(contentPanel)
    moveListener.installTo(panel)
    moveListener.installTo(header)

    Disposer.register(disposable) {
      moveListener.uninstallFrom(panel)
      moveListener.uninstallFrom(header)
    }

    pack()
  }

  override fun createContentPaneBorder() = JBUI.Borders.empty()
  override fun createNorthPanel(): JComponent = header
  override fun createCenterPanel(): JComponent = panel
  override fun createActions(): Array<Action> = emptyArray()

  fun handleCleanUp(parentDisposable: Disposable) {
    // Close the dialog if the dialog loses focus:
    val windowListener = object : WindowAdapter() {
      override fun windowLostFocus(event: WindowEvent) {
        logAround("WindowAdapter.focusLost") {
          close(OK_EXIT_CODE)
        }
      }

      override fun windowGainedFocus(event: WindowEvent) {
        log("WindowAdapter.focusGained")
      }
    }
    window.addWindowFocusListener(windowListener)

    registerCleanup(disposable) {
      logAround("removeWindowFocusListener") {
        window.removeWindowFocusListener(windowListener)
      }
    }
    registerCleanup(parentDisposable) {
      logAround("parentDisposable.close") {
        close(OK_EXIT_CODE)
      }
    }
  }

  private fun registerCleanup(parent: Disposable, child: Disposable) {
    val alreadyDisposed = !Disposer.tryRegister(parent, child)
    if (StudioFlags.UI_SETTINGS_B475894230_LOGGING.get()) {
      log("registerCleanup alreadyDisposed: $alreadyDisposed")
    }
    if (alreadyDisposed) {
      Disposer.dispose(child)
    }
  }

  fun logAround(operation: String, block: UiSettingsDialog.() -> Unit) {
    if (StudioFlags.UI_SETTINGS_B475894230_LOGGING.get()) {
      var ex: Throwable? = null
      log("Before $operation")
      try {
        block()
      } catch (t: Throwable) {
        ex = t
      }
      log("After $operation", ex)
      ex?.let { throw it }
    } else {
      block()
    }
  }

  private fun log(text: String, ex: Throwable? = null) {
    if (StudioFlags.UI_SETTINGS_B475894230_LOGGING.get()) {
      thisLogger().info("Instance: $instance, Thread: ${Thread.currentThread().name}, $text, Window visible: ${window.isVisible}, focusOwner: ${KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner}, panel added to window: ${SwingUtilities.getWindowAncestor(panel) != null}", ex)
    }
  }

  fun delayedOpenCheck() {
    // The actual dialog is created via a WriteIntentReadAction. Check if the dialog was really
    // created and is visible.
    val scope = disposable.createCoroutineScope()
    scope.launch {
      delay(4.seconds)
      log("DelayedOpenCheck")
    }
  }
}
