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
package com.android.tools.idea.appinspection.inspectors.workmanager.view

import androidx.work.inspection.WorkManagerInspectorProtocol.CallStack
import androidx.work.inspection.WorkManagerInspectorProtocol.Command
import androidx.work.inspection.WorkManagerInspectorProtocol.Constraints
import androidx.work.inspection.WorkManagerInspectorProtocol.Data
import androidx.work.inspection.WorkManagerInspectorProtocol.DataEntry
import androidx.work.inspection.WorkManagerInspectorProtocol.Event
import androidx.work.inspection.WorkManagerInspectorProtocol.WorkAddedEvent
import androidx.work.inspection.WorkManagerInspectorProtocol.WorkInfo
import androidx.work.inspection.WorkManagerInspectorProtocol.WorkRemovedEvent
import androidx.work.inspection.WorkManagerInspectorProtocol.WorkUpdatedEvent
import com.android.flags.junit.SetFlagRule
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.ui.HideablePanel
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServicesAdapter
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorkManagerInspectorClient
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorkSelectionModel
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorksTableModel
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.InplaceButton
import com.intellij.ui.JBSplitter
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.mock
import java.awt.event.ActionEvent
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

class WorkManagerInspectorTabTest {

  private class FakeAppInspectorMessenger(
    override val scope: CoroutineScope,
  ) : AppInspectorMessenger {
    lateinit var rawDataSent: ByteArray
    override suspend fun sendRawCommand(rawData: ByteArray): ByteArray {
      rawDataSent = rawData
      return ByteArray(0)
    }

    override val eventFlow = emptyFlow<ByteArray>()
  }

  private class TestIdeServices : AppInspectionIdeServicesAdapter() {
    var lastVisitedCodeLocation: AppInspectionIdeServices.CodeLocation? = null

    override suspend fun navigateTo(codeLocation: AppInspectionIdeServices.CodeLocation) {
      lastVisitedCodeLocation = codeLocation
    }
  }

  private val setFlagRule = SetFlagRule(StudioFlags.ENABLE_WORK_MANAGER_GRAPH_VIEW, true)
  private val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(setFlagRule)!!

  private lateinit var executor: ExecutorService
  private lateinit var scope: CoroutineScope
  private lateinit var messenger: FakeAppInspectorMessenger
  private lateinit var client: WorkManagerInspectorClient
  private lateinit var model: WorksTableModel
  private lateinit var uiDispatcher: ExecutorCoroutineDispatcher
  private lateinit var ideServices: TestIdeServices

  private val fakeWorkInfo: WorkInfo = WorkInfo.newBuilder().apply {
    id = "ID1"
    workerClassName = "package1.package2.ClassName1"
    addAllTags(listOf("tag1", "tag2"))
    state = WorkInfo.State.ENQUEUED
    scheduleRequestedAt = 3L
    runAttemptCount = 1

    val frame1 = CallStack.Frame.newBuilder()
      .setClassName("pkg1.Class1")
      .setFileName("File1")
      .setMethodName("method1")
      .setLineNumber(12)
      .build()
    val frame2 = CallStack.Frame.newBuilder()
      .setClassName("pkg2.Class2")
      .setFileName("File2")
      .setMethodName("method2")
      .setLineNumber(33)
      .build()
    callStack = CallStack.newBuilder().addAllFrames(listOf(frame1, frame2)).build()

    data = Data.newBuilder()
      .addEntries(DataEntry.newBuilder().setKey("k").setValue("v").build())
      .build()

    constraints = Constraints.newBuilder().setRequiredNetworkType(Constraints.NetworkType.CONNECTED).build()
    isPeriodic = false
    addPrerequisites("prerequisiteId")
    addDependents("dependentsId")
  }.build()

  @Before
  fun setUp() {
    executor = Executors.newSingleThreadExecutor()
    scope = CoroutineScope(executor.asCoroutineDispatcher() + SupervisorJob())
    messenger = FakeAppInspectorMessenger(scope)
    client = WorkManagerInspectorClient(messenger, scope)
    model = WorksTableModel(client)
    uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()
    ideServices = TestIdeServices()
  }

  @After
  fun tearDown() {
    scope.cancel()
    executor.shutdownNow()
  }

