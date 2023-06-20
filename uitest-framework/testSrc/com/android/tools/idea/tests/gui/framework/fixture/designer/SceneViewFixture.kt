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
package com.android.tools.idea.tests.gui.framework.fixture.designer

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.AndroidDpCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.Scene
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.common.surface.SceneViewPeerPanel
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.util.IconLoader
import com.intellij.util.ui.JBUI
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.MouseButton
import org.fest.swing.core.Robot
import org.fest.swing.driver.ComponentDriver
import org.fest.swing.fixture.JMenuItemFixture
import org.fest.swing.fixture.JPopupMenuFixture
import org.fest.swing.timing.Wait
import java.awt.Dimension
import java.awt.Point
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JMenuItem

private const val TIMEOUT_FOR_SCENE_COMPONENT_ANIMATION_SECONDS = 5L
private val MINIMUM_ANCHOR_GAP = JBUI.scale(6) * 2 // Based on DrawAnchor.java

private fun SceneView.convertToViewport(@SwingCoordinate x: Int, @SwingCoordinate y: Int): Point =
  scene.designSurface.getCoordinatesOnViewport(Point(x, y))

class SceneComponentFixture internal constructor(
  private val robot: Robot,
  private val componentDriver: ComponentDriver<DesignSurface<*>>,
  val sceneComponent: SceneComponent) {

  val parent: SceneComponentFixture?
    get() {
      return SceneComponentFixture(robot, componentDriver, sceneComponent.parent ?: return null)
    }

  val width: Int
    @AndroidDpCoordinate get() = sceneComponent.drawWidth

  val height: Int
    @AndroidDpCoordinate get() = sceneComponent.drawHeight

  val sceneView: SceneView
    get() = sceneComponent.scene.sceneManager.sceneView

  val midPoint: Point
    get() =
      sceneView.convertToViewport(Coordinates.getSwingXDip(sceneView, sceneComponent.centerX),
                                  Coordinates.getSwingYDip(sceneView, sceneComponent.centerY))

  /**
   * Returns the top center point in panel coordinates
   */
  val topCenterPoint: Point
    get() {
      val midX = Coordinates.getSwingXDip(sceneView, sceneComponent.centerX)
      val yDiff = sceneComponent.centerY - sceneComponent.drawY
      var topY = Coordinates.getSwingYDip(sceneView, sceneComponent.drawY)
      if (yDiff < MINIMUM_ANCHOR_GAP) {
        topY = Coordinates.getSwingYDip(sceneView,
                                        sceneComponent.centerY - MINIMUM_ANCHOR_GAP)
      }
      return sceneView.convertToViewport(midX, topY)
    }

  /**
   * Returns the right center point in panel coordinates
   */
  val rightCenterPoint: Point
    get() {
      var rightX = Coordinates.getSwingXDip(sceneView,
                                            sceneComponent.drawX + sceneComponent.drawWidth)
      val xDiff = sceneComponent.drawX + sceneComponent.drawWidth - sceneComponent.centerX
      val midY = Coordinates.getSwingYDip(sceneView, sceneComponent.centerY)
      if (xDiff < MINIMUM_ANCHOR_GAP) {
        rightX = Coordinates.getSwingXDip(sceneView,
                                          sceneComponent.centerX + MINIMUM_ANCHOR_GAP)
      }
      return sceneView.convertToViewport(rightX, midY)
    }

  /**
   * Returns the left center point in panel coordinates
   */
  val leftCenterPoint: Point
    get() {
      var leftX = Coordinates.getSwingXDip(sceneView, sceneComponent.drawX)
      val xDiff = sceneComponent.centerX - sceneComponent.drawX
      val midY = Coordinates.getSwingYDip(sceneView, sceneComponent.centerY)
      if (xDiff < MINIMUM_ANCHOR_GAP) {
        leftX = Coordinates.getSwingXDip(sceneView,
                                         sceneComponent.centerX - MINIMUM_ANCHOR_GAP)
      }
      return sceneView.convertToViewport(leftX, midY)
    }

  /**
   * Returns the right bottom point in panel coordinates
   */
  val rightBottomPoint: Point
    get() =
      sceneView.convertToViewport(Coordinates.getSwingXDip(sceneView, sceneComponent.drawX + sceneComponent.drawWidth),
                                  Coordinates.getSwingYDip(sceneView, sceneComponent.drawY + sceneComponent.drawHeight))

  /**
   * Returns the bottom center point in panel coordinates
   */
  val bottomCenterPoint: Point
  get() {
    val midX = Coordinates.getSwingXDip(sceneView, sceneComponent.centerX)
    val yDiff = sceneComponent.drawY + sceneComponent.drawHeight - sceneComponent.centerY
    var bottomY = Coordinates.getSwingYDip(sceneView,
                                           sceneComponent.drawY + sceneComponent.drawHeight)
    if (yDiff < MINIMUM_ANCHOR_GAP) {
      bottomY = Coordinates.getSwingYDip(sceneView,
                                         sceneComponent.centerY + MINIMUM_ANCHOR_GAP)
    }
    return sceneView.convertToViewport(midX, bottomY)
  }

  val children: List<SceneComponentFixture>
    get() = sceneComponent.children.map { SceneComponentFixture(robot, componentDriver, it) }.toList()

  fun click(): SceneComponentFixture {
    componentDriver.click(sceneComponent.scene.designSurface, midPoint)
    return this
  }

  fun doubleClick(): SceneComponentFixture {
    robot.click(sceneComponent.scene.designSurface, midPoint, MouseButton.LEFT_BUTTON, 2)
    return this
  }

  fun rightClick(): SceneComponentFixture {
    // Can't use ComponentDriver -- need to both set button and where
    robot.click(sceneComponent.scene.designSurface, midPoint, MouseButton.RIGHT_BUTTON, 1)
    return this
  }

  fun openComponentAssistant(): ComponentAssistantFixture {
    click()
    rightClick()
    val popupMenuFixture = JPopupMenuFixture(robot, robot.findActivePopupMenu()!!)
    popupMenuFixture.menuItem(object : GenericTypeMatcher<JMenuItem>(JMenuItem::class.java) {
      override fun isMatching(component: JMenuItem): Boolean {
        return "Set Sample Data" == component.text
      }
    }).click()
    return ComponentAssistantFixture(robot, GuiTests.waitUntilFound(robot, null,
                                                                    Matchers.byName(
                                                                      JComponent::class.java, "Component Assistant")))
  }

  fun invokeContextMenuAction(actionLabel: String): JMenuItemFixture {
    rightClick()
    return JMenuItemFixture(robot, GuiTests.waitUntilShowing(robot,
                                                             Matchers.byText(
                                                               JMenuItem::class.java, actionLabel))).click()
  }

  fun waitForSceneComponentAnimation(): SceneComponentFixture {
    Wait.seconds(TIMEOUT_FOR_SCENE_COMPONENT_ANIMATION_SECONDS)
      .expecting("Expect SceneComponent Animation Finish")
      .until { !sceneComponent.isAnimating }

    return this
  }

  fun waitForConnectToPopup(): JPopupMenuFixture {
    val menu = GuiTests.waitUntilFound(robot, null,
                                       object : GenericTypeMatcher<JBPopupMenu>(
                                         JBPopupMenu::class.java) {
                                         override fun isMatching(menu: JBPopupMenu): Boolean {
                                           return "Connect to:" == menu.label
                                         }
                                       })
    return JPopupMenuFixture(robot, menu)
  }
}

