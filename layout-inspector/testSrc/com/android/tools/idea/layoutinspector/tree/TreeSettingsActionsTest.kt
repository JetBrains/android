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

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatisticsImpl
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.VIEW2
import com.android.tools.idea.layoutinspector.model.VIEW3
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorClient
import com.android.tools.idea.layoutinspector.snapshots.FileEditorInspectorClient
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess.ClientType.APP_INSPECTION_CLIENT
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.ui.treeStructure.Tree
import java.util.EnumSet
import javax.swing.JComponent
import javax.swing.JPanel
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

private val DO_NOT_CARE: () -> Boolean = { false }

class TreeSettingsActionsTest {
  companion object {
    @JvmField @ClassRule val rule = ApplicationRule()
  }

  @get:Rule val disposableRule = DisposableRule()

  private val treeSettings = FakeTreeSettings()
  private val model = createModel()
  private val stats = SessionStatisticsImpl(APP_INSPECTION_CLIENT)
  private val capabilities = EnumSet.noneOf(Capability::class.java)
  private var isConnected = false
  private var inLiveMode = true
  private val appClient: AppInspectionInspectorClient = mock()
  private val snapshotClient: FileEditorInspectorClient = mock()
  private var currentClient: InspectorClient? = appClient

  @Test
  fun testFilterSystemNodeAction() {
    val systemNodeFilterAction = SystemNodeFilterAction { null }
    val event = createEvent()
    systemNodeFilterAction.testActionVisibility(event, Capability.SUPPORTS_SYSTEM_NODES)
    systemNodeFilterAction.testToggleAction(event, statsValue = { stats.hideSystemNodes }) {
      treeSettings.hideSystemNodes
    }
  }

  @Test
  fun testHighlightSemanticsFilterAction() {
    val event = createEvent()
    HighlightSemanticsAction.testActionVisibility(event, Capability.SUPPORTS_SEMANTICS)
    HighlightSemanticsAction.testToggleAction(
      event,
      LayoutInspectorTreePanel::updateSemanticsFiltering,
    ) {
      treeSettings.highlightSemantics
    }
  }

  @Test
  fun testCallstackAction() {
    val event = createEvent()
    CallstackAction.testActionVisibility(event, Capability.SUPPORTS_COMPOSE)
    CallstackAction.testToggleAction(event) { treeSettings.composeAsCallstack }
  }

  @Test
  fun testSupportLines() {
    val event = createEvent()
    val defaultValue = treeSettings.supportLines
    assertThat(SupportLines.isSelected(event)).isEqualTo(defaultValue)
    SupportLines.setSelected(event, !defaultValue)
    assertThat(treeSettings.supportLines).isEqualTo(!defaultValue)
    verify(event.treePanel()?.component)!!.repaint()
    SupportLines.setSelected(event, defaultValue)
    assertThat(treeSettings.supportLines).isEqualTo(defaultValue)
    verify(event.treePanel()?.component, times(2))!!.repaint()
  }

  @Test
  fun testAddingFilterRemovesSystemSelectedAndHoveredNodes() {
    val systemNodeFilterAction = SystemNodeFilterAction { null }
    treeSettings.hideSystemNodes = false
    val event = createEvent()
    model.setSelection(model[VIEW3]!!, SelectionOrigin.INTERNAL)
    model.hoveredNode = model[VIEW2]!!
    systemNodeFilterAction.setSelected(event, true)

    assertThat(model.selection).isSameAs(model[VIEW1]!!)
    assertThat(model.hoveredNode).isNull()
  }

  @Test
  fun testAddingFilterKeepsUserSelectedAndHoveredNode() {
    val systemNodeFilterAction = SystemNodeFilterAction { null }
    treeSettings.hideSystemNodes = false
    val event = createEvent()
    model.setSelection(model[VIEW1]!!, SelectionOrigin.INTERNAL)
    model.hoveredNode = model[VIEW1]!!
    systemNodeFilterAction.setSelected(event, true)

    assertThat(model.selection).isSameAs(model[VIEW1]!!)
    assertThat(model.hoveredNode).isSameAs(model[VIEW1]!!)
  }

  @Test
  fun testRecompositionCounts() {
    val event = createEvent()
    assertThat(RecompositionCounts.isSelected(event)).isEqualTo(DEFAULT_RECOMPOSITIONS)

    RecompositionCounts.testActionVisibility(event, Capability.SUPPORTS_COMPOSE)
    capabilities.add(Capability.HAS_LINE_NUMBER_INFORMATION)
    RecompositionCounts.update(event)

    // Check recomposition not supported in compose inspector:
    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.text)
      .isEqualTo("Show Recomposition Counts (Needs Compose 1.2.1+)")

    capabilities.remove(Capability.HAS_LINE_NUMBER_INFORMATION)
    capabilities.add(Capability.SUPPORTS_COMPOSE_RECOMPOSITION_COUNTS)
    RecompositionCounts.update(event)