  @Test
  fun tableContentsUpdateWithClient() = runBlocking {
    sendWorkAddedEvent(fakeWorkInfo)

    launch(uiDispatcher) {
      val tabView = WorkManagerInspectorTab(client, ideServices, scope)
      val table = tabView.getTable()

      // Check table values.
      assertThat(table.getValueAt(0, WorksTableModel.Column.ORDER.ordinal)).isEqualTo(1)
      assertThat(table.getValueAt(0, WorksTableModel.Column.CLASS_NAME.ordinal)).isEqualTo("ClassName1")
      assertThat(table.getValueAt(0, WorksTableModel.Column.STATE.ordinal)).isEqualTo(1)
      assertThat(table.getValueAt(0, WorksTableModel.Column.TIME_STARTED.ordinal)).isEqualTo(3L)
      assertThat(table.getValueAt(0, WorksTableModel.Column.RUN_ATTEMPT_COUNT.ordinal)).isEqualTo(1)
      assertThat(table.getValueAt(0, WorksTableModel.Column.DATA.ordinal))
        .isEqualTo(WorkInfo.State.ENQUEUED to fakeWorkInfo.data)

      // Check table components with custom renderers.
      val className = "className"
      val classNameComponent = table
        .getCellRenderer(0, WorksTableModel.Column.CLASS_NAME.ordinal)
        .getTableCellRendererComponent(table,
                                       className,
                                       false,
                                       false,
                                       0,
                                       WorksTableModel.Column.CLASS_NAME.ordinal) as DefaultTableCellRenderer
      assertThat(classNameComponent.text).isEqualTo(className)

      val state = WorkInfo.State.CANCELLED
      val stateComponent = table
        .getCellRenderer(0, WorksTableModel.Column.STATE.ordinal)
        .getTableCellRendererComponent(table,
                                       state.ordinal,
                                       false,
                                       false,
                                       0,
                                       WorksTableModel.Column.STATE.ordinal) as DefaultTableCellRenderer
      assertThat(stateComponent.icon).isEqualTo(state.icon())
      assertThat(stateComponent.text).isEqualTo(state.capitalizedName())

      val timeStarted = 123456789L
      val timeComponent = table
        .getCellRenderer(0, WorksTableModel.Column.TIME_STARTED.ordinal)
        .getTableCellRendererComponent(table,
                                       timeStarted,
                                       false,
                                       false,
                                       0,
                                       WorksTableModel.Column.TIME_STARTED.ordinal) as DefaultTableCellRenderer
      assertThat(timeComponent.text).isEqualTo(timeStarted.toFormattedTimeString())

      val dataComponent = table
        .getCellRenderer(0, WorksTableModel.Column.DATA.ordinal)
        .getTableCellRendererComponent(table,
                                       WorkInfo.State.SUCCEEDED to fakeWorkInfo.data,
                                       false,
                                       false,
                                       0,
                                       WorksTableModel.Column.DATA.ordinal) as DefaultTableCellRenderer
      assertThat(dataComponent.text).isEqualTo("{ k: v }")

      val awaitingDataComponent = table
        .getCellRenderer(0, WorksTableModel.Column.DATA.ordinal)
        .getTableCellRendererComponent(table,
                                       WorkInfo.State.ENQUEUED to Data.getDefaultInstance(),
                                       false,
                                       false,
                                       0,
                                       WorksTableModel.Column.DATA.ordinal) as DefaultTableCellRenderer
      assertThat(awaitingDataComponent.text).isEqualTo("Awaiting data...")


      val nullDataComponent = table
        .getCellRenderer(0, WorksTableModel.Column.DATA.ordinal)
        .getTableCellRendererComponent(table,
                                       WorkInfo.State.SUCCEEDED to Data.getDefaultInstance(),
                                       false,
                                       false,
                                       0,
                                       WorksTableModel.Column.DATA.ordinal) as DefaultTableCellRenderer
      assertThat(nullDataComponent.text).isEqualTo("null")
    }.join()
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
    sendWorkAddedEvent(workInfo1)
    sendWorkAddedEvent(workInfo2)
    sendWorkAddedEvent(workInfo3)
    assertThat(client.lockedWorks { it.size }).isEqualTo(3)
    launch(uiDispatcher) {
      val inspectorTab = WorkManagerInspectorTab(client, ideServices, scope)
      val contentView = (inspectorTab.component as JBSplitter).firstComponent as WorksContentView
      val filterActionList = contentView.getFilterActionList()
      assertThat(filterActionList.size).isEqualTo(3)
      assertThat(filterActionList[0].templateText).isEqualTo("All tags")
      val tag1Filter = filterActionList[1]
      assertThat(tag1Filter.templateText).isEqualTo("tag1")
      val tag2Filter = filterActionList[2]
      assertThat(tag2Filter.templateText).isEqualTo("tag2")
      val event: AnActionEvent = mock(AnActionEvent::class.java)
      tag1Filter.setSelected(event, true)
      assertThat(client.lockedWorks { it.size }).isEqualTo(2)
      tag2Filter.setSelected(event, true)
      assertThat(client.lockedWorks { it.size }).isEqualTo(1)
      val allTagsFilter = filterActionList[0]
      allTagsFilter.setSelected(event, true)
      assertThat(client.lockedWorks { it.size }).isEqualTo(3)
    }.join()
  }

