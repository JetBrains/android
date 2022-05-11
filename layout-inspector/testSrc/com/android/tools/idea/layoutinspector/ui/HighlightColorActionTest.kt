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

import com.android.flags.junit.SetFlagRule
import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.swing.FakeKeyboardFocusManager
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.popup.FakeJBPopupFactory
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorClient
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.property.testing.ApplicationRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import java.awt.Component
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.EnumSet
import javax.swing.JPanel

class HighlightColorActionTest {
  private val applicationRule = ApplicationRule()
  private val factory = FakeJBPopupFactory()
  private var balloonIndex = 0
  private lateinit var focusManager: FakeKeyboardFocusManager
  private lateinit var event: AnActionEvent

  @get:Rule
  val rules: RuleChain = RuleChain
    .outerRule(applicationRule)
    .around(SetFlagRule(StudioFlags.USE_COMPONENT_TREE_TABLE, true))
    .around(SetFlagRule(StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_ENABLE_RECOMPOSITION_COUNTS, true))
    .around(SetFlagRule(StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_ENABLE_RECOMPOSITION_HIGHLIGHTS, true))
    .around(EdtRule())

  private val treeSettings = FakeTreeSettings().apply { showRecompositions = true }
  private val viewSettings = FakeDeviceViewSettings()
  private val capabilities = EnumSet.noneOf(Capability::class.java).apply { add(Capability.SUPPORTS_COMPOSE_RECOMPOSITION_COUNTS) }
  private var isConnected = true

  @Before
  fun setUp() {
    val disposable = applicationRule.testRootDisposable
    applicationRule.testApplication.registerService(JBPopupFactory::class.java, factory, disposable)
    event = createEvent()
    focusManager = FakeKeyboardFocusManager(disposable)
    balloonIndex = 0
  }

