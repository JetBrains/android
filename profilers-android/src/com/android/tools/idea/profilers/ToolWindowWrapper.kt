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
package com.android.tools.idea.profilers

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentManager
import javax.swing.Icon

/** Wrapper of [ToolWindow] that abstracts away Intellij's tool window infrastructure from profiler code. */
interface ToolWindowWrapper {
  fun setTitle(title: String?)

  fun setIcon(icon: Icon)

  fun getContentManager(): ContentManager

  fun removeContent()
}

class ToolWindowWrapperImpl(project: Project, private val toolWindow: ToolWindow) : ToolWindowWrapper {
  private val manager = ToolWindowManager.getInstance(project)

  override fun setTitle(title: String?) {
    toolWindow.title = title
  }

  override fun setIcon(icon: Icon) {
      toolWindow.setIcon(icon)
  }

  override fun getContentManager() = toolWindow.contentManager

  override fun removeContent() {
    AndroidProfilerToolWindowFactory.removeContent(toolWindow)
  }
}
