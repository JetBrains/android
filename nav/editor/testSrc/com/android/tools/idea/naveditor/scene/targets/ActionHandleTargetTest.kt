/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.naveditor.scene.targets

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.LayoutTestUtilities
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.Scene
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.InteractionManager
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.analytics.TestNavUsageTracker
import com.android.tools.idea.naveditor.model.actionDestination
import com.android.tools.idea.naveditor.model.isAction
import com.google.wireless.android.sdk.stats.NavActionInfo
import com.google.wireless.android.sdk.stats.NavEditorEvent
import org.mockito.Mockito
import org.mockito.Mockito.verifyNoMoreInteractions
import java.awt.Point
import java.awt.event.MouseEvent

class ActionHandleTargetTest : NavTestCase() {
  private lateinit var surface: DesignSurface<*>
  private lateinit var interactionManager: InteractionManager
  private lateinit var scene: Scene
  private lateinit var view: SceneView

  private fun setModel(model: SyncNlModel) {
    surface = model.surface
    interactionManager = model.surface.interactionManager
    scene = model.surface.scene!!
    view = scene.sceneManager.sceneViews.first()

    scene.layout(0, view.context)
    interactionManager.startListening()
  }

  override fun tearDown() {
    interactionManager.stopListening()
    super.tearDown()
  }

  fun testCreateAction() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
        fragment("fragment2")
        fragment("fragment3")
      }
    }
    setModel(model)

    TestNavUsageTracker.create(model).use { tracker ->
      dragCreate("fragment1", "fragment2")

      val action = model.find("fragment1")!!.children.first { it.isAction }
      assertEquals(model.find("fragment2")!!, action.actionDestination)
      assertEquals("action_fragment1_to_fragment2", action.id)
      assertSameElements(surface.selectionModel.selection, action)

      Mockito.verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                         .setType(NavEditorEvent.NavEditorEventType.CREATE_ACTION)
                                         .setActionInfo(NavActionInfo.newBuilder()
                                                          .setCountFromSource(1)
                                                          .setCountToDestination(1)
                                                          .setCountSame(1)
                                                          .setType(NavActionInfo.ActionType.REGULAR))
                                         .setSource(NavEditorEvent.Source.DESIGN_SURFACE).build())
    }

    TestNavUsageTracker.create(model).use { tracker ->
      dragCreate("fragment1", "fragment3")

      val action = model.find("fragment1")!!.children.first { it.isAction && it.id == "action_fragment1_to_fragment3" }
      assertEquals(model.find("fragment3")!!, action.actionDestination)
      assertSameElements(surface.selectionModel.selection, action)

      Mockito.verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                         .setType(NavEditorEvent.NavEditorEventType.CREATE_ACTION)
                                         .setActionInfo(NavActionInfo.newBuilder()
                                                          .setCountFromSource(2)
                                                          .setCountToDestination(1)
                                                          .setCountSame(1)
                                                          .setType(NavActionInfo.ActionType.REGULAR))
                                         .setSource(NavEditorEvent.Source.DESIGN_SURFACE).build())
    }
  }

  fun testCreateToInclude() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
        include("navigation")
      }
    }

    setModel(model)

    TestNavUsageTracker.create(model).use { tracker ->
      dragCreate("fragment1", "nav")

      val action = model.find("fragment1")!!.children.first { it.isAction }
      assertEquals(model.find("nav")!!, action.actionDestination)
      assertEquals("action_fragment1_to_nav", action.id)
      assertSameElements(surface.selectionModel.selection, action)

      Mockito.verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                         .setType(NavEditorEvent.NavEditorEventType.CREATE_ACTION)
                                         .setActionInfo(NavActionInfo.newBuilder()
                                                          .setCountFromSource(1)
                                                          .setCountToDestination(1)
                                                          .setCountSame(1)
                                                          .setType(NavActionInfo.ActionType.REGULAR))
                                         .setSource(NavEditorEvent.Source.DESIGN_SURFACE).build())
    }
  }

  fun testCreateSelfAction() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
      }
    }

    setModel(model)

    TestNavUsageTracker.create(model).use { tracker ->
      dragCreate("fragment1", "fragment1")

      val action = model.find("fragment1")!!.children.first { it.isAction }
      assertEquals(model.find("fragment1")!!, action.actionDestination)
      assertEquals("action_fragment1_self", action.id)
      assertSameElements(surface.selectionModel.selection, action)

      Mockito.verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                         .setType(NavEditorEvent.NavEditorEventType.CREATE_ACTION)
                                         .setActionInfo(NavActionInfo.newBuilder()
                                                          .setCountFromSource(1)
                                                          .setCountToDestination(1)
                                                          .setCountSame(1)
                                                          .setType(NavActionInfo.ActionType.SELF))
                                         .setSource(NavEditorEvent.Source.DESIGN_SURFACE).build())
    }
  }

  fun testDragAbandon() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
      }
    }

    setModel(model)

    TestNavUsageTracker.create(model).use { tracker ->
      val sourceComponent = scene.getSceneComponent("fragment1")!!
      val p1 = sourceComponent.handlePoint()

      // drag release to a point over the root and verify no action is created
      dragAndRelease(p1.x, p1.y, p1.x + 50, p1.y)
      assertNull(model.find { it.isAction })
      verifyNoMoreInteractions(tracker)
    }
  }

  fun testDragInvalid() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
        addChild(ComponentDescriptor("fragment"), null)
      }
    }

    setModel(model)

    TestNavUsageTracker.create(model).use { tracker ->
      val sourceComponent = scene.getSceneComponent("fragment1")!!
      val p1 = sourceComponent.handlePoint()

      val destinationComponent = scene.getSceneComponent(model.find { it.id == null })!!
      val p2 = destinationComponent.centerPoint()

      dragAndRelease(p1.x, p1.y, p2.x, p2.y)
      assertNull(model.find { it.isAction })
      verifyNoMoreInteractions(tracker)
    }
  }

  private fun dragCreate(sourceId: String, destinationId: String) {
    val sourceComponent = scene.getSceneComponent(sourceId)!!
    val p1 = sourceComponent.handlePoint()

    val destinationComponent = scene.getSceneComponent(destinationId)!!
    val p2 = destinationComponent.centerPoint()

    dragAndRelease(p1.x, p1.y, p2.x, p2.y)
  }

  private fun dragAndRelease(@SwingCoordinate x1: Int, @SwingCoordinate y1: Int,
                             @SwingCoordinate x2: Int, @SwingCoordinate y2: Int) {
    LayoutTestUtilities.pressMouse(interactionManager, MouseEvent.BUTTON1, x1, y1, 0)
    LayoutTestUtilities.dragMouse(interactionManager, x1, y1, x2, y2, 0)
    LayoutTestUtilities.releaseMouse(interactionManager, MouseEvent.BUTTON1, x2, y2, 0)
  }

  @SwingCoordinate
  private fun SceneComponent.handlePoint(): Point {
    val rectangle = this.fillRect(null)
    @SwingCoordinate val x = Coordinates.getSwingXDip(view, rectangle.x + rectangle.width)
    @SwingCoordinate val y = Coordinates.getSwingXDip(view, rectangle.centerY.toInt())
    return Point(x, y)
  }

  @SwingCoordinate
  private fun SceneComponent.centerPoint(): Point {
    val rectangle = this.fillRect(null)
    @SwingCoordinate val x = Coordinates.getSwingXDip(view, rectangle.centerX.toInt())
    @SwingCoordinate val y = Coordinates.getSwingXDip(view, rectangle.centerY.toInt())
    return Point(x, y)
  }
}