  @After
  fun cleanUp() {
    for (index in 0 until balloonIndex) {
      factory.getBalloon(index).dispose()
    }
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

  @RunsInEdt
  @Test
  fun testInitialSelectedColor() {
    for (color in listOf(COLOR1_START, COLOR2_START, COLOR3_START, COLOR4_START, COLOR5_START, COLOR6_START)) {
      viewSettings.highlightColor = color
      openPopup()
      val box = focusManager.focusOwner as GradientButton
      assertThat(box.colorStart.rgb).isEqualTo(color.or(0xFF000000L.toInt()))
      factory.getBalloon(balloonIndex++).hide()
    }
  }

  @RunsInEdt
  @Test
  fun testMouseClicks() {
    // Click on the last color button
    openPopup()
    var box = focusManager.focusOwner as GradientButton
    var boxes = box.parent.components.filterIsInstance<GradientButton>()
    var balloon = factory.getBalloon(balloonIndex++)
    balloon.ui!!.mouseClickOn(boxes.last())
    assertThat(viewSettings.highlightColor).isEqualTo(COLOR6_START)
    // A mouse click should close the balloon:
    assertThat(balloon.isDisposed).isTrue()

    // Then click on the forth color button
    openPopup()
    box = focusManager.focusOwner as GradientButton
    boxes = box.parent.components.filterIsInstance<GradientButton>()
    balloon = factory.getBalloon(balloonIndex++)
    balloon.ui!!.mouseClickOn(boxes[3])
    assertThat(viewSettings.highlightColor).isEqualTo(COLOR4_START)
    // A mouse click should close the balloon:
    assertThat(balloon.isDisposed).isTrue()
  }

  @RunsInEdt
  @Test
  fun testKeyboardNavigation() {
    openPopup()
    val balloon = factory.getBalloon(balloonIndex++)
    val ui = balloon.ui!!

    // Navigate with the left arrow (start at COLOR1)
    for (color in listOf(COLOR1_START, COLOR2_START, COLOR3_START, COLOR4_START, COLOR5_START, COLOR6_START).reversed()) {
      ui.keyboard.pressAndRelease(KeyEvent.VK_LEFT)
      assertThat(viewSettings.highlightColor).isEqualTo(color)
      assertThat(balloon.isDisposed).isFalse()
    }

    // Navigate with the right arrow  (start at COLOR1)
    for (color in listOf(COLOR2_START, COLOR3_START, COLOR4_START, COLOR5_START, COLOR6_START, COLOR1_START)) {
      ui.keyboard.pressAndRelease(KeyEvent.VK_RIGHT)
      assertThat(viewSettings.highlightColor).isEqualTo(color)
      assertThat(balloon.isDisposed).isFalse()
    }

    // Navigate with the back TAB key  (start at COLOR1)
    for (color in listOf(COLOR1_START, COLOR2_START, COLOR3_START, COLOR4_START, COLOR5_START, COLOR6_START).reversed()) {
      ui.keyboard.press(KeyEvent.VK_SHIFT)
      ui.keyboard.pressAndRelease(KeyEvent.VK_TAB)
      ui.keyboard.release(KeyEvent.VK_SHIFT)
      assertThat(viewSettings.highlightColor).isEqualTo(color)
      assertThat(balloon.isDisposed).isFalse()
    }

    // Navigate with the TAB key  (start at COLOR1)
    for (color in listOf(COLOR2_START, COLOR3_START, COLOR4_START, COLOR5_START, COLOR6_START, COLOR1_START)) {
      ui.keyboard.pressAndRelease(KeyEvent.VK_TAB)
      assertThat(viewSettings.highlightColor).isEqualTo(color)
      assertThat(balloon.isDisposed).isFalse()
    }

    ui.keyboard.pressAndRelease(KeyEvent.VK_END)
    assertThat(viewSettings.highlightColor).isEqualTo(COLOR6_START)

    ui.keyboard.pressAndRelease(KeyEvent.VK_HOME)
    assertThat(viewSettings.highlightColor).isEqualTo(COLOR1_START)
  }

  @RunsInEdt
  @Test
  fun testKeyboardCloseActions() {
    openPopup()
    var balloon = factory.getBalloon(balloonIndex++)
    var ui = balloon.ui!!

    // Escape will close the balloon
    ui.keyboard.pressAndRelease(KeyEvent.VK_ESCAPE)
    assertThat(balloon.isDisposed).isTrue()

    // Reopen
    openPopup()
    balloon = factory.getBalloon(balloonIndex++)
    ui = balloon.ui!!

    // Space will close the balloon
    ui.keyboard.pressAndRelease(KeyEvent.VK_SPACE)
    assertThat(balloon.isDisposed).isTrue()

    // Reopen
    openPopup()
    balloon = factory.getBalloon(balloonIndex++)
    ui = balloon.ui!!

    // Enter will close the balloon
    ui.keyboard.pressAndRelease(KeyEvent.VK_ENTER)
    assertThat(balloon.isDisposed).isTrue()
  }

  private fun openPopup() {
    HighlightColorAction.update(event)
    HighlightColorAction.actionPerformed(event)
    // Allow events to propagate.
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  private fun FakeUi.mouseClickOn(component: Component) {
    val location: Point = getPosition(component)
    mouse.click(location.x, location.y)
    // Allow events to propagate.
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  private fun createEvent(): AnActionEvent {
    val component = JPanel()
    component.setBounds(0, 0, 500, 1000)
    FakeUi(component, createFakeWindow = true)

    val popupStatus = PopupStatus()
    val inspector: LayoutInspector = mock()
    val client: AppInspectionInspectorClient = mock()
    `when`(inspector.treeSettings).thenReturn(treeSettings)
    `when`(inspector.currentClient).thenReturn(client)
    doAnswer { capabilities }.`when`(client).capabilities
    doAnswer { isConnected }.`when`(client).isConnected

    val dataContext = object : DataContext {
      override fun getData(dataId: String): Any? {
        return null
      }

      override fun <T> getData(key: DataKey<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return when (key) {
          DEVICE_VIEW_SETTINGS_KEY -> viewSettings as T
          LAYOUT_INSPECTOR_DATA_KEY -> inspector as T
          DEVICE_VIEW_POPUP_STATUS -> popupStatus as T
          else -> null
        }
      }
    }
    val actionManager: ActionManager = mock()
    val inputEvent = mock<InputEvent>()
    `when`(inputEvent.component).thenReturn(component)
    return AnActionEvent(inputEvent, dataContext, ActionPlaces.UNKNOWN, Presentation(), actionManager, 0)
  }
}

class FakeDeviceViewSettings: DeviceViewSettings {
  override val modificationListeners = mutableListOf<() -> Unit>()
  override var scalePercent = 100
  override var drawBorders = true
  override var drawUntransformedBounds = false
  override var drawLabel = true
  override var drawFold = false
  override var highlightColor = 0xFF0000
}
