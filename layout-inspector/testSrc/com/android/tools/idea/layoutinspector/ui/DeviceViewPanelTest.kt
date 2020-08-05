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

import com.android.testutils.MockitoKt.mock
import com.android.testutils.PropertySetterRule
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.swing.FakeKeyboard
import com.android.tools.adtui.swing.FakeMouse.Button
import com.android.tools.adtui.swing.FakeMouse.Button.LEFT
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.layoutinspector.DEFAULT_DEVICE
import com.android.tools.idea.layoutinspector.DEFAULT_PROCESS
import com.android.tools.idea.layoutinspector.DEFAULT_STREAM
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorTransportRule
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.REBOOT_FOR_LIVE_INSPECTOR_MESSAGE_KEY
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.VIEW2
import com.android.tools.idea.layoutinspector.transport.DefaultInspectorClient
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.idea.layoutinspector.util.ComponentUtil.flatten
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.JBScrollPane
import junit.framework.TestCase
import org.jetbrains.android.util.AndroidBundle
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Container
import java.awt.Dimension
import java.awt.Point
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JViewport

@RunsInEdt
class DeviceViewPanelWithFullInspectorTest {

  @get:Rule
  val disposableRule = DisposableRule()

  @get:Rule
  val edtRule = EdtRule()

  @get:Rule
  val inspectorRule = LayoutInspectorTransportRule().withDefaultDevice().attach()

  @Test
  fun testLiveControlEnabled() {
    val settings = DeviceViewSettings()
    val toolbar = getToolbar(DeviceViewPanel(inspectorRule.inspector, settings, disposableRule.disposable))
    val checkbox = toolbar.components.find { it is JCheckBox && it.text == "Live updates" } as JCheckBox
    assertThat(checkbox.isEnabled).isTrue()
    assertThat(checkbox.toolTipText).isNull()
  }
}

@RunsInEdt
class DeviceViewPanelTest {

  @get:Rule
  val disposableRule = DisposableRule()

  @get:Rule
  val edtRule = EdtRule()

  @get:Rule
  val projectRule = ProjectRule()

  @get:Rule
  val clientFactoryRule = PropertySetterRule(
    { _, _ -> listOf(mock<DefaultInspectorClient>()) },
    InspectorClient.Companion::clientFactory)

  @Test
  fun testZoomOnConnect() {
    val viewSettings = DeviceViewSettings(scalePercent = 100)
    val model = InspectorModel(projectRule.project)
    val panel = DeviceViewPanel(LayoutInspector(model, disposableRule.disposable), viewSettings, disposableRule.disposable)

    val scrollPane = flatten(panel).filterIsInstance<JBScrollPane>().first()
    scrollPane.setSize(200, 300)

    assertThat(viewSettings.scalePercent).isEqualTo(100)

    val newModel = model {
      view(ROOT, 0, 0, 100, 200) {
        view(VIEW1, 25, 30, 50, 50) {
          image()
        }
      }
    }

    model.update(newModel.root, ROOT, listOf(ROOT))

    // now we should be zoomed to fit
    assertThat(viewSettings.scalePercent).isEqualTo(135)

    viewSettings.scalePercent = 200

    // Update the model
    val newModel2 = model {
      view(ROOT, 0, 0, 100, 200) {
        view(VIEW2, 50, 20, 30, 40) {
          image()
        }
      }
    }
    model.update(newModel2.root, ROOT, listOf(ROOT))

    // Should still have the manually set zoom
    assertThat(viewSettings.scalePercent).isEqualTo(200)
  }

  @Test
  fun testFocusableActionButtons() {
    val model = model { view(1, 0, 0, 1200, 1600, qualifiedName = "RelativeLayout") }
    val inspector = LayoutInspector(model, disposableRule.disposable)
    val settings = DeviceViewSettings()
    val toolbar = getToolbar(DeviceViewPanel(inspector, settings, disposableRule.disposable))
    toolbar.components.forEach { assertThat(it.isFocusable).isTrue() }
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
        view(VIEW1, 25, 30, 50, 50) {
          image()
        }
      }
    }

    val panel = DeviceViewPanel(LayoutInspector(model, disposableRule.disposable),
                                DeviceViewSettings(scalePercent = 100), disposableRule.disposable)
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
  val inspectorRule = LayoutInspectorTransportRule().withLegacyClient().withDefaultDevice().attach()

  @Test
  fun testLiveControlDisabled() {
    val settings = DeviceViewSettings()
    val toolbar = getToolbar(DeviceViewPanel(inspectorRule.inspector, settings, disposableRule.disposable))
    val checkbox = toolbar.components.find { it is JCheckBox && it.text == "Live updates" } as JCheckBox
    assertThat(checkbox.isEnabled).isFalse()
    assertThat(checkbox.toolTipText).isEqualTo("Live updates not available for devices below API 29")
  }
}

@RunsInEdt
class DeviceViewPanelLegacyWithApi29DeviceTest {
  @get:Rule
  val disposableRule = DisposableRule()

