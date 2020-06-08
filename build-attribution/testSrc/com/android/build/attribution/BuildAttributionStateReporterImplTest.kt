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
package com.android.build.attribution

import com.android.build.attribution.BuildAttributionStateReporter.State
import com.android.build.attribution.ui.BuildAttributionUiManager
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.ProjectStructure
import com.android.tools.idea.gradle.project.ProjectStructure.AndroidPluginVersionsInProject
import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.AndroidProjectRule.Companion.inMemory
import com.google.common.truth.Truth
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class BuildAttributionStateReporterImplTest {

  val projectRule: AndroidProjectRule = inMemory()

  @get:Rule
  val chain = RuleChain(projectRule, EdtRule()) // AndroidProjectRule must get initialized off the EDT thread

  private val uiManagerMock = mock(BuildAttributionUiManager::class.java)
  private val projectStructureMock = mock(ProjectStructure::class.java)
  private val receivedStateUpdates: MutableList<State> = arrayListOf()
  private var moduleAgpVersion = GradleVersion.parse("4.0.0")

  @Before
  fun setUp() {
    projectRule.replaceProjectService(BuildAttributionUiManager::class.java, uiManagerMock)
    projectRule.replaceProjectService(ProjectStructure::class.java, projectStructureMock)

    val androidPluginVersions = mock(AndroidPluginVersionsInProject::class.java)
    `when`(androidPluginVersions.allVersions).thenAnswer { listOf(moduleAgpVersion) }
    `when`(projectStructureMock.androidPluginVersions).thenAnswer { androidPluginVersions }

    projectRule.project.messageBus.connect(projectRule.fixture.testRootDisposable)
      .subscribe(BuildAttributionStateReporter.FEATURE_STATE_TOPIC, object : BuildAttributionStateReporter.Notifier {
        override fun stateUpdated(newState: State) {
          receivedStateUpdates.add(newState)
        }
      })
  }

  @After
  fun tearDown() {
    StudioFlags.BUILD_ATTRIBUTION_ENABLED.clearOverride()
  }

  @RunsInEdt
  @Test
  fun testInitHasData() {
    `when`(uiManagerMock.hasDataToShow()).thenReturn(true)

    val stateReporter = createStateReporter()

    Truth.assertThat(stateReporter.currentState()).isEqualTo(State.REPORT_DATA_READY)
    Truth.assertThat(receivedStateUpdates).isEmpty()
  }

  @RunsInEdt
  @Test
  fun testInitHasNoData() {
    `when`(uiManagerMock.hasDataToShow()).thenReturn(false)

    val stateReporter = createStateReporter()

    Truth.assertThat(stateReporter.currentState()).isEqualTo(State.NO_DATA)
    Truth.assertThat(receivedStateUpdates).isEmpty()
  }

  @RunsInEdt
  @Test
  fun testInitWhenAGPVersionLow() {
    moduleAgpVersion = GradleVersion.parse("3.5.3")
    `when`(uiManagerMock.hasDataToShow()).thenReturn(false)

    val stateReporter = createStateReporter()

    Truth.assertThat(stateReporter.currentState()).isEqualTo(State.AGP_VERSION_LOW)
    Truth.assertThat(receivedStateUpdates).isEmpty()
  }

  @RunsInEdt
  @Test
  fun testBuildStartedAfterNoData() {
    `when`(uiManagerMock.hasDataToShow()).thenReturn(false)

    val stateReporter = createStateReporter()
    Truth.assertThat(stateReporter.currentState()).isEqualTo(State.NO_DATA)

    sendBuildStarted()

    Truth.assertThat(stateReporter.currentState()).isEqualTo(State.NO_DATA_BUILD_RUNNING)
    Truth.assertThat(receivedStateUpdates).isEqualTo(listOf(State.NO_DATA_BUILD_RUNNING))
  }

  @RunsInEdt
  @Test
  fun testBuildFailedAfterNoData() {
    `when`(uiManagerMock.hasDataToShow()).thenReturn(false)

    val stateReporter = createStateReporter()
    Truth.assertThat(stateReporter.currentState()).isEqualTo(State.NO_DATA)

    sendBuildStarted()
    sendBuildFailed()

    Truth.assertThat(stateReporter.currentState()).isEqualTo(State.NO_DATA_BUILD_FAILED_TO_FINISH)
    Truth.assertThat(receivedStateUpdates).isEqualTo(listOf(State.NO_DATA_BUILD_RUNNING, State.NO_DATA_BUILD_FAILED_TO_FINISH))

  }

  @RunsInEdt
  @Test
  fun testBuildFinishedButStillNoData() {
    `when`(uiManagerMock.hasDataToShow()).thenReturn(false)

    val stateReporter = createStateReporter()
    Truth.assertThat(stateReporter.currentState()).isEqualTo(State.NO_DATA)

    sendBuildStarted()
    sendBuildSuccess()

    // Should not say we have data if uiManager still returns false
    Truth.assertThat(stateReporter.currentState()).isEqualTo(State.NO_DATA_BUILD_RUNNING)
    Truth.assertThat(receivedStateUpdates).isEqualTo(listOf(State.NO_DATA_BUILD_RUNNING))
  }

  @RunsInEdt
  @Test
  fun testBuildFinishedWithNewData() {
    `when`(uiManagerMock.hasDataToShow()).thenReturn(false)

    val stateReporter = createStateReporter()
    Truth.assertThat(stateReporter.currentState()).isEqualTo(State.NO_DATA)

    sendBuildStarted()
    sendBuildSuccess()
    `when`(uiManagerMock.hasDataToShow()).thenReturn(true)
    // Supposed to be called from uiManager when it receives data.
    stateReporter.setStateDataExist()

    Truth.assertThat(stateReporter.currentState()).isEqualTo(State.REPORT_DATA_READY)
    Truth.assertThat(receivedStateUpdates).isEqualTo(listOf(State.NO_DATA_BUILD_RUNNING, State.REPORT_DATA_READY))
  }

  @RunsInEdt
  @Test
  fun testBuildStartedAfterDataExist() {
    `when`(uiManagerMock.hasDataToShow()).thenReturn(true)

    val stateReporter = createStateReporter()
    Truth.assertThat(stateReporter.currentState()).isEqualTo(State.REPORT_DATA_READY)

    sendBuildStarted()

    Truth.assertThat(stateReporter.currentState()).isEqualTo(State.REPORT_DATA_READY)
    Truth.assertThat(receivedStateUpdates).isEmpty()

  }

  @RunsInEdt
  @Test
  fun testBuildFailedAfterDataExist() {
    `when`(uiManagerMock.hasDataToShow()).thenReturn(true)

    val stateReporter = createStateReporter()
    Truth.assertThat(stateReporter.currentState()).isEqualTo(State.REPORT_DATA_READY)

    sendBuildStarted()
    sendBuildFailed()

    Truth.assertThat(stateReporter.currentState()).isEqualTo(State.NO_DATA_BUILD_FAILED_TO_FINISH)
    Truth.assertThat(receivedStateUpdates).isEqualTo(listOf(State.NO_DATA_BUILD_FAILED_TO_FINISH))
  }

  @RunsInEdt
  @Test
  fun testBuildStartedWhenAgpLow() {
    moduleAgpVersion = GradleVersion.parse("3.5.3")
    `when`(uiManagerMock.hasDataToShow()).thenReturn(false)

    val stateReporter = createStateReporter()
    Truth.assertThat(stateReporter.currentState()).isEqualTo(State.AGP_VERSION_LOW)

    sendBuildStarted()
    sendBuildFailed()

    Truth.assertThat(stateReporter.currentState()).isEqualTo(State.AGP_VERSION_LOW)
    Truth.assertThat(receivedStateUpdates).isEmpty()
  }

  @RunsInEdt
  @Test
  fun testBuildFailedWhenAgpLow() {
    moduleAgpVersion = GradleVersion.parse("3.5.3")
    `when`(uiManagerMock.hasDataToShow()).thenReturn(false)

    val stateReporter = createStateReporter()
    Truth.assertThat(stateReporter.currentState()).isEqualTo(State.AGP_VERSION_LOW)

    sendBuildStarted()
    sendBuildFailed()

    Truth.assertThat(stateReporter.currentState()).isEqualTo(State.AGP_VERSION_LOW)
    Truth.assertThat(receivedStateUpdates).isEmpty()
  }

  @RunsInEdt
  @Test
  fun testAgpVersionUpdated() {
    moduleAgpVersion = GradleVersion.parse("3.5.3")
    `when`(uiManagerMock.hasDataToShow()).thenReturn(false)

    val stateReporter = createStateReporter()
    Truth.assertThat(stateReporter.currentState()).isEqualTo(State.AGP_VERSION_LOW)

    moduleAgpVersion = GradleVersion.parse("4.0.0")
    sendSyncFinished()

    Truth.assertThat(stateReporter.currentState()).isEqualTo(State.NO_DATA)
    Truth.assertThat(receivedStateUpdates).isEqualTo(listOf(State.NO_DATA))
  }

  @RunsInEdt
  @Test
  fun testFeatureFlagOff() {
    StudioFlags.BUILD_ATTRIBUTION_ENABLED.override(false)
    `when`(uiManagerMock.hasDataToShow()).thenReturn(false)

    val stateReporter = createStateReporter()

    Truth.assertThat(stateReporter.currentState()).isEqualTo(State.FEATURE_TURNED_OFF)
    Truth.assertThat(receivedStateUpdates).isEmpty()
  }

  @RunsInEdt
  @Test
  fun testFeatureFlagCheckPrecedesOverAgpVersionCheck() {
    StudioFlags.BUILD_ATTRIBUTION_ENABLED.override(false)
    moduleAgpVersion = GradleVersion.parse("3.5.3")
    `when`(uiManagerMock.hasDataToShow()).thenReturn(false)

    val stateReporter = createStateReporter()

    Truth.assertThat(stateReporter.currentState()).isEqualTo(State.FEATURE_TURNED_OFF)
    Truth.assertThat(receivedStateUpdates).isEmpty()
  }

  @RunsInEdt
  @Test
  fun testBuildRunWhenFeatureFlagOff() {
    StudioFlags.BUILD_ATTRIBUTION_ENABLED.override(false)
    `when`(uiManagerMock.hasDataToShow()).thenReturn(false)

    val stateReporter = createStateReporter()
    Truth.assertThat(stateReporter.currentState()).isEqualTo(State.FEATURE_TURNED_OFF)

    sendBuildStarted()

    Truth.assertThat(stateReporter.currentState()).isEqualTo(State.FEATURE_TURNED_OFF)

    sendBuildFailed()

    Truth.assertThat(stateReporter.currentState()).isEqualTo(State.FEATURE_TURNED_OFF)
    Truth.assertThat(receivedStateUpdates).isEmpty()

  }


  private fun createStateReporter() = BuildAttributionStateReporterImpl(projectRule.project, uiManagerMock).apply {
    Disposer.register(projectRule.fixture.testRootDisposable, this)
  }

  private fun sendBuildStarted() {
    val mock = mock(BuildContext::class.java)
    GradleBuildState.getInstance(projectRule.project).buildStarted(mock)
  }

  private fun sendBuildFailed() {
    GradleBuildState.getInstance(projectRule.project).buildFinished(BuildStatus.FAILED)
  }

  private fun sendBuildSuccess() {
    GradleBuildState.getInstance(projectRule.project).buildFinished(BuildStatus.SUCCESS)
  }

  private fun sendSyncFinished() {
    projectRule.project.messageBus.syncPublisher(GradleSyncState.GRADLE_SYNC_TOPIC).syncSucceeded(projectRule.project)
  }
}
