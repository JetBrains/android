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

import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.gradle.project.GradleProjectInfo
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncReason
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResultListener
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystemSyncManager
import com.android.tools.idea.testing.IdeComponents
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.messages.MessageBusConnection
import org.junit.Assert
import org.mockito.Mockito.mock


class GradleProjectSystemSyncManagerTest : PlatformTestCase() {
  private lateinit var ideComponents: IdeComponents
  private lateinit var gradleProjectInfo: GradleProjectInfo
  private lateinit var syncManager: ProjectSystemSyncManager
  private lateinit var gradleBuildState: GradleBuildState
  private lateinit var syncTopicConnection: MessageBusConnection
  private lateinit var syncTopicListener: SyncResultListener

  override fun setUp() {
    super.setUp()
    ideComponents = IdeComponents(myProject)

    ideComponents.mockProjectService(GradleDependencyManager::class.java)
    gradleProjectInfo = ideComponents.mockProjectService(GradleProjectInfo::class.java)
    whenever<Boolean>(gradleProjectInfo.isBuildWithGradle).thenReturn(true)

    syncManager = GradleProjectSystemSyncManager(myProject)
    gradleBuildState = GradleBuildState.getInstance(myProject)

    syncTopicConnection = project.messageBus.connect(project)
    syncTopicListener = mock(SyncResultListener::class.java)
    syncTopicConnection.subscribe(PROJECT_SYSTEM_SYNC_TOPIC, syncTopicListener)
  }

  fun testGetLastSyncResult_unknownIfNeverSynced() {
    ideComponents.replaceApplicationService(GradleSyncInvoker::class.java, GradleSyncInvoker.FakeInvoker())

    assertThat(syncManager.getLastSyncResult()).isSameAs(SyncResult.UNKNOWN)
  }
}