  @Test
  fun addAndSelectWorkInfo_displayDetailsView() = runBlocking {
    sendWorkAddedEvent(fakeWorkInfo)

    launch(uiDispatcher) {
      val inspectorTab = WorkManagerInspectorTab(client, ideServices, scope)
      inspectorTab.isDetailsViewVisible = true

      val table = inspectorTab.getTable()
      table.selectionModel.setSelectionInterval(0, 0)

      val detailedPanel = inspectorTab.getDetailsView()!!

      val descriptionPanel = detailedPanel.getCategoryPanel("Description") as JPanel
      val classComponent = descriptionPanel.getValueComponent("Class") as HyperlinkLabel
      assertThat(classComponent.text).isEqualTo(fakeWorkInfo.workerClassName)
      classComponent.doClick()
      scope.launch {
        assertThat(ideServices.lastVisitedCodeLocation!!.fqcn).isEqualTo(fakeWorkInfo.workerClassName)
      }.join()
      val tagsComponent = descriptionPanel.getValueComponent("Tags") as JPanel
      assertThat(tagsComponent.componentCount).isEqualTo(2)
      assertThat((tagsComponent.getComponent(0) as JLabel).text).isEqualTo("\"tag1\"")
      assertThat((tagsComponent.getComponent(1) as JLabel).text).isEqualTo("\"tag2\"")
      val idComponent = descriptionPanel.getValueComponent("UUID") as JLabel
      assertThat(idComponent.text).isEqualTo(fakeWorkInfo.id)

      val executionPanel = detailedPanel.getCategoryPanel("Execution") as JPanel
      val enqueuedAtComponent = executionPanel.getValueComponent("Enqueued by") as HyperlinkLabel
      assertThat(enqueuedAtComponent.text).isEqualTo("File1 (12)")
      enqueuedAtComponent.doClick()
      scope.launch {
        assertThat(ideServices.lastVisitedCodeLocation!!.fqcn).isEqualTo("pkg1.Class1")
        assertThat(ideServices.lastVisitedCodeLocation!!.fileName).isEqualTo("File1")
        assertThat(ideServices.lastVisitedCodeLocation!!.lineNumber).isEqualTo(12)
      }.join()
      val constraintsAtComponent = executionPanel.getValueComponent("Constraints") as JPanel
      assertThat(constraintsAtComponent.componentCount).isEqualTo(1)
      assertThat((constraintsAtComponent.getComponent(0) as JLabel).text).isEqualTo("Network must be connected")
      val frequencyComponent = executionPanel.getValueComponent("Frequency") as JLabel
      assertThat(frequencyComponent.text).isEqualTo("One Time")
      val stateComponent = executionPanel.getValueComponent("State") as JLabel
      assertThat(stateComponent.text).isEqualTo("Enqueued")

      val workContinuationPanel = detailedPanel.getCategoryPanel("WorkContinuation") as JPanel
      val previousComponent = workContinuationPanel.getValueComponent("Previous") as JPanel
      assertThat(previousComponent.componentCount).isEqualTo(1)
      assertThat((previousComponent.getComponent(0) as JLabel).text).isEqualTo("prerequisiteId")
      val nextComponent = workContinuationPanel.getValueComponent("Next") as JPanel
      assertThat(nextComponent.componentCount).isEqualTo(1)
      assertThat((nextComponent.getComponent(0) as JLabel).text).isEqualTo("dependentsId")
      val chainComponent = workContinuationPanel.getValueComponent("Unique work chain") as JPanel
      assertThat(chainComponent.componentCount).isEqualTo(1)
      assertThat((chainComponent.getComponent(0) as HyperlinkLabel).text).isEqualTo("ID1  (Current)")

      val resultsPanel = detailedPanel.getCategoryPanel("Results") as JPanel
      val timeStartedComponent = resultsPanel.getValueComponent("Time started") as JLabel
      assertThat(timeStartedComponent.text).isEqualTo(fakeWorkInfo.scheduleRequestedAt.toFormattedTimeString())
      val retryCountComponent = resultsPanel.getValueComponent("Retries") as JLabel
      assertThat(retryCountComponent.text).isEqualTo("1")
      val dataComponent = resultsPanel.getValueComponent("Output data") as HideablePanel
      val keyLabel = TreeWalker(dataComponent).descendantStream().filter { (it as? JLabel)?.text == "k = " }.findFirst().get()
      val valueLabel = (keyLabel.parent as JPanel).getComponent(1) as JLabel
      assertThat(valueLabel.text).isEqualTo("\"v\"")
    }.join()
  }

