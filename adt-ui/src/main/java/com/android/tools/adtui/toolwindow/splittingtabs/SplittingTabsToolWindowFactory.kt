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
package com.android.tools.adtui.toolwindow.splittingtabs

import com.android.tools.adtui.toolwindow.splittingtabs.actions.NewTabAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import javax.swing.JComponent

abstract class SplittingTabsToolWindowFactory : ToolWindowFactory {
  override fun init(toolWindow: ToolWindow) {
    toolWindow.setToHideOnEmptyContent(true)
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val contentManager = toolWindow.contentManager
    (toolWindow as ToolWindowEx).setTabActions(
      NewTabAction(SplittingTabsBundle.lazyMessage("SplittingTabsToolWindow.newTab")) { createNewTab(contentManager) })
    createNewTab(contentManager)
  }

  abstract fun generateTabName(tabNames: Set<String>): String

  abstract fun generateChildComponent(): JComponent

  private fun createNewTab(contentManager: ContentManager, requestFocus: Boolean = false) {
    val content = createContent(contentManager)
    contentManager.addContent(content)
    contentManager.setSelectedContent(content, requestFocus)
  }

  private fun createContent(contentManager: ContentManager): Content {
    val tabName = generateTabName(contentManager.contents.mapTo(hashSetOf()) { it.displayName })
    return contentManager.factory.createContent(null, tabName, false).also {
      val component = generateChildComponent()
      if (component is Disposable) {
        Disposer.register(it, component)
      }
      it.component = component
      it.isCloseable = true
      it.setIsSplittingTab()
    }
  }
}