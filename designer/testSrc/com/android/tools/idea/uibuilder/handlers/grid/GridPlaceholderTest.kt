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
package com.android.tools.idea.uibuilder.handlers.grid

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.model.TestNlAttributeHolder
import com.android.tools.idea.common.scene.Region
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SnappingInfo
import com.android.tools.idea.uibuilder.applyPlaceholderToSceneComponent
import com.android.tools.idea.uibuilder.scene.SceneTest
import java.awt.Point
import org.mockito.Mockito

class GridPlaceholderTest : SceneTest() {

  fun testRegion() {
    val gridLayout = myScene.getSceneComponent("grid")!!

    val handler = GridLayoutHandler()
    val placeholders = handler.getPlaceholders(gridLayout, emptyList())

    val expected =
      arrayOf(
        // Row 0
        Region(0, 0, 200, 200),
        Region(0, 200, 200, 400),
        Region(0, 400, 200, 900),
        Region(0, 900, 200, 1000),
        // Row 1
        Region(200, 0, 400, 200),
        Region(200, 200, 400, 400),
        Region(200, 400, 400, 900),
        Region(200, 900, 400, 1000),
        // Row 2
        Region(400, 0, 900, 200),
        Region(400, 200, 900, 400),
        Region(400, 400, 900, 900),
        Region(400, 900, 900, 1000),
        // Row 3
        Region(900, 0, 1000, 200),
        Region(900, 200, 1000, 400),
        Region(900, 400, 1000, 900),
        Region(900, 900, 1000, 1000),
      )

    assertEquals(expected.size, placeholders.size)

    for (r in expected) {
      assertNotNull(placeholders.singleOrNull { it.region == r })
    }
  }

  fun testSnap() {
    val gridLayout = myScene.getSceneComponent("grid")!!

    val region = Region(50, 50, 100, 100)
    val placeholder = GridPlaceholder(region, 0, 0, SdkConstants.ANDROID_URI, gridLayout)
    val p = Point(-1, -1)

    // inside
    assertTrue(placeholder.snap(SnappingInfo(60, 60, 80, 80), p))
    // outside
    assertFalse(placeholder.snap(SnappingInfo(30, 30, 40, 40), p))

    // partially outside but center is in the region
    assertTrue(placeholder.snap(SnappingInfo(45, 45, 65, 65), p))
    assertTrue(placeholder.snap(SnappingInfo(85, 85, 105, 105), p))

    // x inside but y outside
    assertFalse(placeholder.snap(SnappingInfo(70, 30, 80, 40), p))
    // x outside but y inside
    assertFalse(placeholder.snap(SnappingInfo(30, 70, 40, 80), p))

    // bigger than placeholder but center is in the region
    assertTrue(placeholder.snap(SnappingInfo(30, 30, 130, 130), p))

    // bigger than placeholder but center is not in the region
    assertFalse(placeholder.snap(SnappingInfo(30, 30, 200, 200), p))
  }

  fun testApply() {
    val gridLayout = myScene.getSceneComponent("grid")!!

    val handler = GridLayoutHandler()
    val placeholders = handler.getPlaceholders(gridLayout, emptyList())

    val button2 = myScene.getSceneComponent("button2")!!

    assertEquals(
      "0",
      button2.authoritativeNlComponent.getAndroidAttribute(SdkConstants.ATTR_LAYOUT_ROW),
    )
    assertEquals(
      "1",
      button2.authoritativeNlComponent.getAndroidAttribute(SdkConstants.ATTR_LAYOUT_COLUMN),
    )

    val row1column0 = placeholders.single { it.region == Region(0, 200, 200, 400) }
    applyPlaceholderToSceneComponent(button2, row1column0)
    mySceneManager.update()
    assertEquals(
      "1",
      button2.authoritativeNlComponent.getAndroidAttribute(SdkConstants.ATTR_LAYOUT_ROW),
    )
    assertEquals(
      "0",
      button2.authoritativeNlComponent.getAndroidAttribute(SdkConstants.ATTR_LAYOUT_COLUMN),
    )

    val row0column2 = placeholders.single { it.region == Region(400, 0, 900, 200) }
    applyPlaceholderToSceneComponent(button2, row0column2)
    mySceneManager.update()
    assertEquals(
      "0",
      button2.authoritativeNlComponent.getAndroidAttribute(SdkConstants.ATTR_LAYOUT_ROW),
    )
    assertEquals(
      "2",
      button2.authoritativeNlComponent.getAndroidAttribute(SdkConstants.ATTR_LAYOUT_COLUMN),
    )
  }

  fun testGapPlaceholder() {
    // GridLayout has gap area if row and/or column indices are not continue. This test checks the
    // Placeholder of gap area.
    val gridLayout = myScene.getSceneComponent("grid")!!

    val handler = GridLayoutHandler()
    val placeholders = handler.getPlaceholders(gridLayout, emptyList())

    val gapPlaceholders =
      placeholders.filter { it.region.left == 400 }.filter { it.region.top == 400 }
    assertEquals(1, gapPlaceholders.size)

    val attributeHolder = TestNlAttributeHolder()

    val gapPlaceholder = gapPlaceholders[0]
    assertEquals(900, gapPlaceholder.region.right)
    assertEquals(900, gapPlaceholder.region.bottom)
    gapPlaceholder.updateAttribute(Mockito.mock(SceneComponent::class.java), attributeHolder)
    assertEquals(
      "2",
      attributeHolder.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_ROW),
    )
    assertEquals(
      "2",
      attributeHolder.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_COLUMN),
    )
  }

  override fun createModel(): ModelBuilder {
    return model(
      "gridlayout.xml",
      component(SdkConstants.GRID_LAYOUT)
        .withBounds(0, 0, 2000, 2000)
        .id("@id/grid")
        .width("1000dp")
        .height("1000dp")
        .children(
          component(SdkConstants.BUTTON)
            .withBounds(0, 0, 400, 400)
            .id("@id/button1")
            .width("200dp")
            .height("200dp")
            .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_ROW, "0")
            .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_COLUMN, "0"),
          component(SdkConstants.TEXT_VIEW)
            .withBounds(400, 400, 400, 400)
            .id("@id/textView")
            .width("200dp")
            .height("200dp")
            .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_ROW, "1")
            .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_COLUMN, "1"),
          component(SdkConstants.BUTTON)
            .withBounds(400, 0, 400, 400)
            .id("@id/button2")
            .width("200dp")
            .height("200dp")
            .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_ROW, "0")
            .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_COLUMN, "1"),
          component(SdkConstants.BUTTON)
            .withBounds(1800, 1800, 200, 200)
            .id("@id/button3")
            .width("100dp")
            .height("100dp")
            .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_ROW, "3")
            .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_COLUMN, "3"),
        ),
    )
  }
}
