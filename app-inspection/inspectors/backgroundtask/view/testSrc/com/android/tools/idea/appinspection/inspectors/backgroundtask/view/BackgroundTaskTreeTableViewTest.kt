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
import backgroundtask.inspection.BackgroundTaskInspectorProtocol
import com.android.tools.adtui.TreeWalker
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServicesAdapter
import com.android.tools.idea.appinspection.inspectors.backgroundtask.ide.IntellijUiComponentsProvider
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorClient
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTestUtils
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTestUtils.assertEmptyWithMessage
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTestUtils.getAlarmsCategoryNode
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTestUtils.getJobsCategoryNode
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTestUtils.getWakeLocksCategoryNode
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTestUtils.getWorksCategoryNode
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTestUtils.sendBackgroundTaskEvent
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTestUtils.sendWorkAddedEvent
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTestUtils.sendWorkEvent
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTestUtils.sendWorkRemovedEvent
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskTreeModel
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.EntrySelectionModel
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.StubBackgroundTaskInspectorTracker
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.WmiMessengerTarget
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.AlarmEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.BackgroundTaskEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.JobEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.WakeLockEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.WorkEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.view.BackgroundTaskViewTestUtils.getAlarmsCategoryNode
import com.android.tools.idea.appinspection.inspectors.backgroundtask.view.BackgroundTaskViewTestUtils.getJobsCategoryNode
import com.android.tools.idea.appinspection.inspectors.backgroundtask.view.BackgroundTaskViewTestUtils.getWakeLocksCategoryNode
import com.android.tools.idea.appinspection.inspectors.backgroundtask.view.BackgroundTaskViewTestUtils.getWorksCategoryNode
import com.android.tools.idea.appinspection.inspectors.backgroundtask.view.table.CLASS_NAME_COMPARATOR
import com.android.tools.idea.appinspection.inspectors.backgroundtask.view.table.START_TIME_COMPARATOR
import com.android.tools.idea.appinspection.inspectors.backgroundtask.view.table.STATUS_COMPARATOR
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.util.concurrency.EdtExecutorService
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
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

class BackgroundTaskTreeTableViewTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private lateinit var scope: CoroutineScope
  private lateinit var workMessenger: BackgroundTaskViewTestUtils.FakeAppInspectorMessenger
  private lateinit var client: BackgroundTaskInspectorClient
  private lateinit var tab: BackgroundTaskInspectorTab
  private lateinit var uiDispatcher: ExecutorCoroutineDispatcher
  private lateinit var selectionModel: EntrySelectionModel
  private lateinit var entriesView: BackgroundTaskEntriesView

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
    }
  }

  @After
  fun tearDown() {
    scope.cancel()
  }

  @Test
  fun initializeTable() =
    runBlocking(uiDispatcher) {
      val scrollPane =
        TreeWalker(entriesView).descendantStream().filter { it is JScrollPane }.findFirst().get()
      // Make sure there are no ScrollPane outside the table view component.
      assertThat(
          TreeWalker(scrollPane).descendantStream().anyMatch {
            it == entriesView.tableView.component
          }
        )
        .isFalse()
      val tree =
        TreeWalker(entriesView).descendantStream().filter { it is JTree }.findFirst().get() as JTree
      val root = tree.model.root
      val labels =
        (root as DefaultMutableTreeNode).children().toList().map {
          (it as DefaultMutableTreeNode).userObject as String
        }
      assertThat(labels.joinToString()).isEqualTo("Workers, Jobs, Alarms, WakeLocks")
    }

  @Test
  fun addNewEntries() = runBlocking {
    val workInfo = BackgroundTaskInspectorTestUtils.FAKE_WORK_INFO
    client.sendWorkAddedEvent(workInfo)

    client.sendBackgroundTaskEvent(4L) {
      taskId = 1L
      alarmSetBuilder.apply {
        type = BackgroundTaskInspectorProtocol.AlarmSet.Type.UNDEFINED_ALARM_TYPE
      }
    }

    client.sendBackgroundTaskEvent(5L) {
      taskId = 2L
      jobScheduledBuilder.apply {
        jobBuilder.backoffPolicy =
          BackgroundTaskInspectorProtocol.JobInfo.BackoffPolicy.UNDEFINED_BACKOFF_POLICY
      }
    }

    client.sendBackgroundTaskEvent(6L) {
      taskId = 3L
      wakeLockAcquiredBuilder.apply {
        level = BackgroundTaskInspectorProtocol.WakeLockAcquired.Level.UNDEFINED_WAKE_LOCK_LEVEL
      }
    }

    withContext(uiDispatcher) {
      val works = entriesView.getWorksCategoryNode()
      assertThat(works.childCount).isEqualTo(1)
      val newWorkEntry = (works.getChildAt(0) as DefaultMutableTreeNode).userObject as WorkEntry
      assertThat(newWorkEntry.id).isEqualTo(workInfo.id)

      val alarms = entriesView.getAlarmsCategoryNode()
      assertThat(alarms.childCount).isEqualTo(1)
      val newAlarm = (alarms.getChildAt(0) as DefaultMutableTreeNode).userObject as AlarmEntry
      assertThat(newAlarm.id).isEqualTo("1")

      val jobs = entriesView.getJobsCategoryNode()
      assertThat(jobs.childCount).isEqualTo(1)
      val newJob = (jobs.getChildAt(0) as DefaultMutableTreeNode).userObject as JobEntry
      assertThat(newJob.id).isEqualTo("2")

      val wakeLocks = entriesView.getWakeLocksCategoryNode()
      assertThat(wakeLocks.childCount).isEqualTo(1)
      val newWakeLock =
        (wakeLocks.getChildAt(0) as DefaultMutableTreeNode).userObject as WakeLockEntry
      assertThat(newWakeLock.id).isEqualTo("3")
    }
  }

  @Test
  fun removeWorkEntry() = runBlocking {
    val workInfo = BackgroundTaskInspectorTestUtils.FAKE_WORK_INFO
    client.sendWorkAddedEvent(workInfo)

    withContext(uiDispatcher) {
      val works = entriesView.getWorksCategoryNode()
      assertThat(works.childCount).isEqualTo(1)
      val newWorkEntry = (works.getChildAt(0) as DefaultMutableTreeNode).userObject as WorkEntry
      assertThat(newWorkEntry.id).isEqualTo(workInfo.id)
    }
    client.sendWorkRemovedEvent(workInfo.id)
    withContext(uiDispatcher) {
      val works = entriesView.getWorksCategoryNode()
      works.assertEmptyWithMessage("No workers have been detected.")
    }
  }

  @Test
  fun addJobEntryUnderWork() = runBlocking {
    val workInfo = BackgroundTaskInspectorTestUtils.FAKE_WORK_INFO
    client.sendWorkAddedEvent(workInfo)

    client.sendBackgroundTaskEvent(5L) {
      taskId = 2L
      jobScheduledBuilder.apply {
        jobBuilder.backoffPolicy =
          BackgroundTaskInspectorProtocol.JobInfo.BackoffPolicy.UNDEFINED_BACKOFF_POLICY
        jobBuilder.extras =
          BackgroundTaskInspectorTestUtils.createJobInfoExtraWithWorkerId("${workInfo.id}")
      }
    }

    withContext(uiDispatcher) {
      val works = entriesView.getWorksCategoryNode()
      assertThat(works.childCount).isEqualTo(1)
      val newWorkNode = works.getChildAt(0) as DefaultMutableTreeNode
      val newWorkEntry = newWorkNode.userObject as WorkEntry
      assertThat(newWorkEntry.id).isEqualTo(workInfo.id)

      assertThat(newWorkNode.childCount).isEqualTo(1)
      val newJob = (newWorkNode.getChildAt(0) as DefaultMutableTreeNode).userObject as JobEntry
      assertThat(newJob.id).isEqualTo("2")

      val jobs = entriesView.getJobsCategoryNode()
      jobs.assertEmptyWithMessage("No jobs have been detected.")
    }
  }

  @Test
  fun sortEntriesByClassName() =
    runBlocking<Unit> {
      client.sendWorkEvent {
        workAddedBuilder.apply {
          workBuilder.apply {
            workerClassName = "a.b.test"
            id = "123"
            scheduleRequestedAt = 2L
            state = WorkManagerInspectorProtocol.WorkInfo.State.RUNNING
          }
        }
      }
      client.sendWorkEvent {
        workAddedBuilder.apply {
          workBuilder.apply {
            workerClassName = "a.b.test3"
            id = "12345"
            scheduleRequestedAt = 1L
            state = WorkManagerInspectorProtocol.WorkInfo.State.ENQUEUED
          }
        }
      }
      client.sendWorkEvent {
        workAddedBuilder.apply {
          workBuilder.apply {
            workerClassName = "a.b.test2"
            id = "1234"
            scheduleRequestedAt = 1L
            state = WorkManagerInspectorProtocol.WorkInfo.State.SUCCEEDED
          }
        }
      }

      client.sendBackgroundTaskEvent(4L) {
        taskId = 1
        alarmSetBuilder.apply {
          type = BackgroundTaskInspectorProtocol.AlarmSet.Type.UNDEFINED_ALARM_TYPE
          triggerMs = 123
        }
      }
      client.sendBackgroundTaskEvent(5L) {
        taskId = 2
        alarmSetBuilder.apply {
          type = BackgroundTaskInspectorProtocol.AlarmSet.Type.UNDEFINED_ALARM_TYPE
          triggerMs = 12
        }
      }

      client.sendBackgroundTaskEvent(5L) {
        taskId = 1
        alarmCancelled = BackgroundTaskInspectorProtocol.AlarmCancelled.getDefaultInstance()
      }

      client.sendBackgroundTaskEvent(5L) {
        taskId = 3
        jobScheduledBuilder.apply {
          jobBuilder.backoffPolicy =
            BackgroundTaskInspectorProtocol.JobInfo.BackoffPolicy.UNDEFINED_BACKOFF_POLICY
        }
      }
      // This should be nested under a worker. Will not show up under Jobs category.
      client.sendBackgroundTaskEvent(6L) {
        taskId = 4
        jobScheduledBuilder.apply {
          jobBuilder.apply {
            backoffPolicy =
              BackgroundTaskInspectorProtocol.JobInfo.BackoffPolicy.UNDEFINED_BACKOFF_POLICY
            extras = BackgroundTaskInspectorTestUtils.createJobInfoExtraWithWorkerId("123")
          }
        }
      }
      client.sendBackgroundTaskEvent(4L) {
        taskId = 5
        jobScheduledBuilder.apply {
          jobBuilder.backoffPolicy =
            BackgroundTaskInspectorProtocol.JobInfo.BackoffPolicy.UNDEFINED_BACKOFF_POLICY
        }
      }
      client.sendBackgroundTaskEvent(4L) {
        taskId = 5
        jobFinished = BackgroundTaskInspectorProtocol.JobFinished.getDefaultInstance()
      }
      client.sendBackgroundTaskEvent(6L) {
        taskId = 6
        wakeLockAcquiredBuilder.apply {
          level = BackgroundTaskInspectorProtocol.WakeLockAcquired.Level.UNDEFINED_WAKE_LOCK_LEVEL
        }
      }
      client.sendBackgroundTaskEvent(5L) {
        taskId = 7
        wakeLockAcquiredBuilder.apply {
          level = BackgroundTaskInspectorProtocol.WakeLockAcquired.Level.UNDEFINED_WAKE_LOCK_LEVEL
        }
      }
      client.sendBackgroundTaskEvent(7L) {
        taskId = 6
        wakeLockReleased = BackgroundTaskInspectorProtocol.WakeLockReleased.getDefaultInstance()
      }

      withContext(uiDispatcher) {
        val tree =
          TreeWalker(entriesView).descendantStream().filter { it is JTree }.findFirst().get() as
            JTree
        val root = tree.model.root as DefaultMutableTreeNode
        val model = tree.model as BackgroundTaskTreeModel
        assertThat(root.getWorksCategoryNode().childCount).isEqualTo(3)
        assertThat(root.getAlarmsCategoryNode().childCount).isEqualTo(2)
        assertThat(root.getJobsCategoryNode().childCount).isEqualTo(2)
        assertThat(root.getWakeLocksCategoryNode().childCount).isEqualTo(2)

        // Class names are sorted in alphabetical order.
        model.sort(CLASS_NAME_COMPARATOR)
        root.verifyNaturalOrdering { className }

        // Ordered by the value of Status enum
        model.sort(STATUS_COMPARATOR)
        assertThat(
            root
              .getWorksCategoryNode()
              .children()
              .toList()
              .map { ((it as DefaultMutableTreeNode).userObject as BackgroundTaskEntry).id }
              .toList()
          )
          .containsExactly("12345", "123", "1234")
        assertThat(
            root
              .getAlarmsCategoryNode()
              .children()
              .toList()
              .map { ((it as DefaultMutableTreeNode).userObject as BackgroundTaskEntry).id }
              .toList()
          )
          .containsExactly("2", "1")
        assertThat(
            root
              .getJobsCategoryNode()
              .children()
              .toList()
              .map { ((it as DefaultMutableTreeNode).userObject as BackgroundTaskEntry).id }
              .toList()
          )
          .containsExactly("3", "5")
        assertThat(
            root
              .getWakeLocksCategoryNode()
              .children()
              .toList()
              .map { ((it as DefaultMutableTreeNode).userObject as BackgroundTaskEntry).id }
              .toList()
          )
          .containsExactly("7", "6")

        // Ordered by timestamp
        model.sort(START_TIME_COMPARATOR)
        root.verifyNaturalOrdering { startTimeMs }
      }
    }
}

private fun <T> DefaultMutableTreeNode.verifyNaturalOrdering(
  extractor: BackgroundTaskEntry.() -> T
) {
  assertThat(
      getWorksCategoryNode()
        .children()
        .toList()
        .map { extractor((it as DefaultMutableTreeNode).userObject as BackgroundTaskEntry) }
        .toList()
    )
    .isOrdered()
  assertThat(
      getAlarmsCategoryNode()
        .children()
        .toList()
        .map { extractor((it as DefaultMutableTreeNode).userObject as BackgroundTaskEntry) }
        .toList()
    )
    .isOrdered()
  assertThat(
      getJobsCategoryNode()
        .children()
        .toList()
        .map { extractor((it as DefaultMutableTreeNode).userObject as BackgroundTaskEntry) }
        .toList()
    )
    .isOrdered()
  assertThat(
      getWakeLocksCategoryNode()
        .children()
        .toList()
        .map { extractor((it as DefaultMutableTreeNode).userObject as BackgroundTaskEntry) }
        .toList()
    )
    .isOrdered()
}
