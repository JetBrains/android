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

import com.android.SdkConstants.*
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.uibuilder.LayoutTestUtilities
import com.android.tools.idea.uibuilder.scene.SceneTest
import org.mockito.Mockito

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
      Mockito.`when`(this.tagName).thenReturn(TEXT_VIEW)
    })
    val parent = myScene.getSceneComponent("parent")

    assertSize(1, parent!!.children)
    assertNull(sceneComponent.parent)

    parent.addChild(sceneComponent)

    assertSize(2, parent.children)
    assertEquals(sceneComponent, parent.children[1])
    assertEquals(parent, sceneComponent.parent)
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