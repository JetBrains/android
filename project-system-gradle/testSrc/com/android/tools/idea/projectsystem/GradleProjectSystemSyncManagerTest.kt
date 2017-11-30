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
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SourceGenerationCallback
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncReason
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystemSyncManager
import com.android.tools.idea.testing.IdeComponents
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.openapi.startup.StartupManager
import com.intellij.testFramework.IdeaTestCase
import org.mockito.Mockito.*

class GradleProjectSystemSyncManagerTest : IdeaTestCase() {
  private lateinit var myIdeComponents: IdeComponents
  private lateinit var myGradleProjectInfo: GradleProjectInfo
  private lateinit var mySyncManager: ProjectSystemSyncManager
  private lateinit var myGradleBuildState: GradleBuildState
  private lateinit var mySourceGenerationCallback: SourceGenerationCallback

  override fun setUp() {
    super.setUp()
    myIdeComponents = IdeComponents(myProject)

    myIdeComponents.mockProjectService(GradleDependencyManager::class.java)
    myIdeComponents.mockProjectService(GradleProjectBuilder::class.java)
    myGradleProjectInfo = myIdeComponents.mockProjectService(GradleProjectInfo::class.java)
    `when`<Boolean>(myGradleProjectInfo.isBuildWithGradle).thenReturn(true)

    mySyncManager = GradleProjectSystemSyncManager(myProject)
    myGradleBuildState = GradleBuildState.getInstance(myProject)
    mySourceGenerationCallback = mock(SourceGenerationCallback::class.java)
  }

  override fun tearDown() {
    try {
      myIdeComponents.restore()
    }
    finally {
      super.tearDown()
    }
  }

  fun testSyncProjectWithUninitializedProject() {
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
    `when`(myGradleProjectInfo.isImportedProject).thenReturn(true)
    val mySyncInvoker = myIdeComponents.mockService(GradleSyncInvoker::class.java)

    project.getProjectSystem().getSyncManager().syncProject(SyncReason.PROJECT_LOADED, true)
    verify(mySyncInvoker, never()).requestProjectSync(same(project), any(), any())
  }

  fun testGetLastSyncResult_unknownIfNeverSynced() {
    assertThat(mySyncManager.getLastSyncResult()).isSameAs(SyncResult.UNKNOWN)
  }

  fun testGetLastSyncResult_sameAsSyncResult() {
    // Emulate the completion of a successful sync.
    myProject.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(SyncResult.SUCCESS)

    assertThat(mySyncManager.getLastSyncResult()).isSameAs(SyncResult.SUCCESS)
  }

  fun testAddSourceGenerationCallback_sourcesAlreadyAvailable() {
    myGradleBuildState.buildFinished(BuildStatus.SUCCESS)
    mySyncManager.addSourceGenerationCallback(mySourceGenerationCallback)

    verify(mySourceGenerationCallback).sourcesGenerated()
    verifyNoMoreInteractions(mySourceGenerationCallback)
  }

  fun testAddSourceGenerationCallback_errorsAlreadyOccurredAndSourcesAlreadyAvailable() {
    // User cancels initial build
    myGradleBuildState.buildFinished(BuildStatus.CANCELED)

    // User re-syncs, which generates sources
    project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(SyncResult.SUCCESS)
    myGradleBuildState.buildFinished(BuildStatus.SUCCESS)

    // A later sync fails
    project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(SyncResult.FAILURE)

    mySyncManager.addSourceGenerationCallback(mySourceGenerationCallback)
    verify(mySourceGenerationCallback).sourcesGenerated()
    verifyNoMoreInteractions(mySourceGenerationCallback) // Ignore errors as long as sources have been built once this session.
  }

  fun testAddSourceGenerationCallback_syncErrorAlreadyOccurredButSourcesNotAvailable() {
    project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(SyncResult.FAILURE)
    mySyncManager.addSourceGenerationCallback(mySourceGenerationCallback)

    verify(mySourceGenerationCallback).sourceGenerationError()
    verifyNoMoreInteractions(mySourceGenerationCallback)
  }

  fun testAddSourceGenerationCallback_buildErrorAlreadyOccurredButSourcesNotAvailable() {
    myGradleBuildState.buildFinished(BuildStatus.FAILED)
    mySyncManager.addSourceGenerationCallback(mySourceGenerationCallback)

    verify(mySourceGenerationCallback).sourceGenerationError()
    verifyNoMoreInteractions(mySourceGenerationCallback)
  }

  fun testAddSourceGenerationCallback_waits() {
    mySyncManager.addSourceGenerationCallback(mySourceGenerationCallback)
    verifyZeroInteractions(mySourceGenerationCallback)
  }

  fun testAddSourceGenerationCallback_syncErrorAlreadyOccurredAndThenSourcesAvailable() {
    // Initial sync fails
    project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(SyncResult.FAILURE)

    mySyncManager.addSourceGenerationCallback(mySourceGenerationCallback)

    // Sources generated successfully later
    project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(SyncResult.SUCCESS)
    myGradleBuildState.buildFinished(BuildStatus.SUCCESS)

    verify(mySourceGenerationCallback).sourcesGenerated()
  }


  fun testAddSourceGenerationCallback_buildErrorAlreadyOccurredAndThenSourcesAvailable() {
    // Initial build fails
    myGradleBuildState.buildFinished(BuildStatus.FAILED)

    mySyncManager.addSourceGenerationCallback(mySourceGenerationCallback)

    // Sources generated successfully later
    project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(SyncResult.SUCCESS)
    myGradleBuildState.buildFinished(BuildStatus.SUCCESS)

    verify(mySourceGenerationCallback).sourcesGenerated()
  }

  fun testAddSourceGenerationCallback_sourcesAvailableLater() {
    mySyncManager.addSourceGenerationCallback(mySourceGenerationCallback)
    myGradleBuildState.buildFinished(BuildStatus.SUCCESS)

    verify(mySourceGenerationCallback).sourcesGenerated()
    verifyNoMoreInteractions(mySourceGenerationCallback)
  }

  fun testAddSourceGenerationCallback_syncErrorLater() {
    mySyncManager.addSourceGenerationCallback(mySourceGenerationCallback)
    project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(SyncResult.FAILURE)

    verify(mySourceGenerationCallback).sourceGenerationError()
    verifyNoMoreInteractions(mySourceGenerationCallback)
  }

  fun testAddSourceGenerationCallback_syncErrorLaterAndThenSourcesAvailable() {
    mySyncManager.addSourceGenerationCallback(mySourceGenerationCallback)

    project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(SyncResult.FAILURE)

    project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(SyncResult.SUCCESS)
    myGradleBuildState.buildFinished(BuildStatus.SUCCESS)

    verify(mySourceGenerationCallback).sourcesGenerated()
  }

  fun testAddSourceGenerationCallback_buildErrorLater() {
    mySyncManager.addSourceGenerationCallback(mySourceGenerationCallback)
    myGradleBuildState.buildFinished(BuildStatus.FAILED)

    verify(mySourceGenerationCallback).sourceGenerationError()
    verifyNoMoreInteractions(mySourceGenerationCallback)
  }

  fun testAddSourceGenerationCallback_buildErrorLaterAndThenSourcesAvailable() {
    mySyncManager.addSourceGenerationCallback(mySourceGenerationCallback)

    myGradleBuildState.buildFinished(BuildStatus.FAILED)

    project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(SyncResult.SUCCESS)
    myGradleBuildState.buildFinished(BuildStatus.SUCCESS)

    verify(mySourceGenerationCallback).sourcesGenerated()
  }
}
