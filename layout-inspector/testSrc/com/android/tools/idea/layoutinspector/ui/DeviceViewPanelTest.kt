/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.adtui.swing.FakeKeyboard
import com.android.tools.adtui.swing.FakeMouse.Button
import com.android.tools.adtui.swing.FakeMouse.Button.LEFT
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorTransportRule
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.transport.DefaultInspectorClient
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import junit.framework.TestCase
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Image
import java.awt.Point
import javax.swing.JCheckBox
import javax.swing.JViewport

@RunsInEdt
class DeviceViewPanelTest {

  @get:Rule
  val disposableRule = DisposableRule()

  @get:Rule
  val edtRule = EdtRule()

  @get:Rule
  val inspectorRule = LayoutInspectorTransportRule().withDefaultDevice(connected = true)

  @Before
  fun setUp() {
    InspectorClient.clientFactory = { mock(InspectorClient::class.java) }
  }

  @After
  fun tearDown() {
    InspectorClient.clientFactory = { DefaultInspectorClient(it) }
  }

  @Test
  fun testFocusableActionButtons() {
    val model = model { view(1, 0, 0, 1200, 1600, "RelativeLayout") }
    val inspector = LayoutInspector(model)
    val settings = DeviceViewSettings()
    val toolbar = getToolbar(DeviceViewPanel(inspector, settings, disposableRule.disposable))
    toolbar.components.forEach { assertThat(it.isFocusable).isTrue() }
  }

  @Test
  fun testLiveControlEnabled() {
    val settings = DeviceViewSettings()
    val toolbar = getToolbar(DeviceViewPanel(inspectorRule.inspector, settings, disposableRule.disposable))
    val checkbox = toolbar.components.find { it is JCheckBox && it.text == "Live updates" } as JCheckBox
    assertThat(checkbox.isEnabled).isTrue()
    assertThat(checkbox.toolTipText).isNull()
  }

  @Test
  fun testDragWithSpace() {
    testPan({ ui, _ -> ui.keyboard.press(FakeKeyboard.Key.SPACE) },
            { ui, _ -> ui.keyboard.release(FakeKeyboard.Key.SPACE) })
  }

  @Test
  fun testDragInPanMode() {
    testPan({ _, panel -> panel.isPanning = true },
            { _, panel -> panel.isPanning = false })
  }

  @Test
  fun testDragWithMiddleButton() {
    testPan({ _, _ -> }, { _, _ -> }, Button.MIDDLE)
  }

  private fun testPan(startPan: (FakeUi, DeviceViewPanel) -> Unit, endPan: (FakeUi, DeviceViewPanel) -> Unit, panButton: Button = LEFT) {
    val model = model {
      view(ROOT, 0, 0, 100, 200) {
        view(VIEW1, 25, 30, 50, 50, imageBottom = mock(Image::class.java))
      }
    }

    val panel = DeviceViewPanel(LayoutInspector(model), DeviceViewSettings(scalePercent = 100), disposableRule.disposable)
    val contentPanel = flatten(panel).filterIsInstance<DeviceViewContentPanel>().first()
    val viewport = flatten(panel).filterIsInstance<JViewport>().first()

    contentPanel.setSize(200, 300)
    viewport.extentSize = Dimension(100, 100)

    assertThat(viewport.viewPosition).isEqualTo(Point(0, 0))

    val fakeUi = FakeUi(contentPanel)
    fakeUi.keyboard.setFocus(contentPanel)

    startPan(fakeUi, panel)
    fakeUi.mouse.drag(20, 20, -10, -10, panButton)
    TestCase.assertEquals(0.0, contentPanel.model.xOff)
    TestCase.assertEquals(0.0, contentPanel.model.yOff)
    assertThat(viewport.viewPosition).isEqualTo(Point(10, 10))

    endPan(fakeUi, panel)
    fakeUi.mouse.drag(20, 20, -10, -10)
    TestCase.assertEquals(-0.01, contentPanel.model.xOff)
    TestCase.assertEquals(-0.01, contentPanel.model.yOff)

  }
}

@RunsInEdt
class DeviceViewPanelLegacyTest {
  @get:Rule
  val disposableRule = DisposableRule()

  @get:Rule
  val edtRule = EdtRule()

  @get:Rule
  val inspectorRule = LayoutInspectorTransportRule().withLegacyClient().withDefaultDevice(connected = true)

  @Test
  fun testLiveControlDisabled() {
    val settings = DeviceViewSettings()
    val toolbar = getToolbar(DeviceViewPanel(inspectorRule.inspector, settings, disposableRule.disposable))
    val checkbox = toolbar.components.find { it is JCheckBox && it.text == "Live updates" } as JCheckBox
    assertThat(checkbox.isEnabled).isFalse()
    assertThat(checkbox.toolTipText).isEqualTo("Live updates not available for devices below API 29")
  }
}

private fun getToolbar(panel: DeviceViewPanel) = flatten(panel).find { it.name == DEVICE_VIEW_ACTION_TOOLBAR_NAME } as Container

private fun flatten(component: Component): List<Component> {
  if (component !is Container) {
    return listOf(component)
  }
  return component.components.flatMap { flatten(it) }.plus(component)
}
