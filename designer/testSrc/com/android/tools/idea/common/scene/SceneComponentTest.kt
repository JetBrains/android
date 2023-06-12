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
package com.android.tools.idea.common.scene

import com.android.SdkConstants.BUTTON
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.common.LayoutTestUtilities
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.scene.SceneComponent.DrawState
import com.android.tools.idea.common.scene.target.CommonDragTarget
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.scene.SceneTest
import org.junit.Assert.assertNotEquals

open class SceneComponentTest: SceneTest() {

  fun testRemoveSceneComponent() {
    val parent = myScene.getSceneComponent("parent")
    val child = myScene.getSceneComponent("child")

    println("$parent and $child")

    assertEquals(child, parent!!.children[0])
    assertEquals(child!!.parent, parent)

    child.removeFromParent()

    assertEmpty(parent.children)
    assertNull(child.parent)
  }

  fun testAddSceneComponent() {
    val sceneComponent = TemporarySceneComponent(myScene, LayoutTestUtilities.createMockComponent().apply {
      whenever(this.tagName).thenReturn(TEXT_VIEW)
    })
    val parent = myScene.getSceneComponent("parent")

    assertSize(1, parent!!.children)
    assertNull(sceneComponent.parent)

    parent.addChild(sceneComponent)

    assertSize(2, parent.children)
    assertEquals(sceneComponent, parent.children[1])
    assertEquals(parent, sceneComponent.parent)
  }

  fun testDoNotCreateCommonDragTargetOnRootComponent() {
    StudioFlags.NELE_DRAG_PLACEHOLDER.override(true)

    val root = myScene.getSceneComponent("parent")!!
    root.updateTargets()
    val rootCommonDragTargets = root.targets.filterIsInstance<CommonDragTarget>()
    assertEmpty(rootCommonDragTargets)

    val child = myScene.getSceneComponent("child")!!
    child.updateTargets()
    val childCommonDragTargets = child.targets.filterIsInstance<CommonDragTarget>()
    assertSize(1, childCommonDragTargets)

    StudioFlags.NELE_DRAG_PLACEHOLDER.clearOverride()
  }

  fun testDrawStates() {
    val root = myScene.getSceneComponent("parent")!!

    val statesWithoutSelected = DrawState.values().filter { it != DrawState.SELECTED }
    for (isSelected in listOf(true, false)) {
      for(prioritizeSelected in listOf(true, false)) {
        // Test how the change of isSelected affects the drawState
        root.setPrioritizeSelectedDrawState(prioritizeSelected)
        root.isSelected = isSelected
        if (!isSelected || !prioritizeSelected) {
          assertNotEquals(DrawState.SELECTED, root.drawState)
        } else {
          assertEquals(DrawState.SELECTED, root.drawState)
        }

        // Test forcing selected state
        // i.e. the "prioritize" flag won't affect a "forced" selected state
        root.drawState = DrawState.SELECTED
        assertEquals(DrawState.SELECTED, root.drawState)

        // Test all but selected state
        for (state in statesWithoutSelected) {
          root.drawState = state
          if (!isSelected || !prioritizeSelected) {
            assertEquals(state, root.drawState)
          } else {
            assertEquals(DrawState.SELECTED, root.drawState)
          }
        }
      }
    }
  }

  override fun createModel(): ModelBuilder {
    return model("scene_component_test.xml",
        component(LINEAR_LAYOUT)
            .id("@+id/parent")
            .withBounds(0, 0, 2000, 2000)
            .matchParentWidth()
            .matchParentHeight()
            .children(
                component(BUTTON)
                    .id("@+id/child")
                    .withBounds(0, 0, 2000, 2000)
                    .matchParentWidth()
                    .matchParentHeight()
            )
    )
  }
}