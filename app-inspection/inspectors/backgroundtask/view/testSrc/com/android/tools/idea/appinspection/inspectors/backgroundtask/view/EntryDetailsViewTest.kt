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
import com.android.tools.adtui.ui.HideablePanel
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServicesAdapter
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorClient
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTestUtils
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTestUtils.sendBackgroundTaskEvent
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTestUtils.sendWorkAddedEvent
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTestUtils.sendWorkEvent
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTestUtils.sendWorkRemovedEvent
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.EntrySelectionModel
import com.android.tools.idea.appinspection.inspectors.backgroundtask.view.BackgroundTaskViewTestUtils.findLabels
import com.android.tools.idea.appinspection.inspectors.backgroundtask.view.BackgroundTaskViewTestUtils.getCategoryPanel
import com.android.tools.idea.appinspection.inspectors.backgroundtask.view.BackgroundTaskViewTestUtils.getValueComponent
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.icons.AllIcons
import com.intellij.ui.InplaceButton
import com.intellij.ui.components.ActionLink
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.containers.isEmpty
import icons.StudioIcons
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
import javax.swing.JLabel
import javax.swing.JPanel

class EntryDetailsViewTest {
  private class TestIdeServices : AppInspectionIdeServicesAdapter() {
    var lastVisitedCodeLocation: AppInspectionIdeServices.CodeLocation? = null

    override suspend fun navigateTo(codeLocation: AppInspectionIdeServices.CodeLocation) {
      lastVisitedCodeLocation = codeLocation
    }
  }

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private lateinit var scope: CoroutineScope
  private lateinit var client: BackgroundTaskInspectorClient
  private lateinit var ideServices: TestIdeServices
  private lateinit var tab: BackgroundTaskInspectorTab
  private lateinit var uiDispatcher: ExecutorCoroutineDispatcher
  private lateinit var detailsView: EntryDetailsView
  private lateinit var selectionModel: EntrySelectionModel

