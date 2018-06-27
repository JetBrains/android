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
package com.android.tools.idea.naveditor.scene

import com.android.resources.ScreenOrientation
import com.android.sdklib.devices.Hardware
import com.android.sdklib.devices.Screen
import com.android.sdklib.devices.State
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.editor.Destination
import com.android.tools.idea.naveditor.scene.layout.NEW_DESTINATION_MARKER_PROPERTY
import com.android.tools.idea.naveditor.scene.targets.ScreenDragTarget
import com.intellij.openapi.command.WriteCommandAction
import org.mockito.Mockito
import java.awt.Point

class NavSceneManagerTest : NavTestCase() {

  fun testLayout() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
        fragment("fragment2")
        fragment("fragment3")
      }
    }
    val scene = model.surface.scene!!


    val fragment2x = scene.getSceneComponent("fragment2")!!.drawX
    val fragment2y = scene.getSceneComponent("fragment2")!!.drawY
    val fragment3x = scene.getSceneComponent("fragment3")!!.drawX
    val fragment3y = scene.getSceneComponent("fragment3")!!.drawY

    val sceneComponent = scene.getSceneComponent("fragment1")!!
    val dragTarget = sceneComponent.targets.filterIsInstance(ScreenDragTarget::class.java).first()
    dragTarget.mouseDown(0, 0)
    sceneComponent.isDragging = true
    sceneComponent.setPosition(100, 50)
    // the release position isn't used
    dragTarget.mouseRelease(2, 2, listOf())

    scene.sceneManager.requestRender()

    assertEquals(100, scene.getSceneComponent("fragment1")!!.drawX)
    assertEquals(50, scene.getSceneComponent("fragment1")!!.drawY)
    assertEquals(fragment2x, scene.getSceneComponent("fragment2")!!.drawX)
    assertEquals(fragment2y, scene.getSceneComponent("fragment2")!!.drawY)
    assertEquals(fragment3x, scene.getSceneComponent("fragment3")!!.drawX)
    assertEquals(fragment3y, scene.getSceneComponent("fragment3")!!.drawY)
  }

  fun testLandscape() {
    val model = NavModelBuilderUtil.model("nav.xml", myFacet, myFixture, {
      navigation {
        fragment("fragment1")
      }
    }, "navigation-land").build()
    val scene = model.surface.scene!!
    val component = scene.getSceneComponent("fragment1")!!
    assertEquals(256, component.drawWidth)
    assertEquals(153, component.drawHeight)
  }

  fun testPortrait() {
    val model = NavModelBuilderUtil.model("nav.xml", myFacet, myFixture, {
      navigation {
        fragment("fragment1")
      }
    }, "navigation-port").build()
    val scene = model.surface.scene!!
    val component = scene.getSceneComponent("fragment1")!!
    assertEquals(153, component.drawWidth)
    assertEquals(256, component.drawHeight)
  }

  fun testConfigurations() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
      }
    }

    val configuration = Mockito.mock(Configuration::class.java)
    model.configuration = configuration

    val state = Mockito.mock(State::class.java)
    Mockito.`when`(configuration.deviceState).thenReturn(state)

    val hardware = Mockito.mock(Hardware::class.java)
    Mockito.`when`(state.hardware).thenReturn(hardware)

    val screen = Mockito.mock(Screen::class.java)
    Mockito.`when`(hardware.screen).thenReturn(screen)

    Mockito.`when`(screen.xDimension).thenReturn(1920)
    Mockito.`when`(screen.yDimension).thenReturn(1080)
    Mockito.`when`(state.orientation).thenReturn(ScreenOrientation.PORTRAIT)

    val scene = model.surface.scene!!
    val sceneManager = scene.sceneManager

    sceneManager.update()
    var component = scene.getSceneComponent("fragment1")!!
    assertEquals(144, component.drawWidth)
    assertEquals(256, component.drawHeight)

    Mockito.`when`(state.orientation).thenReturn(ScreenOrientation.LANDSCAPE)

    sceneManager.update()
    component = scene.getSceneComponent("fragment1")!!
    assertEquals(256, component.drawWidth)
    assertEquals(144, component.drawHeight)

    Mockito.`when`(screen.xDimension).thenReturn(480)
    Mockito.`when`(screen.yDimension).thenReturn(800)

    sceneManager.update()
    component = scene.getSceneComponent("fragment1")!!
    assertEquals(256, component.drawWidth)
    assertEquals(153, component.drawHeight)

    Mockito.`when`(state.orientation).thenReturn(ScreenOrientation.PORTRAIT)
    sceneManager.update()
    component = scene.getSceneComponent("fragment1")!!
    assertEquals(153, component.drawWidth)
    assertEquals(256, component.drawHeight)
  }

  fun testNewDestination() {
    val scale = 0.5
    val initialOffset = (40 / scale).toInt()
    val incrementalOffset = (30 / scale).toInt()
    val scrollPosition = Point(5, 10)

    val model = model("nav.xml") {
      navigation("root")
    }

    val scene = model.surface.scene!!

    val sceneManager = scene.sceneManager as NavSceneManager
    val designSurface = sceneManager.designSurface
    val sceneView = designSurface.currentSceneView!!
    Mockito.`when`<Double>(sceneView.scale).thenReturn(scale)
    Mockito.`when`(designSurface.scrollPosition).thenAnswer { Point(scrollPosition) }

    val currentNavigation = designSurface.currentNavigation

    val p = Point((scrollPosition.x / scale).toInt() + initialOffset,
                  (scrollPosition.y / scale).toInt() + initialOffset)

    listOf("first", "second", "third", "fourth", "fifth").forEach {
      val destination = Destination.RegularDestination(currentNavigation, "fragment", idBase = it)
      WriteCommandAction.runWriteCommandAction(project) { destination.addToGraph() }
      destination.component!!.putClientProperty(NEW_DESTINATION_MARKER_PROPERTY, true)
      sceneManager.update()

      val component = scene.getSceneComponent(it)!!
      assertEquals(p.x, component.drawX)
      assertEquals(p.y, component.drawY)

      p.translate(incrementalOffset, incrementalOffset)
    }
  }
}