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
package com.android.tools.idea.uibuilder.handlers.relative

import com.android.SdkConstants.*
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.util.NlTreeDumper
import com.android.tools.idea.uibuilder.LayoutTestUtilities.mockViewWithBaseline
import com.android.tools.idea.uibuilder.scene.SceneTest
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget

class RelativeLayoutHandlerKtTest : SceneTest() {

  fun testResizeFromTopLeft() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", ResizeBaseTarget.Type.LEFT_TOP)
    myInteraction.mouseRelease(110f, 130f)
    myScreen.get("@id/checkbox")
        .expectXml("<CheckBox\n" +
            "        android:id=\"@id/checkbox\"\n" +
            "        android:layout_width=\"50dp\"\n" +
            "        android:layout_height=\"30dp\"\n" +
            "        android:layout_marginLeft=\"10dp\"\n" +
            "        android:layout_marginTop=\"30dp\"\n" +
            "        android:layout_toRightOf=\"@id/button\"\n" +
            "        android:layout_below=\"@id/button\" />")
  }

  fun testResizeFromTopRight() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", ResizeBaseTarget.Type.RIGHT_TOP)
    myInteraction.mouseRelease(220f, 130f)
    myScreen.get("@id/checkbox")
        .expectXml("<CheckBox\n" +
            "        android:id=\"@id/checkbox\"\n" +
            "        android:layout_width=\"70dp\"\n" +
            "        android:layout_height=\"30dp\"\n" +
            "        android:layout_marginLeft=\"100dp\"\n" +
            "        android:layout_marginTop=\"30dp\"\n" +
            "        android:layout_toRightOf=\"@id/button\"\n" +
            "        android:layout_below=\"@id/button\" />")
  }

  fun testResizeFromBottomLeft() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", ResizeBaseTarget.Type.LEFT_BOTTOM)
    myInteraction.mouseRelease(110f, 230f)
    myScreen.get("@id/checkbox")
        .expectXml("<CheckBox\n" +
            "        android:id=\"@id/checkbox\"\n" +
            "        android:layout_width=\"50dp\"\n" +
            "        android:layout_height=\"80dp\"\n" +
            "        android:layout_marginLeft=\"10dp\"\n" +
            "        android:layout_marginTop=\"100dp\"\n" +
            "        android:layout_toRightOf=\"@id/button\"\n" +
            "        android:layout_below=\"@id/button\" />")
  }

  fun testResizeFromBottomRight() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", ResizeBaseTarget.Type.RIGHT_BOTTOM)
    myInteraction.mouseRelease(220f, 230f)
    myScreen.get("@id/checkbox")
        .expectXml("<CheckBox\n" +
            "        android:id=\"@id/checkbox\"\n" +
            "        android:layout_width=\"70dp\"\n" +
            "        android:layout_height=\"80dp\"\n" +
            "        android:layout_marginLeft=\"100dp\"\n" +
            "        android:layout_marginTop=\"100dp\"\n" +
            "        android:layout_toRightOf=\"@id/button\"\n" +
            "        android:layout_below=\"@id/button\" />")
  }

  fun testDragComponentToLeftTopSide() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox")
    myInteraction.mouseRelease(150f, 150f)
    myScreen.get("@id/checkbox")
        .expectXml("<CheckBox\n" +
            "        android:id=\"@id/checkbox\"\n" +
            "        android:layout_width=\"20dp\"\n" +
            "        android:layout_height=\"20dp\"\n" +
            "        android:layout_marginStart=\"145dp\"\n" +
            "        android:layout_marginTop=\"145dp\"\n" +
            "        android:layout_alignParentStart=\"true\"\n" +
            "        android:layout_alignParentTop=\"true\" />")
  }

  fun testDragComponentToRightBottomSide() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox")
    myInteraction.mouseRelease(400f, 400f)
    myScreen.get("@id/checkbox")
        .expectXml("<CheckBox\n" +
            "        android:id=\"@id/checkbox\"\n" +
            "        android:layout_width=\"20dp\"\n" +
            "        android:layout_height=\"20dp\"\n" +
            "        android:layout_marginEnd=\"95dp\"\n" +
            "        android:layout_marginBottom=\"95dp\"\n" +
            "        android:layout_alignParentEnd=\"true\"\n" +
            "        android:layout_alignParentBottom=\"true\" />")
  }

  fun testDragComponentOverLeftTopEdges() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox")
    myInteraction.mouseRelease(-100f, -100f)
    myScreen.get("@id/checkbox")
        .expectXml("<CheckBox\n" +
            "        android:id=\"@id/checkbox\"\n" +
            "        android:layout_width=\"20dp\"\n" +
            "        android:layout_height=\"20dp\"\n" +
            "        android:layout_alignParentStart=\"true\"\n" +
            "        android:layout_alignParentTop=\"true\" />")
  }

  fun testDragComponentOverRightBottomEdges() {
    // FIXME: The actual XML has weired format.
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox")
    myInteraction.mouseRelease(500f, 500f)
    myScreen.get("@id/checkbox")
        .expectXml("<CheckBox\n" +
            "        android:id=\"@id/checkbox\"\n" +
            "        android:layout_width=\"20dp\"\n" +
            "        android:layout_height=\"20dp\"\n" +
            "        android:layout_alignParentEnd=\"true\"\n" +
            "        android:layout_alignParentBottom=\"true\" />")
  }

  fun testDragComponentCenterHorizontal() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox")
    myInteraction.mouseRelease(250f, 30f)
    myScreen.get("@id/checkbox")
        .expectXml("<CheckBox\n" +
            "        android:id=\"@id/checkbox\"\n" +
            "        android:layout_width=\"20dp\"\n" +
            "        android:layout_height=\"20dp\"\n" +
            "        android:layout_marginTop=\"25dp\"\n" +
            "        android:layout_alignParentTop=\"true\"\n" +
            "        android:layout_centerHorizontal=\"true\" />")
  }

  fun testDragComponentCenterVertical() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox")
    myInteraction.mouseRelease(30f, 250f)
    myScreen.get("@id/checkbox")
        .expectXml("<CheckBox\n" +
            "        android:id=\"@id/checkbox\"\n" +
            "        android:layout_width=\"20dp\"\n" +
            "        android:layout_height=\"20dp\"\n" +
            "        android:layout_marginStart=\"25dp\"\n" +
            "        android:layout_alignParentStart=\"true\"\n" +
            "        android:layout_centerVertical=\"true\" />")
  }

  fun testDragComponentToCenterOfParent() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox")
    myInteraction.mouseRelease(250f, 250f)
    myScreen.get("@id/checkbox")
        .expectXml("<CheckBox\n" +
            "        android:id=\"@id/checkbox\"\n" +
            "        android:layout_width=\"20dp\"\n" +
            "        android:layout_height=\"20dp\"\n" +
            "        android:layout_centerInParent=\"true\" />")
  }

  fun testDragComponentToAlignWidgetStartAndTop() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox")
    myInteraction.mouseRelease(50f, 50f)
    myScreen.get("@id/checkbox")
        .expectXml("<CheckBox\n" +
            "        android:id=\"@id/checkbox\"\n" +
            "        android:layout_width=\"20dp\"\n" +
            "        android:layout_height=\"20dp\"\n" +
            "        android:layout_alignStart=\"@+id/button\"\n" +
            "        android:layout_alignTop=\"@+id/button\" />")
  }

  fun testDragComponentToBelowAndToEndOfWidget() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox")
    myInteraction.mouseRelease(100f, 100f)
    myScreen.get("@id/checkbox")
        .expectXml("<CheckBox\n" +
            "        android:id=\"@id/checkbox\"\n" +
            "        android:layout_width=\"20dp\"\n" +
            "        android:layout_height=\"20dp\"\n" +
            "        android:layout_toEndOf=\"@+id/button\"\n" +
            "        android:layout_below=\"@+id/button\" />")
  }

  override fun createModel(): ModelBuilder {
    val builder = model("relative_kt.xml",
        component(RELATIVE_LAYOUT)
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
                    .withAttribute("android:layout_alignParentTop", "true")
                    .withAttribute("android:layout_alignParentLeft", "true")
                    .withAttribute("android:layout_alignParentStart", "true")
                    .withAttribute("android:layout_marginTop", "100dp")
                    .withAttribute("android:layout_marginLeft", "100dp")
                    .withAttribute("android:layout_marginStart", "100dp"),

                component(CHECK_BOX)
                    .withBounds(300, 300, 20, 20)
                    .viewObject(mockViewWithBaseline(17))
                    .id("@id/checkbox")
                    .width("20dp")
                    .height("20dp")
                    .withAttribute("android:layout_below", "@id/button")
                    .withAttribute("android:layout_toRightOf", "@id/button")
                    .withAttribute("android:layout_marginLeft", "100dp")
                    .withAttribute("android:layout_marginTop", "100dp")
            ))
    val model = builder.build()
    assertEquals(1, model.components.size)
    assertEquals("NlComponent{tag=<RelativeLayout>, bounds=[0,0:1000x1000}\n" +
        "    NlComponent{tag=<Button>, bounds=[100,100:100x100}\n" +
        "    NlComponent{tag=<CheckBox>, bounds=[300,300:20x20}",
        NlTreeDumper.dumpTree(model.components))

    format(model.file)
    assertEquals("<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
        "    android:layout_width=\"match_parent\"\n" +
        "    android:layout_height=\"match_parent\">\n" +
        "\n" +
        "    <Button\n" +
        "        android:id=\"@id/button\"\n" +
        "        android:layout_width=\"100dp\"\n" +
        "        android:layout_height=\"100dp\"\n" +
        "        android:layout_alignParentTop=\"true\"\n" +
        "        android:layout_alignParentLeft=\"true\"\n" +
        "        android:layout_alignParentStart=\"true\"\n" +
        "        android:layout_marginTop=\"100dp\"\n" +
        "        android:layout_marginLeft=\"100dp\"\n" +
        "        android:layout_marginStart=\"100dp\" />\n" +
        "\n" +
        "    <CheckBox\n" +
        "        android:id=\"@id/checkbox\"\n" +
        "        android:layout_width=\"20dp\"\n" +
        "        android:layout_height=\"20dp\"\n" +
        "        android:layout_below=\"@id/button\"\n" +
        "        android:layout_toRightOf=\"@id/button\"\n" +
        "        android:layout_marginLeft=\"100dp\"\n" +
        "        android:layout_marginTop=\"100dp\" />\n" +
        "\n" +
        "</RelativeLayout>\n", model.file.text)
    return builder
  }
}
