/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.common.editor

import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildListener
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.util.listenUntilNextSync
import com.android.tools.idea.util.runWhenSmartAndSyncedOnEdt
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.ui.EditorNotifications
import java.util.function.Consumer


interface SmartRefreshable : Disposable {
  fun refresh()
}

/**
 * This is the component that receives updates every time gradle build finishes. On successful build, it calls [refresh] method of the
 * passed [SmartRefreshable]. On the failed build it displays an error message on a passed [WorkBench] stating that the preview is not
 * available unless the build is successful.
 *
 * This is intended to be used by [com.intellij.openapi.fileEditor.FileEditor]'s. The editor should recreate/amend the model to reflect
 * build changes. This component should be created the last, so that all other members are initialized as it could call [refresh] method
 * straight away.
 */
class SmartAutoRefresher(psiFile: PsiFile,
                         private val refreshable: SmartRefreshable,
                         private val workbench: WorkBench<DesignSurface>) {
  private val project = psiFile.project
  private val virtualFile = psiFile.virtualFile!!

  /**
   * Method triggered on successful gradle build.
   */
  private fun refresh() {
    if (!Disposer.isDisposed(refreshable)) {
      refreshable.refresh()
    }
  }

  /**
   * Initializes the preview editor and triggers a refresh. This method can only be called once
   * the project has synced and is smart.
   */
  private fun initPreviewWhenSmartAndSynced() {
    refresh()

    GradleBuildState.subscribe(project, object : GradleBuildListener.Adapter() {
      override fun buildStarted(context: BuildContext) {
        EditorNotifications.getInstance(project).updateNotifications(virtualFile)
      }

      override fun buildFinished(status: BuildStatus, context: BuildContext?) {
        EditorNotifications.getInstance(project).updateNotifications(virtualFile)
        refresh()
      }
    }, refreshable)
  }

  /**
   * Initialize the preview. This method does not make assumptions about the project sync and smart status.
   */
  private fun initPreview() {
    // We are not registering before the constructor finishes, so we should be safe here
    project.runWhenSmartAndSyncedOnEdt(refreshable, Consumer { result ->
      if (result.isSuccessful) {
        initPreviewWhenSmartAndSynced()
      }
      else {
        workbench.loadingStopped("Preview is unavailable until after a successful project sync")
        // The project failed to sync, run initialization when the project syncs correctly
        project.listenUntilNextSync(refreshable, object : ProjectSystemSyncManager.SyncResultListener {
          override fun syncEnded(result: ProjectSystemSyncManager.SyncResult) {
            // Sync has completed but we might not be in smart mode so re-run the initialization
            initPreview()
          }
        })
      }
    })
  }

  init {
    initPreview()
  }
}