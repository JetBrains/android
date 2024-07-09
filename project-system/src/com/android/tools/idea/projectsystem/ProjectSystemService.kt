/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.projectsystem

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.impl.ModuleRootEventImpl
import org.jetbrains.annotations.TestOnly

class ProjectSystemService(val project: Project) {
  private val cachedProjectSystemDelegate = lazy(LazyThreadSafetyMode.PUBLICATION) { detectProjectSystem(project) }
  private val cachedProjectSystem by cachedProjectSystemDelegate
  private var projectSystemForTests: AndroidProjectSystem? = null

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ProjectSystemService {
      return project.getService(ProjectSystemService::class.java)!!
    }
  }

  val projectSystem: AndroidProjectSystem
    get() = projectSystemForTests ?: cachedProjectSystem

  private fun detectProjectSystem(project: Project): AndroidProjectSystem {
    val extensions = EP_NAME.extensionList
    val provider = extensions.find { it.isApplicable(project) }
                   ?: extensions.find { it.id == "" }
                   ?: throw IllegalStateException("Default AndroidProjectSystem not found for project " + project.name)
    return provider.projectSystemFactory(project)
  }

  /**
   * Replaces the project system of the current [project] and re-initializes it by sending [ModuleRootListener.TOPIC] notifications.
   */
  @TestOnly
  fun replaceProjectSystemForTests(projectSystem: AndroidProjectSystem) {
    projectSystemForTests = projectSystem
    if (cachedProjectSystemDelegate.isInitialized()) {
      runWriteAction {
        val publisher = project.messageBus.syncPublisher(ModuleRootListener.TOPIC)
        val rootChangedEvent = ModuleRootEventImpl(project, false)
        publisher.beforeRootsChange(rootChangedEvent)
        publisher.rootsChanged(rootChangedEvent)
      }
    }
  }
}
