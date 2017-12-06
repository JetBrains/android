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
package com.android.tools.idea.uibuilder.handlers.grid

import com.android.SdkConstants.*
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.util.NlTreeDumper
import com.android.tools.idea.uibuilder.LayoutTestUtilities.mockViewWithBaseline
import com.android.tools.idea.uibuilder.scene.SceneTest
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget

/**
 * TODO: find the way to share the test between [GridLayoutHandlerTest] and [GridLayoutV7HandlerTest]
 */
class GridLayoutV7HandlerTest : SceneTest() {

  fun testResizeFromBottomRight() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", ResizeBaseTarget.Type.RIGHT_BOTTOM)
    myInteraction.mouseRelease(220f, 230f)
    myScreen.get("@id/checkbox")
        .expectXml("<CheckBox\n" +
            "        android:id=\"@id/checkbox\"\n" +
            "        android:layout_width=\"70dp\"\n" +
            "        android:layout_height=\"80dp\"\n" +
            "        app:layout_column=\"1\"\n" +
            "        app:layout_row=\"1\" />")
  }

  fun testDragComponentCell_0_0() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox")
    myInteraction.mouseRelease(0f, 0f)
    myScreen.get("@id/checkbox")
        .expectXml("<CheckBox\n" +
            "    android:id=\"@id/checkbox\"\n" +
            "    android:layout_width=\"20dp\"\n" +
            "    android:layout_height=\"20dp\"\n" +
            "    app:layout_row=\"0\"\n" +
            "    app:layout_column=\"0\"/>")
  }

  fun testDragComponentCell_0_1() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox")
    myInteraction.mouseRelease(150f, 0f)
    myScreen.get("@id/checkbox")
        .expectXml("<CheckBox\n" +
            "    android:id=\"@id/checkbox\"\n" +
            "    android:layout_width=\"20dp\"\n" +
            "    android:layout_height=\"20dp\"\n" +
            "    app:layout_row=\"0\"\n" +
            "    app:layout_column=\"1\"/>")
  }

  fun testDragComponentCell_1_0() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox")
    myInteraction.mouseRelease(0f, 150f)
    myScreen.get("@id/checkbox")
        .expectXml("<CheckBox\n" +
            "    android:id=\"@id/checkbox\"\n" +
            "    android:layout_width=\"20dp\"\n" +
            "    android:layout_height=\"20dp\"\n" +
            "    app:layout_row=\"1\"\n" +
            "    app:layout_column=\"0\"/>")
  }

  fun testDragComponentCell_2_0() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox")
    myInteraction.mouseRelease(0f, 200f)
    myScreen.get("@id/checkbox")
        .expectXml("<CheckBox\n" +
            "    android:id=\"@id/checkbox\"\n" +
            "    android:layout_width=\"20dp\"\n" +
            "    android:layout_height=\"20dp\"\n" +
            "    app:layout_row=\"2\"\n" +
            "    app:layout_column=\"0\"/>")
  }

  fun testDragComponentCell_0_2() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox")
    myInteraction.mouseRelease(200f, 0f)
    myScreen.get("@id/checkbox")
        .expectXml("<CheckBox\n" +
            "    android:id=\"@id/checkbox\"\n" +
            "    android:layout_width=\"20dp\"\n" +
            "    android:layout_height=\"20dp\"\n" +
            "    app:layout_row=\"0\"\n" +
            "    app:layout_column=\"2\"/>")
  }

  override fun createModel(): ModelBuilder {
    val builder = model("grid_layout_v7.xml",
        component(GRID_LAYOUT_V7.defaultName())
            .withBounds(0, 0, 1000, 1000)
            .matchParentWidth()
            .matchParentHeight()
            .children(
                component(BUTTON)
                    .withBounds(100, 100, 100, 100)
                    .viewObject(mockViewWithBaseline(17))
                    .id("@id/button")
                    .width("100dp")
                    .height("100dp")
                    .withAttribute("app:layout_row", "0")
                    .withAttribute("app:layout_column", "0"),

                component(CHECK_BOX)
                    .withBounds(300, 300, 20, 20)
                    .viewObject(mockViewWithBaseline(17))
                    .id("@id/checkbox")
                    .width("20dp")
                    .height("20dp")
                    .withAttribute("app:layout_row", "1")
                    .withAttribute("app:layout_column", "1")
            ))
    val model = builder.build()
    assertEquals(1, model.components.size)
    assertEquals("NlComponent{tag=<android.support.v7.widget.GridLayout>, bounds=[0,0:1000x1000}\n" +
        "    NlComponent{tag=<Button>, bounds=[100,100:100x100}\n" +
        "    NlComponent{tag=<CheckBox>, bounds=[300,300:20x20}",
        NlTreeDumper.dumpTree(model.components))

    format(model.file)
    assertEquals("<android.support.v7.widget.GridLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
        "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
        "    android:layout_width=\"match_parent\"\n" +
        "    android:layout_height=\"match_parent\">\n" +
        "\n" +
        "    <Button\n" +
        "        android:id=\"@id/button\"\n" +
        "        android:layout_width=\"100dp\"\n" +
        "        android:layout_height=\"100dp\"\n" +
        "        app:layout_row=\"0\"\n" +
        "        app:layout_column=\"0\" />\n" +
        "\n" +
        "    <CheckBox\n" +
        "        android:id=\"@id/checkbox\"\n" +
        "        android:layout_width=\"20dp\"\n" +
        "        android:layout_height=\"20dp\"\n" +
        "        app:layout_row=\"1\"\n" +
        "        app:layout_column=\"1\" />\n" +
        "\n" +
        "</android.support.v7.widget.GridLayout>\n", model.file.text)
    return builder
  }
}
