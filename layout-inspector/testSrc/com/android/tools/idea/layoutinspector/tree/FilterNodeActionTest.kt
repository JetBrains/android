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
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.adtui.workbench.ToolContent
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
import com.android.tools.property.testing.ApplicationRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.actionSystem.Presentation
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import java.util.EnumSet
import javax.swing.JPanel

class FilterNodeActionTest {

  @get:Rule
  val appRule = ApplicationRule()

  @Before
  fun before() {
    appRule.testApplication.registerService(PropertiesComponent::class.java, PropertiesComponentMock())
  }

  @Test
  fun testFilterSystemNodeActionDefaultValue() {
    val event = createEvent(mock())
    FilterNodeAction.update(event)
    assertThat(event.presentation.isVisible).isTrue()
    assertThat(FilterNodeAction.isSelected(event)).isTrue()
  }

  @Test
  fun testTurnOffFiltering() {
    TreeSettings.hideSystemNodes = true
    val treePanel: LayoutInspectorTreePanel = mock()
    val event = createEvent(treePanel)
    FilterNodeAction.update(event)
    assertThat(event.presentation.isVisible).isTrue()
    FilterNodeAction.setSelected(event, false)

    assertThat(TreeSettings.hideSystemNodes).isFalse()
    verify(treePanel).refresh()
  }

  @Test
  fun testTurnOnFiltering() {
    TreeSettings.hideSystemNodes = false
    val treePanel: LayoutInspectorTreePanel = mock()
    val event = createEvent(treePanel)
    FilterNodeAction.update(event)
    assertThat(event.presentation.isVisible).isTrue()
    FilterNodeAction.setSelected(event, true)

    assertThat(TreeSettings.hideSystemNodes).isTrue()
    verify(treePanel).refresh()
  }

  @Test
  fun testFilterSystemNodeActionNotAvailableIfUnsupportedByClient() {
    val event = createEvent(canSeparateSystemViews = false)
    TreeSettings.hideSystemNodes = false
    FilterNodeAction.update(event)
    assertThat(event.presentation.isVisible).isFalse()
  }

  @Test
  fun testFilterSystemNodeActionAlwaysAvailableIfNotConnected() {
    TreeSettings.hideSystemNodes = false
    FilterNodeAction.update(createEvent(connected = false, canSeparateSystemViews = false))
    assertThat(createEvent().presentation.isVisible).isTrue()
  }

  @Test
  fun testAddingFilterRemovesSystemSelectedAndHoveredNodes() {
    TreeSettings.hideSystemNodes = false
    val event = createEvent()
    val model = LayoutInspector.get(event)?.layoutInspectorModel!!
    model.setSelection(model[VIEW3]!!, SelectionOrigin.INTERNAL)
    model.hoveredNode = model[VIEW2]!!
    FilterNodeAction.setSelected(event, true)

    assertThat(model.selection).isSameAs(model[VIEW1]!!)
    assertThat(model.hoveredNode).isNull()
  }

  @Test
  fun testAddingFilterKeepsUserSelectedAndHoveredNode() {
    TreeSettings.hideSystemNodes = false
    val event = createEvent()
    val model = LayoutInspector.get(event)?.layoutInspectorModel!!
    model.setSelection(model[VIEW1]!!, SelectionOrigin.INTERNAL)
    model.hoveredNode = model[VIEW1]!!
    FilterNodeAction.setSelected(event, true)

    assertThat(model.selection).isSameAs(model[VIEW1]!!)
    assertThat(model.hoveredNode).isSameAs(model[VIEW1]!!)
  }

  private fun createEvent(
    treePanel: LayoutInspectorTreePanel = mock(),
    connected: Boolean = true,
    canSeparateSystemViews: Boolean = true
  ): AnActionEvent {
    val panel = JPanel()
    panel.putClientProperty(ToolContent.TOOL_CONTENT_KEY, treePanel)
    val inspector: LayoutInspector = mock()
    val client: InspectorClient = mock()
    val screenSimple = ResourceReference(ResourceNamespace.ANDROID, ResourceType.LAYOUT, "screen_simple")
    val appcompatScreenSimple = ResourceReference(ResourceNamespace.APPCOMPAT, ResourceType.LAYOUT, "abc_screen_simple")
    val mainLayout = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT, "activity_main")

    val model = model {
      view(ROOT) {
        view(VIEW1, layout = mainLayout) {
          view(VIEW2, layout = screenSimple) {
            view(VIEW3, layout = appcompatScreenSimple)
          }
        }
      }
    }
    val capabilities = if (canSeparateSystemViews) EnumSet.of(Capability.SUPPORTS_SYSTEM_NODES) else EnumSet.noneOf(Capability::class.java)
    `when`(inspector.layoutInspectorModel).thenReturn(model)
    `when`(inspector.currentClient).thenReturn(client)
    `when`(client.capabilities).thenReturn(capabilities)
    `when`(client.isConnected).thenReturn(connected)

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
