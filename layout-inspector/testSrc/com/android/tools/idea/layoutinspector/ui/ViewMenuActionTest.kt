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

import com.android.flags.junit.FlagRule
import com.android.testutils.MockitoCleanerRule
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorClient
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.testFramework.ApplicationRule
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.doAnswer
import java.awt.event.InputEvent
import java.util.EnumSet

class ViewMenuActionTest {
  private lateinit var event: AnActionEvent

  companion object {
    @JvmField
    @ClassRule
    val rule = ApplicationRule()
  }

  @get:Rule
  val rules: RuleChain = RuleChain
    .outerRule(MockitoCleanerRule())
    .around(FlagRule(StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_ENABLE_RECOMPOSITION_COUNTS, true))
    .around(FlagRule(StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_ENABLE_RECOMPOSITION_HIGHLIGHTS, true))

  private val treeSettings = FakeTreeSettings().apply { showRecompositions = true }
  private val viewSettings = FakeRenderSettings()
  private val capabilities = EnumSet.noneOf(Capability::class.java).apply { add(Capability.SUPPORTS_COMPOSE_RECOMPOSITION_COUNTS) }
  private var isConnected = true

  @Before
  fun setUp() {
    event = createEvent()
  }

  @Test
  fun testActionVisibility() {
    isConnected = false
    treeSettings.showRecompositions = false
    capabilities.clear()

    HighlightColorAction.update(event)
    assertThat(event.presentation.isVisible).isFalse()

    treeSettings.showRecompositions = true
    HighlightColorAction.update(event)
    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isFalse()

    isConnected = true
    HighlightColorAction.update(event)
    assertThat(event.presentation.isVisible).isFalse()

    capabilities.add(Capability.SUPPORTS_COMPOSE_RECOMPOSITION_COUNTS)
    HighlightColorAction.update(event)
    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isTrue()

    treeSettings.showRecompositions = false
    HighlightColorAction.update(event)
    assertThat(event.presentation.isVisible).isFalse()
  }

  @Test
  fun testSelectedColor() {
    val colors = mapOf(
      0xFF0000 to "Red",
      0x4F9EE3 to "Blue",
      0x479345 to "Green",
      0xFFC66D to "Yellow",
      0x871094 to "Purple",
      0xE1A336 to "Orange"
    )

    for ((color, text) in colors) {
      viewSettings.highlightColor = color
      for (action in HighlightColorAction.getChildren(event)) {
        action.update(event)
        assertThat(Toggleable.isSelected(event.presentation)).isEqualTo(action.templateText == text)
      }
    }

    for (action in HighlightColorAction.getChildren(event)) {
      viewSettings.highlightColor = 0
      action.update(event)
      (action as CheckboxAction).setSelected(event, true)
      val expected = colors.filter { it.value == action.templateText }.map { it.key }.single()
      assertThat(viewSettings.highlightColor).isEqualTo(expected)
    }
  }

  private fun createEvent(): AnActionEvent {
    val inspector: LayoutInspector = mock()
    val client: AppInspectionInspectorClient = mock()
    whenever(inspector.treeSettings).thenReturn(treeSettings)
    whenever(inspector.currentClient).thenReturn(client)
    doAnswer { capabilities }.whenever(client).capabilities
    doAnswer { isConnected }.whenever(client).isConnected

    val dataContext = object : DataContext {
      override fun getData(dataId: String): Any? {
        return null
      }

      override fun <T> getData(key: DataKey<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return when (key) {
          DEVICE_VIEW_SETTINGS_KEY -> viewSettings as T
          LAYOUT_INSPECTOR_DATA_KEY -> inspector as T
          else -> null
        }
      }
    }
    val actionManager: ActionManager = mock()
    val inputEvent = mock<InputEvent>()
    return AnActionEvent(inputEvent, dataContext, ActionPlaces.UNKNOWN, Presentation(), actionManager, 0)
  }
}

class FakeRenderSettings: RenderSettings {
  override val modificationListeners = mutableListOf<() -> Unit>()
  override var scalePercent = 100
  override var drawBorders = true
  override var drawUntransformedBounds = false
  override var drawLabel = true
  override var drawFold = false
  override var highlightColor = HIGHLIGHT_COLOR_RED
}