  @Test
  fun updateSelectedWorkInfo_detailsViewUpdateAccordingly() = runBlocking {
    sendWorkAddedEvent(fakeWorkInfo)
    lateinit var inspectorTab: WorkManagerInspectorTab
    launch(uiDispatcher) {
      inspectorTab = WorkManagerInspectorTab(client, ideServices, scope)
      inspectorTab.isDetailsViewVisible = true
      val table = inspectorTab.getTable()
      table.selectionModel.setSelectionInterval(0, 0)
      val detailedPanel = inspectorTab.getDetailsView()!!

      val executionPanel = detailedPanel.getCategoryPanel("Execution") as JPanel
      val stateComponent = executionPanel.getValueComponent("State") as JLabel
      assertThat(stateComponent.text).isEqualTo("Enqueued")
    }.join()

    sendWorkStateUpdatedEvent(fakeWorkInfo.id, WorkInfo.State.FAILED)
    launch(uiDispatcher) {
      val detailedPanel = inspectorTab.getDetailsView()!!
      val executionPanel = detailedPanel.getCategoryPanel("Execution") as JPanel
      val stateComponent = executionPanel.getValueComponent("State") as JLabel
      assertThat(stateComponent.text).isEqualTo("Failed")
    }.join()
  }

  @Test
  fun updateUnSelectedWorkInfo_detailsViewUpdateAccordingly() = runBlocking {
    sendWorkAddedEvent(fakeWorkInfo)
    val dependentWork = fakeWorkInfo.toBuilder().setId(fakeWorkInfo.getDependents(0)).build()
    sendWorkAddedEvent(dependentWork)
    lateinit var inspectorTab: WorkManagerInspectorTab
    lateinit var oldDependentWorkLabel: HyperlinkLabel
    launch(uiDispatcher) {
      inspectorTab = WorkManagerInspectorTab(client, ideServices, scope)
      inspectorTab.isDetailsViewVisible = true
      val table = inspectorTab.getTable()
      table.selectionModel.setSelectionInterval(0, 0)
      val detailsPanel = inspectorTab.getDetailsView()!!
      val workContinuationPanel = detailsPanel.getCategoryPanel("WorkContinuation") as JPanel
      val chainComponent = workContinuationPanel.getValueComponent("Unique work chain") as JPanel
      assertThat(chainComponent.componentCount).isEqualTo(2)
      oldDependentWorkLabel = chainComponent.getComponent(1) as HyperlinkLabel
      assertThat(oldDependentWorkLabel.text).isEqualTo("dependentsId")
    }.join()

    sendWorkStateUpdatedEvent(dependentWork.id, WorkInfo.State.FAILED)
    launch(uiDispatcher) {
      val detailsPanel = inspectorTab.getDetailsView()!!
      assertThat(detailsPanel).isNotEqualTo(oldDependentWorkLabel)
      val workContinuationPanel = detailsPanel.getCategoryPanel("WorkContinuation") as JPanel
      val chainComponent = workContinuationPanel.getValueComponent("Unique work chain") as JPanel
      assertThat(chainComponent.componentCount).isEqualTo(2)
      val newDependentWorkLabel = chainComponent.getComponent(1) as HyperlinkLabel
      assertThat(newDependentWorkLabel.text).isEqualTo("dependentsId")
      // Ideally, we want to check if the two labels are with different icons.
      // Unfortunately, [HyperlinkLabel] does not have icon access so we compare labels directly.
      assertThat(oldDependentWorkLabel).isNotEqualTo(newDependentWorkLabel)
    }.join()
  }

