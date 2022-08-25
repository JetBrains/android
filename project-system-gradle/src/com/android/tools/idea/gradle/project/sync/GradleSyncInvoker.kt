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

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.TestOnly

interface GradleSyncInvoker {
  fun requestProjectSync(project: Project, request: Request, listener: GradleSyncListener? = null)

  @WorkerThread
  fun fetchAndMergeNativeVariants(project: Project, requestedAbis: Set<String>)

  @WorkerThread
  fun fetchGradleModels(project: Project): GradleProjectModels

  data class Request @JvmOverloads constructor(
    val trigger: GradleSyncStats.Trigger,
    val requestedVariantChange: SwitchVariantRequest? = null,
    val dontFocusSyncFailureOutput: Boolean = false,
  ) {
    val progressExecutionMode: ProgressExecutionMode
      get() = ProgressExecutionMode.IN_BACKGROUND_ASYNC

    companion object {
      // Perform a variant-only sync if not null.
      @VisibleForTesting
      @JvmStatic
      fun testRequest(): Request = Request(GradleSyncStats.Trigger.TRIGGER_TEST_REQUESTED)
    }
  }

  @TestOnly
  open class FakeInvoker : GradleSyncInvoker {
    override fun requestProjectSync(project: Project, request: Request, listener: GradleSyncListener?) {
      listener?.syncSkipped(project)
      project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(ProjectSystemSyncManager.SyncResult.SKIPPED)
    }

    @WorkerThread
    override fun fetchAndMergeNativeVariants(project: Project, requestedAbis: Set<String>) = Unit

    @WorkerThread
    override fun fetchGradleModels(project: Project): GradleProjectModels = GradleProjectModels(emptyList(), null)
  }

  companion object {
    @JvmStatic
    fun getInstance(): GradleSyncInvoker =
      ApplicationManager.getApplication().getService(GradleSyncInvoker::class.java)
  }
}

fun GradleSyncInvoker.requestProjectSync(
  project: Project,
  trigger: GradleSyncStats.Trigger,
  listener: GradleSyncListener? = null
) = requestProjectSync(project, GradleSyncInvoker.Request(trigger), listener)
