/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.adtui.TreeWalker
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServicesAdapter
import com.android.tools.idea.appinspection.inspectors.backgroundtask.ide.IdeBackgroundTaskInspectorTracker
import com.android.tools.idea.appinspection.inspectors.backgroundtask.ide.IntellijUiComponentsProvider
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorClient
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTestUtils
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTestUtils.sendWorkAddedEvent
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.EntrySelectionModel
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.WmiMessengerTarget
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.APP_INSPECTION
import com.google.wireless.android.sdk.stats.AppInspectionEvent.BackgroundTaskInspectorEvent
import com.google.wireless.android.sdk.stats.AppInspectionEvent.BackgroundTaskInspectorEvent.Type.GRAPH_MODE_SELECTED
import com.google.wireless.android.sdk.stats.AppInspectionEvent.BackgroundTaskInspectorEvent.Type.TABLE_MODE_SELECTED
import com.google.wireless.android.sdk.stats.AppInspectionEvent.BackgroundTaskInspectorEvent.Type.WORK_SELECTED
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TestActionEvent
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Tests for [BackgroundTaskEntriesView] */
class BackgroundTaskEntriesViewTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val usageTrackerRule = UsageTrackerRule()
  private val disposableRule = DisposableRule()

  @get:Rule val rule = RuleChain(projectRule, usageTrackerRule, disposableRule)

  private val scope =
    CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher() + SupervisorJob())
  private val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()
  private lateinit var workMessenger: BackgroundTaskViewTestUtils.FakeAppInspectorMessenger
  private lateinit var client: BackgroundTaskInspectorClient
  private lateinit var tab: BackgroundTaskInspectorTab
  private lateinit var selectionModel: EntrySelectionModel
  private lateinit var entriesView: BackgroundTaskEntriesView

  @Before
  fun setUp() = runBlocking {
    withContext(uiDispatcher) {
      val backgroundTaskInspectorMessenger =
        BackgroundTaskViewTestUtils.FakeAppInspectorMessenger(scope)
      workMessenger = BackgroundTaskViewTestUtils.FakeAppInspectorMessenger(scope)
      client =
        BackgroundTaskInspectorClient(
          backgroundTaskInspectorMessenger,
          WmiMessengerTarget.Resolved(workMessenger),
          scope,
          IdeBackgroundTaskInspectorTracker(projectRule.project),
        )
      tab =
        BackgroundTaskInspectorTab(
          client,
          AppInspectionIdeServicesAdapter(),
          IntellijUiComponentsProvider(projectRule.project, disposableRule.disposable),
          scope,
          uiDispatcher,
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
  fun graphViewAction_tracksUsage(): Unit = runBlocking {
    val workInfo = BackgroundTaskInspectorTestUtils.FAKE_WORK_INFO
    client.sendWorkAddedEvent(workInfo)
    withContext(uiDispatcher) {
      selectionModel.selectedEntry = client.getEntry(workInfo.id)
      val graphViewAction = tab.getAction("Show Graph View")

      graphViewAction.actionPerformed(TestActionEvent.createTestEvent())

      assertThat(usageTrackerRule.backgroundInspectorEvents().map { it.type })
        .containsExactly(WORK_SELECTED, GRAPH_MODE_SELECTED)
    }
  }

  @Test
  fun listViewAction_tracksUsage(): Unit = runBlocking {
    val workInfo = BackgroundTaskInspectorTestUtils.FAKE_WORK_INFO
    client.sendWorkAddedEvent(workInfo)
    withContext(uiDispatcher) {
      selectionModel.selectedEntry = client.getEntry(workInfo.id)
      val graphViewAction = tab.getAction("Show Graph View")
      val listViewAction = tab.getAction("Show List View")
      graphViewAction.actionPerformed(TestActionEvent.createTestEvent())

      listViewAction.actionPerformed(TestActionEvent.createTestEvent())

      assertThat(usageTrackerRule.backgroundInspectorEvents().map { it.type })
        .containsExactly(WORK_SELECTED, GRAPH_MODE_SELECTED, TABLE_MODE_SELECTED)
    }
  }
}

// TODO(aalbert): Move this to a shared location and use in other tests
private fun BackgroundTaskInspectorTab.getAction(title: String) =
  TreeWalker(component)
    .descendants()
    .filterIsInstance<ActionToolbar>()
    .flatMap { it.actions }
    .first { it.templatePresentation.text == title }

private fun UsageTrackerRule.backgroundInspectorEvents(): List<BackgroundTaskInspectorEvent> =
  usages
    .filter { it.studioEvent.kind == APP_INSPECTION }
    .map { it.studioEvent.appInspectionEvent.backgroundTaskInspectorEvent }
