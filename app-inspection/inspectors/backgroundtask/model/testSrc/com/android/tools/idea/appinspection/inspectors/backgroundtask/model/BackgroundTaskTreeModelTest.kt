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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.model

import androidx.work.inspection.WorkManagerInspectorProtocol
import backgroundtask.inspection.BackgroundTaskInspectorProtocol
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTestUtils.getWorksCategoryNode
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import javax.swing.tree.DefaultMutableTreeNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Before
import org.junit.Test

class BackgroundTaskTreeModelTest {

  private class FakeAppInspectorMessenger(
    override val scope: CoroutineScope,
  ) : AppInspectorMessenger {
    override suspend fun sendRawCommand(rawData: ByteArray): ByteArray = rawData
    override val eventFlow = emptyFlow<ByteArray>()
  }

  private lateinit var scope: CoroutineScope
  private lateinit var backgroundTaskInspectorMessenger: FakeAppInspectorMessenger
  private lateinit var workManagerInspectorMessenger: FakeAppInspectorMessenger
  private lateinit var client: BackgroundTaskInspectorClient
  private lateinit var model: BackgroundTaskTreeModel

  @Before
  fun setUp() {
    val dispatcher = MoreExecutors.directExecutor().asCoroutineDispatcher()
    scope = CoroutineScope(dispatcher + SupervisorJob())
    backgroundTaskInspectorMessenger = FakeAppInspectorMessenger(scope)
    workManagerInspectorMessenger = FakeAppInspectorMessenger(scope)
    client =
      BackgroundTaskInspectorClient(
        backgroundTaskInspectorMessenger,
        WmiMessengerTarget.Resolved(workManagerInspectorMessenger),
        scope,
        StubBackgroundTaskInspectorTracker()
      )
    model = BackgroundTaskTreeModel(client, scope, dispatcher)
  }

  @After
  fun tearDown() {
    scope.cancel()
  }

  @Test
  fun addTreeNodes() {
    val newWorkEvent =
      WorkManagerInspectorProtocol.Event.newBuilder()
        .apply {
          workAddedBuilder.workBuilder.apply {
            id = "test"
            state = WorkManagerInspectorProtocol.WorkInfo.State.ENQUEUED
          }
        }
        .build()

    val newJobEvent =
      BackgroundTaskInspectorProtocol.Event.newBuilder()
        .apply {
          backgroundTaskEventBuilder.apply {
            taskId = 0L
            jobScheduledBuilder.apply {
              jobBuilder.backoffPolicy =
                BackgroundTaskInspectorProtocol.JobInfo.BackoffPolicy.UNDEFINED_BACKOFF_POLICY
            }
          }
        }
        .build()

    val newAlarmEvent =
      BackgroundTaskInspectorProtocol.Event.newBuilder()
        .apply {
          backgroundTaskEventBuilder.apply {
            taskId = 1L
            alarmSetBuilder.apply {
              type = BackgroundTaskInspectorProtocol.AlarmSet.Type.UNDEFINED_ALARM_TYPE
            }
          }
        }
        .build()

    val newWakeLockEvent =
      BackgroundTaskInspectorProtocol.Event.newBuilder()
        .apply {
          backgroundTaskEventBuilder.apply {
            taskId = 2L
            wakeLockAcquiredBuilder.apply {
              level =
                BackgroundTaskInspectorProtocol.WakeLockAcquired.Level.UNDEFINED_WAKE_LOCK_LEVEL
            }
          }
        }
        .build()

    listOf(newWakeLockEvent, newAlarmEvent, newJobEvent).forEach { event ->
      client.handleEvent(EventWrapper(EventWrapper.Case.BACKGROUND_TASK, event.toByteArray()))
    }
    client.handleEvent(EventWrapper(EventWrapper.Case.WORK, newWorkEvent.toByteArray()))

    val root = model.root as DefaultMutableTreeNode
    assertThat(root.childCount).isEqualTo(4)
    val workChild = root.firstChild as DefaultMutableTreeNode
    assertThat(workChild.childCount).isEqualTo(1)
    assertThat(workChild.userObject).isEqualTo("Workers")
    assertThat(workChild.firstChild as DefaultMutableTreeNode).isEqualTo(model.getTreeNode("test"))
    val jobChild = root.getChildAfter(workChild) as DefaultMutableTreeNode
    assertThat(jobChild.childCount).isEqualTo(1)
    assertThat(jobChild.userObject).isEqualTo("Jobs")
    assertThat(jobChild.firstChild as DefaultMutableTreeNode).isEqualTo(model.getTreeNode("0"))
    val alarmChild = root.getChildAfter(jobChild) as DefaultMutableTreeNode
    assertThat(alarmChild.childCount).isEqualTo(1)
    assertThat(alarmChild.userObject).isEqualTo("Alarms")
    assertThat(alarmChild.firstChild as DefaultMutableTreeNode).isEqualTo(model.getTreeNode("1"))
    val wakeLockChild = root.getChildAfter(alarmChild) as DefaultMutableTreeNode
    assertThat(wakeLockChild.childCount).isEqualTo(1)
    assertThat(wakeLockChild.userObject).isEqualTo("WakeLocks")
    assertThat(wakeLockChild.firstChild as DefaultMutableTreeNode).isEqualTo(model.getTreeNode("2"))
  }

