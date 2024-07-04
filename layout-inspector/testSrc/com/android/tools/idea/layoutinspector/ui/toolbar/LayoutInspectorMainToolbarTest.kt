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
package com.android.tools.idea.layoutinspector.ui.toolbar

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.test.DEFAULT_TEST_INSPECTION_STREAM
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.VIEW2
import com.android.tools.idea.layoutinspector.model.VIEW3
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorRule
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.toImageType
import com.android.tools.idea.layoutinspector.runningdevices.withEmbeddedLayoutInspector
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.LayerSpacingSliderAction
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.RefreshAction
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.ToggleLiveUpdatesAction
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.layoutinspector.window
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.property.panel.impl.model.util.FakeAction
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import java.awt.Dimension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

private val MODERN_PROCESS =
  MODERN_DEVICE.createProcess(streamId = DEFAULT_TEST_INSPECTION_STREAM.streamId)

@RunsInEdt
class LayoutInspectorMainToolbarTest {
  private val androidProjectRule: AndroidProjectRule = AndroidProjectRule.onDisk()
  private val appInspectorRule =
    AppInspectionInspectorRule(androidProjectRule, withDefaultResponse = false)
  private val layoutInspectorRule =
    LayoutInspectorRule(
      clientProviders = listOf(appInspectorRule.createInspectorClientProvider()),
      projectRule = androidProjectRule,
      isPreferredProcess = { it.name == MODERN_PROCESS.name },
    )

  @get:Rule
  val ruleChain: RuleChain =
    RuleChain.outerRule(androidProjectRule)
      .around(appInspectorRule)
      .around(layoutInspectorRule)
      .around(EdtRule())

  @Before
  fun setUp() {
    layoutInspectorRule.attachDevice(MODERN_DEVICE)
  }

  @After
  fun tearDown() {
    runBlocking { layoutInspectorRule.inspectorClient.stopFetching() }
  }

  @Test
  fun testLiveControlEnabledAndSetByDefaultWhenDisconnected() =
    withEmbeddedLayoutInspector(false) {
      val toggle = findActionButton(ToggleLiveUpdatesAction::class.java)!!
      assertThat(toggle.isEnabled).isTrue()
      assertThat(toggle.isSelected).isTrue()
      assertThat(getPresentation(toggle.action).description)
        .isEqualTo(
          "Stream updates to your app's layout from your device in realtime. Enabling live updates consumes more device resources and might " +
            "impact runtime performance."
        )
    }

  @Test
  fun testLiveControlEnabledAndNotSetInSnapshotModeWhenDisconnected() =
    withEmbeddedLayoutInspector(false) {
      val clientSettings = InspectorClientSettings(androidProjectRule.project)
      clientSettings.inLiveMode = false

      val toggle = findActionButton(ToggleLiveUpdatesAction::class.java)!!
      assertThat(toggle.isEnabled).isTrue()
      assertThat(toggle.isSelected).isFalse()
      assertThat(getPresentation(toggle.action).description)
        .isEqualTo(
          "Stream updates to your app's layout from your device in realtime. Enabling live updates consumes more device resources and might " +
            "impact runtime performance."
        )
    }

  @Test
  fun testLiveControlEnabledAndSetByDefaultWhenConnected() =
    withEmbeddedLayoutInspector(false) {
      installCommandHandlers()
      latch = CountDownLatch(1)
      connect(MODERN_PROCESS)
      assertThat(latch?.await(1L, TimeUnit.SECONDS)).isTrue()

      val toggle = findActionButton(ToggleLiveUpdatesAction::class.java)!!
      assertThat(toggle.isEnabled).isTrue()
      assertThat(toggle.isSelected).isTrue()
      assertThat(getPresentation(toggle.action).description)
        .isEqualTo(
          "Stream updates to your app's layout from your device in realtime. Enabling live updates consumes more device resources and might " +
            "impact runtime performance."
        )
      assertThat(commands).hasSize(2)
      assertThat(commands[1].hasStartFetchCommand()).isTrue()
    }

  @Test
  fun testLiveControlEnabledAndNotSetInSnapshotModeWhenConnected() =
    withEmbeddedLayoutInspector(false) {
      val clientSettings = InspectorClientSettings(androidProjectRule.project)
      clientSettings.inLiveMode = false

      installCommandHandlers()
      latch = CountDownLatch(1)
      connect(MODERN_PROCESS)
      assertThat(latch?.await(1L, TimeUnit.SECONDS)).isTrue()

      val toggle = findActionButton(ToggleLiveUpdatesAction::class.java)!!
      assertThat(toggle.isEnabled).isTrue()
      assertThat(toggle.isSelected).isFalse()
      assertThat(getPresentation(toggle.action).description)
        .isEqualTo(
          "Stream updates to your app's layout from your device in realtime. Enabling live updates consumes more device resources and might " +
            "impact runtime performance."
        )
      assertThat(commands).hasSize(2)
      assertThat(commands[0].startFetchCommand.continuous).isFalse()
    }

