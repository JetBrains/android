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
package com.android.tools.idea.layoutinspector.tree

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.test.testutils.TestUtils.resolveWorkspacePath
import com.android.testutils.waitForCondition
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.NO_COMPOSE_SOURCE_INFO_APP_KEY
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatistics
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatisticsImpl
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.util.DemoExample
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.ui.FileOpenCaptureRule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess.ClientType.APP_INSPECTION_CLIENT
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorSession
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.testFramework.runInEdtAndGet
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class GotoDeclarationActionTest {

  private val projectRule = AndroidProjectRule.withSdk()
  private val fileOpenCaptureRule = FileOpenCaptureRule(projectRule)

  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(fileOpenCaptureRule)!!

  @Before
  fun setup() {
    loadComposeFiles()
  }

  @Test
  fun testViewNode() {
    val model = runInEdtAndGet { createModel() }
    model.setSelection(model["title"], SelectionOrigin.INTERNAL)
    val stats = SessionStatisticsImpl(APP_INSPECTION_CLIENT)
    val event = createEvent(model, stats)
    GotoDeclarationAction.update(event)
    assertThat(event.presentation.isEnabled).isTrue()
    GotoDeclarationAction.actionPerformed(event)
    runBlocking { GotoDeclarationAction.lastAction?.join() }
    fileOpenCaptureRule.checkEditor("demo.xml", 9, "<TextView")
    checkStats(stats, clickCount = 1)
  }

  @Test
  fun testOnlyOneNotFoundMessage() {
    val model = runInEdtAndGet { createModel() }
    model.setSelection(model[8], SelectionOrigin.INTERNAL)
    val stats = SessionStatisticsImpl(APP_INSPECTION_CLIENT)
    val notificationModel = NotificationModel(projectRule.project)
    val event = createEvent(model, stats, notificationModel)
    GotoDeclarationAction.actionPerformed(event)
    runBlocking { GotoDeclarationAction.lastAction?.join() }
    assertThat(notificationModel.notifications).hasSize(1)
    assertThat(notificationModel.notifications.first().message)
      .isEqualTo(
        "Cannot navigate to source because LinearLayout in the layout demo.xml doesn't have an id."
      )
    model.setSelection(model[5], SelectionOrigin.INTERNAL)
    GotoDeclarationAction.actionPerformed(event)
    runBlocking { GotoDeclarationAction.lastAction?.join() }
    assertThat(notificationModel.notifications).hasSize(1)
    assertThat(notificationModel.notifications.first().message)
      .isEqualTo(
        "Cannot navigate to source because TextView in the layout demo.xml doesn't have an id."
      )
  }

  @Test
  fun testComposeViewNode() {
    val model = runInEdtAndGet { createModel() }
    model.setSelection(model[-2], SelectionOrigin.INTERNAL)
    val stats = SessionStatisticsImpl(APP_INSPECTION_CLIENT)
    val event = createEvent(model, stats, fromShortcut = true)
    GotoDeclarationAction.update(event)
    assertThat(event.presentation.isEnabled).isTrue()
    GotoDeclarationAction.actionPerformed(event)
    runBlocking { GotoDeclarationAction.lastAction?.join() }
    fileOpenCaptureRule.checkEditor(
      "MyCompose.kt",
      17,
      "modifier = Modifier.padding(20.dp).clickable(onClick = { selectColumn() }),",
    )
    checkStats(stats, keyStrokeCount = 1)
  }

  @Test
  fun testComposeViewNodeWithoutSourceCodeInformation() {
    val model = runInEdtAndGet { createModel() }
    model.setSelection(model[-4], SelectionOrigin.INTERNAL)
    val stats = SessionStatisticsImpl(APP_INSPECTION_CLIENT)
    val event = createEvent(model, stats, fromShortcut = true)
    GotoDeclarationAction.update(event)
    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.text).isEqualTo("Go To Declaration (No Source Information Found)")
  }

  @Test
  fun testNavigateToSelectedViewWithoutViewId() {
    val model = runInEdtAndGet { createModel() }
    model.setSelection(model[0], SelectionOrigin.INTERNAL)
    val stats = SessionStatisticsImpl(APP_INSPECTION_CLIENT)
    val notificationModel = NotificationModel(projectRule.project)

    // Make sure an earlier missing compose source information warning is cleared
    notificationModel.addNotification(
      NO_COMPOSE_SOURCE_INFO_NODE_KEY,
      "Source information for composable not found",
    )

    val capabilities = setOf(InspectorClient.Capability.SUPPORTS_COMPOSE)
    val inspector = createLayoutInspector(model, stats, capabilities, notificationModel)
    GotoDeclarationAction.navigateToSelectedView(
      inspector.coroutineScope,
      model,
      inspector.currentClient,
      notificationModel,
    )
    waitForCondition(10.seconds) { notificationModel.hasNotification(VIEW_NOT_FOUND_KEY) }
    assertThat(notificationModel.notifications.single().message)
      .isEqualTo(
        "Cannot navigate to source because AndroidComposeView in the layout defaultLayout.xml doesn't have an id."
      )
  }

  @Test
  fun testNavigateToSelectedComposeViewWithoutSourceCodeInformation() {
    val model = runInEdtAndGet { createModel() }
    model.setSelection(model[-4], SelectionOrigin.INTERNAL)
    val stats = SessionStatisticsImpl(APP_INSPECTION_CLIENT)
    val notificationModel = NotificationModel(projectRule.project)

    // Make sure an earlier missing view id warning is cleared
    notificationModel.addNotification(VIEW_NOT_FOUND_KEY, "View not found")

    val inspector = createLayoutInspector(model, stats, setOf(), notificationModel)
    GotoDeclarationAction.navigateToSelectedView(
      inspector.coroutineScope,
      model,
      inspector.currentClient,
      notificationModel,
    )
    waitForCondition(10.seconds) {
      notificationModel.hasNotification(NO_COMPOSE_SOURCE_INFO_NODE_KEY)
    }
    assertThat(notificationModel.notifications.single().message)
      .isEqualTo(
        "No source information found for Greeting Composable. Perhaps the code is from an obfuscated 3rd party library ?"
      )
  }

  @Test
  fun testNavigateToSelectedComposeViewWithoutSourceCodeInformationInApplication() {
    val model = runInEdtAndGet { createModel() }
    model.setSelection(model[-4], SelectionOrigin.INTERNAL)
    val stats = SessionStatisticsImpl(APP_INSPECTION_CLIENT)
    val notificationModel = NotificationModel(projectRule.project)

    // Make sure an earlier missing view id or missing compose source information warning is cleared
    notificationModel.addNotification(VIEW_NOT_FOUND_KEY, "View not found")
    notificationModel.addNotification(
      NO_COMPOSE_SOURCE_INFO_NODE_KEY,
      "Source information for composable not found",
    )

    val capabilities = setOf(InspectorClient.Capability.SUPPORTS_COMPOSE)
    val inspector = createLayoutInspector(model, stats, capabilities, notificationModel)
    GotoDeclarationAction.navigateToSelectedView(
      inspector.coroutineScope,
      model,
      inspector.currentClient,
      notificationModel,
    )
    waitForCondition(10.seconds) {
      notificationModel.hasNotification(NO_COMPOSE_SOURCE_INFO_APP_KEY)
    }
    assertThat(notificationModel.notifications.single().message)
      .isEqualTo(
        "No compose source information found. For full inspector functionality: Make sure that sourceInformation is turned on for the Kotlin compiler plugin and the app code is not obfuscated."
      )
  }

  @Test
  fun testComposeViewNodeInOtherFileWithSameName() {
    val model = runInEdtAndGet { createModel() }
    model.setSelection(model[-5], SelectionOrigin.INTERNAL)
    val stats = SessionStatisticsImpl(APP_INSPECTION_CLIENT)
    val event = createEvent(model, stats)
    GotoDeclarationAction.update(event)
    assertThat(event.presentation.isEnabled).isTrue()
    GotoDeclarationAction.actionPerformed(event)
    runBlocking { GotoDeclarationAction.lastAction?.join() }
    fileOpenCaptureRule.checkEditor("MyCompose.kt", 8, "Text(text = \"Hello \$name!\")")
    checkStats(stats, clickCount = 1)
  }

  @Test
  fun testGoToDeclarationDisabledWhenNoResolver() {
    val model = runInEdtAndGet { createModel() }
    model.setSelection(model[-5], SelectionOrigin.INTERNAL)
    model.resourceLookup.updateConfiguration(420, 1f, Dimension(1080, 2400))
    val stats = SessionStatisticsImpl(APP_INSPECTION_CLIENT)
    val event = createEvent(model, stats)
    GotoDeclarationAction.update(event)
    assertThat(event.presentation.isEnabled).isFalse()
  }

  private fun loadComposeFiles() {
    val fixture = projectRule.fixture
    fixture.testDataPath =
      resolveWorkspacePath("tools/adt/idea/layout-inspector/testData/compose").toString()
    fixture.copyFileToProject("java/com/example/MyCompose.kt")
    fixture.copyFileToProject("java/com/example/composable/MyCompose.kt")
  }

  private fun createModel(): InspectorModel =
    model(
      projectRule.testRootDisposable,
      projectRule.project,
      FakeTreeSettings(),
      body =
        DemoExample.setUpDemo(projectRule.fixture) {
          view(0, qualifiedName = "androidx.ui.core.AndroidComposeView") {
            compose(-2, "Column", "MyCompose.kt", 49835523, 540, 17) {
              compose(-3, "Text", "MyCompose.kt", 49835523, 593, 18)
              compose(-4, "Greeting", "MyCompose.kt", -1, -1, 0) {
                compose(-5, "Text", "MyCompose.kt", 1216697758, 164, 3)
              }
            }
          }
        },
    )

  private fun createEvent(
    model: InspectorModel,
    stats: SessionStatistics,
    notificationModel: NotificationModel = mock(),
    fromShortcut: Boolean = false,
  ): AnActionEvent {
    val dataContext: DataContext = mock()
    val inspector = createLayoutInspector(model, stats, setOf(), notificationModel)
    whenever(dataContext.getData(LAYOUT_INSPECTOR_DATA_KEY)).thenReturn(inspector)
    val actionManager: ActionManager = mock()
    val inputEvent = if (fromShortcut) mock<KeyEvent>() else mock<MouseEvent>()
    return AnActionEvent(
      inputEvent,
      dataContext,
      ActionPlaces.UNKNOWN,
      Presentation(),
      actionManager,
      0,
    )
  }

  private fun createLayoutInspector(
    model: InspectorModel,
    stats: SessionStatistics,
    capabilities: Set<InspectorClient.Capability> = emptySet(),
    notificationModel: NotificationModel = mock(),
  ): LayoutInspector {
    val client: InspectorClient = mock()
    whenever(client.stats).thenReturn(stats)
    whenever(client.capabilities).thenReturn(capabilities)
    val coroutineScope = AndroidCoroutineScope(projectRule.testRootDisposable)
    val clientSettings = InspectorClientSettings(projectRule.project)
    return LayoutInspector(coroutineScope, clientSettings, client, model, notificationModel, mock())
  }

  private fun checkStats(stats: SessionStatistics, clickCount: Int = 0, keyStrokeCount: Int = 0) {
    val data = DynamicLayoutInspectorSession.newBuilder()
    stats.save(data)
    assertThat(data.gotoDeclaration.clicksMenuAction).isEqualTo(clickCount)
    assertThat(data.gotoDeclaration.keyStrokesShortcut).isEqualTo(keyStrokeCount)
  }

  private fun InspectorModel.findByTagName(tagName: String): ViewNode? =
    ViewNode.readAccess { root.flatten().firstOrNull { it.qualifiedName == tagName } }
}
