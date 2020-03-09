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
package com.android.tools.idea.whatsnew.assistant

import com.android.build.attribution.BuildAttributionStateReporter
import com.android.tools.idea.assistant.StatefulButtonNotifier
import com.android.tools.idea.assistant.StatefulButtonNotifier.BUTTON_STATE_TOPIC
import com.android.tools.idea.assistant.datamodel.ActionData
import com.android.tools.idea.whatsnew.assistant.actions.BuildAnalyzerShowActionStateManager
import com.google.common.truth.Truth
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.MessageBusConnection
import org.junit.After
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class BuildAnalyzerShowActionStateManagerTest {

  private val rootDisposable = Disposer.newDisposable()

  @After
  fun cleanup() {
    Disposer.dispose(rootDisposable)
  }

  @Test
  fun testStateManagerInitSubscribesForUpdates() {
    val projectMocks = mockProject()

    val manager = BuildAnalyzerShowActionStateManager()

    manager.init(projectMocks.project, mock(ActionData::class.java))

    verify(projectMocks.messageBusConnection).subscribe(eq(BuildAttributionStateReporter.FEATURE_STATE_TOPIC), any())
  }

  @Test
  fun testStateManagerInitMultipleProjects() {
    val project1Mocks = mockProject()
    val project2Mocks = mockProject()

    val manager = BuildAnalyzerShowActionStateManager()

    manager.init(project1Mocks.project, mock(ActionData::class.java))
    manager.init(project2Mocks.project, mock(ActionData::class.java))

    verify(project1Mocks.messageBusConnection).subscribe(eq(BuildAttributionStateReporter.FEATURE_STATE_TOPIC), any())
    verify(project2Mocks.messageBusConnection).subscribe(eq(BuildAttributionStateReporter.FEATURE_STATE_TOPIC), any())
  }

  @Test
  fun testStateManagerInitForSameProjectTwice() {
    val projectMocks = mockProject()

    val manager = BuildAnalyzerShowActionStateManager()

    manager.init(projectMocks.project, mock(ActionData::class.java))
    manager.init(projectMocks.project, mock(ActionData::class.java))

    verify(projectMocks.messageBusConnection).subscribe(eq(BuildAttributionStateReporter.FEATURE_STATE_TOPIC), any())
  }

  @Test
  fun testStateManagerInitForSameProjectAfterDisposal() {
    val projectMocks1 = mockProject()

    val manager = BuildAnalyzerShowActionStateManager()

    manager.init(projectMocks1.project, mock(ActionData::class.java))

    verify(projectMocks1.messageBusConnection).subscribe(eq(BuildAttributionStateReporter.FEATURE_STATE_TOPIC), any())

    // Dispose existing connection and create a new one.
    Disposer.dispose(projectMocks1.messageBusConnection)

    val projectMocks2 = mockProject(projectMocks1.project)

    manager.init(projectMocks2.project, mock(ActionData::class.java))

    verify(projectMocks2.messageBusConnection).subscribe(eq(BuildAttributionStateReporter.FEATURE_STATE_TOPIC), any())
  }

  @Test
  fun testStateUpdateTriggersButtonRefresh() {
    val projectMocks = mockProject()
    val buttonStateNotifier: StatefulButtonNotifier = mock(StatefulButtonNotifier::class.java)
    `when`(projectMocks.messageBus.syncPublisher(eq(BUTTON_STATE_TOPIC))).thenReturn(buttonStateNotifier)

    var capturedSubscriptionListener: BuildAttributionStateReporter.Notifier? = null
    `when`(projectMocks.messageBusConnection.subscribe(eq(BuildAttributionStateReporter.FEATURE_STATE_TOPIC), any())).then {
      capturedSubscriptionListener = it.getArgument(1)
      Unit
    }

    val manager = BuildAnalyzerShowActionStateManager()
    manager.init(projectMocks.project, mock(ActionData::class.java))

    Truth.assertThat(capturedSubscriptionListener).isNotNull()
    capturedSubscriptionListener!!.stateUpdated(BuildAttributionStateReporter.State.REPORT_DATA_READY)

    verify(buttonStateNotifier).stateUpdated()
  }

  private fun mockProject(project: Project = mock(Project::class.java).also { Disposer.register(rootDisposable, it) }): ProjectMocks {
    return ProjectMocks(project)
  }

  private inner class ProjectMocks(
    val project: Project
  ) {
    val messageBus: MessageBus = mock(MessageBus::class.java).also { Disposer.register(rootDisposable, it) }
    val messageBusConnection: MessageBusConnection = mock(MessageBusConnection::class.java).also { Disposer.register(rootDisposable, it) }

    init {
      `when`(project.messageBus).thenReturn(messageBus)
      `when`(messageBus.connect(eq(project))).thenReturn(messageBusConnection)
    }
  }
}