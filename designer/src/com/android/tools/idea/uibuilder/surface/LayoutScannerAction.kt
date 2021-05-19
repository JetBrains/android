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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.idea.actions.DesignerActions
import com.android.tools.idea.actions.LAYOUT_SCANNER_KEY
import com.android.tools.idea.actions.NOTIFICATION_KEY
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.ui.alwaysEnableLayoutScanner
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import icons.StudioIcons.LayoutEditor.Toolbar.ACCESSIBILITY
import java.awt.event.MouseEvent
import java.util.concurrent.CompletableFuture

/**
 * Action to toggle accessibility scanner in [NlDesignSurface].
 * For now, all icons are temporary.
 */
class LayoutScannerAction: DumbAwareAction(
  "Run accessibility scanner", "Run accessibility testing framework scanner on current layout",
  ACCESSIBILITY) {

  companion object {
    @JvmStatic
    fun getInstance(): LayoutScannerAction {
      return ActionManager.getInstance().getAction(
        DesignerActions.ACTION_RUN_LAYOUT_SCANNER) as LayoutScannerAction
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = StudioFlags.NELE_LAYOUT_SCANNER_IN_EDITOR.get()
  }

  override fun actionPerformed(e: AnActionEvent) {
    // Show progress notification while scanner is running
    val notificationControl = e.getData(NOTIFICATION_KEY)
    notificationControl?.showNotification("Running accessibility scanner...")

    // run scanner
    e.getData(LAYOUT_SCANNER_KEY)?.runLayoutScanner()?.thenAccept { result ->
      // Scanner finished
      notificationControl?.hideNotification()
      if (!result) {
        // No result to show.
        displayBalloon(e)
      }
    }
  }

  private fun displayBalloon(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT) ?: return
    // No result to show.
    if (e.inputEvent is MouseEvent) {
      val pos = (e.inputEvent as MouseEvent).locationOnScreen
      val messageType = MessageType.INFO

      val balloon = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(
        "Scanning finished, no accessibility issues found",
        null,
        messageType.titleForeground,
        messageType.popupBackground,
        null)
        .setBorderColor(messageType.borderColor)
        .setShadow(false) // Shadow jumps around when balloon appears. Disable it.
        .setDisposable(project)
        .createBalloon()

      val relativePoint = RelativePoint(pos)
      balloon.show(relativePoint, Balloon.Position.below)
    }
  }
}

/**
 * Controller for layout scanner that checks the layout and produces lint checks.
 * It runs Accessibility Testing Framework.
 */
interface LayoutScannerControl {
  /** Return the scanner capable of checking the layout. */
  val scanner: NlLayoutScanner

  /**
   * Trigger the scanner, and show lint results.
   * @return future that returns true if the scan was successful. False if no result was available to show.
   * The returned future is one off. Meaning if the function is called multiple times before previous futures
   * are completed, the previous futures will be ignored and only the newest future will receive the event.
   */
  fun runLayoutScanner(): CompletableFuture<Boolean>
}

/** Configuration for layout scanner */
interface LayoutScannerConfiguration {

  /** Returns true if it layout scanner should be enabled. False otherwise. */
  var isLayoutScannerEnabled: Boolean

  companion object {

    /** Configuration for when layout scanner is not applicable. */
    @JvmStatic
    val DISABLED = object: LayoutScannerConfiguration {
      override var isLayoutScannerEnabled: Boolean
        get() = false
        set(value) { }
    }
  }
}

/** Configuration for when layout scanner is available. */
class LayoutScannerEnabled : LayoutScannerConfiguration {

  private var isEnabled = false

  override var isLayoutScannerEnabled: Boolean
    get() = alwaysEnableLayoutScanner || isEnabled
    set(value) {
      isEnabled = value
    }
}