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
package com.android.tools.idea.uibuilder.handlers.linear

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.uibuilder.handlers.linear.actions.ToggleOrientationAction
import com.android.tools.idea.uibuilder.scene.SceneTest

class ToggleOrientationActionTest : SceneTest() {

  fun testToggleWithoutSelection() {
    val root = myScene.getSceneComponent("root")!!

    myInteraction.performToolbarAction(root) { target -> target is ToggleOrientationAction }
    assertEquals(
      SdkConstants.VALUE_HORIZONTAL,
      root.authoritativeNlComponent.getAndroidAttribute(SdkConstants.ATTR_ORIENTATION),
    )
  }

  fun testToggleWhenSelectingRoot() {
    val root = myScene.getSceneComponent("root")!!

    myInteraction.select(root, true)
    myInteraction.performToolbarAction(root) { target -> target is ToggleOrientationAction }
    assertEquals(
      SdkConstants.VALUE_HORIZONTAL,
      root.authoritativeNlComponent.getAndroidAttribute(SdkConstants.ATTR_ORIENTATION),
    )
  }

  fun testToggleWhenSelectingChild() {
    val root = myScene.getSceneComponent("root")!!
    val button = myScene.getSceneComponent("button1")!!

    myInteraction.select(button, true)
    myInteraction.performToolbarAction(root) { target -> target is ToggleOrientationAction }
    assertEquals(
      SdkConstants.VALUE_HORIZONTAL,
      root.authoritativeNlComponent.getAndroidAttribute(SdkConstants.ATTR_ORIENTATION),
    )
  }

  fun testToggleWhenSelectingMultipleChildren() {
    val root = myScene.getSceneComponent("root")!!
    val button1 = myScene.getSceneComponent("button1")!!
    val button2 = myScene.getSceneComponent("button2")!!

    myInteraction.select(button1, button2)
    myInteraction.performToolbarAction(root) { target -> target is ToggleOrientationAction }
    assertEquals(
      SdkConstants.VALUE_HORIZONTAL,
      root.authoritativeNlComponent.getAndroidAttribute(SdkConstants.ATTR_ORIENTATION),
    )
  }

  fun testToggleWhenSelectingNestedLinearLayout() {
    val nested = myScene.getSceneComponent("inner1")!!

    myInteraction.select(nested, true)
    myInteraction.performToolbarAction(nested) { target -> target is ToggleOrientationAction }
    assertEquals(
      SdkConstants.VALUE_VERTICAL,
      nested.authoritativeNlComponent.getAndroidAttribute(SdkConstants.ATTR_ORIENTATION),
    )
  }

  fun testToggleWhenSelectingMultipleNestedLinearLayout() {
    val root = myScene.getSceneComponent("root")!!
    val nested1 = myScene.getSceneComponent("inner1")!!
    val nested2 = myScene.getSceneComponent("inner2")!!
    myInteraction.select(nested1, nested2)
    myInteraction.performToolbarAction(root) { target -> target is ToggleOrientationAction }
    assertEquals(
      SdkConstants.VALUE_VERTICAL,
      nested1.authoritativeNlComponent.getAndroidAttribute(SdkConstants.ATTR_ORIENTATION),
    )
    assertEquals(
      SdkConstants.VALUE_HORIZONTAL,
      nested2.authoritativeNlComponent.getAndroidAttribute(SdkConstants.ATTR_ORIENTATION),
    )
  }

  override fun createModel(): ModelBuilder {
    return model(
      "model.xml",
      component(SdkConstants.LINEAR_LAYOUT)
        .id("@+id/root")
        .withBounds(0, 0, 1000, 1000)
        .withAttribute(
          SdkConstants.ANDROID_URI,
          SdkConstants.ATTR_ORIENTATION,
          SdkConstants.VALUE_VERTICAL,
        )
        .children(
          component(SdkConstants.BUTTON)
            .id("@id/button1")
            .withBounds(0, 0, 10, 10)
            .width("5dp")
            .height("5dp"),
          component(SdkConstants.LINEAR_LAYOUT)
            .id("@id/inner1")
            .withBounds(0, 10, 500, 90)
            .width("250dp")
            .height("45dp")
            // No orientation attribute means default, which is horizontal.
            .children(
              component(SdkConstants.TEXT_VIEW)
                .id("@+id/textView")
                .withBounds(0, 10, 10, 10)
                .width("5dp")
                .height("5dp"),
              component(SdkConstants.TEXT_VIEW)
                .id("@+id/textView")
                .withBounds(10, 10, 10, 10)
                .width("5dp")
                .height("5dp"),
            ),
          component(SdkConstants.BUTTON)
            .id("@id/button2")
            .withBounds(0, 100, 10, 10)
            .width("5dp")
            .height("5dp"),
          component(SdkConstants.LINEAR_LAYOUT)
            .id("@id/inner2")
            .withBounds(0, 110, 500, 90)
            .width("250dp")
            .height("45dp")
            .withAttribute(
              SdkConstants.ANDROID_URI,
              SdkConstants.ATTR_ORIENTATION,
              SdkConstants.VALUE_VERTICAL,
            ),
        ),
    )
  }
}