    // Check recomposition no source information:
    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.text)
      .isEqualTo("Show Recomposition Counts (No Source Information Found)")

    capabilities.add(Capability.HAS_LINE_NUMBER_INFORMATION)
    currentClient = snapshotClient
    RecompositionCounts.update(event)
    assertThat(event.presentation.isVisible).isFalse()

    currentClient = appClient
    RecompositionCounts.update(event)
    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isTrue()
    assertThat(event.presentation.text).isEqualTo("Show Recomposition Counts")

    RecompositionCounts.setSelected(event, true)
    assertThat(treeSettings.showRecompositions).isEqualTo(true)
    verify(event.treePanel())!!.updateRecompositionColumnVisibility()
    verify(event.treePanel())!!.resetRecompositionCounts()

    RecompositionCounts.setSelected(event, false)
    assertThat(treeSettings.showRecompositions).isEqualTo(false)
    verify(event.treePanel(), times(2))!!.updateRecompositionColumnVisibility()
    verify(event.treePanel(), times(2))!!.resetRecompositionCounts()

    // Disconnect and check modifying setting:
    isConnected = false
    inLiveMode = false
    assertThat(RecompositionCounts.isSelected(event)).isFalse()
    RecompositionCounts.setSelected(event, true)
    assertThat(RecompositionCounts.isSelected(event)).isTrue()
    RecompositionCounts.setSelected(event, false)
    assertThat(RecompositionCounts.isSelected(event)).isFalse()
  }

  private fun AnAction.testActionVisibility(
    event: AnActionEvent,
    vararg controllingCapabilities: Capability,
  ) {
    // All actions should be visible when not connected; no matter the controlling capability:
    isConnected = false
    capabilities.clear()
    update(event)
    assertThat(event.presentation.isVisible).isTrue()

    for (excluded in controllingCapabilities) {
      capabilities.addAll(controllingCapabilities)
      capabilities.remove(excluded)
      update(event)
      assertThat(event.presentation.isVisible).isTrue()
    }

    capabilities.addAll(controllingCapabilities)
    update(event)
    assertThat(event.presentation.isVisible).isTrue()

    // All actions should be hidden when connected and their controlling capability is off:
    isConnected = true
    capabilities.clear()
    update(event)
    assertThat(event.presentation.isVisible).isFalse()

    // If any capability is missing the action is hidden:
    for (excluded in controllingCapabilities) {
      capabilities.addAll(controllingCapabilities)
      capabilities.remove(excluded)
      update(event)
      assertThat(event.presentation.isVisible).isFalse()
    }

    // All actions should be visible when connected and their controlling capability is on:
    capabilities.addAll(controllingCapabilities)
    update(event)
    assertThat(event.presentation.isVisible).isTrue()
  }

  private fun ToggleAction.testToggleAction(
    event: AnActionEvent,
    update: (LayoutInspectorTreePanel) -> Unit = LayoutInspectorTreePanel::refresh,
    statsValue: () -> Boolean = DO_NOT_CARE,
    value: () -> Boolean,
  ) {
    val defaultValue = value()
    assertThat(isSelected(event)).isEqualTo(defaultValue)
    setSelected(event, !defaultValue)
    assertThat(value()).isEqualTo(!defaultValue)
    if (statsValue != DO_NOT_CARE) {
      assertThat(statsValue()).isEqualTo(!defaultValue)
    }
    update(verify(event.treePanel())!!)
    setSelected(event, defaultValue)
    assertThat(value()).isEqualTo(defaultValue)
    if (statsValue != DO_NOT_CARE) {
      assertThat(statsValue()).isEqualTo(defaultValue)
    }
    update(verify(event.treePanel(), times(2))!!)
  }

  private fun createEvent(): AnActionEvent {
    val tree: Tree = mock()
    val component: JComponent = mock()
    val treePanel: LayoutInspectorTreePanel = mock()
    val panel = JPanel()
    panel.putClientProperty(ToolContent.TOOL_CONTENT_KEY, treePanel)
    val inspector: LayoutInspector = mock()
    whenever(treePanel.tree).thenReturn(tree)
    whenever(treePanel.component).thenReturn(component)
    whenever(inspector.inspectorModel).thenReturn(model)
    whenever(inspector.treeSettings).thenReturn(treeSettings)
    whenever(appClient.stats).thenReturn(stats)
    whenever(snapshotClient.stats).thenReturn(stats)
    Mockito.doAnswer { currentClient }.whenever(inspector).currentClient
    Mockito.doAnswer { capabilities }.whenever(appClient).capabilities
    Mockito.doAnswer { capabilities }.whenever(snapshotClient).capabilities
    Mockito.doAnswer { isConnected }.whenever(appClient).isConnected
    Mockito.doAnswer { isConnected }.whenever(snapshotClient).isConnected
    Mockito.doAnswer { inLiveMode }.whenever(appClient).inLiveMode
    Mockito.doAnswer { inLiveMode }.whenever(snapshotClient).inLiveMode

    val dataContext =
      object : DataContext {
        override fun getData(dataId: String): Any? {
          return null
        }

        override fun <T> getData(key: DataKey<T>): T? {
          @Suppress("UNCHECKED_CAST")
          return when (key) {
            PlatformCoreDataKeys.CONTEXT_COMPONENT -> panel as T
            LAYOUT_INSPECTOR_DATA_KEY -> inspector as T
            else -> null
          }
        }
      }
    val actionManager: ActionManager = mock()
    return AnActionEvent(null, dataContext, ActionPlaces.UNKNOWN, Presentation(), actionManager, 0)
  }

  private fun createModel(): InspectorModel {
    val screenSimple =
      ResourceReference(ResourceNamespace.ANDROID, ResourceType.LAYOUT, "screen_simple")
    val appcompatScreenSimple =
      ResourceReference(ResourceNamespace.APPCOMPAT, ResourceType.LAYOUT, "abc_screen_simple")
    val mainLayout =
      ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT, "activity_main")
    return model(disposableRule.disposable) {
      view(ROOT) {
        view(VIEW1, layout = mainLayout) {
          view(VIEW2, layout = screenSimple) { view(VIEW3, layout = appcompatScreenSimple) }
        }
      }
    }
  }
}