  @Test
  fun testTurnOnSnapshotModeWhenDisconnected() =
    withEmbeddedLayoutInspector(false) {
      installCommandHandlers()

      val clientSettings = InspectorClientSettings(androidProjectRule.project)
      clientSettings.inLiveMode = true

      val stats = layoutInspectorRule.inspector.currentClient.stats
      stats.currentModeIsLive = true

      val toolbar = createToolbar()
      val toggle = findActionButton(ToggleLiveUpdatesAction::class.java, toolbar)!!
      toolbar.component.size = Dimension(800, 200)
      toolbar.component.doLayout()
      val fakeUi = FakeUi(toggle)
      fakeUi.mouse.click(10, 10)
      assertThat(toggle.isEnabled).isTrue()
      assertThat(toggle.isSelected).isFalse()
      assertThat(getPresentation(toggle.action).description)
        .isEqualTo(
          "Stream updates to your app's layout from your device in realtime. Enabling live updates consumes more device resources and might " +
            "impact runtime performance."
        )

      assertThat(commands).isEmpty()
      assertThat(clientSettings.inLiveMode).isFalse()
      assertThat(stats.currentModeIsLive).isTrue() // unchanged
    }

  @Test
  fun testTurnOnLiveModeWhenDisconnected() =
    withEmbeddedLayoutInspector(false) {
      installCommandHandlers()
      val clientSettings = InspectorClientSettings(androidProjectRule.project)
      clientSettings.inLiveMode = false

      val stats = layoutInspectorRule.inspector.currentClient.stats
      stats.currentModeIsLive = false
      val toolbar = createToolbar()
      val toggle = findActionButton(ToggleLiveUpdatesAction::class.java, toolbar)!!
      toolbar.component.size = Dimension(800, 200)
      toolbar.component.doLayout()
      val fakeUi = FakeUi(toggle)
      fakeUi.mouse.click(10, 10)
      assertThat(toggle.isEnabled).isTrue()
      assertThat(toggle.isSelected).isTrue()
      assertThat(getPresentation(toggle.action).description)
        .isEqualTo(
          "Stream updates to your app's layout from your device in realtime. Enabling live updates consumes more device resources and might " +
            "impact runtime performance."
        )

      assertThat(commands).isEmpty()
      assertThat(clientSettings.inLiveMode).isTrue()
      assertThat(stats.currentModeIsLive).isFalse() // unchanged
    }

  @Test
  fun testTurnOnSnapshotMode() =
    withEmbeddedLayoutInspector(false) {
      latch = CountDownLatch(1)
      installCommandHandlers()

      connect(MODERN_PROCESS)
      assertThat(latch?.await(1, TimeUnit.SECONDS)).isTrue()
      val stats = layoutInspectorRule.inspector.currentClient.stats
      stats.currentModeIsLive = true
      latch = CountDownLatch(2)
      val toolbar = createToolbar()
      val toggle = findActionButton(ToggleLiveUpdatesAction::class.java, toolbar)!!
      toolbar.component.size = Dimension(800, 200)
      toolbar.component.doLayout()
      val fakeUi = FakeUi(toggle)
      fakeUi.mouse.click(10, 10)
      assertThat(toggle.isEnabled).isTrue()
      assertThat(toggle.isSelected).isFalse()
      assertThat(getPresentation(toggle.action).description)
        .isEqualTo(
          "Stream updates to your app's layout from your device in realtime. Enabling live updates consumes more device resources and might " +
            "impact runtime performance."
        )

      assertThat(latch?.await(1, TimeUnit.SECONDS)).isTrue()
      assertThat(commands).hasSize(4)
      assertThat(commands[1].hasStartFetchCommand()).isTrue()
      // stop and update screenshot type can come in either order
      assertThat(commands.find { it.hasStopFetchCommand() }).isNotNull()
      assertThat(commands.find { it.hasUpdateScreenshotTypeCommand() }).isNotNull()
      assertThat(stats.currentModeIsLive).isFalse()
    }

  @Test
  fun testTurnOnLiveMode() =
    withEmbeddedLayoutInspector(false) {
      latch = CountDownLatch(1)
      installCommandHandlers()

      val clientSettings = InspectorClientSettings(androidProjectRule.project)
      clientSettings.inLiveMode = false

      connect(MODERN_PROCESS)
      assertThat(latch?.await(1, TimeUnit.SECONDS)).isTrue()
      val stats = layoutInspectorRule.inspector.currentClient.stats
      stats.currentModeIsLive = false

      latch = CountDownLatch(1)
      val toolbar = createToolbar()
      val toggle = findActionButton(ToggleLiveUpdatesAction::class.java, toolbar)!!
      toolbar.component.size = Dimension(800, 200)
      toolbar.component.doLayout()

      val fakeUi = FakeUi(toggle)
      fakeUi.mouse.click(10, 10)
      assertThat(toggle.isEnabled).isTrue()
      assertThat(toggle.isSelected).isTrue()
      assertThat(getPresentation(toggle.action).description)
        .isEqualTo(
          "Stream updates to your app's layout from your device in realtime. Enabling live updates consumes more device resources and might" +
            " impact runtime performance."
        )

      assertThat(latch?.await(1, TimeUnit.SECONDS)).isTrue()
      assertThat(commands).hasSize(3)
      assertThat(commands[0].startFetchCommand.continuous).isFalse()
      assertThat(commands[2].startFetchCommand.continuous).isTrue()

      assertThat(stats.currentModeIsLive).isTrue()
    }