  @Test
  fun closeDetailsView() = runBlocking {
    sendWorkAddedEvent(fakeWorkInfo)
    launch(uiDispatcher) {
      val inspectorTab = WorkManagerInspectorTab(client, ideServices, scope)
      inspectorTab.isDetailsViewVisible = true

      val table = inspectorTab.getTable()
      table.selectionModel.setSelectionInterval(0, 0)
      val detailedPanel = inspectorTab.getDetailsView()!!
      val detailedPanelTitleLabel =
        TreeWalker(detailedPanel).descendantStream().filter { (it as? JLabel)?.text == "Work Details" }.findFirst().get()
      val titlePanel = detailedPanelTitleLabel.parent as JPanel
      val closeButton = titlePanel.getComponent(1) as InplaceButton
      assertThat(closeButton.toolTipText).isEqualTo("Close")
      closeButton.doClick()
      assertThat(inspectorTab.getDetailsView()).isNull()
    }.join()
  }

  @Test
  fun removeSelectedWork_selectBackupWork() = runBlocking {
    val backupWorkInfo = fakeWorkInfo.toBuilder().setId("backup").build()
    sendWorkAddedEvent(fakeWorkInfo)
    sendWorkAddedEvent(backupWorkInfo)
    lateinit var inspectorTab: WorkManagerInspectorTab
    launch(uiDispatcher) {
      inspectorTab = WorkManagerInspectorTab(client, ideServices, scope)
      inspectorTab.isDetailsViewVisible = true
      val table = inspectorTab.getTable()
      table.selectionModel.setSelectionInterval(0, 0)
      assertThat(inspectorTab.getDetailsView()).isNotNull()
    }.join()
    sendWorkRemovedEvent(fakeWorkInfo.id)
    launch(uiDispatcher) {
      assertThat(inspectorTab.workSelectionModel.selectedWork).isEqualTo(backupWorkInfo)
    }.join()
  }

  @Test
  fun removeAllWorks_detailsViewClosed() = runBlocking {
    sendWorkAddedEvent(fakeWorkInfo)
    lateinit var inspectorTab: WorkManagerInspectorTab
    launch(uiDispatcher) {
      inspectorTab = WorkManagerInspectorTab(client, ideServices, scope)
      inspectorTab.isDetailsViewVisible = true
      val table = inspectorTab.getTable()
      table.selectionModel.setSelectionInterval(0, 0)
      assertThat(inspectorTab.getDetailsView()).isNotNull()
    }.join()
    // After removing fakeWorkInfo, the table becomes empty and the details view should be closed.
    sendWorkRemovedEvent(fakeWorkInfo.id)
    // Adding backupWorkInfo should not select a new work.
    val backupWorkInfo = fakeWorkInfo.toBuilder().setId("backup").build()
    sendWorkAddedEvent(backupWorkInfo)
    launch(uiDispatcher) {
      assertThat(inspectorTab.getDetailsView()).isNull()
    }.join()
  }

