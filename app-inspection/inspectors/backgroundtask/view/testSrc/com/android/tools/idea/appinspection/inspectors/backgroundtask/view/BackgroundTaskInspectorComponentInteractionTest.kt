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

import androidx.work.inspection.WorkManagerInspectorProtocol.Command
import androidx.work.inspection.WorkManagerInspectorProtocol.WorkInfo
import com.android.tools.adtui.TreeWalker
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServicesAdapter
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspectors.backgroundtask.ide.IntellijUiComponentsProvider
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorClient
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTestUtils
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.EntrySelectionModel
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.WmiMessengerTarget
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTestUtils.sendWorkAddedEvent
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTestUtils.sendWorkRemovedEvent
import com.android.tools.idea.appinspection.inspectors.backgroundtask.view.table.BackgroundTaskTreeTableView
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.testFramework.TestActionEvent
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.streams.toList

class BackgroundTaskInspectorComponentInteractionTest {
  private class FakeAppInspectorMessenger(
    override val scope: CoroutineScope
  ) : AppInspectorMessenger {
    var rawDataSent: ByteArray = ByteArray(0)
    override suspend fun sendRawCommand(rawData: ByteArray): ByteArray {
      rawDataSent = rawData
      return rawDataSent
    }

    override val eventFlow = emptyFlow<ByteArray>()
  }

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private lateinit var scope: CoroutineScope
  private lateinit var workMessenger: FakeAppInspectorMessenger
  private lateinit var client: BackgroundTaskInspectorClient
  private lateinit var tab: BackgroundTaskInspectorTab
  private lateinit var uiDispatcher: ExecutorCoroutineDispatcher
  private lateinit var selectionModel: EntrySelectionModel
  private lateinit var entriesView: BackgroundTaskEntriesView
  private lateinit var detailsView: EntryDetailsView
  private lateinit var tableView: BackgroundTaskTreeTableView
  private lateinit var graphView: WorkDependencyGraphView

