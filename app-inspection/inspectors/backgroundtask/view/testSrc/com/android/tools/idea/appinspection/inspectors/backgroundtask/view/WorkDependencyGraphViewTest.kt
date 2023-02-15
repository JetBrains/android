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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.view

import androidx.work.inspection.WorkManagerInspectorProtocol
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServicesAdapter
import com.android.tools.idea.appinspection.inspectors.backgroundtask.ide.IntellijUiComponentsProvider
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorClient
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTestUtils.sendWorkAddedEvent
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.EntrySelectionModel
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.StubBackgroundTaskInspectorTracker
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.WmiMessengerTarget
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.util.concurrency.EdtExecutorService
import java.awt.event.ActionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class WorkDependencyGraphViewTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private lateinit var scope: CoroutineScope
  private lateinit var workMessenger: BackgroundTaskViewTestUtils.FakeAppInspectorMessenger
  private lateinit var client: BackgroundTaskInspectorClient
  private lateinit var tab: BackgroundTaskInspectorTab
  private lateinit var uiDispatcher: ExecutorCoroutineDispatcher
  private lateinit var selectionModel: EntrySelectionModel
  private lateinit var entriesView: BackgroundTaskEntriesView
  private lateinit var graphView: WorkDependencyGraphView

  @Before
  fun setUp() = runBlocking {
    scope = CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher() + SupervisorJob())
    uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()
    withContext(uiDispatcher) {
      val backgroundTaskInspectorMessenger =
        BackgroundTaskViewTestUtils.FakeAppInspectorMessenger(scope)
      workMessenger = BackgroundTaskViewTestUtils.FakeAppInspectorMessenger(scope)
      client =
        BackgroundTaskInspectorClient(
          backgroundTaskInspectorMessenger,
          WmiMessengerTarget.Resolved(workMessenger),
          scope,
          StubBackgroundTaskInspectorTracker()
        )
      tab =
        BackgroundTaskInspectorTab(
          client,
          AppInspectionIdeServicesAdapter(),
          IntellijUiComponentsProvider(projectRule.project),
          scope,
          uiDispatcher
        )
      selectionModel = tab.selectionModel
      entriesView = tab.component.firstComponent as BackgroundTaskEntriesView
      graphView = entriesView.graphView
    }
  }

  @After
  fun tearDown() {
    scope.cancel()
  }

  @Test
  fun navigateWorks() = runBlocking {
    val parentWorkInfo: WorkManagerInspectorProtocol.WorkInfo =
      WorkManagerInspectorProtocol.WorkInfo.newBuilder()
        .apply {
          id = "parent"
          workerClassName = "package1.package2.ClassName2"
          state = WorkManagerInspectorProtocol.WorkInfo.State.ENQUEUED
          scheduleRequestedAt = 1L
          runAttemptCount = 1
          constraints = WorkManagerInspectorProtocol.Constraints.getDefaultInstance()
          isPeriodic = false
          addAllDependents(listOf("left_child", "right_child"))
        }
        .build()

    val leftChildWorkInfo: WorkManagerInspectorProtocol.WorkInfo =
      WorkManagerInspectorProtocol.WorkInfo.newBuilder()
        .apply {
          id = "left_child"
          workerClassName = "package1.package2.ClassName3"
          state = WorkManagerInspectorProtocol.WorkInfo.State.ENQUEUED
          scheduleRequestedAt = 2L
          runAttemptCount = 1
          constraints = WorkManagerInspectorProtocol.Constraints.getDefaultInstance()
          isPeriodic = false
          addPrerequisites("parent")
        }
        .build()

    val rightChildWorkInfo: WorkManagerInspectorProtocol.WorkInfo =
      WorkManagerInspectorProtocol.WorkInfo.newBuilder()
        .apply {
          id = "right_child"
          workerClassName = "package1.package2.ClassName4"
          state = WorkManagerInspectorProtocol.WorkInfo.State.ENQUEUED
          scheduleRequestedAt = 2L
          runAttemptCount = 1
          constraints = WorkManagerInspectorProtocol.Constraints.getDefaultInstance()
          isPeriodic = false
          addPrerequisites("parent")
        }
        .build()

    client.sendWorkAddedEvent(parentWorkInfo)
    client.sendWorkAddedEvent(leftChildWorkInfo)
    client.sendWorkAddedEvent(rightChildWorkInfo)
    withContext(uiDispatcher) {
      selectionModel.selectedEntry = client.getEntry(parentWorkInfo.id)
      // Move down to left_child work.
      val actionEvent: ActionEvent = Mockito.mock(ActionEvent::class.java)
      graphView.actionMap["Down"].actionPerformed(actionEvent)
      assertThat(selectionModel.selectedWork).isEqualTo(leftChildWorkInfo)
      // Move right to right_child work.
      graphView.actionMap["Right"].actionPerformed(actionEvent)
      assertThat(selectionModel.selectedWork).isEqualTo(rightChildWorkInfo)
      // Move left to left_child work.
      graphView.actionMap["Left"].actionPerformed(actionEvent)
      assertThat(selectionModel.selectedWork).isEqualTo(leftChildWorkInfo)
      // Move up to parent work.
      graphView.actionMap["Up"].actionPerformed(actionEvent)
      assertThat(selectionModel.selectedWork).isEqualTo(parentWorkInfo)
    }
  }
}
