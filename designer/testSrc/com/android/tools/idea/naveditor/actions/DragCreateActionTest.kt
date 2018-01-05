/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.actions

import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.Scene
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.surface.InteractionManager
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.common.util.NlTreeDumper
import com.android.tools.idea.naveditor.NavModelBuilderUtil.*
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.naveditor.surface.NavView
import com.android.tools.idea.uibuilder.LayoutTestUtilities
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.`when`
import java.awt.event.MouseEvent.BUTTON1

class DragCreateActionTest : NavTestCase() {

  fun testDragCreateToSelf() {
    val model = model("nav.xml",
        rootComponent("root").unboundedChildren(
            fragmentComponent(FRAGMENT1))).build()

    val surface = initializeNavDesignSurface(model)
    val scene = initializeScene(surface)
    val interactionManager = initializeInteractionManager(surface)

    val component = scene.getSceneComponent(FRAGMENT1)!!

    dragFromActionHandle(interactionManager, component, component.centerX, component.centerY, surface.currentSceneView)

    val expected = "NlComponent{tag=<navigation>, instance=0}\n" +
        "    NlComponent{tag=<fragment>, instance=1}\n" +
        "        NlComponent{tag=<action>, instance=2}"

    verifyModel(model, expected)
  }

  fun testDragCreateToOtherFragment() {
    val model = model("nav.xml",
        rootComponent("root").unboundedChildren(
            fragmentComponent(FRAGMENT1),
            fragmentComponent(FRAGMENT2).unboundedChildren(
                actionComponent(ACTION).withDestinationAttribute(FRAGMENT1)))).build()

    val surface = initializeNavDesignSurface(model)
    val scene = initializeScene(surface)
    val interactionManager = initializeInteractionManager(surface)

    val component = scene.getSceneComponent(FRAGMENT2)!!
    dragFromActionHandle(interactionManager, scene.getSceneComponent(FRAGMENT1)!!, component.centerX, component.centerY,
        surface.currentSceneView)

    val expected = "NlComponent{tag=<navigation>, instance=0}\n" +
        "    NlComponent{tag=<fragment>, instance=1}\n" +
        "        NlComponent{tag=<action>, instance=2}\n" +
        "    NlComponent{tag=<fragment>, instance=3}\n" +
        "        NlComponent{tag=<action>, instance=4}"

    verifyModel(model, expected)
  }

  fun testDragCreateToInclude() {
    val model = model("nav.xml",
        rootComponent("root").unboundedChildren(
            fragmentComponent(FRAGMENT1),
            includeComponent("navigation"))).build()

    val surface = initializeNavDesignSurface(model)
    val scene = initializeScene(surface)
    val interactionManager = initializeInteractionManager(surface)

    val component = scene.getSceneComponent("nav")!!
    dragFromActionHandle(interactionManager, scene.getSceneComponent(FRAGMENT1)!!, component.centerX, component.centerY,
        surface.currentSceneView)

    val expected = "NlComponent{tag=<navigation>, instance=0}\n" +
        "    NlComponent{tag=<fragment>, instance=1}\n" +
        "        NlComponent{tag=<action>, instance=2}\n" +
        "    NlComponent{tag=<include>, instance=3}"

    verifyModel(model, expected)
  }

  fun testDragAbandon() {
    val model = model("nav.xml",
        rootComponent("root").unboundedChildren(
            fragmentComponent(FRAGMENT1))).build()

    val surface = initializeNavDesignSurface(model)
    val scene = initializeScene(surface)
    val interactionManager = initializeInteractionManager(surface)

    val root = scene.root!!
    val rootRect = root.fillRect(null)

    val component = scene.getSceneComponent(FRAGMENT1)
    val componentRect = component!!.fillRect(null)

    // make sure the top of the component is lower than the top of the root
    assertTrue(rootRect.y < componentRect.y)

    // drag release to a point over the root and verify no action is created
    dragFromActionHandle(interactionManager, component, component.centerX, (rootRect.y + componentRect.y) / 2, surface.currentSceneView)

    val expected = "NlComponent{tag=<navigation>, instance=0}\n" + "    NlComponent{tag=<fragment>, instance=1}"

    verifyModel(model, expected)
  }

  companion object {
    private val FRAGMENT1 = "fragment1"
    private val FRAGMENT2 = "fragment2"
    private val ACTION = "action1"

    private fun initializeNavDesignSurface(model: SyncNlModel): NavDesignSurface {
      val surface = model.surface as NavDesignSurface
      val view = NavView(surface, surface.sceneManager!!)
      `when`<SceneView>(surface.currentSceneView).thenReturn(view)
      `when`<SceneView>(surface.getSceneView(anyInt(), anyInt())).thenReturn(view)
      return surface
    }

    private fun initializeScene(surface: NavDesignSurface): Scene {
      val scene = surface.scene!!
      scene.layout(0, SceneContext.get())
      return scene
    }

    private fun initializeInteractionManager(surface: NavDesignSurface): InteractionManager {
      val interactionManager = InteractionManager(surface)
      interactionManager.startListening()
      return interactionManager
    }

    private fun dragFromActionHandle(interactionManager: InteractionManager,
                                     component: SceneComponent,
                                     @NavCoordinate x: Int,
                                     @NavCoordinate y: Int,
                                     view: SceneView?) {
      val rect = Coordinates.getSwingRect(view!!, component.fillRect(null))
      LayoutTestUtilities.pressMouse(interactionManager, BUTTON1, rect.x + rect.width, Coordinates.getSwingY(view, component.centerY), 0)
      LayoutTestUtilities.releaseMouse(interactionManager, BUTTON1, Coordinates.getSwingX(view, x), Coordinates.getSwingY(view, y), 0)
    }

    private fun verifyModel(model: SyncNlModel, expected: String) {
      val tree = NlTreeDumper().toTree(model.components)
      assertEquals(expected, tree)
    }
  }
}
