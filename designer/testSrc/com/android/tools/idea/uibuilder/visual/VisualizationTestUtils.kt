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
package com.android.tools.idea.uibuilder.visual

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import kotlin.test.assertNotNull

class VisualizationTestToolWindowManager(
  private val project: Project,
  private val disposableParent: Disposable,
) : ToolWindowHeadlessManagerImpl(project) {
  private val idToToolWindow = mutableMapOf<String, ToolWindow>()

  init {
    // In headless mode the toolWindow doesn't register the ToolWindow from extension point. We
    // register them programmatically here.
    val ep =
      ToolWindowEP.EP_NAME.extensions.firstOrNull { ex ->
        ex.id == VisualizationToolWindowFactory.TOOL_WINDOW_ID
      }
    assertNotNull(
      ep,
      "Layout validation tool window (id = ${VisualizationToolWindowFactory.TOOL_WINDOW_ID}) is not registered as plugin",
    )

    val factory = ep.getToolWindowFactory(ep.pluginDescriptor)
    val anchor = ToolWindowAnchor.fromText(ep.anchor ?: ToolWindowAnchor.LEFT.toString())
    registerToolWindow(
      RegisterToolWindowTask(id = ep.id, anchor = anchor, contentFactory = factory)
    )
  }

  override fun registerToolWindow(task: RegisterToolWindowTask): ToolWindow {
    val toolWindow = VisualizationTestToolWindow(project)
    idToToolWindow[task.id] = toolWindow
    task.contentFactory?.createToolWindowContent(project, toolWindow)
    fireStateChange()
    Disposer.register(disposableParent, toolWindow.disposable)
    return toolWindow
  }

  override fun getToolWindow(id: String?): ToolWindow? {
    return idToToolWindow[id]
  }

  private fun fireStateChange() {
    project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).stateChanged(this)
  }
}

/** This window is used to test the change of availability. */
class VisualizationTestToolWindow(project: Project) :
  ToolWindowHeadlessManagerImpl.MockToolWindow(project) {
  private var _isAvailable = false

  override fun setAvailable(available: Boolean, runnable: Runnable?) {
    _isAvailable = available
  }

  override fun setAvailable(value: Boolean) {
    _isAvailable = value
  }

  override fun isAvailable() = _isAvailable

  override fun isDisposed(): Boolean {
    return contentManager.isDisposed
  }

  override fun show(runnable: Runnable?) {
    super.show(runnable)
    runnable?.run()
  }

  override fun hide(runnable: Runnable?) {
    super.hide()
    runnable?.run()
  }
}

object TestVisualizationFormInitializer : VisualizationForm.ContentInitializer {
  override fun initContent(project: Project, form: VisualizationForm, onComplete: () -> Unit) {
    form.createContentPanel()
    onComplete()
  }
}
