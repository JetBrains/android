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

import backgroundtask.inspection.BackgroundTaskInspectorProtocol
import com.android.tools.adtui.TreeWalker
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorClient
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.EntrySelectionModel
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.AlarmEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.JobEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.WakeLockEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.WorkEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.view.BackgroundTaskInspectorTestUtils.sendBackgroundTaskEvent
import com.android.tools.idea.appinspection.inspectors.backgroundtask.view.BackgroundTaskInspectorTestUtils.sendWorkAddedEvent
import com.android.tools.idea.appinspection.inspectors.backgroundtask.view.BackgroundTaskInspectorTestUtils.sendWorkRemovedEvent
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.util.concurrency.EdtExecutorService
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
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

class BackgroundTaskTreeTableView {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private lateinit var scope: CoroutineScope
  private lateinit var client: BackgroundTaskInspectorClient
  private lateinit var selectionModel: EntrySelectionModel
  private lateinit var entriesView: BackgroundTaskEntriesView
  private lateinit var uiDispatcher: ExecutorCoroutineDispatcher

  @Before
  fun setUp() = runBlocking {
    scope = CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher() + SupervisorJob())
    client = BackgroundTaskInspectorTestUtils.getFakeClient(scope)
    uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()
    withContext(uiDispatcher) {
      selectionModel = EntrySelectionModel()
      entriesView = BackgroundTaskEntriesView(client, selectionModel, scope, uiDispatcher)
    }
  }

  @After
  fun tearDown() {
    scope.cancel()
  }

  @Test
  fun initializeTable() = runBlocking(uiDispatcher) {
    val tree = TreeWalker(entriesView).descendantStream().filter { it is JTree }.findFirst().get() as JTree
    val root = tree.model.root
    val labels = (root as DefaultMutableTreeNode).children().toList().map { (it as DefaultMutableTreeNode).userObject as String }
    assertThat(labels.joinToString()).isEqualTo("Works, Jobs, Alarms, WakeLocks")
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
        jobBuilder.backoffPolicy = BackgroundTaskInspectorProtocol.JobInfo.BackoffPolicy.UNDEFINED_BACKOFF_POLICY
      }
    }

    client.sendBackgroundTaskEvent(6L) {
      taskId = 3L
      wakeLockAcquiredBuilder.apply {
        level = BackgroundTaskInspectorProtocol.WakeLockAcquired.Level.UNDEFINED_WAKE_LOCK_LEVEL
      }
    }

    withContext(uiDispatcher) {
      val tree = TreeWalker(entriesView).descendantStream().filter { it is JTree }.findFirst().get() as JTree
      val root = tree.model.root
      val works = (root as DefaultMutableTreeNode).children().asSequence().first { (it as DefaultMutableTreeNode).userObject == "Works" }
      assertThat(works.childCount).isEqualTo(1)
      val newWorkEntry = (works.getChildAt(0) as DefaultMutableTreeNode).userObject as WorkEntry
      assertThat(newWorkEntry.id).isEqualTo(workInfo.id)

      val alarms = root.children().asSequence().first { (it as DefaultMutableTreeNode).userObject == "Alarms" }
      assertThat(alarms.childCount).isEqualTo(1)
      val newAlarm = (alarms.getChildAt(0) as DefaultMutableTreeNode).userObject as AlarmEntry
      assertThat(newAlarm.id).isEqualTo("1")

      val jobs = root.children().asSequence().first { (it as DefaultMutableTreeNode).userObject == "Jobs" }
      assertThat(jobs.childCount).isEqualTo(1)
      val newJob = (jobs.getChildAt(0) as DefaultMutableTreeNode).userObject as JobEntry
      assertThat(newJob.id).isEqualTo("2")

      val wakeLocks = root.children().asSequence().first { (it as DefaultMutableTreeNode).userObject == "WakeLocks" }
      assertThat(wakeLocks.childCount).isEqualTo(1)
      val newWakeLock = (wakeLocks.getChildAt(0) as DefaultMutableTreeNode).userObject as WakeLockEntry
      assertThat(newWakeLock.id).isEqualTo("3")
    }
  }

  @Test
  fun removeWorkEntry() = runBlocking {
    val workInfo = BackgroundTaskInspectorTestUtils.FAKE_WORK_INFO
    client.sendWorkAddedEvent(workInfo)

    withContext(uiDispatcher) {
      val tree = TreeWalker(entriesView).descendantStream().filter { it is JTree }.findFirst().get() as JTree
      val root = tree.model.root
      val works = (root as DefaultMutableTreeNode).children().asSequence().first { (it as DefaultMutableTreeNode).userObject == "Works" }
      assertThat(works.childCount).isEqualTo(1)
      val newWorkEntry = (works.getChildAt(0) as DefaultMutableTreeNode).userObject as WorkEntry
      assertThat(newWorkEntry.id).isEqualTo(workInfo.id)
    }
    client.sendWorkRemovedEvent(workInfo.id)
    withContext(uiDispatcher) {
      val tree = TreeWalker(entriesView).descendantStream().filter { it is JTree }.findFirst().get() as JTree
      val root = tree.model.root
      val works = (root as DefaultMutableTreeNode).children().asSequence().first { (it as DefaultMutableTreeNode).userObject == "Works" }
      assertThat(works.childCount).isEqualTo(0)
    }
  }

  @Test
  fun addJobEntryUnderWork() = runBlocking {
    val workInfo = BackgroundTaskInspectorTestUtils.FAKE_WORK_INFO
    client.sendWorkAddedEvent(workInfo)

    client.sendBackgroundTaskEvent(5L) {
      taskId = 2L
      jobScheduledBuilder.apply {
        jobBuilder.backoffPolicy = BackgroundTaskInspectorProtocol.JobInfo.BackoffPolicy.UNDEFINED_BACKOFF_POLICY
        jobBuilder.extras = "{EXTRA_WORK_SPEC_ID=${workInfo.id}}"
      }
    }

    withContext(uiDispatcher) {
      val tree = TreeWalker(entriesView).descendantStream().filter { it is JTree }.findFirst().get() as JTree
      val root = tree.model.root
      val works = (root as DefaultMutableTreeNode).children().asSequence().first { (it as DefaultMutableTreeNode).userObject == "Works" }
      assertThat(works.childCount).isEqualTo(1)
      val newWorkNode = works.getChildAt(0) as DefaultMutableTreeNode
      val newWorkEntry = newWorkNode.userObject as WorkEntry
      assertThat(newWorkEntry.id).isEqualTo(workInfo.id)

      assertThat(newWorkNode.childCount).isEqualTo(1)
      val newJob = (newWorkNode.getChildAt(0) as DefaultMutableTreeNode).userObject as JobEntry
      assertThat(newJob.id).isEqualTo("2")

      val jobs = root.children().asSequence().first { (it as DefaultMutableTreeNode).userObject == "Jobs" }
      assertThat(jobs.childCount).isEqualTo(0)
    }
  }
}
