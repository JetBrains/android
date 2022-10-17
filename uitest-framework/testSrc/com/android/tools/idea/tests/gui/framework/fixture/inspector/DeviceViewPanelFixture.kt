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
package com.android.tools.idea.tests.gui.framework.fixture.inspector

import com.android.tools.adtui.actions.PanSurfaceAction
import com.android.tools.adtui.actions.ZoomInAction
import com.android.tools.adtui.actions.ZoomOutAction
import com.android.tools.adtui.actions.ZoomToFitAction
import com.android.tools.idea.layoutinspector.ui.DeviceViewContentPanel
import com.android.tools.idea.layoutinspector.ui.DeviceViewPanel
import com.android.tools.idea.layoutinspector.ui.Toggle3dAction
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture
import com.android.tools.idea.tests.gui.framework.fixture.JComponentFixture
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import com.intellij.util.ui.JBUI
import org.fest.swing.core.MouseButton
import org.fest.swing.core.Robot
import org.fest.swing.edt.GuiQuery
import org.fest.swing.timing.Wait
import java.awt.Container
import java.awt.Point
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.sqrt

private const val MARGIN = 50

/**
 * Fixture for the device view panel in the dynamic layout inspector.
 *
 * This panel contains: the DeviceViewContentPanel and the floating menu with buttons for zoom and 3D control.
 */
