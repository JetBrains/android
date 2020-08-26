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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.common.util.isSuccess
import com.android.tools.idea.compose.preview.util.hasBeenBuiltSuccessfully
import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildListener
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.util.runWhenSmartAndSyncedOnEdt
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiFile
import java.util.function.Consumer

/** The project status */
sealed class ProjectStatus

/** The project is indexing or not synced yet */
object NotReady : ProjectStatus()

/** The project has not been built */
object NeedsBuild : ProjectStatus()

/** The project is compiled but one or more files are out of date **/
object OutOfDate : ProjectStatus()

/** The project is compiled and up to date */
class Ready(val successfulBuild: Boolean = true) : ProjectStatus()

private val LOG = Logger.getInstance(ProjectStatus::class.java)

/**
 * Class managing the build status of a project and its state.
 *
 * @param parentDisposable [Disposable] to track for disposing this manager.
 * @param editorFile the file in the editor to track changes and the build status. If the project has not been
 *  built since it was open, this file is used to find if there are any existing .class files that indicate that has
 *  been built before.
 */
class ProjectBuildStatusManager(parentDisposable: Disposable, editorFile: PsiFile) {
  private val modificationTracker: ModificationTracker = editorFile.virtualFile
  private var lastModificationCount = modificationTracker.modificationCount
  private val project: Project = editorFile.project
  var status: ProjectStatus = NotReady
    get() = if (isBuildOutOfDate()) {
      OutOfDate
    }
    else field
    private set(value) {
      if (field != value) {
        LOG.debug("Status change old = $field, new = $value")
        field = value
      }
    }

  init {
    GradleBuildState.subscribe(project, object : GradleBuildListener.Adapter() {
      // We do not have to check isDisposed inside the callbacks since they won't get called if parentDisposable is disposed
      override fun buildStarted(context: BuildContext) {
        LOG.debug("buildStarted $context")
        if (context.buildMode == BuildMode.CLEAN) {
          status = NeedsBuild
        }
      }

      override fun buildFinished(buildStatus: BuildStatus, context: BuildContext?) {
        LOG.debug("buildFinished $buildStatus $context")
        lastModificationCount = modificationTracker.modificationCount
        if (context?.buildMode == BuildMode.CLEAN) return
        if (buildStatus.isSuccess()) {
          status = Ready()
        }
        else {
          status = when (status) {
            // If the project was ready before, we keep it as Ready since it was just the new build
            // that failed.
            is Ready -> Ready(false)
            // If the project was not ready, then it needs a build since this one failed.
            else -> NeedsBuild
          }
        }
      }
    })

    project.runWhenSmartAndSyncedOnEdt(parentDisposable, Consumer {
      if (status === NotReady) {
        // Set the initial state of the project and initialize the modification count.
        lastModificationCount = modificationTracker.modificationCount
        status = if (hasBeenBuiltSuccessfully(project) { editorFile }) Ready() else NeedsBuild
      }
    })
  }

  private fun isBuildOutOfDate() = lastModificationCount < modificationTracker.modificationCount
}