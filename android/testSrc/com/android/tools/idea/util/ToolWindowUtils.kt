/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import com.intellij.ui.content.ContentManager
import com.intellij.util.concurrency.ThreadingAssertions

class TestToolWindowManager(private val project: Project) : ToolWindowHeadlessManagerImpl(project) {
  private val idToToolWindow = mutableMapOf<String, ToolWindow>()

  override fun doRegisterToolWindow(id: String): ToolWindow {
    val window = TestToolWindow(project)
    idToToolWindow[id] = window
    return window
  }

  override fun getToolWindow(id: String?): ToolWindow? {
    return idToToolWindow[id]
  }
}

/** This window is used to test the change of availability. */
class TestToolWindow(project: Project) : ToolWindowHeadlessManagerImpl.MockToolWindow(project) {
  private var _isAvailable = false
  private var _isVisible = false
  private var _isFocused = false
  private var _isActivated = false
  private var isContentManagerInitialized = false

  override fun setAvailable(available: Boolean, runnable: Runnable?) {
    _isAvailable = available
  }

  override fun setAvailable(value: Boolean) {
    setAvailable(value, null)
  }

  override fun isAvailable() = _isAvailable

  override fun isVisible() = _isVisible

  override fun isActive(): Boolean {
    return _isActivated
  }

  override fun show() {
    show(null)
  }

  override fun activate(runnable: Runnable?, autoFocusContents: Boolean) {
    _isActivated = true
    runnable?.run()
    _isFocused = autoFocusContents
  }

  override fun activate(runnable: Runnable?, autoFocusContents: Boolean, forced: Boolean) =
    activate(runnable, autoFocusContents)

  override fun show(runnable: Runnable?) {
    _isVisible = true
    runnable?.run()
  }

  override fun hide() {
    hide(null)
  }

  override fun hide(runnable: Runnable?) {
    _isVisible = false
    runnable?.run()
  }

  fun isFocused(): Boolean {
    return isVisible && _isFocused
  }

  override fun isDisposed(): Boolean {
    return contentManager.isDisposed
  }

  override fun getContentManager(): ContentManager {
    if (!isContentManagerInitialized) {
      isContentManagerInitialized = true
      ThreadingAssertions.assertEventDispatchThread()
    }
    return super.getContentManager()
  }

  override fun getContentManagerIfCreated(): ContentManager? {
    return if (isContentManagerInitialized) {
      super.getContentManagerIfCreated()
    } else {
      null
    }
  }
}
