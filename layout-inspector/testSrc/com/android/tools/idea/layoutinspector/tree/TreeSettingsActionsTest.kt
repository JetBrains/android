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

import com.android.flags.junit.SetFlagRule
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.VIEW2
import com.android.tools.idea.layoutinspector.model.VIEW3
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.property.testing.ApplicationRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.ui.treeStructure.Tree
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import javax.swing.JPanel

class TreeSettingsActionsTest {
  @get:Rule
  val applicationRule = ApplicationRule()

  @get:Rule
  val setFlagRule = SetFlagRule(StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_SHOW_SEMANTICS, true)

  private val treeSettings = FakeTreeSettings()
  private val capabilities = mutableSetOf<Capability>()
  private var isConnected = false

  @Test
  fun testFilterGroupAction() {
    val event = createEvent()
    FilterGroupAction.testActionVisibility(event, Capability.SUPPORTS_SYSTEM_NODES)
    FilterGroupAction.testActionVisibility(event, Capability.SUPPORTS_SEMANTICS)
  }

  @Test
  fun testFilterSystemNodeAction() {
    val event = createEvent()
    SystemNodeFilterAction.testActionVisibility(event, Capability.SUPPORTS_SYSTEM_NODES)
    SystemNodeFilterAction.testToggleAction(event) { treeSettings.hideSystemNodes }
  }

  @Test
  fun testMergedSemanticsFilterAction() {
    val event = createEvent()
    MergedSemanticsFilterAction.testActionVisibility(event, Capability.SUPPORTS_SEMANTICS)
    MergedSemanticsFilterAction.testToggleAction(event) { treeSettings.mergedSemanticsTree }
  }

  @Test
  fun testUnmergedSemanticsFilterAction() {
    val event = createEvent()
    UnmergedSemanticsFilterAction.testActionVisibility(event, Capability.SUPPORTS_SEMANTICS)
    UnmergedSemanticsFilterAction.testToggleAction(event) { treeSettings.unmergedSemanticsTree }
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
    verify(event.tree())!!.repaint()
    SupportLines.setSelected(event, defaultValue)
    assertThat(treeSettings.supportLines).isEqualTo(defaultValue)
    verify(event.tree(), times(2))!!.repaint()
  }

  @Test
  fun testAddingFilterRemovesSystemSelectedAndHoveredNodes() {
    treeSettings.hideSystemNodes = false
    val event = createEvent()
    val model = LayoutInspector.get(event)?.layoutInspectorModel!!
    model.setSelection(model[VIEW3]!!, SelectionOrigin.INTERNAL)
    model.hoveredNode = model[VIEW2]!!
    SystemNodeFilterAction.setSelected(event, true)

    assertThat(model.selection).isSameAs(model[VIEW1]!!)
    assertThat(model.hoveredNode).isNull()
  }

  @Test
  fun testAddingFilterKeepsUserSelectedAndHoveredNode() {
    treeSettings.hideSystemNodes = false
    val event = createEvent()
    val model = LayoutInspector.get(event)?.layoutInspectorModel!!
    model.setSelection(model[VIEW1]!!, SelectionOrigin.INTERNAL)
    model.hoveredNode = model[VIEW1]!!
    SystemNodeFilterAction.setSelected(event, true)

    assertThat(model.selection).isSameAs(model[VIEW1]!!)
    assertThat(model.hoveredNode).isSameAs(model[VIEW1]!!)
  }

  private fun AnAction.testActionVisibility(event: AnActionEvent, controllingCapability: Capability) {
    // All actions should be visible when not connected no matter the controlling capability:
    isConnected = false
    capabilities.clear()
    update(event)
    assertThat(event.presentation.isVisible).isTrue()

    capabilities.add(controllingCapability)
    update(event)
    assertThat(event.presentation.isVisible).isTrue()

    // All actions should be hidden when connected and their controlling capability is off:
    isConnected = true
    capabilities.clear()
    update(event)
    assertThat(event.presentation.isVisible).isFalse()

    // All actions should be visible when connected and their controlling capability is on:
    capabilities.add(controllingCapability)
    update(event)
    assertThat(event.presentation.isVisible).isTrue()
  }

  private fun ToggleAction.testToggleAction(event: AnActionEvent, value: () -> Boolean) {
    val defaultValue = value()
    assertThat(isSelected(event)).isEqualTo(defaultValue)
    setSelected(event, !defaultValue)
    assertThat(value()).isEqualTo(!defaultValue)
    verify(event.treePanel())!!.refresh()
    setSelected(event, defaultValue)
    assertThat(value()).isEqualTo(defaultValue)
    verify(event.treePanel(), times(2))!!.refresh()
  }

  private fun createEvent(): AnActionEvent {
    val tree: Tree = mock()
    val treePanel: LayoutInspectorTreePanel = mock()
    val panel = JPanel()
    panel.putClientProperty(ToolContent.TOOL_CONTENT_KEY, treePanel)
    val inspector: LayoutInspector = mock()
    val client: InspectorClient = mock()
    val screenSimple = ResourceReference(ResourceNamespace.ANDROID, ResourceType.LAYOUT, "screen_simple")
    val appcompatScreenSimple = ResourceReference(ResourceNamespace.APPCOMPAT, ResourceType.LAYOUT, "abc_screen_simple")
    val mainLayout = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT, "activity_main")
    `when`(inspector.treeSettings).thenReturn(treeSettings)
    `when`(treePanel.tree).thenReturn(tree)

    val model = model {
      view(ROOT) {
        view(VIEW1, layout = mainLayout) {
          view(VIEW2, layout = screenSimple) {
            view(VIEW3, layout = appcompatScreenSimple)
          }
        }
      }
    }
    `when`(inspector.layoutInspectorModel).thenReturn(model)
    `when`(inspector.currentClient).thenReturn(client)
    Mockito.doAnswer { capabilities }.`when`(client).capabilities
    Mockito.doAnswer { isConnected }.`when`(client).isConnected

    val dataContext = object : DataContext {
      override fun getData(dataId: String): Any? {
        return null
      }

      override fun <T> getData(key: DataKey<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return when (key) {
          CONTEXT_COMPONENT -> panel as T
          LAYOUT_INSPECTOR_DATA_KEY -> inspector as T
          else -> null
        }
      }
    }
    val actionManager: ActionManager = mock()
    return AnActionEvent(null, dataContext, ActionPlaces.UNKNOWN, Presentation(), actionManager, 0)
  }
}