  @Test
  fun removeTreeNode() {
    val newWorkEvent =
      WorkManagerInspectorProtocol.Event.newBuilder()
        .apply {
          workAddedBuilder.workBuilder.apply {
            id = "test"
            state = WorkManagerInspectorProtocol.WorkInfo.State.ENQUEUED
          }
        }
        .build()

    val newJobEvent =
      BackgroundTaskInspectorProtocol.Event.newBuilder()
        .apply {
          backgroundTaskEventBuilder.apply {
            taskId = 0L
            jobScheduledBuilder.apply {
              jobBuilder.backoffPolicy =
                BackgroundTaskInspectorProtocol.JobInfo.BackoffPolicy.UNDEFINED_BACKOFF_POLICY
              jobBuilder.extras =
                BackgroundTaskInspectorTestUtils.createJobInfoExtraWithWorkerId("test")
            }
          }
        }
        .build()

    client.handleEvent(EventWrapper(EventWrapper.Case.WORK, newWorkEvent.toByteArray()))
    client.handleEvent(EventWrapper(EventWrapper.Case.BACKGROUND_TASK, newJobEvent.toByteArray()))

    val root = model.root as DefaultMutableTreeNode
    assertThat(root.childCount).isEqualTo(4)
    val workChild = root.getWorksCategoryNode()
    assertThat(workChild.childCount).isEqualTo(1)
    assertThat(workChild.userObject).isEqualTo("Workers")
    val entryNode = workChild.firstChild as DefaultMutableTreeNode
    assertThat(entryNode).isEqualTo(model.getTreeNode("test"))

    val removeWorkEvent =
      WorkManagerInspectorProtocol.Event.newBuilder()
        .apply { workRemovedBuilder.apply { id = "test" } }
        .build()
    client.handleEvent(EventWrapper(EventWrapper.Case.WORK, removeWorkEvent.toByteArray()))

    assertThat(entryNode.parent).isNull()
    assertThat(model.getTreeNode("test")).isNull()
    assertThat(model.getTreeNode("0")).isNull()
  }

  @Test
  fun emptyMessageAddedAndRemoved() {
    val root = model.root as DefaultMutableTreeNode
    assertThat(root.childCount).isEqualTo(4)
    val categoryNodes =
      root.children().toList().filterIsInstance<BackgroundTaskCategoryNode>().toList()
    assertThat(categoryNodes).hasSize(4)

    assertThat(categoryNodes.map { (it.firstChild as DefaultMutableTreeNode).userObject as String })
      .containsExactly(
        "No workers have been detected.",
        "No jobs have been detected.",
        "No alarms have been detected.",
        "No wake locks have been detected.",
      )

    val newWorkEvent =
      WorkManagerInspectorProtocol.Event.newBuilder()
        .apply {
          workAddedBuilder.workBuilder.apply {
            id = "test"
            state = WorkManagerInspectorProtocol.WorkInfo.State.ENQUEUED
          }
        }
        .build()

    val newJobEvent =
      BackgroundTaskInspectorProtocol.Event.newBuilder()
        .apply {
          backgroundTaskEventBuilder.apply {
            taskId = 0L
            jobScheduledBuilder.apply {
              jobBuilder.backoffPolicy =
                BackgroundTaskInspectorProtocol.JobInfo.BackoffPolicy.UNDEFINED_BACKOFF_POLICY
            }
          }
        }
        .build()

    val newAlarmEvent =
      BackgroundTaskInspectorProtocol.Event.newBuilder()
        .apply {
          backgroundTaskEventBuilder.apply {
            taskId = 1L
            alarmSetBuilder.apply {
              type = BackgroundTaskInspectorProtocol.AlarmSet.Type.UNDEFINED_ALARM_TYPE
            }
          }
        }
        .build()

    val newWakeLockEvent =
      BackgroundTaskInspectorProtocol.Event.newBuilder()
        .apply {
          backgroundTaskEventBuilder.apply {
            taskId = 2L
            wakeLockAcquiredBuilder.apply {
              level =
                BackgroundTaskInspectorProtocol.WakeLockAcquired.Level.UNDEFINED_WAKE_LOCK_LEVEL
            }
          }
        }
        .build()

    listOf(newWakeLockEvent, newAlarmEvent, newJobEvent).forEach { event ->
      client.handleEvent(EventWrapper(EventWrapper.Case.BACKGROUND_TASK, event.toByteArray()))
    }
    client.handleEvent(EventWrapper(EventWrapper.Case.WORK, newWorkEvent.toByteArray()))

    assertThat(categoryNodes.map { (it.firstChild as DefaultMutableTreeNode).userObject })
      .isNotInstanceOf(String::class.java)
  }
}