  @Test
  fun removeSelectedWork_firstRowSelected() = runBlocking {
    val secondWork = fakeWorkInfo.toBuilder().setId("ID2").build()
    sendWorkAddedEvent(fakeWorkInfo)
    sendWorkAddedEvent(secondWork)
    lateinit var inspectorTab: WorkManagerInspectorTab

    launch(uiDispatcher) {
      inspectorTab = WorkManagerInspectorTab(client, ideServices, scope)
      inspectorTab.isDetailsViewVisible = true
      val table = inspectorTab.getTable()
      table.selectionModel.setSelectionInterval(1, 1)
      assertThat(inspectorTab.getDetailsView()).isNotNull()
    }.join()
    sendWorkRemovedEvent(secondWork.id)

    launch(uiDispatcher) {
      val table = inspectorTab.getTable()
      // Details View not empty.
      assertThat(inspectorTab.getDetailsView()).isNotNull()
      // First row selected.
      assertThat(table.selectedRow).isEqualTo(0)
    }.join()
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

    launch(uiDispatcher) {
      val inspectorTab = WorkManagerInspectorTab(client, ideServices, scope)
      val toolbar =
        TreeWalker(inspectorTab.component).descendantStream().filter { it is ActionToolbar }.findFirst().get() as ActionToolbarImpl

      val cancelAction = toolbar.actions[0] as AnAction
      val event = TestActionEvent.createTestEvent()
      assertThat(cancelAction.templateText).isEqualTo("Cancel Selected Work")
      cancelAction.update(event)
      assertThat(event.presentation.isEnabled).isFalse()

      val table = inspectorTab.getTable()
      works.forEachIndexed { i, work ->
        sendWorkAddedEvent(work)
        table.selectionModel.setSelectionInterval(i, i)
        cancelAction.update(event)

        val canCancel = cancellableStates.contains(work.state)
        assertThat(event.presentation.isEnabled).isEqualTo(canCancel)
        if (canCancel) {
          cancelAction.actionPerformed(event)
          scope.launch {
            assertThat(Command.parseFrom(messenger.rawDataSent).cancelWork.id).isEqualTo(work.id)
          }.join()
        }
      }
    }.join()
  }

  @Test
  fun openDependencyGraphView() = runBlocking {
    sendWorkAddedEvent(fakeWorkInfo)
    launch(uiDispatcher) {
      val inspectorTab = WorkManagerInspectorTab(client, ideServices, scope)

      val contentView = (inspectorTab.component as JBSplitter).firstComponent as WorksContentView
      val table = contentView.getFirstChildIsInstance<JTable>()
      table.selectionModel.setSelectionInterval(0, 0)
      val toolbar = TreeWalker(inspectorTab.component)
        .descendantStream()
        .filter { it is ActionToolbar }
        .toList()[1] as ActionToolbarImpl
      val graphViewAction = toolbar.actions[1] as AnAction
      assertThat(graphViewAction.templateText).isEqualTo("Show Graph View")
      val event: AnActionEvent = mock(AnActionEvent::class.java)
      graphViewAction.actionPerformed(event)
      val graphView = contentView.getFirstChildIsInstance<WorkDependencyGraphView>()
      assertThat(graphView.getFirstChildIsInstance<JLabel>().text).isEqualTo("ClassName1")
    }.join()
  }

  @Test
  fun navigateWorksInDependencyView() = runBlocking {
    val parentWorkInfo: WorkInfo = WorkInfo.newBuilder().apply {
      id = "parent"
      workerClassName = "package1.package2.ClassName2"
      state = WorkInfo.State.ENQUEUED
      scheduleRequestedAt = 1L
      runAttemptCount = 1
      constraints = Constraints.getDefaultInstance()
      isPeriodic = false
      addAllDependents(listOf("left_child", "right_child"))
    }.build()

    val leftChildWorkInfo: WorkInfo = WorkInfo.newBuilder().apply {
      id = "left_child"
      workerClassName = "package1.package2.ClassName3"
      state = WorkInfo.State.ENQUEUED
      scheduleRequestedAt = 2L
      runAttemptCount = 1
      constraints = Constraints.getDefaultInstance()
      isPeriodic = false
      addPrerequisites("parent")
    }.build()

    val rightChildWorkInfo: WorkInfo = WorkInfo.newBuilder().apply {
      id = "right_child"
      workerClassName = "package1.package2.ClassName4"
      state = WorkInfo.State.ENQUEUED
      scheduleRequestedAt = 2L
      runAttemptCount = 1
      constraints = Constraints.getDefaultInstance()
      isPeriodic = false
      addPrerequisites("parent")
    }.build()

    sendWorkAddedEvent(parentWorkInfo)
    sendWorkAddedEvent(leftChildWorkInfo)
    sendWorkAddedEvent(rightChildWorkInfo)
    launch(uiDispatcher) {
      val inspectorTab = WorkManagerInspectorTab(client, ideServices, scope)

      val contentView = (inspectorTab.component as JBSplitter).firstComponent as WorksContentView
      val toolbar = TreeWalker(inspectorTab.component)
        .descendantStream()
        .filter { it is ActionToolbar }
        .toList()[1] as ActionToolbarImpl
      val graphViewAction = toolbar.actions[1] as AnAction
      assertThat(graphViewAction.templateText).isEqualTo("Show Graph View")

      // Select parent work.
      val selectionModel = inspectorTab.workSelectionModel
      selectionModel.setSelectedWork(parentWorkInfo, WorkSelectionModel.Context.GRAPH)

      // Switch to graph mode.
      val event1: AnActionEvent = mock(AnActionEvent::class.java)
      graphViewAction.actionPerformed(event1)
      val graphView = contentView.getFirstChildIsInstance<WorkDependencyGraphView>()

      // Move down to left_child work.
      val actionEvent: ActionEvent = mock(ActionEvent::class.java)
      graphView.actionMap["Down"].actionPerformed(actionEvent)
      assertThat(inspectorTab.workSelectionModel.selectedWork).isEqualTo(leftChildWorkInfo)
      // Move right to right_child work.
      graphView.actionMap["Right"].actionPerformed(actionEvent)
      assertThat(inspectorTab.workSelectionModel.selectedWork).isEqualTo(rightChildWorkInfo)
      // Move left to left_child work.
      graphView.actionMap["Left"].actionPerformed(actionEvent)
      assertThat(inspectorTab.workSelectionModel.selectedWork).isEqualTo(leftChildWorkInfo)
      // Move up to parent work.
      graphView.actionMap["Up"].actionPerformed(actionEvent)
      assertThat(inspectorTab.workSelectionModel.selectedWork).isEqualTo(parentWorkInfo)
    }.join()
  }

