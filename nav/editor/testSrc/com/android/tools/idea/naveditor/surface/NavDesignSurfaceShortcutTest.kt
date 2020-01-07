/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.naveditor.surface

import com.android.tools.adtui.actions.ZoomActualAction
import com.android.tools.adtui.actions.ZoomInAction
import com.android.tools.adtui.actions.ZoomOutAction
import com.android.tools.adtui.actions.ZoomToFitAction
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.uibuilder.LayoutTestUtilities.findActionForKey
import com.intellij.openapi.util.SystemInfo
import java.awt.Event
import java.awt.Event.SHIFT_MASK
import java.awt.event.KeyEvent

private val ACTION_MASK = if (SystemInfo.isMac) Event.META_MASK else Event.CTRL_MASK

class NavDesignSurfaceShortcutTest : NavTestCase() {
  fun testZoomIn() {
    val zoomClass = ZoomInAction::class.java
    testActionForKey(KeyEvent.VK_PLUS, ACTION_MASK, zoomClass)
    testActionForKey(KeyEvent.VK_PLUS, ACTION_MASK + SHIFT_MASK, zoomClass)
    testActionForKey(KeyEvent.VK_EQUALS, ACTION_MASK, zoomClass)
    testActionForKey(KeyEvent.VK_EQUALS, ACTION_MASK + SHIFT_MASK, zoomClass)
    testActionForKey(KeyEvent.VK_ADD, ACTION_MASK, zoomClass)
    testActionForKey(KeyEvent.VK_ADD, ACTION_MASK + SHIFT_MASK, zoomClass)
  }

  fun testZoomOut() {
    val zoomClass = ZoomOutAction::class.java
    testActionForKey(KeyEvent.VK_MINUS, ACTION_MASK, zoomClass)
    testActionForKey(KeyEvent.VK_MINUS, ACTION_MASK + SHIFT_MASK, zoomClass)
    testActionForKey(KeyEvent.VK_UNDERSCORE, ACTION_MASK, zoomClass)
    testActionForKey(KeyEvent.VK_UNDERSCORE, ACTION_MASK + SHIFT_MASK, zoomClass)
    testActionForKey(KeyEvent.VK_SUBTRACT, ACTION_MASK, zoomClass)
    testActionForKey(KeyEvent.VK_SUBTRACT, ACTION_MASK + SHIFT_MASK, zoomClass)
  }

  fun testZoomToFit() {
    val zoomClass = ZoomToFitAction::class.java
    testActionForKey(KeyEvent.VK_0, ACTION_MASK, zoomClass)
    testActionForKey(KeyEvent.VK_0, ACTION_MASK + SHIFT_MASK, zoomClass)
    testActionForKey(KeyEvent.VK_RIGHT_PARENTHESIS, ACTION_MASK, zoomClass)
    testActionForKey(KeyEvent.VK_RIGHT_PARENTHESIS, ACTION_MASK + SHIFT_MASK, zoomClass)
    testActionForKey(KeyEvent.VK_NUMPAD0, ACTION_MASK, zoomClass)
    testActionForKey(KeyEvent.VK_NUMPAD0, ACTION_MASK + SHIFT_MASK, zoomClass)
  }

  fun testZoomToActual() {
    val zoomClass = ZoomActualAction::class.java
    testActionForKey(KeyEvent.VK_SLASH, ACTION_MASK, zoomClass)
    testActionForKey(KeyEvent.VK_SLASH, ACTION_MASK + SHIFT_MASK, zoomClass)
    testActionForKey(KeyEvent.VK_DIVIDE, ACTION_MASK, zoomClass)
    testActionForKey(KeyEvent.VK_DIVIDE, ACTION_MASK + SHIFT_MASK, zoomClass)
  }

  private fun <T> testActionForKey(keyCode: Int, modifiers: Int, aClass: Class<T>) {
    val surface = NavDesignSurface(project, myRootDisposable)
    val action = findActionForKey(surface, keyCode, modifiers)
    assertInstanceOf(action, aClass)
  }
}