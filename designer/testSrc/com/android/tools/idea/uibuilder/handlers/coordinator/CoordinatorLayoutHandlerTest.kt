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
package com.android.tools.idea.uibuilder.handlers.coordinator

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.util.NlTreeDumper
import com.android.tools.idea.uibuilder.LayoutTestUtilities
import com.android.tools.idea.uibuilder.scene.SceneTest
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget

class CoordinatorLayoutHandlerTest : SceneTest() {

  fun testResizeFromTopLeft() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", ResizeBaseTarget.Type.LEFT_TOP)
    myInteraction.mouseRelease(110f, 130f)
    myScreen.get("@id/checkbox")
        .expectXml("<CheckBox\n" +
            "        android:id=\"@id/checkbox\"\n" +
            "        android:layout_width=\"50dp\"\n" +
            "        android:layout_height=\"30dp\" />")
  }

  fun testResizeFromTopRight() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", ResizeBaseTarget.Type.RIGHT_TOP)
    myInteraction.mouseRelease(220f, 130f)
    myScreen.get("@id/checkbox")
        .expectXml("<CheckBox\n" +
            "        android:id=\"@id/checkbox\"\n" +
            "        android:layout_width=\"70dp\"\n" +
            "        android:layout_height=\"30dp\" />")
  }

  fun testResizeFromBottomLeft() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", ResizeBaseTarget.Type.LEFT_BOTTOM)
    myInteraction.mouseRelease(110f, 230f)
    myScreen.get("@id/checkbox")
        .expectXml("<CheckBox\n" +
            "        android:id=\"@id/checkbox\"\n" +
            "        android:layout_width=\"50dp\"\n" +
            "        android:layout_height=\"80dp\" />")
  }

  fun testResizeFromBottomRight() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", ResizeBaseTarget.Type.RIGHT_BOTTOM)
    myInteraction.mouseRelease(220f, 230f)
    myScreen.get("@id/checkbox")
        .expectXml("<CheckBox\n" +
            "        android:id=\"@id/checkbox\"\n" +
            "        android:layout_width=\"70dp\"\n" +
            "        android:layout_height=\"80dp\" />")
  }

  fun testDragComponentToLeftTopSide() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox")
    myInteraction.mouseRelease(51f, 51f)
    myScreen.get("@id/checkbox")
        .expectXml("<CheckBox\n" +
            "        android:id=\"@id/checkbox\"\n" +
            "        android:layout_width=\"20dp\"\n" +
            "        android:layout_height=\"20dp\"\n" +
            "        app:layout_anchor=\"@+id/button\"\n" +
            "        app:layout_anchorGravity=\"left|top\" />")
  }

  fun testDragComponentToRightBottomSide() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox")
    myInteraction.mouseRelease(99f, 99f)
    myScreen.get("@id/checkbox")
        .expectXml("<CheckBox\n" +
            "        android:id=\"@id/checkbox\"\n" +
            "        android:layout_width=\"20dp\"\n" +
            "        android:layout_height=\"20dp\"\n" +
            "        app:layout_anchor=\"@+id/button\"\n" +
            "        app:layout_anchorGravity=\"right|bottom\" />")
  }

  override fun createModel(): ModelBuilder {
    val builder = model("coordinator.xml",
        component(SdkConstants.COORDINATOR_LAYOUT.defaultName())
            .withBounds(0, 0, 1000, 1000)
            .matchParentWidth()
            .matchParentHeight()
            .children(
                component(SdkConstants.BUTTON)
                    .withBounds(100, 100, 100, 100)
                    .viewObject(LayoutTestUtilities.mockViewWithBaseline(17))
                    .id("@id/button")
                    .width("100dp")
                    .height("100dp"),
                component(SdkConstants.CHECK_BOX)
                    .withBounds(300, 300, 20, 20)
                    .viewObject(LayoutTestUtilities.mockViewWithBaseline(17))
                    .id("@id/checkbox")
                    .width("20dp")
                    .height("20dp")
            ))
    val model = builder.build()
    assertEquals(1, model.components.size)
    assertEquals("NlComponent{tag=<android.support.design.widget.CoordinatorLayout>, bounds=[0,0:1000x1000}\n" +
        "    NlComponent{tag=<Button>, bounds=[100,100:100x100}\n" +
        "    NlComponent{tag=<CheckBox>, bounds=[300,300:20x20}",
        NlTreeDumper.dumpTree(model.components))

    format(model.file)
    assertEquals("<android.support.design.widget.CoordinatorLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
        "    android:layout_width=\"match_parent\"\n" +
        "    android:layout_height=\"match_parent\">\n" +
        "\n" +
        "    <Button\n" +
        "        android:id=\"@id/button\"\n" +
        "        android:layout_width=\"100dp\"\n" +
        "        android:layout_height=\"100dp\" />\n" +
        "\n" +
        "    <CheckBox\n" +
        "        android:id=\"@id/checkbox\"\n" +
        "        android:layout_width=\"20dp\"\n" +
        "        android:layout_height=\"20dp\" />\n" +
        "\n" +
        "</android.support.design.widget.CoordinatorLayout>\n", model.file.text)
    return builder
  }
}