  @Test
  fun openDependencyGraphViewFromDetailsView() = runBlocking {
    sendWorkAddedEvent(fakeWorkInfo)
    launch(uiDispatcher) {
      val inspectorTab = WorkManagerInspectorTab(client, ideServices, scope)
      inspectorTab.isDetailsViewVisible = true
      val table = inspectorTab.getTable()
      table.selectionModel.setSelectionInterval(0, 0)
      val detailsPanel = inspectorTab.getDetailsView()!!
      val workContinuationPanel = detailsPanel.getCategoryPanel("WorkContinuation") as JPanel
      val showInGraphLabel = TreeWalker(workContinuationPanel)
        .descendantStream()
        .filter { (it as? HyperlinkLabel)?.text == "Show in graph" }
        .findFirst()
        .get() as HyperlinkLabel
      val contentView = (inspectorTab.component as JBSplitter).firstComponent as WorksContentView
      assertThat(contentView.contentMode).isEqualTo(WorksContentView.Mode.TABLE)
      showInGraphLabel.doClick()
      assertThat(contentView.contentMode).isEqualTo(WorksContentView.Mode.GRAPH)
    }.join()
  }

  @Test
  fun openTableViewAfterGraphView() = runBlocking {
    sendWorkAddedEvent(fakeWorkInfo)
    launch(uiDispatcher) {
      val inspectorTab = WorkManagerInspectorTab(client, ideServices, scope)

      val contentView = (inspectorTab.component as JBSplitter).firstComponent as WorksContentView
      val table = contentView.getFirstChildIsInstance<JTable>()
      table.selectionModel.setSelectionInterval(0, 0)
      val toolbar = TreeWalker(inspectorTab.component)
        .descendantStream()
        .filter { it is ActionToolbar }
        .toList()[1] as ActionToolbarImpl
      val graphViewAction = toolbar.actions[1] as AnAction
      val event: AnActionEvent = mock(AnActionEvent::class.java)
      graphViewAction.actionPerformed(event)

      val tableViewAction = toolbar.actions[0] as AnAction
      assertThat(tableViewAction.templateText).isEqualTo("Show List View")
      tableViewAction.actionPerformed(event)

      val newTable = contentView.getFirstChildIsInstance<JTable>()
      assertThat(newTable).isEqualTo(table)
      assertThat(newTable.selectedRow).isEqualTo(0)
    }.join()
  }

