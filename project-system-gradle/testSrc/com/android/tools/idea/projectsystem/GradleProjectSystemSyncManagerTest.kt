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
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.*
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystemSyncManager
import com.android.tools.idea.testing.IdeComponents
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.startup.StartupManager
import com.intellij.testFramework.JavaProjectTestCase
import com.intellij.testFramework.replaceService
import com.intellij.util.messages.MessageBusConnection
import org.mockito.Mockito.*

class GradleProjectSystemSyncManagerTest : JavaProjectTestCase() {
  private lateinit var gradleProjectInfo: GradleProjectInfo
  private lateinit var syncManager: ProjectSystemSyncManager
  private lateinit var gradleBuildState: GradleBuildState
  private lateinit var gradleSyncState: GradleSyncState
  private lateinit var syncInvoker: GradleSyncInvoker

  private lateinit var syncTopicConnection: MessageBusConnection
  private lateinit var syncTopicListener: SyncResultListener

  override fun setUp() {
    super.setUp()

    syncInvoker = IdeComponents.mockApplicationService(GradleSyncInvoker::class.java, testRootDisposable)

    IdeComponents.mockProjectService(project, GradleDependencyManager::class.java, testRootDisposable)
    IdeComponents.mockProjectService(project, GradleProjectBuilder::class.java, testRootDisposable)
    gradleProjectInfo = IdeComponents.mockProjectService(project, GradleProjectInfo::class.java, testRootDisposable)
    `when`<Boolean>(gradleProjectInfo.isBuildWithGradle).thenReturn(true)

    syncManager = GradleProjectSystemSyncManager(myProject)
    gradleBuildState = GradleBuildState.getInstance(myProject)
    gradleSyncState = GradleSyncState.getInstance(myProject)

    syncTopicConnection = project.messageBus.connect(project)
    syncTopicListener = mock(SyncResultListener::class.java)
    syncTopicConnection.subscribe(PROJECT_SYSTEM_SYNC_TOPIC, syncTopicListener)
  }

  private fun emulateSync(requireSourceGeneration: Boolean, syncSuccessful: Boolean):
      ListenableFuture<SyncResult> {

    doAnswer { invocation ->
      val request = invocation.getArgument<GradleSyncInvoker.Request>(1)

      ApplicationManager.getApplication().invokeAndWait {
        gradleSyncState.syncStarted(false, request)

        if (syncSuccessful) {
          gradleSyncState.syncEnded()
        }
        else {
          gradleSyncState.syncFailed("")
        }
      }
    }.`when`(syncInvoker).requestProjectSync(any(), any())

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

    project.replaceService(StartupManager::class.java, startupManager, testRootDisposable)
    // http://b/62543184
    `when`(gradleProjectInfo.isImportedProject).thenReturn(true)

    project.getProjectSystem().getSyncManager().syncProject(SyncReason.PROJECT_LOADED, true)
    verify(syncInvoker, never()).requestProjectSync(same(project), any())
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
    assertThat(result.get()).isSameAs(SyncResult.SOURCE_GENERATION_FAILURE)
    verify(syncTopicListener).syncEnded(SyncResult.SOURCE_GENERATION_FAILURE)
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
