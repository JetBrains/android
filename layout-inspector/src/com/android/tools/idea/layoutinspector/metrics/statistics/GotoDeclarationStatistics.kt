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
package com.android.tools.idea.layoutinspector.metrics.statistics

import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorGotoDeclaration
import com.intellij.openapi.actionSystem.AnActionEvent
import java.awt.event.KeyEvent

/**
 * Accumulator for Goto Declaration events.
 */
class GotoDeclarationStatistics {
  /**
   * How many times was the popup menu for "Goto Declaration" used
   */
  private var menuActionClicks = 0

  /**
   * How many times was the keyboard shortcut for "Goto Declaration" used
   */
  private var actionShortcutKeyStrokes = 0

  /**
   * How many times was double click for "Goto Declaration" used
   */
  private var doubleClicks = 0

  /**
   * Start a new session by resetting all counters.
   */
  fun start() {
    menuActionClicks = 0
    actionShortcutKeyStrokes = 0
    doubleClicks = 0
  }

  /**
   * Save the session data recorded since [start].
   */
  fun save(dataSupplier: () -> DynamicLayoutInspectorGotoDeclaration.Builder) {
    if (menuActionClicks > 0 || actionShortcutKeyStrokes > 0 || doubleClicks > 0) {
      dataSupplier().let {
        it.clicksMenuAction = menuActionClicks
        it.keyStrokesShortcut = actionShortcutKeyStrokes
        it.doubleClicks = doubleClicks
      }
    }
  }

  fun gotoSourceFromTreeActionMenu(event: AnActionEvent) {
    if (event.inputEvent is KeyEvent) {
      actionShortcutKeyStrokes++
    }
    else {
      menuActionClicks++
    }
  }

  fun gotoSourceFromDoubleClick() {
    doubleClicks++
  }
}