  @Test
  fun openTableViewFromDetailsView() = runBlocking {
    sendWorkAddedEvent(fakeWorkInfo)
    launch(uiDispatcher) {
      val inspectorTab = WorkManagerInspectorTab(client, ideServices, scope)
      inspectorTab.isDetailsViewVisible = true
      val table = inspectorTab.getTable()
      table.selectionModel.setSelectionInterval(0, 0)
      val toolbar = TreeWalker(inspectorTab.component)
        .descendantStream()
        .filter { it is ActionToolbar }
        .toList()[1] as ActionToolbarImpl
      val graphViewAction = toolbar.actions[1] as AnAction
      val event: AnActionEvent = mock(AnActionEvent::class.java)
      graphViewAction.actionPerformed(event)
      val contentView = (inspectorTab.component as JBSplitter).firstComponent as WorksContentView
      assertThat(contentView.contentMode).isEqualTo(WorksContentView.Mode.GRAPH)

      val detailsPanel = inspectorTab.getDetailsView()!!
      val workContinuationPanel = detailsPanel.getCategoryPanel("WorkContinuation") as JPanel
      val showInGraphLabel = TreeWalker(workContinuationPanel)
        .descendantStream()
        .filter { (it as? HyperlinkLabel)?.text == "Show in table" }
        .findFirst()
        .get() as HyperlinkLabel
      showInGraphLabel.doClick()
      assertThat(contentView.contentMode).isEqualTo(WorksContentView.Mode.TABLE)
    }.join()
  }

  @Test
  fun worksRemovedInGraphView_returnToEmptyTable() = runBlocking {
    sendWorkAddedEvent(fakeWorkInfo)
    lateinit var inspectorTab: WorkManagerInspectorTab

    launch(uiDispatcher) {
      inspectorTab = WorkManagerInspectorTab(client, ideServices, scope)
      inspectorTab.isDetailsViewVisible = true
      val contentView = (inspectorTab.component as JBSplitter).firstComponent as WorksContentView
      val table = contentView.getFirstChildIsInstance<JTable>()
      table.selectionModel.setSelectionInterval(0, 0)
      val toolbar = TreeWalker(inspectorTab.component)
        .descendantStream()
        .filter { it is ActionToolbar }
        .toList()[1] as ActionToolbarImpl
      val graphViewAction = toolbar.actions[1] as AnAction
      val event: AnActionEvent = mock(AnActionEvent::class.java)
      graphViewAction.actionPerformed(event)
    }.join()
    sendWorkRemovedEvent(fakeWorkInfo.id)

    launch(uiDispatcher) {
      val table = inspectorTab.getTable()
      assertThat(inspectorTab.getDetailsView()).isNull()
      assertThat(table.rowCount).isEqualTo(0)
    }.join()
  }

  private inline fun <reified T> JComponent.getFirstChildIsInstance(): T =
    TreeWalker(this).descendantStream().filter { it is T }.findFirst().get() as T

  private fun WorkManagerInspectorTab.getTable() = component.getFirstChildIsInstance<JTable>()

  private fun WorkManagerInspectorTab.getDetailsView(): JComponent? {
    var detailedPanel: JComponent? = null
    ApplicationManager.getApplication().invokeAndWait {
      val splitter = TreeWalker(component).descendantStream().filter { it is JBSplitter }.findFirst().get() as JBSplitter
      detailedPanel = splitter.secondComponent
    }
    return detailedPanel
  }

  private fun JComponent.getValueComponent(key: String) =
    TreeWalker(this).descendantStream().filter { (it as? JLabel)?.text == key }.findFirst().get().parent.parent.getComponent(1)

  private fun JComponent.getCategoryPanel(key: String) =
    TreeWalker(this).descendantStream().filter { (it as? JLabel)?.text == key }.findFirst().get().parent.parent

  private fun sendWorkAddedEvent(workInfo: WorkInfo) {
    val workAddedEvent = WorkAddedEvent.newBuilder().setWork(workInfo).build()
    val event = Event.newBuilder().setWorkAdded(workAddedEvent).build()
    client.handleEvent(event.toByteArray())
  }

  private fun sendWorkRemovedEvent(id: String) {
    val workAddedEvent = WorkRemovedEvent.newBuilder().setId(id).build()
    val event = Event.newBuilder().setWorkRemoved(workAddedEvent).build()
    client.handleEvent(event.toByteArray())
  }

  private fun sendWorkStateUpdatedEvent(id: String, state: WorkInfo.State) {
    val workUpdatedEvent = WorkUpdatedEvent.newBuilder().setId(id).setState(state).build()
    val event = Event.newBuilder().setWorkUpdated(workUpdatedEvent).build()
    client.handleEvent(event.toByteArray())
  }
}
