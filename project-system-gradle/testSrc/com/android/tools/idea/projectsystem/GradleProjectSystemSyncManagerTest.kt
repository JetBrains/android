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

import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.gradle.project.GradleProjectInfo
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncReason
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResultListener
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystemSyncManager
import com.android.tools.idea.testing.IdeComponents
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.IdeaTestCase
import com.intellij.util.messages.MessageBusConnection
import org.mockito.Mockito.*

class GradleProjectSystemSyncManagerTest : IdeaTestCase() {
  private lateinit var ideComponents: IdeComponents
  private lateinit var gradleProjectInfo: GradleProjectInfo
  private lateinit var syncManager: ProjectSystemSyncManager
  private lateinit var gradleBuildState: GradleBuildState
  private lateinit var gradleSyncState: GradleSyncState
  private lateinit var syncInvoker: GradleSyncInvoker

  private lateinit var syncTopicConnection: MessageBusConnection
  private lateinit var syncTopicListener: SyncResultListener

  override fun setUp() {
    super.setUp()
    ideComponents = IdeComponents(myProject)

    syncInvoker = ideComponents.mockService(GradleSyncInvoker::class.java)

    ideComponents.mockProjectService(GradleDependencyManager::class.java)
    ideComponents.mockProjectService(GradleProjectBuilder::class.java)
    gradleProjectInfo = ideComponents.mockProjectService(GradleProjectInfo::class.java)
    `when`<Boolean>(gradleProjectInfo.isBuildWithGradle).thenReturn(true)

    syncManager = GradleProjectSystemSyncManager(myProject)
    gradleBuildState = GradleBuildState.getInstance(myProject)
    gradleSyncState = GradleSyncState.getInstance(myProject)

    syncTopicConnection = project.messageBus.connect(project)
    syncTopicListener = mock(SyncResultListener::class.java)
    syncTopicConnection.subscribe(PROJECT_SYSTEM_SYNC_TOPIC, syncTopicListener)
  }

  override fun tearDown() {
    try {
      ideComponents.restore()
      Disposer.dispose(syncTopicConnection)
    }
    finally {
      super.tearDown()
    }
  }

  private fun emulateSync(requireSourceGeneration: Boolean, syncSuccessful: Boolean):
    ListenableFuture<SyncResult> {

    doAnswer({ invocation ->
      val request = invocation.getArgument<GradleSyncInvoker.Request>(1)
      val listener = invocation.getArgument<GradleSyncListener>(2)

      ApplicationManager.getApplication().invokeAndWait {
        listener.syncStarted(project, false, request.generateSourcesOnSuccess)
        gradleSyncState.syncStarted(false, request)

        if (syncSuccessful) {
          listener.syncSucceeded(project)
          gradleSyncState.syncEnded()
        }
        else {
          listener.syncFailed(project, "")
          gradleSyncState.syncFailed("")
        }
      }
    }).`when`(syncInvoker).requestProjectSync(any(), any(), any())

    return syncManager.syncProject(SyncReason.PROJECT_MODIFIED, requireSourceGeneration)
  }

  private fun emulateBuild(result: BuildStatus) {
    ApplicationManager.getApplication().invokeAndWait {
      gradleBuildState.buildFinished(result)
    }
  }

  fun testSyncProject_uninitializedProject() {
    val startupManager = object : StartupManagerImpl(project) {
      override fun startupActivityPassed(): Boolean {
        return false // this will make Project.isInitialized return false;
      }

      override fun runWhenProjectIsInitialized(action: Runnable) {
        action.run()
      }
    }
    IdeComponents.replaceService(project, StartupManager::class.java, startupManager)
    // http://b/62543184
    `when`(gradleProjectInfo.isImportedProject).thenReturn(true)

    project.getProjectSystem().getSyncManager().syncProject(SyncReason.PROJECT_LOADED, true)
    verify(syncInvoker, never()).requestProjectSync(same(project), any(), any())
  }

  fun testSyncProject_noSourceGeneration() {

    val result = emulateSync(false, true)

    assertThat(result.isDone).isTrue()
    assertThat(result.get()).isSameAs(SyncResult.SUCCESS)
    verify(syncTopicListener).syncEnded(SyncResult.SUCCESS)
  }

  fun testSyncProject_sourceGenerationRequestedAndSyncFails() {
    val result = emulateSync(true, false)

    assertThat(result.isDone).isTrue()
    assertThat(result.get()).isSameAs(SyncResult.FAILURE)
    verify(syncTopicListener).syncEnded(SyncResult.FAILURE)
  }

  fun testSyncProject_waitsForSourceGeneration() {
    val result = emulateSync(true, true)

    assertThat(result.isDone).isFalse()
  }

  fun testSyncProject_sourceGenerationRequestedAndBuildFails() {
    val result = emulateSync(true, true)
    emulateBuild(BuildStatus.FAILED)

    assertThat(result.isDone).isTrue()
    assertThat(result.get()).isSameAs(SyncResult.PARTIAL_SUCCESS)
    verify(syncTopicListener).syncEnded(SyncResult.PARTIAL_SUCCESS)
  }

  fun testSyncProject_sourceGenerationSuccessful() {
    val result = emulateSync(true, true)
    emulateBuild(BuildStatus.SUCCESS)

    assertThat(result.isDone).isTrue()
    assertThat(result.get()).isSameAs(SyncResult.SUCCESS)
    verify(syncTopicListener).syncEnded(SyncResult.SUCCESS)
  }

  fun testGetLastSyncResult_unknownIfNeverSynced() {
    assertThat(syncManager.getLastSyncResult()).isSameAs(SyncResult.UNKNOWN)
  }

  fun testGetLastSyncResult_sameAsSyncResult() {
    emulateSync(false, true)

    assertThat(syncManager.getLastSyncResult()).isSameAs(SyncResult.SUCCESS)
  }
}