class DeviceViewPanelFixture(
  deviceViewPanel: DeviceViewPanel,
  robot: Robot
) : JComponentFixture<DeviceViewPanelFixture, DeviceViewPanel>(DeviceViewPanelFixture::class.java, robot, deviceViewPanel) {

  val mode3DActionButton: ActionButtonFixture
    get() = ActionButtonFixture.findByActionInstance(
      Toggle3dAction, robot(), target())

  val zoomInButton: ActionButtonFixture
    get() = ActionButtonFixture.findByActionClass(
      ZoomInAction::class.java, robot(), target())

  val zoomOutButton: ActionButtonFixture
    get() = ActionButtonFixture.findByActionClass(
      ZoomOutAction::class.java, robot(), target())

  val zoomToFitButton: ActionButtonFixture
    get() = ActionButtonFixture.findByActionClass(
      ZoomToFitAction::class.java, robot(), target())

  val panButton: ActionButtonFixture
    get() = ActionButtonFixture.findByActionInstance(
      PanSurfaceAction, robot(), target())

  /**
   * Click on the inspector image at the specified view coordinate.
   */
  fun clickOnImage(x: Int, y: Int) {
    val where = convertToSwingCoordinates(Point(x, y))
    robot().click(contentPanel, where, MouseButton.LEFT_BUTTON, 1)
  }

  /**
   * Click on the inspector image at the specified view coordinate and drag.
   */
  fun clickAndDrag(x: Int, y: Int, deltaX: Int, deltaY: Int, button: MouseButton = MouseButton.LEFT_BUTTON) {
    val start = convertToSwingCoordinates(Point(x, y))
    val end = convertToSwingCoordinates(Point(x + deltaX, y + deltaY))
    val startInViewport = SwingUtilities.convertPoint(contentPanel, start, scrollPane.viewport)
    if (!scrollPane.viewport.contains(startInViewport)) {
      error("This click $startInViewport is outside the bounds of the viewport: ${scrollPane.viewport.bounds}")
    }
    robot().pressMouse(contentPanel, start, button)
    robot().moveMouse(contentPanel, end)
    robot().releaseMouse(button)
  }

  private val scale: Double
    get() {
      val panel = target()
      return GuiQuery.get { panel.scale }!!
    }

  val angleAfterLastPaint: Double
    get() {
      val model = contentPanel.model
      val (xOff, yOff) = GuiQuery.get { Pair(model.xOff, model.yOff) }!!
      return Math.toDegrees(sqrt(xOff * xOff + yOff * yOff))
    }

  var layerSpacing: Int
    get() {
      val model = contentPanel.model
      return GuiQuery.get { model.layerSpacing }!!
    }
    set(value) {
      val model = contentPanel.model
      GuiQuery.get { model.layerSpacing = value }!!
    }

  /**
   * The top,left position of the DeviceViewPanel in view coordinates.
   */
  val viewPosition: Point
    get() = convertToViewCoordinates(computeViewPosition(scrollPane))

  /**
   * The top,left position of the DeviceViewPanel in view coordinates.
   */
  val viewEndPosition: Point
    get() = convertToViewCoordinates(computeViewEndPosition(scrollPane))

  fun waitUntilExpectedViewPosition(expected: Point) {
    Wait.seconds(10)
      .expecting("Expected view pos: $expected actual: $viewPosition")
      .until {
        val pos = viewPosition
        abs(pos.x - expected.x) < 2 && abs(pos.y - expected.y) < 2
      }
  }

  fun waitUntilExpectedViewportHeight(expected: Int, tolerance: Int) {
    Wait.seconds(10)
      .expecting("Expected viewport height: $expected actual: ${viewEndPosition.y - viewPosition.y}")
      .until {
        val height = viewEndPosition.y - viewPosition.y
        abs(height - expected) < tolerance
      }
  }

  private val scrollPane: JScrollPane by lazy(LazyThreadSafetyMode.NONE) {
    GuiTests.waitUntilFound(robot, target(), Matchers.byType(JScrollPane::class.java))
  }

  val contentPanel: DeviceViewContentPanel by lazy(LazyThreadSafetyMode.NONE) {
    GuiTests.waitUntilFound(robot, target(), Matchers.byType(DeviceViewContentPanel::class.java))
  }

  private fun computeViewPosition(scrollPane: JScrollPane): Point {
    return GuiQuery.get { scrollPane.viewport.viewPosition }!!
  }

  private fun computeViewEndPosition(scrollPane: JScrollPane): Point {
    return GuiQuery.get { scrollPane.viewport.viewPosition.apply { translate(scrollPane.viewport.width, scrollPane.viewport.height) } }!!
  }

  private fun convertToViewCoordinates(pos: Point): Point {
    val scaleFraction = scale
    val scrollPane = scrollPane
    val contentPanel = contentPanel
    val size = GuiQuery.get { scrollPane.viewport.view.size }!!
    size.width -= JBUI.scale(MARGIN)
    size.height -= JBUI.scale(MARGIN)
    val rootBounds = GuiQuery.get { contentPanel.inspectorModel.root.renderBounds.bounds }!!
    return Point(((pos.x - size.getWidth() / 2.0) / scaleFraction + rootBounds.getWidth() / 2.0).toInt(),
                 ((pos.y - size.getHeight() / 2.0) / scaleFraction + rootBounds.getHeight() / 2.0).toInt())
  }

  private fun convertToSwingCoordinates(pos: Point): Point {
    val scaleFraction = scale
    val scrollPane = scrollPane
    val contentPanel = contentPanel
    val size = GuiQuery.get { scrollPane.viewport.view.size }!!
    val rootBounds = GuiQuery.get { contentPanel.inspectorModel.root.renderBounds.bounds }!!
    return Point(((pos.x - rootBounds.width / 2) * scaleFraction + size.width.toDouble() / 2.0).toInt(),
                 ((pos.y - rootBounds.height / 2) * scaleFraction + size.height.toDouble() / 2.0).toInt())
  }

  companion object {

    /**
     * Use this method to create this fixture.
     *
     * Since the properties panel is used several places a [Container] must be supplied.
     */
    fun findDeviceViewPanelInContainer(container: Container, robot: Robot): DeviceViewPanelFixture {
      val panel = GuiTests.waitUntilFound<DeviceViewPanel>(robot, container, Matchers.byType(DeviceViewPanel::class.java))
      return DeviceViewPanelFixture(panel, robot)
    }
  }
}
