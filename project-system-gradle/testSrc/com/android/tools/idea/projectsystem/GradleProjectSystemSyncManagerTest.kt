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
import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.startup.StartupManager
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.messages.MessageBusConnection
import org.mockito.Mockito.*

class GradleProjectSystemSyncManagerTest : PlatformTestCase() {
  private lateinit var ideComponents: IdeComponents
  private lateinit var gradleProjectInfo: GradleProjectInfo
  private lateinit var syncManager: ProjectSystemSyncManager
  private lateinit var gradleBuildState: GradleBuildState
  private lateinit var syncInvoker: GradleSyncInvoker

  private lateinit var syncTopicConnection: MessageBusConnection
  private lateinit var syncTopicListener: SyncResultListener

  override fun setUp() {
    super.setUp()
    ideComponents = IdeComponents(myProject)

    syncInvoker = ideComponents.mockApplicationService(GradleSyncInvoker::class.java)

    ideComponents.mockProjectService(GradleDependencyManager::class.java)
    ideComponents.mockProjectService(GradleProjectBuilder::class.java)
    gradleProjectInfo = ideComponents.mockProjectService(GradleProjectInfo::class.java)
    `when`<Boolean>(gradleProjectInfo.isBuildWithGradle).thenReturn(true)

    syncManager = GradleProjectSystemSyncManager(myProject)
    gradleBuildState = GradleBuildState.getInstance(myProject)

    syncTopicConnection = project.messageBus.connect()
    syncTopicListener = mock(SyncResultListener::class.java)
    syncTopicConnection.subscribe(PROJECT_SYSTEM_SYNC_TOPIC, syncTopicListener)
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
    ideComponents.replaceProjectService(StartupManager::class.java, startupManager)
    // http://b/62543184
    `when`(gradleProjectInfo.isImportedProject).thenReturn(true)

    project.getProjectSystem().getSyncManager().syncProject(SyncReason.PROJECT_LOADED)
    verify(syncInvoker, never()).requestProjectSync(same(project), any<GradleSyncInvoker.Request>())
  }

  fun testGetLastSyncResult_unknownIfNeverSynced() {
    assertThat(syncManager.getLastSyncResult()).isSameAs(SyncResult.UNKNOWN)
  }

  fun testGetLastSyncResult_sameAsSyncResult() {
    doAnswer { invocation ->
      ApplicationManager.getApplication().invokeAndWait {
        project.messageBus.syncPublisher(GradleSyncState.GRADLE_SYNC_TOPIC).syncSucceeded(project)
      }
    }.`when`(syncInvoker).requestProjectSync(any(), any<GradleSyncInvoker.Request>())
    syncManager.syncProject(SyncReason.PROJECT_MODIFIED)
    ApplicationManager.getApplication().invokeAndWait { gradleBuildState.buildFinished(BuildStatus.SUCCESS) }
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    assertThat(syncManager.getLastSyncResult()).isSameAs(SyncResult.SUCCESS)
  }
}
