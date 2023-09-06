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
package com.android.tools.adtui

import com.android.tools.idea.sdk.AndroidSdkPathStore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

private class AndroidToolWindowManager : ProjectActivity {
  init {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  @OptIn(FlowPreview::class)
  override suspend fun execute(project: Project) {
    coroutineScope {
      val checkRequests = MutableSharedFlow<String?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
      checkRequests.emit(null)

      val connection = project.messageBus.connect(this)
      connection.subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
        override fun rootsChanged(event: ModuleRootEvent) {
          check(checkRequests.tryEmit(null))
        }
      })
      connection.subscribe(AdditionalLibraryRootsListener.TOPIC,
                           AdditionalLibraryRootsListener { _: String?, _: Collection<VirtualFile?>?, _: Collection<VirtualFile?>?, _: String? ->
                             check(checkRequests.tryEmit(null))
                           })

      checkRequests
        .debounce(100.milliseconds)
        .collectLatest {
          checkToolWindowStatuses(project = project, extensionId = it)
        }
    }
  }
}

@JvmRecord
private data class AndroidWindowsState(@JvmField val project: Project,
                                       @JvmField val extensions: List<AndroidToolWindow>,
                                       @JvmField val existing: Set<AndroidToolWindow>)

internal class AndroidToolWindow : ToolWindowEP()

private val EXTENSION_POINT_NAME: ExtensionPointName<AndroidToolWindow> = ExtensionPointName("com.intellij.android.toolWindow")

private suspend fun checkToolWindowStatuses(project: Project, extensionId: String? = null) {
  var extensions = EXTENSION_POINT_NAME.extensionList
  if (extensions.isEmpty()) {
    return
  }

  val state = readAction {
    if (extensionId != null) {
      extensions = extensions.filter { it.id == extensionId }
    }
    val existing = if (hasAndroidSdk()) extensions else emptyList()

    if (extensions.isEmpty()) {
      null
    }
    else {
      AndroidWindowsState(project = project, extensions = extensions, existing = existing.toSet())
    }
  } ?: return

  withContext(Dispatchers.EDT) {
    applyWindowsState(state)
  }
}

private fun hasAndroidSdk(): Boolean {
  return AndroidSdkPathStore.getInstance().androidSdkPath != null
}

private suspend fun applyWindowsState(state: AndroidWindowsState) {
  val toolWindowManager = ToolWindowManager.getInstance(state.project) as ToolWindowManagerImpl
  for (libraryToolWindow in state.extensions) {
    val toolWindow = toolWindowManager.getToolWindow(libraryToolWindow.id)
    if (state.existing.contains(libraryToolWindow)) {
      if (toolWindow == null) {
        toolWindowManager.initToolWindow(libraryToolWindow, libraryToolWindow.pluginDescriptor)
      }
    }
    else {
      toolWindow?.remove()
    }
  }
}