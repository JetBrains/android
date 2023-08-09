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
package com.android.tools.idea.project

import com.android.tools.idea.isAndroidEnvironment
import com.android.tools.idea.sdk.AndroidEnvironmentChecker
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.ext.LibraryDependentToolWindow
import com.intellij.toolWindow.findIconFromBean
import com.intellij.toolWindow.getStripeTitleSupplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A [ProjectActivity] that initializes [LibraryDependentToolWindow]s early.
 *
 * Normally, LibraryDependentToolWindow's don't get initialized until after Gradle sync is completed.
 *
 * Android plugin has some tool windows that don't depend on Gradle sync if the application is Android Studio (rather than IntelliJ). These
 * tool windows can and should be initialized early.
 */
internal class LibraryToolWindowInitializer : ProjectActivity {

  /**
   * Initialize [LibraryDependentToolWindow]s early if running in Android Studio environment.
   *
   * Based on `applyWindowsState()` in `LibraryDependentToolWindowManager.kt`.
   */
  override suspend fun execute(project: Project) {
    if (!isAndroidEnvironment(project)) {
      return
    }
    val toolWindowManager = ToolWindowManagerEx.getInstanceEx(project)
    val checkerName = AndroidEnvironmentChecker::class.qualifiedName
    withContext(Dispatchers.EDT) {
      LibraryDependentToolWindow.EXTENSION_POINT_NAME.extensions
        .filter { it.librarySearchClass == checkerName && toolWindowManager.getToolWindow(it.id) == null }
        .forEach { extension ->
          toolWindowManager.initToolWindow(project, extension)
          // showOnStripeByDefault is deprecated due to the New UI not supporting it. We still want to use it until old UI is removed.
          if (!extension.showOnStripeByDefault) {
            val toolWindow = toolWindowManager.getToolWindow(extension.id)
            if (toolWindow != null) {
              @Suppress("UnstableApiUsage") // Internal API
              val windowInfo = toolWindowManager.getLayout().getInfo(extension.id)
              if (windowInfo != null && !windowInfo.isFromPersistentSettings) {
                toolWindow.isShowStripeButton = false
              }
            }
          }
        }
    }
  }
}

/**
 * A replacement for [ToolWindowManagerEx.initToolWindow]
 *
 * The `initToolWindow` function is deprecated in favor of [ToolWindowManager.registerToolWindow] but the latter requires quite a bit of
 * setup in order to work, so it's wrapped by this helper function.
 *
 * Based on the deprecated implementation.
 */
private fun ToolWindowManager.initToolWindow(project: Project, bean: ToolWindowEP) {
  val plugin = bean.pluginDescriptor
  val condition = bean.getCondition(plugin)
  if (condition != null && !condition.value(project)) {
    return
  }

  val factory = bean.getToolWindowFactory(plugin)
  if (!factory.isApplicable(project)) {
    return
  }

  @Suppress("UnstableApiUsage")
  val anchor = factory.anchor ?: ToolWindowAnchor.fromText(bean.anchor ?: ToolWindowAnchor.LEFT.toString())

  @Suppress("DEPRECATION")
  val sideTool = bean.secondary || bean.side
  @Suppress("UnstableApiUsage")
  registerToolWindow(
    RegisterToolWindowTask(
      id = bean.id,
      icon = findIconFromBean(bean, factory, plugin),
      anchor = anchor,
      sideTool = sideTool,
      canCloseContent = bean.canCloseContents,
      canWorkInDumbMode = DumbService.isDumbAware(factory),
      shouldBeAvailable = factory.shouldBeAvailable(project),
      contentFactory = factory,
      stripeTitle = getStripeTitleSupplier(bean.id, project, plugin)
    )
  )
}