  @get:Rule
  val edtRule = EdtRule()

  @get:Rule
  val inspectorRule = LayoutInspectorTransportRule().withLegacyClient()

  @Test
  fun testLiveControlDisabled() {
    inspectorRule.addProcess(DEFAULT_DEVICE, DEFAULT_PROCESS)
    inspectorRule.attachTo(DEFAULT_STREAM, DEFAULT_PROCESS)
    val settings = DeviceViewSettings()
    val toolbar = getToolbar(DeviceViewPanel(inspectorRule.inspector, settings, disposableRule.disposable))
    val checkbox = toolbar.components.find { it is JCheckBox && it.text == "Live updates" } as JCheckBox
    assertThat(checkbox.isEnabled).isFalse()
    assertThat(checkbox.toolTipText).isEqualTo(AndroidBundle.message(REBOOT_FOR_LIVE_INSPECTOR_MESSAGE_KEY))
  }
}

@RunsInEdt
class MyViewportLayoutManagerTest {
  private lateinit var scrollPane: JScrollPane
  private lateinit var contentPanel: JComponent
  private lateinit var layoutManager: MyViewportLayoutManager

  private var layerSpacing = INITIAL_LAYER_SPACING

  private var rootPosition = Point(400, 500)

  @get:Rule
  val edtRule = EdtRule()

  @Before
  fun setUp() {
    contentPanel = JPanel()
    scrollPane = JBScrollPane(contentPanel)
    scrollPane.size = Dimension(502, 202)
    scrollPane.preferredSize = Dimension(502, 202)
    contentPanel.preferredSize = Dimension(1000, 1000)
    layoutManager = MyViewportLayoutManager(scrollPane.viewport, { layerSpacing }, { rootPosition })
    layoutManager.layoutContainer(scrollPane.viewport)
    scrollPane.layout.layoutContainer(scrollPane)
  }

  @Test
  fun testAdjustLayerSpacing() {
    // Start view as centered
    scrollPane.viewport.viewPosition = Point(250, 400)
    // expand spacing
    layerSpacing = 200
    contentPanel.preferredSize = Dimension(1200, 1200)
    layoutManager.layoutContainer(scrollPane.viewport)
    // Still centered
    assertThat(scrollPane.viewport.viewPosition).isEqualTo(Point(350, 500))

    // offset view (-100, -50) from center
    scrollPane.viewport.viewPosition = Point(250, 450)
    // put spacing and size back
    layerSpacing = INITIAL_LAYER_SPACING
    contentPanel.preferredSize = Dimension(1000, 1000)
    layoutManager.layoutContainer(scrollPane.viewport)

    // view still offset (-100, -50) from center
    assertThat(scrollPane.viewport.viewPosition).isEqualTo(Point(150, 350))
  }

  @Test
  fun testZoomToFit() {
    assertThat(scrollPane.viewport.viewPosition).isEqualTo(Point(0, 0))
    layoutManager.currentZoomOperation = ZoomType.FIT
    layoutManager.layoutContainer(scrollPane.viewport)
    // view is centered after fit
    assertThat(scrollPane.viewport.viewPosition).isEqualTo(Point(250, 400))
  }

  @Test
  fun testZoom() {
    // Start view as centered
    scrollPane.viewport.viewPosition = Point(250, 400)
    // zoom in
    layoutManager.currentZoomOperation = ZoomType.IN
    contentPanel.preferredSize = Dimension(1200, 1200)

    layoutManager.layoutContainer(scrollPane.viewport)
    // Still centered
    assertThat(scrollPane.viewport.viewPosition).isEqualTo(Point(350, 500))

    // offset view (-100, -50) from center
    scrollPane.viewport.viewPosition = Point(250, 450)
    // zoom out
    layoutManager.currentZoomOperation = ZoomType.OUT
    contentPanel.preferredSize = Dimension(1000, 1000)

    layoutManager.layoutContainer(scrollPane.viewport)

    // view proportionally offset from center
    assertThat(scrollPane.viewport.viewPosition).isEqualTo(Point(166, 358))
  }

  @Test
  fun testChangeSize() {
    // Start view as centered
    scrollPane.viewport.viewPosition = Point(250, 400)
    layoutManager.layoutContainer(scrollPane.viewport)

    // view grows
    contentPanel.preferredSize = Dimension(1200, 1200)
    layoutManager.layoutContainer(scrollPane.viewport)

    // view should still be in the same place
    assertThat(scrollPane.viewport.viewPosition).isEqualTo(Point(250, 400))

    // view grows, root location moves
    contentPanel.preferredSize = Dimension(1300, 1300)
    rootPosition = Point(500, 600)
    layoutManager.layoutContainer(scrollPane.viewport)

    // scroll changes to keep view in the same place
    assertThat(scrollPane.viewport.viewPosition).isEqualTo(Point(350, 500))
  }
}

private fun getToolbar(panel: DeviceViewPanel) = flatten(panel).find { it.name == DEVICE_VIEW_ACTION_TOOLBAR_NAME } as Container
