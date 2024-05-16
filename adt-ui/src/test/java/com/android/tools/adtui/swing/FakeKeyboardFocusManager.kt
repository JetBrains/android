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
package com.android.tools.adtui.swing

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.awt.Component
import java.awt.DefaultKeyboardFocusManager
import java.awt.KeyboardFocusManager
import java.awt.event.FocusEvent
import java.awt.event.FocusEvent.FOCUS_GAINED
import java.awt.event.FocusEvent.FOCUS_LOST

/**
 * Implementation of [KeyboardFocusManager] intended for tests. Once instantiated, this focus
 * manager replaces the current focus manager. The original focus manager is restored when
 * `parentDisposable` is disposed.
 *
 * Using this class it is possible to manipulate the focus owner and to generate focus owner
 * property change events.
 */
class FakeKeyboardFocusManager(parentDisposable: Disposable) : DefaultKeyboardFocusManager() {
  private var focusOwner: Component? = null

  init {
    replaceKeyboardFocusManager(this, parentDisposable)
  }

  override fun getFocusOwner(): Component? {
    return focusOwner
  }

  fun setFocusOwner(newFocusOwner: Component?) {
    setFocusOwner(newFocusOwner, false)
  }

  fun setFocusOwner(newFocusOwner: Component?, temporary: Boolean, cause: FocusEvent.Cause = FocusEvent.Cause.UNKNOWN) {
    if (newFocusOwner != focusOwner) {
      val oldFocusOwner = focusOwner
      focusOwner = newFocusOwner
      oldFocusOwner?.focusListeners?.forEach { it.focusLost(FocusEvent(oldFocusOwner, FOCUS_LOST, temporary, newFocusOwner, cause)) }
      newFocusOwner?.focusListeners?.forEach { it.focusGained(FocusEvent(newFocusOwner, FOCUS_GAINED, temporary, oldFocusOwner, cause)) }
      firePropertyChange("focusOwner", oldFocusOwner, newFocusOwner)
      if (!temporary) {
        firePropertyChange("permanentFocusOwner", oldFocusOwner, newFocusOwner)
      }
    }
  }

  override fun clearFocusOwner() {
    focusOwner = null
  }
}

/**
 * Replaces the keyboard focus manager with [focusManager]. The original focus manager is restored
 * when [parentDisposable] is disposed.
 */
fun replaceKeyboardFocusManager(focusManager: KeyboardFocusManager, parentDisposable: Disposable) {
  val originalFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
  Disposer.register(parentDisposable) { KeyboardFocusManager.setCurrentKeyboardFocusManager(originalFocusManager) }
  KeyboardFocusManager.setCurrentKeyboardFocusManager(focusManager)
}