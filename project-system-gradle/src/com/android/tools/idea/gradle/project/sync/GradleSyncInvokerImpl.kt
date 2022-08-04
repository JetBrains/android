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
package com.android.tools.idea.gradle.project.sync

import com.android.annotations.concurrency.UiThread
import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.gradle.project.GradleProjectInfo
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.importing.OpenMigrationToGradleUrlHyperlink
import com.android.tools.idea.gradle.project.sync.idea.GradleSyncExecutor
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.project.AndroidNotification
import com.android.tools.idea.projectsystem.requiresAndroidModel
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.project.isDirectoryBased
import com.intellij.ui.AppUIUtil
import com.intellij.util.ui.UIUtil

class GradleSyncInvokerImpl : GradleSyncInvoker {
  /**
   * This method should not be called within a [DumbModeTask], the platform will take care of ensuring that
   * sync is not run at the same time as indexing.
   */
  override fun requestProjectSync(project: Project, request: GradleSyncInvoker.Request, listener: GradleSyncListener?) {
    if (GradleSyncState.getInstance(project).isSyncInProgress) {
      listener?.syncSkipped(project)
      return
    }
    if (GradleBuildInvoker.getInstance(project).internalIsBuildRunning) {
      listener?.syncSkipped(project)
      return
    }
    val syncTask = Runnable {
      ExternalSystemUtil.ensureToolWindowContentInitialized(project, GradleUtil.GRADLE_SYSTEM_ID)
      if (prepareProject(project, listener)) {
        sync(project, request, listener)
      }
    }
    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode) {
      application.invokeAndWait(syncTask)
    } else {
      ApplicationManager.getApplication().invokeLater(syncTask)
    }
  }

  @WorkerThread
  override fun fetchAndMergeNativeVariants(
    project: Project,
    requestedAbis: Set<String>
  ) {
    GradleSyncExecutor(project).fetchAndMergeNativeVariants(requestedAbis)
  }

  @WorkerThread
  override fun fetchGradleModels(project: Project): GradleProjectModels {
    return GradleSyncExecutor(project).fetchGradleModels()
  }

  companion object {
    private val LOG = Logger.getInstance(GradleSyncInvoker::class.java)
    private fun prepareProject(project: Project, listener: GradleSyncListener?): Boolean {
      val projectInfo = GradleProjectInfo.getInstance(project)
      if (projectInfo.isBuildWithGradle) {
        FileDocumentManager.getInstance().saveAllDocuments()
        return true // continue with sync.
      }
      AppUIUtil.invokeLaterIfProjectAlive(project) {
        val msg = String.format("The project '%s' is not a Gradle-based project", project.name)
        LOG.error(msg)
        AndroidNotification.getInstance(project)
          .showBalloon("Project Sync", msg, NotificationType.ERROR, OpenMigrationToGradleUrlHyperlink())
        listener?.syncFailed(project, msg)
      }
      return false // stop sync.
    }

    @UiThread
    private fun sync(project: Project, request: GradleSyncInvoker.Request, listener: GradleSyncListener?) {
      UIUtil.invokeAndWaitIfNeeded(Runnable { GradleSyncMessages.getInstance(project).removeAllMessages() })
      GradleSyncExecutor(project).sync(request, listener)
    }
  }
}
