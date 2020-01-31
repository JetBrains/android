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
package com.android.tools.idea.tests.gui.framework.fixture.newpsd

import org.fest.swing.core.MouseButton
import org.fest.swing.core.Robot
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.Window
import javax.swing.JPopupMenu

class ReliableRobot(private val baseRobot: Robot) : Robot by baseRobot {
  override fun waitForIdle() {
    com.android.tools.idea.tests.gui.framework.fixture.newpsd.waitForIdle()
  }

  override fun close(w: Window) {
    baseRobot.close(w)
    waitForIdle()
  }

  override fun click(c: Component) {
    baseRobot.click(c)
    waitForIdle()
  }

  override fun click(c: Component, button: MouseButton) {
    baseRobot.click(c, button)
    waitForIdle()
  }

  override fun doubleClick(c: Component) {
    baseRobot.doubleClick(c)
    waitForIdle()
  }

  override fun click(c: Component, button: MouseButton, times: Int) {
    baseRobot.click(c, button, times)
    waitForIdle()
  }

  override fun click(c: Component, where: Point) {
    baseRobot.click(c, where)
    waitForIdle()
  }

  override fun click(c: Component, where: Point, button: MouseButton, times: Int) {
    baseRobot.click(c, where, button, times)
    waitForIdle()
  }

  override fun click(where: Point, button: MouseButton, times: Int) {
    baseRobot.click(where, button, times)
    waitForIdle()
  }

  override fun enterText(text: String) {
    baseRobot.enterText(text)
    waitForIdle()
  }

  override fun focus(c: Component) {
    baseRobot.focus(c)
    waitForIdle()
  }

  override fun focusAndWaitForFocusGain(c: Component) {
    baseRobot.focusAndWaitForFocusGain(c)
    waitForIdle()
  }

  override fun moveMouse(c: Component) {
    baseRobot.moveMouse(c)
    waitForIdle()
  }

  override fun moveMouse(c: Component, p: Point) {
    baseRobot.moveMouse(c, p)
    waitForIdle()
  }

  override fun moveMouse(c: Component, x: Int, y: Int) {
    baseRobot.moveMouse(c, x, y)
    waitForIdle()
  }

  override fun moveMouse(p: Point) {
    baseRobot.moveMouse(p)
    waitForIdle()
  }

  override fun moveMouse(x: Int, y: Int) {
    baseRobot.moveMouse(x, y)
    waitForIdle()
  }

  override fun jitter(c: Component) {
    baseRobot.jitter(c)
    waitForIdle()
  }

  override fun jitter(c: Component, where: Point) {
    baseRobot.jitter(c, where)
    waitForIdle()
  }

  override fun pressMouse(button: MouseButton) {
    baseRobot.pressMouse(button)
    waitForIdle()
  }

  override fun pressMouse(c: Component, where: Point) {
    baseRobot.pressMouse(c, where)
    waitForIdle()
  }

  override fun pressMouse(c: Component, where: Point, button: MouseButton) {
    baseRobot.pressMouse(c, where, button)
    waitForIdle()
  }

  override fun pressMouse(where: Point, button: MouseButton) {
    baseRobot.pressMouse(where, button)
    waitForIdle()
  }

  override fun pasteText(text: String) {
    baseRobot.pasteText(text)
    waitForIdle()
  }

  override fun pressAndReleaseKey(keyCode: Int, vararg modifiers: Int) {
    baseRobot.pressAndReleaseKey(keyCode, *modifiers)
    waitForIdle()
  }

  override fun pressAndReleaseKeys(vararg keyCodes: Int) {
    baseRobot.pressAndReleaseKeys(*keyCodes)
    waitForIdle()
  }

  override fun pressKey(keyCode: Int) {
    baseRobot.pressKey(keyCode)
    waitForIdle()
  }

  override fun pressModifiers(modifierMask: Int) {
    baseRobot.pressModifiers(modifierMask)
    waitForIdle()
  }

  override fun showWindow(w: Window) {
    baseRobot.showWindow(w)
    waitForIdle()
  }

  override fun showWindow(w: Window, size: Dimension) {
    baseRobot.showWindow(w, size)
    waitForIdle()
  }

  override fun showWindow(w: Window, size: Dimension?, pack: Boolean) {
    baseRobot.showWindow(w, size, pack)
    waitForIdle()
  }

  override fun rightClick(c: Component) {
    baseRobot.rightClick(c)
    waitForIdle()
  }

  override fun releaseMouse(button: MouseButton) {
    baseRobot.releaseMouse(button)
    waitForIdle()
  }

  override fun releaseMouseButtons() {
    baseRobot.releaseMouseButtons()
    waitForIdle()
  }

  override fun rotateMouseWheel(c: Component, amount: Int) {
    baseRobot.rotateMouseWheel(c, amount)
    waitForIdle()
  }

  override fun rotateMouseWheel(amount: Int) {
    baseRobot.rotateMouseWheel(amount)
    waitForIdle()
  }

  override fun typeText(text: String) {
    baseRobot.typeText(text)
    waitForIdle()
  }

  override fun type(character: Char) {
    baseRobot.type(character)
    waitForIdle()
  }

  override fun releaseKey(keyCode: Int) {
    baseRobot.releaseKey(keyCode)
    waitForIdle()
  }

  override fun releaseModifiers(modifierMask: Int) {
    baseRobot.releaseModifiers(modifierMask)
    waitForIdle()
  }

  override fun showPopupMenu(invoker: Component): JPopupMenu {
    return baseRobot
      .showPopupMenu(invoker)
      .also {
        waitForIdle()
      }
  }

  override fun showPopupMenu(invoker: Component, location: Point): JPopupMenu {
    return baseRobot
      .showPopupMenu(invoker, location)
      .also {
        waitForIdle()
      }
  }
}