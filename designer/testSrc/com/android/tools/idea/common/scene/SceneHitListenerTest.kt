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
package com.android.tools.idea.common.scene

import com.android.AndroidXConstants
import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.scene.target.CommonDragTarget
import com.android.tools.idea.uibuilder.scene.SceneTest
import java.awt.event.InputEvent

class SceneHitListenerTest : SceneTest() {

  fun testHitSelectedComponentFistWhenAltIsHold() {
    val inner = myScene.getSceneComponent("inner")!!

    myInteraction.select(inner)
    myInteraction.mouseDown(155f, 155f, InputEvent.ALT_DOWN_MASK)

    val dragTarget = inner.targets.filterIsInstance(CommonDragTarget::class.java)[0]
    assertEquals(dragTarget, myScene.interactingTarget)
  }

  fun testHitDepthComponentFirstWhenAltIsNotHold() {
    val inner = myScene.getSceneComponent("inner")!!
    val testView = myScene.getSceneComponent("textView")!!

    myInteraction.select(inner)
    myInteraction.mouseDown(155f, 155f, 0)

    val dragTarget = testView.targets.filterIsInstance(CommonDragTarget::class.java)[0]
    assertEquals(dragTarget, myScene.interactingTarget)
  }

  override fun createModel(): ModelBuilder {
    return model(
      "model.xml",
      component(AndroidXConstants.CONSTRAINT_LAYOUT.defaultName())
        .id("@+id/root")
        .withBounds(0, 0, 1000, 1000)
        .children(
          component(AndroidXConstants.CONSTRAINT_LAYOUT.defaultName())
            .id("@+id/inner")
            .withBounds(200, 200, 200, 200)
            .width("100dp")
            .height("100dp")
            .withAttribute(
              SdkConstants.SHERPA_URI,
              SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X,
              "100dp",
            )
            .withAttribute(
              SdkConstants.SHERPA_URI,
              SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y,
              "100dp",
            )
            .children(
              component(SdkConstants.TEXT_VIEW)
                .id("@+id/textView")
                .withBounds(300, 300, 100, 50)
                .width("50dp")
                .height("25dp")
                .withAttribute(
                  SdkConstants.SHERPA_URI,
                  SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X,
                  "50dp",
                )
                .withAttribute(
                  SdkConstants.SHERPA_URI,
                  SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y,
                  "50dp",
                )
            )
        ),
    )
  }
}
