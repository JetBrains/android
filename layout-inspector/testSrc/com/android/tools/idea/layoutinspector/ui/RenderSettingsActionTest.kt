/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.ui

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorClient
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.HIGHLIGHT_COLOR_RED
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.HighlightColorAction
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.testFramework.ApplicationRule
import java.awt.event.MouseEvent
import java.util.EnumSet
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.mockito.Mockito.doAnswer

class RenderSettingsActionTest {
  private lateinit var event: AnActionEvent

  companion object {
    @JvmField @ClassRule val rule = ApplicationRule()
  }

  private val treeSettings = FakeTreeSettings().apply { showRecompositions = true }
  private val fakeRenderSettings = FakeRenderSettings()
  private val capabilities =
    EnumSet.noneOf(Capability::class.java).apply {
      add(Capability.SUPPORTS_COMPOSE_RECOMPOSITION_COUNTS)
    }
  private var isConnected = true

  @Before
  fun setUp() {
    event = createEvent()
  }

  @Test
  fun testActionVisibility() {
    val highlightColorAction = HighlightColorAction { fakeRenderSettings }

    isConnected = false
    treeSettings.showRecompositions = false
    capabilities.clear()

    highlightColorAction.update(event)
    assertThat(event.presentation.isVisible).isFalse()

    treeSettings.showRecompositions = true
    highlightColorAction.update(event)
    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isFalse()

    isConnected = true
    highlightColorAction.update(event)
    assertThat(event.presentation.isVisible).isFalse()

    capabilities.add(Capability.SUPPORTS_COMPOSE_RECOMPOSITION_COUNTS)
    highlightColorAction.update(event)
    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isTrue()

    treeSettings.showRecompositions = false
    highlightColorAction.update(event)
    assertThat(event.presentation.isVisible).isFalse()
  }

  @Test
  fun testSelectedColor() {
    val colors =
      mapOf(
        0xFF0000 to "Red",
        0x4F9EE3 to "Blue",
        0x479345 to "Green",
        0xFFC66D to "Yellow",
        0x871094 to "Purple",
        0xE1A336 to "Orange",
      )
    val highlightColorAction = HighlightColorAction { fakeRenderSettings }

    for ((color, text) in colors) {
      fakeRenderSettings.highlightColor = color
      for (action in highlightColorAction.getChildren(event)) {
        action.update(event)
        assertThat(Toggleable.isSelected(event.presentation)).isEqualTo(action.templateText == text)
      }
    }

    for (action in highlightColorAction.getChildren(event)) {
      fakeRenderSettings.highlightColor = 0
      action.update(event)
      (action as CheckboxAction).setSelected(event, true)
      val expected = colors.filter { it.value == action.templateText }.map { it.key }.single()
      assertThat(fakeRenderSettings.highlightColor).isEqualTo(expected)
    }
  }

  private fun createEvent(): AnActionEvent {
    val inspector: LayoutInspector = mock()
    val client: AppInspectionInspectorClient = mock()
    whenever(inspector.treeSettings).thenReturn(treeSettings)
    whenever(inspector.currentClient).thenReturn(client)
    doAnswer { capabilities }.whenever(client).capabilities
    doAnswer { isConnected }.whenever(client).isConnected

    val dataContext = DataContext { dataId ->
      when (dataId) {
        LAYOUT_INSPECTOR_DATA_KEY.name -> inspector
        else -> null
      }
    }
    val actionManager: ActionManager = mock()
    val inputEvent = mock<MouseEvent>()
    return AnActionEvent(
      inputEvent,
      dataContext,
      ActionPlaces.UNKNOWN,
      Presentation(),
      actionManager,
      0,
    )
  }
}

class FakeRenderSettings : RenderSettings {
  override val modificationListeners = mutableListOf<() -> Unit>()
  override var scalePercent = 100
  override var drawBorders = true
  override var drawUntransformedBounds = false
  override var drawLabel = true
  override var drawFold = false
  override var highlightColor = HIGHLIGHT_COLOR_RED
}