  @Before
  fun setUp() = runBlocking {
    scope = CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher() + SupervisorJob())
    uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()
    withContext(uiDispatcher) {
      client = BackgroundTaskInspectorTestUtils.getFakeClient(scope)
      ideServices = TestIdeServices()
      tab = BackgroundTaskInspectorTab(client, ideServices, StubUiComponentsProvider(), scope, uiDispatcher)
      tab.isDetailsViewVisible = true
      detailsView = tab.component.secondComponent as EntryDetailsView
      tab.isDetailsViewVisible = false
      selectionModel = detailsView.selectionModel
    }
  }

  @After
  fun tearDown() {
    scope.cancel()
  }

  @Test
  fun workEntrySelected() = runBlocking {
    val workInfo = BackgroundTaskInspectorTestUtils.FAKE_WORK_INFO
    client.sendWorkAddedEvent(workInfo)

    client.sendBackgroundTaskEvent(0) {
      taskId = 1
      jobScheduledBuilder.apply {
        jobBuilder.apply {
          jobId = 222
          serviceName = "SERVICE"
          extras = BackgroundTaskInspectorTestUtils.createJobInfoExtraWithWorkerId(workInfo.id)
        }
      }
    }
    withContext(uiDispatcher) {
      selectionModel.selectedEntry = client.getEntry(workInfo.id)

      val descriptionPanel = detailsView.getCategoryPanel("Description") as JPanel
      val classComponent = descriptionPanel.getValueComponent("Class") as ActionLink
      assertThat(classComponent.text).isEqualTo(workInfo.workerClassName)
      classComponent.doClick()
      assertThat(ideServices.lastVisitedCodeLocation!!.fqcn).isEqualTo(workInfo.workerClassName)
      val tagsComponent = descriptionPanel.getValueComponent("Tags") as JPanel
      assertThat(tagsComponent.componentCount).isEqualTo(2)
      assertThat((tagsComponent.getComponent(0) as JLabel).text).isEqualTo("\"tag1\"")
      assertThat((tagsComponent.getComponent(1) as JLabel).text).isEqualTo("\"tag2\"")
      val idComponent = descriptionPanel.getValueComponent("UUID") as JLabel
      assertThat(idComponent.text).isEqualTo(workInfo.id)

      val executionPanel = detailsView.getCategoryPanel("Execution") as JPanel
      val enqueuedAtComponent = executionPanel.getValueComponent("Enqueued by") as ActionLink
      assertThat(enqueuedAtComponent.text).isEqualTo("File1 (12)")
      enqueuedAtComponent.doClick()
      assertThat(ideServices.lastVisitedCodeLocation!!.fileName).isEqualTo("File1")
      assertThat(ideServices.lastVisitedCodeLocation!!.lineNumber).isEqualTo(12)
      val constraintsAtComponent = executionPanel.getValueComponent("Constraints") as JPanel
      assertThat(constraintsAtComponent.componentCount).isEqualTo(1)
      assertThat((constraintsAtComponent.getComponent(0) as JLabel).text).isEqualTo("Network must be connected")
      val frequencyComponent = executionPanel.getValueComponent("Frequency") as JLabel
      assertThat(frequencyComponent.text).isEqualTo("OneTime")
      val stateComponent = executionPanel.getValueComponent("State") as JLabel
      assertThat(stateComponent.text).isEqualTo("Enqueued")
      assertThat(stateComponent.icon).isEqualTo(StudioIcons.LayoutEditor.Palette.CHRONOMETER)
      val jobComponent = executionPanel.getValueComponent("Related Job") as ActionLink
      assertThat(jobComponent.text).isEqualTo("SERVICE")

      val workContinuationPanel = detailsView.getCategoryPanel("WorkContinuation") as JPanel
      val previousComponent = workContinuationPanel.getValueComponent("Previous") as JPanel
      assertThat(previousComponent.componentCount).isEqualTo(1)
      assertThat((previousComponent.getComponent(0) as JLabel).text).isEqualTo("prerequisiteId")
      val nextComponent = workContinuationPanel.getValueComponent("Next") as JPanel
      assertThat(nextComponent.componentCount).isEqualTo(1)
      assertThat((nextComponent.getComponent(0) as JLabel).text).isEqualTo("dependentsId")
      val chainComponent = workContinuationPanel.getValueComponent("Unique work chain") as JPanel
      assertThat(chainComponent.componentCount).isEqualTo(1)
      assertThat(((chainComponent.getComponent(0) as JPanel).getComponent(0) as ActionLink).text).isEqualTo("ID1")
      assertThat(((chainComponent.getComponent(0) as JPanel).getComponent(1) as JLabel).text).isEqualTo("(Current)")

      val resultsPanel = detailsView.getCategoryPanel("Results") as JPanel
      val timeStartedComponent = resultsPanel.getValueComponent("Time started") as JLabel
      assertThat(timeStartedComponent.text).isEqualTo(workInfo.scheduleRequestedAt.toFormattedTimeString())
      val retryCountComponent = resultsPanel.getValueComponent("Retries") as JLabel
      assertThat(retryCountComponent.text).isEqualTo("1")
      val dataComponent = resultsPanel.getValueComponent("Output data") as HideablePanel
      val keyLabel = TreeWalker(dataComponent).descendantStream().filter { (it as? JLabel)?.text == "k = " }.findFirst().get()
      val valueLabel = (keyLabel.parent as JPanel).getComponent(1) as JLabel
      assertThat(valueLabel.text).isEqualTo("\"v\"")
    }

    // Remove work from client.
    client.sendWorkRemovedEvent(workInfo.id)
    withContext(uiDispatcher) {
      assertThat(detailsView.findLabels("WorkContinuation").isEmpty()).isTrue()
    }
  }

  @Test
  fun selectedWorkEntryUpdated() = runBlocking {
    val workInfo = BackgroundTaskInspectorTestUtils.FAKE_WORK_INFO
    client.sendWorkAddedEvent(workInfo)
    withContext(uiDispatcher) {
      selectionModel.selectedEntry = client.getEntry(workInfo.id)

      val executionPanel = detailsView.getCategoryPanel("Execution") as JPanel
      val stateComponent = executionPanel.getValueComponent("State") as JLabel
      assertThat(stateComponent.text).isEqualTo("Enqueued")
      assertThat(stateComponent.icon).isEqualTo(StudioIcons.LayoutEditor.Palette.CHRONOMETER)
    }

    client.sendWorkEvent {
      workUpdatedBuilder.apply {
        id = workInfo.id
        state = WorkManagerInspectorProtocol.WorkInfo.State.FAILED
      }
    }
    withContext(uiDispatcher) {
      val executionPanel = detailsView.getCategoryPanel("Execution") as JPanel
      val stateComponent = executionPanel.getValueComponent("State") as JLabel
      assertThat(stateComponent.text).isEqualTo("Failed")
      assertThat(stateComponent.icon).isEqualTo(AllIcons.RunConfigurations.ToolbarError)
    }
  }

  @Test
  fun unselectedWorkEntryUpdated() = runBlocking {
    val workInfo = BackgroundTaskInspectorTestUtils.FAKE_WORK_INFO
    client.sendWorkAddedEvent(workInfo)
    val dependentWork = workInfo.toBuilder().setId(workInfo.getDependents(0)).build()
    client.sendWorkAddedEvent(dependentWork)
    withContext(uiDispatcher) {
      selectionModel.selectedEntry = client.getEntry(workInfo.id)
      val workContinuationPanel = detailsView.getCategoryPanel("WorkContinuation") as JPanel
      val chainComponent = workContinuationPanel.getValueComponent("Unique work chain") as JPanel
      assertThat(chainComponent.componentCount).isEqualTo(2)
      val oldDependentWorkLabel = (chainComponent.getComponent(1) as JPanel).getComponent(0) as ActionLink
      assertThat(oldDependentWorkLabel.text).isEqualTo("dependentsId")
      assertThat(oldDependentWorkLabel.icon).isEqualTo(WorkManagerInspectorProtocol.WorkInfo.State.ENQUEUED.icon())
    }

    client.sendWorkEvent {
      workUpdatedBuilder.apply {
        id = dependentWork.id
        state = WorkManagerInspectorProtocol.WorkInfo.State.FAILED
      }
    }
    withContext(uiDispatcher) {
      val workContinuationPanel = detailsView.getCategoryPanel("WorkContinuation") as JPanel
      val chainComponent = workContinuationPanel.getValueComponent("Unique work chain") as JPanel
      assertThat(chainComponent.componentCount).isEqualTo(2)
      val newDependentWorkLabel = (chainComponent.getComponent(1) as JPanel).getComponent(0) as ActionLink
      assertThat(newDependentWorkLabel.text).isEqualTo("dependentsId")
      assertThat(newDependentWorkLabel.icon).isEqualTo(WorkManagerInspectorProtocol.WorkInfo.State.FAILED.icon())
    }
  }

  @Test
  fun alarmEntrySelected() = runBlocking {
    val event = client.sendBackgroundTaskEvent(0) {
      taskId = 1
      alarmSetBuilder.apply {
        type = BackgroundTaskInspectorProtocol.AlarmSet.Type.RTC
        intervalMs = 5000
        operationBuilder.apply {
          creatorPackage = "creator.package"
          creatorUid = 100
        }
      }
      stacktrace = "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:27)"
    }
    val alarmSet = event.backgroundTaskEvent.alarmSet

    withContext(uiDispatcher) {
      selectionModel.selectedEntry = client.getEntry("1")

      val descriptionPanel = detailsView.getCategoryPanel("Description") as JPanel
      val typeComponent = descriptionPanel.getValueComponent("Type") as JLabel
      assertThat(typeComponent.text).isEqualTo(alarmSet.type.name)
      val intervalComponent = descriptionPanel.getValueComponent("Interval time") as JLabel
      assertThat(intervalComponent.text).isEqualTo("5 s")
      val creatorComponent = descriptionPanel.getValueComponent("Creator") as JLabel
      assertThat(creatorComponent.text).isEqualTo("creator.package (UID: 100)")

      val resultsPanel = detailsView.getCategoryPanel("Results") as JPanel
      val timeStartedComponent = resultsPanel.getValueComponent("Time started") as JLabel
      assertThat(timeStartedComponent.text).isEqualTo(0L.toFormattedTimeString())

      with(detailsView.stackTraceViews[0].stackTraceModel.codeLocations) {
        assertThat(size).isEqualTo(1)
        assertThat(this[0].className).isEqualTo("com.example.android.displayingbitmaps.util.ImageFetcher")
        assertThat(this[0].methodName).isEqualTo("downloadUrlToStream")
        assertThat(this[0].lineNumber).isEqualTo(26)
      }
      assertThat(detailsView.stackTraceViews[1].stackTraceModel.codeLocations).isEmpty()
    }

    client.sendBackgroundTaskEvent(10000) {
      taskId = 1
      alarmFiredBuilder.build()
      stacktrace = "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:27)"
    }

    withContext(uiDispatcher) {
      val resultsPanel = detailsView.getCategoryPanel("Results") as JPanel
      val timeCancelledComponent = resultsPanel.getValueComponent("Time fired #1") as JLabel
      assertThat(timeCancelledComponent.text).isEqualTo(10000L.toFormattedTimeString())
      val elapsedTimeComponent = resultsPanel.getValueComponent("Elapsed time") as JLabel
      assertThat(elapsedTimeComponent.text).isEqualTo("10 s")
    }
  }

  @Test
  fun alarmEntryWithListenerSelected() = runBlocking {
    val event = client.sendBackgroundTaskEvent(0) {
      taskId = 1
      alarmSetBuilder.apply {
        type = BackgroundTaskInspectorProtocol.AlarmSet.Type.RTC
        triggerMs = 10000
        listenerBuilder.apply {
          tag = "tag"
        }
      }
      stacktrace = "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:27)"
    }
    val alarmSet = event.backgroundTaskEvent.alarmSet

    withContext(uiDispatcher) {
      selectionModel.selectedEntry = client.getEntry("1")

      val descriptionPanel = detailsView.getCategoryPanel("Description") as JPanel
      val typeComponent = descriptionPanel.getValueComponent("Type") as JLabel
      assertThat(typeComponent.text).isEqualTo(alarmSet.type.name)
      val triggerComponent = descriptionPanel.getValueComponent("Trigger time") as JLabel
      assertThat(triggerComponent.text).isEqualTo(10000L.toFormattedTimeString())
      val creatorComponent = descriptionPanel.getValueComponent("Listener tag") as JLabel
      assertThat(creatorComponent.text).isEqualTo("tag")

      val resultsPanel = detailsView.getCategoryPanel("Results") as JPanel
      val timeStartedComponent = resultsPanel.getValueComponent("Time started") as JLabel
      assertThat(timeStartedComponent.text).isEqualTo(0L.toFormattedTimeString())

      with(detailsView.stackTraceViews[0].stackTraceModel.codeLocations) {
        assertThat(size).isEqualTo(1)
        assertThat(this[0].className).isEqualTo("com.example.android.displayingbitmaps.util.ImageFetcher")
        assertThat(this[0].methodName).isEqualTo("downloadUrlToStream")
        assertThat(this[0].lineNumber).isEqualTo(26)
      }
      assertThat(detailsView.stackTraceViews[1].stackTraceModel.codeLocations).isEmpty()
    }

    client.sendBackgroundTaskEvent(10000) {
      taskId = 1
      alarmFiredBuilder.build()
    }

    withContext(uiDispatcher) {
      val resultsPanel = detailsView.getCategoryPanel("Results") as JPanel
      val timeFiredComponent = resultsPanel.getValueComponent("Time fired") as JLabel
      assertThat(timeFiredComponent.text).isEqualTo(10000L.toFormattedTimeString())
      val elapsedTimeComponent = resultsPanel.getValueComponent("Elapsed time") as JLabel
      assertThat(elapsedTimeComponent.text).isEqualTo("10 s")
    }
  }

  @Test
  fun wakeLockEntrySelected() = runBlocking {
    val event = client.sendBackgroundTaskEvent(0) {
      taskId = 1
      wakeLockAcquiredBuilder.apply {
        tag = "tag"
        level = BackgroundTaskInspectorProtocol.WakeLockAcquired.Level.PARTIAL_WAKE_LOCK
      }
      stacktrace = "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:27)"
    }
    val wakeLockAcquired = event.backgroundTaskEvent.wakeLockAcquired

    withContext(uiDispatcher) {
      selectionModel.selectedEntry = client.getEntry("1")

      val descriptionPanel = detailsView.getCategoryPanel("Description") as JPanel
      val tagComponent = descriptionPanel.getValueComponent("Tag") as JLabel
      assertThat(tagComponent.text).isEqualTo(wakeLockAcquired.tag)
      val levelComponent = descriptionPanel.getValueComponent("Level") as JLabel
      assertThat(levelComponent.text).isEqualTo(wakeLockAcquired.level.name)

      val resultsPanel = detailsView.getCategoryPanel("Results") as JPanel
      val timeStartedComponent = resultsPanel.getValueComponent("Time started") as JLabel
      assertThat(timeStartedComponent.text).isEqualTo(0L.toFormattedTimeString())

      with(detailsView.stackTraceViews[0].stackTraceModel.codeLocations) {
        assertThat(size).isEqualTo(1)
        assertThat(this[0].className).isEqualTo("com.example.android.displayingbitmaps.util.ImageFetcher")
        assertThat(this[0].methodName).isEqualTo("downloadUrlToStream")
        assertThat(this[0].lineNumber).isEqualTo(26)
      }
      assertThat(detailsView.stackTraceViews[1].stackTraceModel.codeLocations).isEmpty()
    }

    client.sendBackgroundTaskEvent(10000) {
      taskId = 1
      wakeLockReleasedBuilder.build()
      stacktrace = "com.Test.download(Tester.java:43)"
    }

    withContext(uiDispatcher) {
      val resultsPanel = detailsView.getCategoryPanel("Results") as JPanel
      val timeCompletedComponent = resultsPanel.getValueComponent("Time completed") as JLabel
      assertThat(timeCompletedComponent.text).isEqualTo(10000L.toFormattedTimeString())
      val elapsedTimeComponent = resultsPanel.getValueComponent("Elapsed time") as JLabel
      assertThat(elapsedTimeComponent.text).isEqualTo("10 s")

      with(detailsView.stackTraceViews[0].stackTraceModel.codeLocations) {
        assertThat(size).isEqualTo(1)
        assertThat(this[0].className).isEqualTo("com.example.android.displayingbitmaps.util.ImageFetcher")
        assertThat(this[0].methodName).isEqualTo("downloadUrlToStream")
        assertThat(this[0].lineNumber).isEqualTo(26)
      }
      with(detailsView.stackTraceViews[1].stackTraceModel.codeLocations) {
        assertThat(size).isEqualTo(1)
        assertThat(this[0].className).isEqualTo("com.Test")
        assertThat(this[0].methodName).isEqualTo("download")
        assertThat(this[0].lineNumber).isEqualTo(42)
      }
    }
  }

  @Test
  fun jobEntrySelected() = runBlocking {
    val workInfo = BackgroundTaskInspectorTestUtils.FAKE_WORK_INFO
    client.sendWorkAddedEvent(workInfo)

    val event = client.sendBackgroundTaskEvent(0) {
      taskId = 1
      jobScheduledBuilder.apply {
        jobBuilder.apply {
          jobId = 222
          serviceName = "SERVICE"
          extras = BackgroundTaskInspectorTestUtils.createJobInfoExtraWithWorkerId(workInfo.id)
          networkType = BackgroundTaskInspectorProtocol.JobInfo.NetworkType.NETWORK_TYPE_METERED
          isPeriodic = false
        }
        result = BackgroundTaskInspectorProtocol.JobScheduled.Result.RESULT_SUCCESS
      }
      stacktrace = "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:27)"
    }
    val jobScheduled = event.backgroundTaskEvent.jobScheduled

    withContext(uiDispatcher) {
      selectionModel.selectedEntry = client.getEntry("1")

      val descriptionPanel = detailsView.getCategoryPanel("Description") as JPanel
      val serviceComponent = descriptionPanel.getValueComponent("Service") as ActionLink
      assertThat(serviceComponent.text).isEqualTo(jobScheduled.job.serviceName)

      val executionPanel = detailsView.getCategoryPanel("Execution") as JPanel
      val constraintsComponent = executionPanel.getValueComponent("Constraints") as JPanel
      assertThat(constraintsComponent.getValueComponent("Network must be metered")).isNotNull()
      val frequencyComponent = executionPanel.getValueComponent("Frequency") as JLabel
      assertThat(frequencyComponent.text).isEqualTo("OneTime")
      val stateComponent = executionPanel.getValueComponent("State") as JLabel
      assertThat(stateComponent.text).isEqualTo("Scheduled")
      assertThat(stateComponent.icon).isEqualTo(StudioIcons.LayoutEditor.Palette.ANALOG_CLOCK)
      val workerComponent = executionPanel.getValueComponent("Related Worker") as ActionLink
      assertThat(workerComponent.text).isEqualTo("ClassName1")

      val resultsPanel = detailsView.getCategoryPanel("Results") as JPanel
      val timeStartedComponent = resultsPanel.getValueComponent("Time started") as JLabel
      assertThat(timeStartedComponent.text).isEqualTo(0L.toFormattedTimeString())
    }

    client.sendBackgroundTaskEvent(10000) {
      taskId = 1
      jobFinishedBuilder.build()
      stacktrace = "com.Test.download(Tester.java:43)"
    }

    withContext(uiDispatcher) {
      val resultsPanel = detailsView.getCategoryPanel("Results") as JPanel
      val timeCompletedComponent = resultsPanel.getValueComponent("Time completed") as JLabel
      assertThat(timeCompletedComponent.text).isEqualTo(10000L.toFormattedTimeString())
      val elapsedTimeComponent = resultsPanel.getValueComponent("Elapsed time") as JLabel
      assertThat(elapsedTimeComponent.text).isEqualTo("10 s")

      with(detailsView.stackTraceViews[0].stackTraceModel.codeLocations) {
        assertThat(size).isEqualTo(1)
        assertThat(this[0].className).isEqualTo("com.example.android.displayingbitmaps.util.ImageFetcher")
        assertThat(this[0].methodName).isEqualTo("downloadUrlToStream")
        assertThat(this[0].lineNumber).isEqualTo(26)
      }
      with(detailsView.stackTraceViews[1].stackTraceModel.codeLocations) {
        assertThat(size).isEqualTo(1)
        assertThat(this[0].className).isEqualTo("com.Test")
        assertThat(this[0].methodName).isEqualTo("download")
        assertThat(this[0].lineNumber).isEqualTo(42)
      }
    }
  }

  @Test
  fun closeDetailsView() = runBlocking {
    val workInfo = BackgroundTaskInspectorTestUtils.FAKE_WORK_INFO
    client.sendWorkAddedEvent(workInfo)
    tab.isDetailsViewVisible = true
    withContext(uiDispatcher) {
      selectionModel.selectedEntry = client.getEntry(workInfo.id)
      val detailedPanelTitleLabel =
        TreeWalker(detailsView).descendantStream().filter { (it as? JLabel)?.text == "Task Details" }.findFirst().get()
      val titlePanel = detailedPanelTitleLabel.parent as JPanel
      val closeButton = titlePanel.getComponent(1) as InplaceButton
      assertThat(closeButton.toolTipText).isEqualTo("Close")
      closeButton.doClick()
      assertThat(tab.isDetailsViewVisible).isFalse()
    }
  }
}