  @Test
  fun testFocusableActionButtons() {
    val toolbar = createToolbar()
    toolbar.component.components.forEach { Truth.assertThat(it.isFocusable).isTrue() }
  }

  @Test
  fun testDeviceSelectionToolbarIsImportant() {
    val toolbar = createToolbar()
    val isImportant =
      toolbar.component.getClientProperty(ActionToolbarImpl.IMPORTANT_TOOLBAR_KEY) as? Boolean
        ?: false
    assertThat(isImportant).isTrue()
  }

  @Test
  fun showLayerSpacingSliderOnlyIfModelIsRotated() {
    val toolbar = createToolbar()
    val slider = toolbar.actions.find { it is LayerSpacingSliderAction } as LayerSpacingSliderAction

    val presentationDisabled = getPresentation(slider)
    assertThat(presentationDisabled.isEnabled).isFalse()
    assertThat(presentationDisabled.isVisible).isFalse()

    layoutInspectorRule.inspector.renderModel.rotate(10.0, 10.0)

    val presentationEnabled = getPresentation(slider)
    assertThat(presentationEnabled.isEnabled).isTrue()
    assertThat(presentationEnabled.isVisible).isTrue()
  }

  @Test
  fun testLiveUpdatesAndRefreshActionsAreMissingFromEmbeddedLayoutInspector() =
    withEmbeddedLayoutInspector {
      val toolbar = createToolbar()
      val liveUpdatesAction = findActionButton(ToggleLiveUpdatesAction::class.java, toolbar)
      val refreshAction = findActionButton(RefreshAction::class.java, toolbar)
      assertThat(liveUpdatesAction).isNull()
      assertThat(refreshAction).isNull()
    }

  // Used by all tests that install command handlers
  private var latch: CountDownLatch? = null
  private val commands = mutableListOf<LayoutInspectorViewProtocol.Command>()
  private var lastImageType = AndroidWindow.ImageType.BITMAP_AS_REQUESTED

  private val appNamespace = ResourceNamespace.fromPackageName("com.example")
  private val demoLayout = ResourceReference(appNamespace, ResourceType.LAYOUT, "demo")
  private val view1Id = ResourceReference(appNamespace, ResourceType.ID, "v1")
  private val view2Id = ResourceReference(appNamespace, ResourceType.ID, "v2")

  private fun installCommandHandlers() {
    appInspectorRule.viewInspector.listenWhen({ true }) { command ->
      commands.add(command)

      when (command.specializedCase) {
        LayoutInspectorViewProtocol.Command.SpecializedCase.UPDATE_SCREENSHOT_TYPE_COMMAND -> {
          lastImageType = command.updateScreenshotTypeCommand.type.toImageType()
        }
        else -> {}
      }
      val window =
        window("w1", 1L, imageType = lastImageType) {
          view(VIEW1, 0, 0, 10, 10, qualifiedName = "v1", layout = demoLayout, viewId = view1Id) {
            view(VIEW2, 0, 0, 10, 10, qualifiedName = "v2", layout = demoLayout, viewId = view2Id)
            view(VIEW3, 0, 5, 10, 5, qualifiedName = "v3", layout = demoLayout)
          }
        }
      layoutInspectorRule.inspectorModel.update(window, listOf("w1"), 1)
      latch?.countDown()
    }
  }

  @Suppress("SameParameterValue")
  private fun connect(process: ProcessDescriptor) {
    layoutInspectorRule.processNotifier.addDevice(process.device)
    layoutInspectorRule.processNotifier.fireConnected(process)
  }

  private fun createToolbar(): ActionToolbar {
    val fakeAction = FakeAction("fake action")
    val toolbar =
      createStandaloneLayoutInspectorToolbar(
        androidProjectRule.testRootDisposable,
        JPanel(),
        layoutInspectorRule.inspector,
        fakeAction,
      )
    waitForCondition(5.seconds) { toolbar.component.components.isNotEmpty() }
    return toolbar
  }

  private fun <T : AnAction> findActionButton(
    actionClass: Class<T>,
    toolbar: ActionToolbar = createToolbar(),
  ): ActionButton? {
    return toolbar.component.components.filterIsInstance<ActionButton>().find {
      actionClass.isInstance(it.action)
    }
  }

  private fun getPresentation(action: AnAction): Presentation {
    val presentation = Presentation()
    val event =
      AnActionEvent(
        null,
        DataContext.EMPTY_CONTEXT,
        "LayoutInspector.MainToolbar",
        presentation,
        ActionManager.getInstance(),
        0,
      )
    action.update(event)
    return presentation
  }
}
