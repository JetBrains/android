/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.res

import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbModeTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.Disposer
import com.intellij.util.application
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.ResourceFolderManager

/**
 * Service that subscribes to project root changes in order to invalidate
 * [AndroidDependenciesCache], the [ResourceFolderManager] cache, and to update resource
 * repositories.
 */
@Service(Service.Level.PROJECT)
class AndroidProjectRootListener private constructor(private val project: Project) :
  Disposable.Default {
  init {
    val messageBusConnection = project.messageBus.connect(this)

    messageBusConnection.subscribe(
      ModuleRootListener.TOPIC,
      object : ModuleRootListener {
        override fun rootsChanged(event: ModuleRootEvent) {
          moduleRootsOrDependenciesChanged()
        }
      },
    )

    messageBusConnection.subscribe(
      PROJECT_SYSTEM_SYNC_TOPIC,
      ProjectSystemSyncManager.SyncResultListener {
        // This event is called on the EDT. Calling `moduleRootsOrDependenciesChanged` directly ends
        // up executing the DumbModeTask synchronously, which has leads to failures due to the state
        // we're in from higher up the stack. Executing this on the EDT later avoids that situation.
        application.invokeLater { moduleRootsOrDependenciesChanged() }
      },
    )
  }

  /**
   * Called when module roots have changed in the given project.
   *
   * @param project the project whose module roots changed
   */
  private fun moduleRootsOrDependenciesChanged() {
    runReadAction {
      if (!project.isDisposed) {
        RootsChangedDumbModeTask(project, this).queue(project)
      }
    }
  }

  companion object {
    /**
     * Makes AndroidProjectRootListener listen to the [ModuleRootListener.TOPIC] events if it has
     * not been listening already.
     *
     * @param project the project to listen on
     */
    @JvmStatic
    fun ensureSubscribed(project: Project) {
      project.service<AndroidProjectRootListener>()
    }
  }
}

private class RootsChangedDumbModeTask(private val project: Project, parent: Disposable) :
  DumbModeTask() {
  init {
    Disposer.register(parent, this)
  }

  override fun performInDumbMode(indicator: ProgressIndicator) {
    if (!project.isDisposed) {
      indicator.text = "Updating resource repository roots"
      for (module in ModuleManager.getInstance(project).modules) {
        moduleRootsOrDependenciesChanged(module)
      }
    }
  }

  override fun tryMergeWith(taskFromQueue: DumbModeTask): DumbModeTask? {
    return this.takeIf {
      taskFromQueue is RootsChangedDumbModeTask && taskFromQueue.project == project
    }
  }

  /**
   * Called when module roots have changed in the given module.
   *
   * @param module the module whose roots changed
   */
  private fun moduleRootsOrDependenciesChanged(module: Module) {
    val facet = AndroidFacet.getInstance(module) ?: return

    if (AndroidModel.isRequired(facet) && AndroidModel.get(facet) == null) {
      // Project not yet fully initialized. No need to do a sync now because our
      // GradleProjectAvailableListener will be called as soon as it is and do a proper sync.
      return
    }

    AndroidDependenciesCache.getInstance(module).dropCache()
    ResourceFolderManager.getInstance(facet).checkForChanges()
    StudioResourceRepositoryManager.getInstance(facet).updateRootsAndLibraries()
  }
}
