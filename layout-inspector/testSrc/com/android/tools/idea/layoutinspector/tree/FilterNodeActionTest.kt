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

import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability.SUPPORTS_FILTERING_SYSTEM_NODES
import com.android.tools.property.testing.ApplicationRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.Presentation
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.util.EnumSet

class FilterNodeActionTest {

  @get:Rule
  val appRule = ApplicationRule()

  @Before
  fun before() {
    appRule.testApplication.registerService(PropertiesComponent::class.java, PropertiesComponentMock())
  }

  @Test
  fun testFilterSystemNodeActionDefaultValue() {
    val inspector = mock(LayoutInspector::class.java)
    val event = createEvent(inspector)
    FilterNodeAction.update(event)
    assertThat(event.presentation.isVisible).isTrue()
    assertThat(FilterNodeAction.isSelected(event)).isTrue()
  }

  @Test
  fun testFilterSystemNodeActionWhenLive() {
    val inspector = mock(LayoutInspector::class.java)
    val client = mock(InspectorClient::class.java)
    val event = createEvent(inspector)
    `when`(inspector.currentClient).thenReturn(client)
    `when`(client.capabilities).thenReturn(EnumSet.of(SUPPORTS_FILTERING_SYSTEM_NODES))
    `when`(client.isConnected).thenReturn(true)
    `when`(client.isCapturing).thenReturn(true)
    FilterNodeAction.setSelected(event, false)

    assertThat(TreeSettings.hideSystemNodes).isFalse()
    verify(client).startFetching()
  }

  @Test
  fun testFilterSystemNodeActionWhenNotLive() {
    val inspector = mock(LayoutInspector::class.java)
    val client = mock(InspectorClient::class.java)
    val event = createEvent(inspector)
    `when`(inspector.currentClient).thenReturn(client)
    `when`(client.capabilities).thenReturn(EnumSet.of(SUPPORTS_FILTERING_SYSTEM_NODES))
    `when`(client.isConnected).thenReturn(true)
    `when`(client.isCapturing).thenReturn(false)
    TreeSettings.hideSystemNodes = false
    FilterNodeAction.update(event)
    assertThat(event.presentation.isVisible).isTrue()
    FilterNodeAction.setSelected(event, true)

    assertThat(TreeSettings.hideSystemNodes).isTrue()
    verify(client).refresh()
  }

  @Test
  fun testFilterSystemNodeActionNotAvailableIfUnsupportedByClient() {
    val inspector = mock(LayoutInspector::class.java)
    val client = mock(InspectorClient::class.java)
    val event = createEvent(inspector)
    `when`(client.capabilities).thenReturn(EnumSet.noneOf(Capability::class.java))
    `when`(inspector.currentClient).thenReturn(client)
    `when`(client.isConnected).thenReturn(true)
    `when`(client.isCapturing).thenReturn(false)
    TreeSettings.hideSystemNodes = false
    FilterNodeAction.update(event)
    assertThat(event.presentation.isVisible).isFalse()
  }

  private fun createEvent(inspector: LayoutInspector): AnActionEvent {
    val dataContext = object : DataContext {
      override fun getData(dataId: String): Any? {
        return null
      }

      override fun <T> getData(key: DataKey<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return if (key == LAYOUT_INSPECTOR_DATA_KEY) inspector as T else null
      }
    }
    val actionManager = Mockito.mock(ActionManager::class.java)
    return AnActionEvent(null, dataContext, ActionPlaces.UNKNOWN, Presentation(), actionManager, 0)
  }
}