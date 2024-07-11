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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.google.common.annotations.VisibleForTesting
import com.android.tools.idea.gradle.project.sync.GradleFiles
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

/**
 * Provides an api to use a [ProjectBuildModel] while refreshing the model if the Gradle build files change,
 * this means that both [read] or [modify] could result in reparsing the Gradle build files.
 *
 * Also provides synchronization by using a [ReentrantReadWriteLock] to guard both [read] and [modify].
 * These methods should not be called from the UI thread.
 *
 * This handler shares an instance of the [ProjectBuildModel] and commits all changes on every call to
 * [modify] if you require more fine grained control please use [ProjectBuildModel.get].
 */
@Service
class ProjectBuildModelHandler @VisibleForTesting constructor(val project: Project) {
  /**
   * The time stamp of the last sync before the [ProjectBuildModel] was created.
   */
  private var modelSyncTime : Long = -1L
  private var projectBuildModel: ProjectBuildModel? = null
  private val lock: Lock = ReentrantLock()

  companion object {
    fun getInstance(project: Project) : ProjectBuildModelHandler = project.getService(ProjectBuildModelHandler::class.java)
  }


  fun <T> read(block: ProjectBuildModel.() -> T): T {
    // TODO: assert is not dispatch thread once all callers are not using it
    lock.withLock {
      return block.invoke(projectModel())
    }
  }

  /**
   * Executes a code [block] which modifies the project model. Obtaining the model can take a long time, so this method must not be called
   * from the EDT. Once the block has been executed, this method will handle applying the model changes on the EDT, blocking the calling
   * thread until the changes have been applied.
   */
  fun <T> modify(block: ProjectBuildModel.() -> T): T {
    assert(ApplicationManager.getApplication().isUnitTestMode || !ApplicationManager.getApplication().isDispatchThread)
    lock.withLock {
      val model = projectModel()
      try {
        return block.invoke(model)
      } finally {
        ApplicationManager.getApplication().invokeAndWait {
          ApplicationManager.getApplication().runWriteAction {
            model.applyChanges()
          }
        }
      }
    }
  }

  /**
   * Returns the [ProjectBuildModel], refreshes it if it falls out of date.
   */
  private fun projectModel(): ProjectBuildModel {
    val lastKnownSyncTime = GradleSyncState.getInstance(project).lastSyncFinishedTimeStamp

    return projectBuildModel?.takeUnless {
      GradleFiles.getInstance(project).areGradleFilesModified() || modelSyncTime != lastKnownSyncTime
    } ?: ProjectBuildModel.get(project).also { modelSyncTime = lastKnownSyncTime }
  }

  /**
   * DO NOT use outside of tests.
   */
  @VisibleForTesting
  constructor(project: Project, projectModel: ProjectBuildModel, lastSync: Long = -1L) : this(project) {
    projectBuildModel = projectModel
    modelSyncTime = lastSync
  }
}