class SceneFixture(private val robot: Robot, private val scene: Scene) {
  private val componentDriver = ComponentDriver<DesignSurface<*>>(robot)

  val sceneComponents: List<SceneComponentFixture>
    get() = scene.sceneComponents
      .map { SceneComponentFixture(robot, componentDriver, it) }
      .toList()

  fun findSceneComponentById(id: String): SceneComponentFixture? {
    return SceneComponentFixture(robot, componentDriver, scene.getSceneComponent(id) ?: return null)
  }

  fun findSceneComponentByNlComponent(component: NlComponent): SceneComponentFixture? {
    return SceneComponentFixture(robot, componentDriver, scene.getSceneComponent(component) ?: return null)
  }
}

class SceneViewTopPanelFixture(private val robot: Robot, private val toolbar: JComponent) {
  fun clickButtonByText(text: String): SceneViewTopPanelFixture = also {
    val button = robot.finder().find(toolbar, Matchers.byText(ActionButtonWithText::class.java, text))
    robot.click(button)
  }

  fun findButtonByIcon(icon: Icon, secondsToWait: Long = 10L): ActionButtonFixture {
    val button = GuiTests.waitUntilShowing(
      robot, toolbar, object : GenericTypeMatcher<ActionButton>(ActionButton::class.java) {
      override fun isMatching(component: ActionButton): Boolean {
        return component.icon == icon || IconLoader.getDisabledIcon(icon) == component.icon
      }
    }, secondsToWait)
    return ActionButtonFixture(robot, button)
  }
}

class SceneViewFixture(private val robot: Robot,
                       private val sceneView: SceneView) {
  private val componentDriver = ComponentDriver<DesignSurface<*>>(robot)

  val midPoint: Point
    get() = Point((sceneView.x + sceneView.x + sceneView.scaledContentSize.width) / 2,
                  (sceneView.y + sceneView.y + sceneView.scaledContentSize.height) / 2)

  val topLeft: Point
    get() = Point(sceneView.x, sceneView.y)

  fun findSceneComponentByTagName(tagName: String): SceneComponentFixture =
    SceneComponentFixture(robot, componentDriver, sceneView.scene.sceneComponents.single { tagName == it.nlComponent.tagName })

  fun countSceneComponents(): Int = sceneView.scene.sceneComponents.size

  fun toolbar() = SceneViewTopPanelFixture(robot, target().sceneViewTopPanel)

  fun target() = robot.finder().find(sceneView.surface) {
    component -> component is SceneViewPeerPanel && component.sceneView == sceneView
  } as SceneViewPeerPanel

  fun size(): Dimension = sceneView.scaledContentSize
}