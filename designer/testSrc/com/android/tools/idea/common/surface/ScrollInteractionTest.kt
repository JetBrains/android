/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.common.surface

import com.android.SdkConstants
import com.android.testutils.delayUntilCondition
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.fixtures.MouseWheelEventBuilder
import com.android.tools.idea.uibuilder.scene.SceneTest
import java.awt.event.MouseWheelEvent
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking

class ScrollInteractionTest : SceneTest() {
  /**
   * Converts the scene `TextView` components to a text representation that contains the x and y
   * drawing coordinates as "X Y".
   */
  private fun textViewsToString(): String =
    myScene.sceneManager.scene.sceneComponents
      .filter { it.nlComponent.tagName == "TextView" }
      .sortedBy { it.drawY }
      .joinToString { "${it.drawX} ${it.drawY}" }

  fun testScrollInteractionUpdatesScene() = runBlocking {
    mySceneManager.requestRenderAndWait()

    assertEquals("0 0, 0 500, 0 1000", textViewsToString())
    val scrollInteraction =
      ScrollInteraction.createScrollInteraction(
        myScene.sceneManager.sceneViews.first(),
        myScene.sceneManager.sceneViews.first().firstComponent!!,
      )!!
    val event =
      MouseWheelEventBuilder(0, 0)
        .withAmount(1)
        .withScrollType(MouseWheelEvent.WHEEL_UNIT_SCROLL)
        .withUnitToScroll(5)
        .build()

    scrollInteraction.update(MouseWheelMovedEvent(event, InteractionInformation(0, 0, 0)))
    // Unfortunately the render and layout triggered by the scroll will happen asynchronously
    // so we need to wait here for it to happen.
    delayUntilCondition(250, 5.seconds) {
      // Verify that the views have moved
      textViewsToString() == "0 -50, 0 950, 0 1950"
    }
  }

  override fun createModel(): ModelBuilder =
    model(
      "scroll.xml",
      component(SdkConstants.SCROLL_VIEW)
        .withMockView()
        .withBounds(0, 0, 90, 90)
        .matchParentWidth()
        .matchParentHeight()
        .unboundedChildren(
          component(SdkConstants.LINEAR_LAYOUT)
            .withMockView()
            .withBounds(0, 0, 90, 3000)
            .withAttribute("android:orientation", "vertical")
            .wrapContentHeight()
            .wrapContentWidth()
            .children(
              component(SdkConstants.TEXT_VIEW)
                .withMockView()
                .id("@id/myText1")
                .withBounds(0, 0, 40, 1000)
                .width("40dp")
                .height("1000dp"),
              component(SdkConstants.TEXT_VIEW)
                .withMockView()
                .id("@id/myText2")
                .withBounds(0, 1000, 40, 1000)
                .width("40dp")
                .height("1000dp"),
              component(SdkConstants.TEXT_VIEW)
                .withMockView()
                .id("@id/myText3")
                .withBounds(0, 2000, 40, 1000)
                .width("40dp")
                .height("1000dp"),
            )
        ),
    )
}