  @Before
  fun setUp() = runBlocking {
    scope = CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher() + SupervisorJob())
    uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()
    withContext(uiDispatcher) {
      val backgroundTaskInspectorMessenger = FakeAppInspectorMessenger(scope)
      workMessenger = FakeAppInspectorMessenger(scope)
      client = BackgroundTaskInspectorClient(backgroundTaskInspectorMessenger,
                                             WmiMessengerTarget.Resolved(workMessenger),
                                             scope)
      tab = BackgroundTaskInspectorTab(client, AppInspectionIdeServicesAdapter(), IntellijUiComponentsProvider(projectRule.project), scope,
                                       uiDispatcher)
      tab.isDetailsViewVisible = true
      detailsView = tab.component.secondComponent as EntryDetailsView
      tab.isDetailsViewVisible = false
      selectionModel = detailsView.selectionModel
      entriesView = tab.component.firstComponent as BackgroundTaskEntriesView
      tableView = entriesView.tableView
      graphView = entriesView.graphView
    }
  }

  @After
  fun tearDown() {
    scope.cancel()
  }

  @Test
  fun filterTableContentsWithTag() = runBlocking {
    val workInfo1 = WorkInfo.newBuilder()
      .setId("id1")
      .addAllTags(listOf("tag1"))
      .build()
    val workInfo2 = WorkInfo.newBuilder()
      .setId("id2")
      .addAllTags(listOf("tag1"))
      .build()
    val workInfo3 = WorkInfo.newBuilder()
      .setId("id3")
      .addAllTags(listOf("tag2"))
      .build()
    client.sendWorkAddedEvent(workInfo1)
    client.sendWorkAddedEvent(workInfo2)
    client.sendWorkAddedEvent(workInfo3)
    withContext(uiDispatcher) {
      val filterActionList = entriesView.getFilterActionList()
      assertThat(filterActionList.size).isEqualTo(3)
      assertThat(filterActionList[0].templateText).isEqualTo("All tags")
      val tag1Filter = filterActionList[1]
      assertThat(tag1Filter.templateText).isEqualTo("tag1")
      val tag2Filter = filterActionList[2]
      assertThat(tag2Filter.templateText).isEqualTo("tag2")
      val event: AnActionEvent = Mockito.mock(AnActionEvent::class.java)

      val tree = TreeWalker(entriesView).descendantStream().filter { it is JTree }.findFirst().get() as JTree
      val root = tree.model.root
      val works = (root as DefaultMutableTreeNode).children().asSequence().first { (it as DefaultMutableTreeNode).userObject == "Workers" }
      tag1Filter.setSelected(event, true)
      assertThat(works.childCount).isEqualTo(2)
      tag2Filter.setSelected(event, true)
      assertThat(works.childCount).isEqualTo(1)
      val allTagsFilter = filterActionList[0]
      allTagsFilter.setSelected(event, true)
      assertThat(works.childCount).isEqualTo(3)
    }
  }

  @Test
  fun cancelSelectedWork() = runBlocking {
    val works = WorkInfo.State.values()
      .filter { state -> state != WorkInfo.State.UNSPECIFIED && state != WorkInfo.State.UNRECOGNIZED }
      .map { state ->
        WorkInfo.newBuilder().apply {
          id = "id$state"
          this.state = state
        }.build()
      }
      .toList()

    val cancellableStates = setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)

    withContext(uiDispatcher) {
      val toolbar =
        TreeWalker(entriesView).descendantStream().filter { it is ActionToolbar }.findFirst().get() as ActionToolbarImpl

      val cancelAction = toolbar.actions[0] as AnAction
      val event: AnActionEvent = TestActionEvent()
      assertThat(cancelAction.templateText).isEqualTo("Cancel Selected Work")
      cancelAction.update(event)
      assertThat(event.presentation.isEnabled).isFalse()

      works.forEachIndexed { i, work ->
        client.sendWorkAddedEvent(work)
        selectionModel.selectedEntry = client.getEntry(work.id)
        cancelAction.update(event)

        val canCancel = cancellableStates.contains(work.state)
        assertThat(event.presentation.isEnabled).isEqualTo(canCancel)
        if (canCancel) {
          cancelAction.actionPerformed(event)
          assertThat(Command.parseFrom(workMessenger.rawDataSent).cancelWork.id).isEqualTo(work.id)
        }
      }
    }
  }

  @Test
  fun openDependencyGraphView() = runBlocking {
    val workInfo = BackgroundTaskInspectorTestUtils.FAKE_WORK_INFO
    client.sendWorkAddedEvent(workInfo)
    withContext(uiDispatcher) {
      selectionModel.selectedEntry = client.getEntry(workInfo.id)
      val toolbar = TreeWalker(tab.component)
        .descendantStream()
        .filter { it is ActionToolbar }
        .toList()[1] as ActionToolbarImpl
      val graphViewAction = toolbar.actions[1] as AnAction
      assertThat(graphViewAction.templateText).isEqualTo("Show Graph View")
      val event: AnActionEvent = Mockito.mock(AnActionEvent::class.java)
      graphViewAction.actionPerformed(event)
      assertThat(graphView.getFirstChildIsInstance<JLabel>().text).isEqualTo("ClassName1")
    }
  }

  @Test
  fun openTableViewAfterGraphView() = runBlocking {
    val workInfo = BackgroundTaskInspectorTestUtils.FAKE_WORK_INFO
    client.sendWorkAddedEvent(workInfo)
    withContext(uiDispatcher) {
      selectionModel.selectedEntry = client.getEntry(workInfo.id)
      val toolbar = TreeWalker(tab.component)
        .descendantStream()
        .filter { it is ActionToolbar }
        .toList()[1] as ActionToolbarImpl
      val graphViewAction = toolbar.actions[1] as AnAction
      val event: AnActionEvent = Mockito.mock(AnActionEvent::class.java)
      graphViewAction.actionPerformed(event)

      val tableViewAction = toolbar.actions[0] as AnAction
      assertThat(tableViewAction.templateText).isEqualTo("Show List View")
      tableViewAction.actionPerformed(event)
      assertThat(entriesView.contentMode).isEqualTo(BackgroundTaskEntriesView.Mode.TABLE)
    }
  }

  @Test
  fun worksRemovedInGraphView_returnToEmptyTable() = runBlocking {
    val workInfo = BackgroundTaskInspectorTestUtils.FAKE_WORK_INFO
    client.sendWorkAddedEvent(workInfo)

    withContext(uiDispatcher) {
      selectionModel.selectedEntry = client.getEntry(workInfo.id)
      val toolbar = TreeWalker(tab.component)
        .descendantStream()
        .filter { it is ActionToolbar }
        .toList()[1] as ActionToolbarImpl
      val graphViewAction = toolbar.actions[1] as AnAction
      val event: AnActionEvent = Mockito.mock(AnActionEvent::class.java)
      graphViewAction.actionPerformed(event)
    }
    client.sendWorkRemovedEvent(workInfo.id)

    withContext(uiDispatcher) {
      val tree = TreeWalker(entriesView).descendantStream().filter { it is JTree }.findFirst().get() as JTree
      val root = tree.model.root
      val works = (root as DefaultMutableTreeNode).children().asSequence().first { (it as DefaultMutableTreeNode).userObject == "Workers" }
      assertThat(works.childCount).isEqualTo(0)
    }
  }

  private inline fun <reified T> JComponent.getFirstChildIsInstance(): T =
    TreeWalker(this).descendantStream().filter { it is T }.findFirst().get() as T
}
