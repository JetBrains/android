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
import androidx.work.inspection.WorkManagerInspectorProtocol.WorkUpdatedEvent
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.ui.HideablePanel
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.api.service.TestAppInspectionIdeServices
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorkManagerInspectorClient
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorksTableModel
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
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
import org.mockito.Mockito.mock
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

    override val rawEventFlow = emptyFlow<ByteArray>()
  }

  private class TestIdeServices : TestAppInspectionIdeServices() {
    var lastVisitedCodeLocation: AppInspectionIdeServices.CodeLocation? = null

    override suspend fun navigateTo(codeLocation: AppInspectionIdeServices.CodeLocation) {
      lastVisitedCodeLocation = codeLocation
    }
  }

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

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
    assertThat(client.getWorkInfoCount()).isEqualTo(3)
    launch(uiDispatcher) {
      val inspectorTab = WorkManagerInspectorTab(client, ideServices, scope)
      val filterActionList = inspectorTab.getFilterActionList()
      assertThat(filterActionList.size).isEqualTo(3)
      assertThat(filterActionList[0].templateText).isEqualTo("All tags")
      val tag1Filter = filterActionList[1]
      assertThat(tag1Filter.templateText).isEqualTo("tag1")
      val tag2Filter = filterActionList[2]
      assertThat(tag2Filter.templateText).isEqualTo("tag2")
      val event: AnActionEvent = mock(AnActionEvent::class.java)
      tag1Filter.setSelected(event, true)
      assertThat(client.getWorkInfoCount()).isEqualTo(2)
      tag2Filter.setSelected(event, true)
      assertThat(client.getWorkInfoCount()).isEqualTo(1)
      val allTagsFilter = filterActionList[0]
      allTagsFilter.setSelected(event, true)
      assertThat(client.getWorkInfoCount()).isEqualTo(3)
    }.join()
  }

  @Test
  fun addAndSelectWorkInfo_displayDetailedPanel() = runBlocking {
    sendWorkAddedEvent(fakeWorkInfo)

    launch(uiDispatcher) {
      val inspectorTab = WorkManagerInspectorTab(client, ideServices, scope)

      val table = inspectorTab.getTable()
      table.selectionModel.setSelectionInterval(0, 0)

      val detailedPanel = inspectorTab.getDetailedPanel()!!

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
      assertThat((chainComponent.getComponent(0) as HyperlinkLabel).text).isEqualTo("ID1 (Current)")

      val resultsPanel = detailedPanel.getCategoryPanel("Results") as JPanel
      val timeStartedComponent = resultsPanel.getValueComponent("Time Started") as JLabel
      assertThat(timeStartedComponent.text).isEqualTo(fakeWorkInfo.scheduleRequestedAt.toFormattedTimeString())
      val retryCountComponent = resultsPanel.getValueComponent("Retries") as JLabel
      assertThat(retryCountComponent.text).isEqualTo("1")
      val dataComponent = resultsPanel.getValueComponent("Output Data") as HideablePanel
      val keyLabel = TreeWalker(dataComponent).descendantStream().filter { (it as? JLabel)?.text == "k = " }.findFirst().get()
      val valueLabel = (keyLabel.parent as JPanel).getComponent(1) as JLabel
      assertThat(valueLabel.text).isEqualTo("\"v\"")
    }.join()
  }

  @Test
  fun updateSelectedWorkInfo_detailedPanelUpdateAccordingly() = runBlocking {
    sendWorkAddedEvent(fakeWorkInfo)
    lateinit var inspectorTab: WorkManagerInspectorTab
    launch(uiDispatcher) {
      inspectorTab = WorkManagerInspectorTab(client, ideServices, scope)

      val table = inspectorTab.getTable()
      table.selectionModel.setSelectionInterval(0, 0)
      val detailedPanel = inspectorTab.getDetailedPanel()!!

      val executionPanel = detailedPanel.getCategoryPanel("Execution") as JPanel
      val stateComponent = executionPanel.getValueComponent("State") as JLabel
      assertThat(stateComponent.text).isEqualTo("Enqueued")
    }.join()

    sendWorkStateUpdatedEvent(fakeWorkInfo.id, WorkInfo.State.FAILED)
    launch(uiDispatcher) {
      val detailedPanel = inspectorTab.getDetailedPanel()!!
      val executionPanel = detailedPanel.getCategoryPanel("Execution") as JPanel
      val stateComponent = executionPanel.getValueComponent("State") as JLabel
      assertThat(stateComponent.text).isEqualTo("Failed")
    }.join()
  }

  @Test
  fun closeDetailedPanel() = runBlocking {
    sendWorkAddedEvent(fakeWorkInfo)
    launch(uiDispatcher) {
      val inspectorTab = WorkManagerInspectorTab(client, ideServices, scope)

      val table = inspectorTab.getTable()
      table.selectionModel.setSelectionInterval(0, 0)
      val detailedPanel = inspectorTab.getDetailedPanel()!!
      val detailedPanelTitleLabel =
        TreeWalker(detailedPanel).descendantStream().filter { (it as? JLabel)?.text == "Work Details" }.findFirst().get()
      val titlePanel = detailedPanelTitleLabel.parent as JPanel
      val closeButton = titlePanel.getComponent(1) as InplaceButton
      assertThat(closeButton.toolTipText).isEqualTo("Close")
      closeButton.doClick()
      assertThat(inspectorTab.getDetailedPanel()).isNull()
    }.join()
  }

  @Test
  fun cancelSelectedWork() = runBlocking {
    sendWorkAddedEvent(fakeWorkInfo)
    launch(uiDispatcher) {
      val inspectorTab = WorkManagerInspectorTab(client, ideServices, scope)

      val table = inspectorTab.getTable()
      table.selectionModel.setSelectionInterval(0, 0)
      val toolbar =
        TreeWalker(inspectorTab.component).descendantStream().filter { it is ActionToolbar }.findFirst().get() as ActionToolbarImpl
      val cancelAction = toolbar.actions[0] as AnAction
      assertThat(cancelAction.templateText).isEqualTo("Cancel Selected Work")
      val event: AnActionEvent = mock(AnActionEvent::class.java)
      cancelAction.actionPerformed(event)
    }.join()
    assertThat(Command.parseFrom(messenger.rawDataSent).cancelWork.id).isEqualTo(fakeWorkInfo.id)
  }

  private fun WorkManagerInspectorTab.getTable() =
    TreeWalker(component).descendantStream().filter { it is JTable }.findFirst().get() as JTable

  private fun WorkManagerInspectorTab.getDetailedPanel(): JComponent? {
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

  private fun sendWorkStateUpdatedEvent(id: String, state: WorkInfo.State) {
    val workUpdatedEvent = WorkUpdatedEvent.newBuilder().setId(id).setState(state).build()
    val event = Event.newBuilder().setWorkUpdated(workUpdatedEvent).build()
    client.handleEvent(event.toByteArray())
  }
}
