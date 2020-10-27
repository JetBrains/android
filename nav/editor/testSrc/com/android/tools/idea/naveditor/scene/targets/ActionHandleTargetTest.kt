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

import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.analytics.TestNavUsageTracker
import com.android.tools.idea.naveditor.model.actionDestination
import com.android.tools.idea.naveditor.model.isAction
import com.google.wireless.android.sdk.stats.NavActionInfo
import com.google.wireless.android.sdk.stats.NavEditorEvent
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.mockito.Mockito
import org.mockito.Mockito.verifyZeroInteractions

class ActionHandleTargetTest : NavTestCase() {

  fun testCreateAction() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
        fragment("fragment2")
      }
    }
    val surface = model.surface
    val scene = surface.scene!!
    scene.layout(0, scene.sceneManager.sceneView.context)

    val fragment2x = scene.getSceneComponent("fragment2")!!.drawX
    val fragment2y = scene.getSceneComponent("fragment2")!!.drawY

    val sceneComponent = scene.getSceneComponent("fragment1")!!
    val actionHandleTarget = sceneComponent.targets.firstIsInstance<ActionHandleTarget>()
    TestNavUsageTracker.create(model).use { tracker ->
      actionHandleTarget.mouseDown(0, 0)
      actionHandleTarget.mouseRelease(fragment2x, fragment2y, listOf())

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
  }

  fun testCreateToInclude() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
        include("navigation")
      }
    }
    val surface = model.surface
    val scene = surface.scene!!
    scene.layout(0, scene.sceneManager.sceneView.context)

    val fragment2x = scene.getSceneComponent("nav")!!.drawX
    val fragment2y = scene.getSceneComponent("nav")!!.drawY

    val sceneComponent = scene.getSceneComponent("fragment1")!!
    val actionHandleTarget = sceneComponent.targets.firstIsInstance<ActionHandleTarget>()
    TestNavUsageTracker.create(model).use { tracker ->
      actionHandleTarget.mouseDown(0, 0)
      actionHandleTarget.mouseRelease(fragment2x, fragment2y, listOf())

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
    val surface = model.surface
    val scene = surface.scene!!
    scene.layout(0, scene.sceneManager.sceneView.context)

    val sceneComponent = scene.getSceneComponent("fragment1")!!
    val fragment1x = sceneComponent.drawX
    val fragment1y = sceneComponent.drawY

    val actionHandleTarget = sceneComponent.targets.firstIsInstance<ActionHandleTarget>()
    TestNavUsageTracker.create(model).use { tracker ->
      actionHandleTarget.mouseDown(0, 0)
      actionHandleTarget.mouseRelease(fragment1x, fragment1y, listOf())


      val nlComponent = sceneComponent.nlComponent
      val action = nlComponent.children.first { it.isAction }
      assertEquals(1, nlComponent.children.size)
      assertEquals(nlComponent, action.actionDestination)
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
    val surface = model.surface
    val scene = surface.scene!!
    scene.layout(0, scene.sceneManager.sceneView.context)

    val sceneComponent = scene.getSceneComponent("fragment1")!!
    val actionHandleTarget = sceneComponent.targets.firstIsInstance<ActionHandleTarget>()
    TestNavUsageTracker.create(model).use { tracker ->
      actionHandleTarget.mouseDown(0, 0)
      // drag release to a point over the root and verify no action is created
      actionHandleTarget.mouseRelease(sceneComponent.drawX - 50, sceneComponent.drawY - 50, listOf())

      assertNull(model.find { it.isAction})

      verifyZeroInteractions(tracker)
    }
  }

  fun testDragInvalid() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
        addChild(ComponentDescriptor("fragment"), null)
      }
    }

    val surface = model.surface
    val scene = surface.scene!!
    scene.layout(0, scene.sceneManager.sceneView.context)

    val sceneComponent = scene.getSceneComponent("fragment1")!!
    val actionHandleTarget = sceneComponent.targets.firstIsInstance<ActionHandleTarget>()
    TestNavUsageTracker.create(model).use { tracker ->
      actionHandleTarget.mouseDown(0, 0)
      val invalidFragment = scene.getSceneComponent(model.find { it.id == null })!!
      actionHandleTarget.mouseRelease(invalidFragment.centerX, invalidFragment.centerY, listOf())

      assertNull(model.find { it.isAction})

      verifyZeroInteractions(tracker)
    }
  }

}