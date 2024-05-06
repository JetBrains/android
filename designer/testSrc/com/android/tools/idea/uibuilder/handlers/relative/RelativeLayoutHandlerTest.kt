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
import com.android.tools.idea.common.LayoutTestUtilities.mockViewWithBaseline
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.scene.target.AnchorTarget
import com.android.tools.idea.common.util.NlTreeDumper
import com.android.tools.idea.uibuilder.scene.SceneTest
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget

class RelativeLayoutHandlerTest : SceneTest(false) {

  fun testResizeFromTopLeft() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", ResizeBaseTarget.Type.LEFT_TOP)
    myInteraction.mouseRelease(110f, 130f)
    myScreen
      .get("@id/checkbox")
      .expectXml(
        "<CheckBox\n" +
          "        android:id=\"@id/checkbox\"\n" +
          "        android:layout_width=\"50dp\"\n" +
          "        android:layout_height=\"30dp\"\n" +
          "        android:layout_below=\"@id/button\"\n" +
          "        android:layout_marginLeft=\"10dp\"\n" +
          "        android:layout_marginTop=\"30dp\"\n" +
          "        android:layout_toRightOf=\"@id/button\" />"
      )
  }

  fun testResizeFromTopRight() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", ResizeBaseTarget.Type.RIGHT_TOP)
    myInteraction.mouseRelease(220f, 130f)
    myScreen
      .get("@id/checkbox")
      .expectXml(
        "<CheckBox\n" +
          "        android:id=\"@id/checkbox\"\n" +
          "        android:layout_width=\"70dp\"\n" +
          "        android:layout_height=\"30dp\"\n" +
          "        android:layout_below=\"@id/button\"\n" +
          "        android:layout_marginLeft=\"50dp\"\n" +
          "        android:layout_marginTop=\"30dp\"\n" +
          "        android:layout_toRightOf=\"@id/button\" />"
      )
  }

  fun testResizeFromBottomLeft() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", ResizeBaseTarget.Type.LEFT_BOTTOM)
    myInteraction.mouseRelease(110f, 230f)
    myScreen
      .get("@id/checkbox")
      .expectXml(
        "<CheckBox\n" +
          "        android:id=\"@id/checkbox\"\n" +
          "        android:layout_width=\"50dp\"\n" +
          "        android:layout_height=\"80dp\"\n" +
          "        android:layout_below=\"@id/button\"\n" +
          "        android:layout_marginLeft=\"10dp\"\n" +
          "        android:layout_marginTop=\"50dp\"\n" +
          "        android:layout_toRightOf=\"@id/button\" />"
      )
  }

  fun testResizeFromBottomRight() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", ResizeBaseTarget.Type.RIGHT_BOTTOM)
    myInteraction.mouseRelease(220f, 230f)
    myScreen
      .get("@id/checkbox")
      .expectXml(
        "<CheckBox\n" +
          "        android:id=\"@id/checkbox\"\n" +
          "        android:layout_width=\"70dp\"\n" +
          "        android:layout_height=\"80dp\"\n" +
          "        android:layout_below=\"@id/button\"\n" +
          "        android:layout_marginLeft=\"50dp\"\n" +
          "        android:layout_marginTop=\"50dp\"\n" +
          "        android:layout_toRightOf=\"@id/button\" />"
      )
  }

  fun testDraggingComponentUpdatesConstraints() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox")
    myInteraction.mouseRelease(150f, 150f)
    myScreen
      .get("@id/checkbox")
      .expectXml(
        "<CheckBox\n" +
          "        android:id=\"@id/checkbox\"\n" +
          "        android:layout_width=\"20dp\"\n" +
          "        android:layout_height=\"20dp\"\n" +
          "        android:layout_below=\"@id/button\"\n" +
          "        android:layout_marginLeft=\"45dp\"\n" +
          "        android:layout_marginTop=\"45dp\"\n" +
          "        android:layout_toRightOf=\"@id/button\" />"
      )
  }

  fun testCreateAlignmentToParentLeft() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", AnchorTarget.Type.LEFT)
    myInteraction.mouseRelease("root", AnchorTarget.Type.LEFT)
    myScreen
      .get("@id/checkbox")
      .expectXml(
        "<CheckBox\n" +
          "        android:id=\"@id/checkbox\"\n" +
          "        android:layout_width=\"20dp\"\n" +
          "        android:layout_height=\"20dp\"\n" +
          "        android:layout_below=\"@id/button\"\n" +
          "        android:layout_alignParentLeft=\"true\"\n" +
          "        android:layout_marginLeft=\"150dp\"\n" +
          "        android:layout_marginTop=\"50dp\" />"
      )
  }

  fun testCreateAlignmentToParentTop() {
    // Test create anchor to parent
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", AnchorTarget.Type.TOP)
    myInteraction.mouseRelease("root", AnchorTarget.Type.TOP)
    myScreen
      .get("@id/checkbox")
      .expectXml(
        "<CheckBox\n" +
          "        android:id=\"@id/checkbox\"\n" +
          "        android:layout_width=\"20dp\"\n" +
          "        android:layout_height=\"20dp\"\n" +
          "        android:layout_alignParentTop=\"true\"\n" +
          "        android:layout_marginLeft=\"50dp\"\n" +
          "        android:layout_marginTop=\"150dp\"\n" +
          "        android:layout_toRightOf=\"@id/button\" />"
      )
  }

  fun testCreateAlignmentToParentRight() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", AnchorTarget.Type.RIGHT)
    myInteraction.mouseRelease("root", AnchorTarget.Type.RIGHT)
    myScreen
      .get("@id/checkbox")
      .expectXml(
        "<CheckBox\n" +
          "        android:id=\"@id/checkbox\"\n" +
          "        android:layout_width=\"20dp\"\n" +
          "        android:layout_height=\"20dp\"\n" +
          "        android:layout_below=\"@id/button\"\n" +
          "        android:layout_alignParentRight=\"true\"\n" +
          "        android:layout_marginLeft=\"50dp\"\n" +
          "        android:layout_marginTop=\"50dp\"\n" +
          "        android:layout_marginRight=\"340dp\"\n" +
          "        android:layout_toRightOf=\"@id/button\" />"
      )
  }

  fun testCreateAlignmentToParentBottom() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", AnchorTarget.Type.BOTTOM)
    myInteraction.mouseRelease("root", AnchorTarget.Type.BOTTOM)
    myScreen
      .get("@id/checkbox")
      .expectXml(
        "<CheckBox\n" +
          "        android:id=\"@id/checkbox\"\n" +
          "        android:layout_width=\"20dp\"\n" +
          "        android:layout_height=\"20dp\"\n" +
          "        android:layout_below=\"@id/button\"\n" +
          "        android:layout_alignParentBottom=\"true\"\n" +
          "        android:layout_marginLeft=\"50dp\"\n" +
          "        android:layout_marginTop=\"50dp\"\n" +
          "        android:layout_marginBottom=\"340dp\"\n" +
          "        android:layout_toRightOf=\"@id/button\" />"
      )
  }

  fun testCreateIllegalAlignmentToParent() {
    val originalXml =
      "<CheckBox\n" +
        "        android:id=\"@id/checkbox\"\n" +
        "        android:layout_width=\"20dp\"\n" +
        "        android:layout_height=\"20dp\"\n" +
        "        android:layout_below=\"@id/button\"\n" +
        "        android:layout_marginLeft=\"50dp\"\n" +
        "        android:layout_marginTop=\"50dp\"\n" +
        "        android:layout_toRightOf=\"@id/button\" />"

    val illegalChildToParentEdgePairs =
      mapOf(
        AnchorTarget.Type.TOP to AnchorTarget.Type.LEFT,
        AnchorTarget.Type.TOP to AnchorTarget.Type.RIGHT,
        AnchorTarget.Type.TOP to AnchorTarget.Type.BOTTOM,
        AnchorTarget.Type.LEFT to AnchorTarget.Type.TOP,
        AnchorTarget.Type.LEFT to AnchorTarget.Type.RIGHT,
        AnchorTarget.Type.LEFT to AnchorTarget.Type.BOTTOM,
        AnchorTarget.Type.BOTTOM to AnchorTarget.Type.TOP,
        AnchorTarget.Type.BOTTOM to AnchorTarget.Type.LEFT,
        AnchorTarget.Type.BOTTOM to AnchorTarget.Type.RIGHT,
        AnchorTarget.Type.RIGHT to AnchorTarget.Type.TOP,
        AnchorTarget.Type.RIGHT to AnchorTarget.Type.LEFT,
        AnchorTarget.Type.RIGHT to AnchorTarget.Type.BOTTOM
      )

    for ((childEdge, parentEdge) in illegalChildToParentEdgePairs) {
      myInteraction.select("checkbox", true)
      myInteraction.mouseDown("checkbox", childEdge)
      myInteraction.mouseRelease("root", parentEdge)
      myScreen.get("@id/checkbox").expectXml(originalXml)
    }
  }

  fun testCreateTopToTopAlignment() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", AnchorTarget.Type.TOP)
    myInteraction.mouseRelease("button", AnchorTarget.Type.TOP)
    myScreen
      .get("@id/checkbox")
      .expectXml(
        "<CheckBox\n" +
          "        android:id=\"@id/checkbox\"\n" +
          "        android:layout_width=\"20dp\"\n" +
          "        android:layout_height=\"20dp\"\n" +
          "        android:layout_alignTop=\"@+id/button\"\n" +
          "        android:layout_marginLeft=\"50dp\"\n" +
          "        android:layout_marginTop=\"100dp\"\n" +
          "        android:layout_toRightOf=\"@id/button\" />"
      )
  }

  fun testCreateTopToBottomAlignment() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", AnchorTarget.Type.TOP)
    myInteraction.mouseRelease("button", AnchorTarget.Type.BOTTOM)
    myScreen
      .get("@id/checkbox")
      .expectXml(
        "<CheckBox\n" +
          "        android:id=\"@id/checkbox\"\n" +
          "        android:layout_width=\"20dp\"\n" +
          "        android:layout_height=\"20dp\"\n" +
          "        android:layout_below=\"@+id/button\"\n" +
          "        android:layout_marginLeft=\"50dp\"\n" +
          "        android:layout_marginTop=\"50dp\"\n" +
          "        android:layout_toRightOf=\"@id/button\" />"
      )
  }

  fun testCreateLeftToLeftAlignment() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", AnchorTarget.Type.LEFT)
    myInteraction.mouseRelease("button", AnchorTarget.Type.LEFT)
    myScreen
      .get("@id/checkbox")
      .expectXml(
        "<CheckBox\n" +
          "        android:id=\"@id/checkbox\"\n" +
          "        android:layout_width=\"20dp\"\n" +
          "        android:layout_height=\"20dp\"\n" +
          "        android:layout_below=\"@id/button\"\n" +
          "        android:layout_alignLeft=\"@+id/button\"\n" +
          "        android:layout_marginLeft=\"100dp\"\n" +
          "        android:layout_marginTop=\"50dp\" />"
      )
  }

  fun testCreateLeftToRightAlignment() {
    // Same as the original one
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", AnchorTarget.Type.LEFT)
    myInteraction.mouseRelease("button", AnchorTarget.Type.RIGHT)
    myScreen
      .get("@id/checkbox")
      .expectXml(
        "<CheckBox\n" +
          "        android:id=\"@id/checkbox\"\n" +
          "        android:layout_width=\"20dp\"\n" +
          "        android:layout_height=\"20dp\"\n" +
          "        android:layout_below=\"@id/button\"\n" +
          "        android:layout_marginLeft=\"50dp\"\n" +
          "        android:layout_marginTop=\"50dp\"\n" +
          "        android:layout_toRightOf=\"@+id/button\" />"
      )
  }

  fun testCreateBottomToTopAlignment() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", AnchorTarget.Type.BOTTOM)
    myInteraction.mouseRelease("button", AnchorTarget.Type.TOP)
    myScreen
      .get("@id/checkbox")
      .expectXml(
        "<CheckBox\n" +
          "        android:id=\"@id/checkbox\"\n" +
          "        android:layout_width=\"20dp\"\n" +
          "        android:layout_height=\"20dp\"\n" +
          "        android:layout_above=\"@+id/button\"\n" +
          "        android:layout_below=\"@id/button\"\n" +
          "        android:layout_marginLeft=\"50dp\"\n" +
          "        android:layout_marginTop=\"50dp\"\n" +
          "        android:layout_marginBottom=\"-110dp\"\n" +
          "        android:layout_toRightOf=\"@id/button\" />"
      )
  }

  fun testCreateBottomToBottomAlignment() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", AnchorTarget.Type.BOTTOM)
    myInteraction.mouseRelease("button", AnchorTarget.Type.BOTTOM)
    myScreen
      .get("@id/checkbox")
      .expectXml(
        "<CheckBox\n" +
          "        android:id=\"@id/checkbox\"\n" +
          "        android:layout_width=\"20dp\"\n" +
          "        android:layout_height=\"20dp\"\n" +
          "        android:layout_below=\"@id/button\"\n" +
          "        android:layout_alignBottom=\"@+id/button\"\n" +
          "        android:layout_marginLeft=\"50dp\"\n" +
          "        android:layout_marginTop=\"50dp\"\n" +
          "        android:layout_marginBottom=\"-60dp\"\n" +
          "        android:layout_toRightOf=\"@id/button\" />"
      )
  }

  fun testCreateRightToLeftAlignment() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", AnchorTarget.Type.RIGHT)
    myInteraction.mouseRelease("button", AnchorTarget.Type.LEFT)
    myScreen
      .get("@id/checkbox")
      .expectXml(
        "<CheckBox\n" +
          "        android:id=\"@id/checkbox\"\n" +
          "        android:layout_width=\"20dp\"\n" +
          "        android:layout_height=\"20dp\"\n" +
          "        android:layout_below=\"@id/button\"\n" +
          "        android:layout_marginLeft=\"50dp\"\n" +
          "        android:layout_marginTop=\"50dp\"\n" +
          "        android:layout_marginRight=\"-110dp\"\n" +
          "        android:layout_toLeftOf=\"@+id/button\"\n" +
          "        android:layout_toRightOf=\"@id/button\" />"
      )
  }

  fun testCreateRightToRightAlignment() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", AnchorTarget.Type.RIGHT)
    myInteraction.mouseRelease("button", AnchorTarget.Type.RIGHT)
    myScreen
      .get("@id/checkbox")
      .expectXml(
        "<CheckBox\n" +
          "        android:id=\"@id/checkbox\"\n" +
          "        android:layout_width=\"20dp\"\n" +
          "        android:layout_height=\"20dp\"\n" +
          "        android:layout_below=\"@id/button\"\n" +
          "        android:layout_alignRight=\"@+id/button\"\n" +
          "        android:layout_marginLeft=\"50dp\"\n" +
          "        android:layout_marginTop=\"50dp\"\n" +
          "        android:layout_marginRight=\"-60dp\"\n" +
          "        android:layout_toRightOf=\"@id/button\" />"
      )
  }

  fun testCreateIllegalAlignmentToAnotherWidget() {
    val originalXml =
      "<CheckBox\n" +
        "    android:id=\"@id/checkbox\"\n" +
        "    android:layout_width=\"20dp\"\n" +
        "    android:layout_height=\"20dp\"\n" +
        "    android:layout_below=\"@id/button\"\n" +
        "    android:layout_toRightOf=\"@id/button\"\n" +
        "    android:layout_marginLeft=\"50dp\"\n" +
        "    android:layout_marginTop=\"50dp\"/>"

    val illegalWidgetToWidgetEdgePairs =
      mapOf(
        AnchorTarget.Type.TOP to AnchorTarget.Type.LEFT,
        AnchorTarget.Type.TOP to AnchorTarget.Type.RIGHT,
        AnchorTarget.Type.LEFT to AnchorTarget.Type.TOP,
        AnchorTarget.Type.LEFT to AnchorTarget.Type.BOTTOM,
        AnchorTarget.Type.BOTTOM to AnchorTarget.Type.LEFT,
        AnchorTarget.Type.BOTTOM to AnchorTarget.Type.RIGHT,
        AnchorTarget.Type.RIGHT to AnchorTarget.Type.TOP,
        AnchorTarget.Type.RIGHT to AnchorTarget.Type.BOTTOM
      )

    for ((childEdge, parentEdge) in illegalWidgetToWidgetEdgePairs) {
      myInteraction.select("checkbox", true)
      myInteraction.mouseDown("checkbox", childEdge)
      myInteraction.mouseRelease("button", parentEdge)
      myScreen.get("@id/checkbox").expectXml(originalXml)
    }
  }

  fun testChangeAlignmentByAnchor() {
    // Test change anchor.
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", AnchorTarget.Type.LEFT)
    myInteraction.mouseRelease("button", AnchorTarget.Type.LEFT)
    myScreen
      .get("@id/checkbox")
      .expectXml(
        "<CheckBox\n" +
          "        android:id=\"@id/checkbox\"\n" +
          "        android:layout_width=\"20dp\"\n" +
          "        android:layout_height=\"20dp\"\n" +
          "        android:layout_below=\"@id/button\"\n" +
          "        android:layout_alignLeft=\"@+id/button\"\n" +
          "        android:layout_marginLeft=\"100dp\"\n" +
          "        android:layout_marginTop=\"50dp\" />"
      )
  }

  fun testRemoveAlignmentByClickAnchor() {
    // Test click anchor to remove alignment.
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", AnchorTarget.Type.LEFT)
    myInteraction.mouseRelease("checkbox", AnchorTarget.Type.LEFT)
    myScreen
      .get("@id/checkbox")
      .expectXml(
        "<CheckBox\n" +
          "        android:id=\"@id/checkbox\"\n" +
          "        android:layout_width=\"20dp\"\n" +
          "        android:layout_height=\"20dp\"\n" +
          "        android:layout_below=\"@id/button\"\n" +
          "        android:layout_marginTop=\"50dp\" />"
      )

    myInteraction.mouseDown("checkbox", AnchorTarget.Type.TOP)
    myInteraction.mouseRelease("checkbox", AnchorTarget.Type.TOP)
    myScreen
      .get("@id/checkbox")
      .expectXml(
        "<CheckBox\n" +
          "        android:id=\"@id/checkbox\"\n" +
          "        android:layout_width=\"20dp\"\n" +
          "        android:layout_height=\"20dp\" />"
      )
  }

  fun testCannotCreateCycleAlignments() {
    myInteraction.select("button", true)
    myInteraction.mouseDown("button", AnchorTarget.Type.LEFT)
    myInteraction.mouseRelease("checkbox", AnchorTarget.Type.LEFT)
    myScreen
      .get("@id/button")
      .expectXml(
        "<Button\n" +
          "    android:id=\"@id/button\"\n" +
          "    android:layout_width=\"100dp\"\n" +
          "    android:layout_height=\"100dp\"\n" +
          "    android:layout_alignParentTop=\"true\"\n" +
          "    android:layout_alignParentLeft=\"true\"\n" +
          "    android:layout_alignParentStart=\"true\"\n" +
          "    android:layout_marginTop=\"50dp\"\n" +
          "    android:layout_marginLeft=\"50dp\"\n" +
          "    android:layout_marginStart=\"50dp\"/>"
      )
    myScreen
      .get("@id/checkbox")
      .expectXml(
        "<CheckBox\n" +
          "    android:id=\"@id/checkbox\"\n" +
          "    android:layout_width=\"20dp\"\n" +
          "    android:layout_height=\"20dp\"\n" +
          "    android:layout_below=\"@id/button\"\n" +
          "    android:layout_toRightOf=\"@id/button\"\n" +
          "    android:layout_marginLeft=\"50dp\"\n" +
          "    android:layout_marginTop=\"50dp\"/>"
      )
  }

  fun testCreateLinkToComponentWithoutIdCreatesANewId() {
    assertNull("TextView should not have an id yet", myScreen.findById("@+id/textView"))
    // TextView does not have an id. Attaching the checkbox will generate one.
    val textView = myScreen.findByTag("TextView")?.sceneComponent!!
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", AnchorTarget.Type.TOP)
    myInteraction.mouseRelease(textView, AnchorTarget.Type.BOTTOM)
    myScreen
      .findById("@+id/textView")!!
      .expectXml(
        "<TextView\n" +
          "         android:id=\"@+id/textView\"\n" +
          "         android:layout_width=\"20dp\"\n" +
          "         android:layout_height=\"20dp\"\n" +
          "         android:layout_marginLeft=\"50dp\"\n" +
          "         android:layout_marginTop=\"50dp\" />"
      )
    myScreen
      .get("@id/checkbox")
      .expectXml(
        "<CheckBox\n" +
          "         android:id=\"@id/checkbox\"\n" +
          "         android:layout_width=\"20dp\"\n" +
          "         android:layout_height=\"20dp\"\n" +
          "         android:layout_below=\"@+id/textView\"\n" +
          "         android:layout_marginLeft=\"50dp\"\n" +
          "         android:layout_marginTop=\"-160dp\"\n" +
          "         android:layout_toRightOf=\"@id/button\" />\n"
      )
  }

  override fun createModel(): ModelBuilder {
    val builder =
      model(
        "relative_kt.xml",
        component(RELATIVE_LAYOUT)
          .withBounds(0, 0, 1000, 1000)
          .id("@id/root")
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
              .withAttribute("android:layout_marginTop", "50dp")
              .withAttribute("android:layout_marginLeft", "50dp")
              .withAttribute("android:layout_marginStart", "50dp"),
            component(CHECK_BOX)
              .withBounds(300, 300, 20, 20)
              .viewObject(mockViewWithBaseline(17))
              .id("@id/checkbox")
              .width("20dp")
              .height("20dp")
              .withAttribute("android:layout_below", "@id/button")
              .withAttribute("android:layout_toRightOf", "@id/button")
              .withAttribute("android:layout_marginLeft", "50dp")
              .withAttribute("android:layout_marginTop", "50dp"),

            // Checkbox without id
            component(TEXT_VIEW)
              .withBounds(300, 600, 20, 20)
              .viewObject(mockViewWithBaseline(17))
              .width("20dp")
              .height("20dp")
              .withAttribute("android:layout_marginLeft", "50dp")
              .withAttribute("android:layout_marginTop", "50dp")
          )
      )
    val model = builder.build()
    assertEquals(1, model.components.size)
    assertEquals(
      "NlComponent{tag=<RelativeLayout>, bounds=[0,0:1000x1000}\n" +
        "    NlComponent{tag=<Button>, bounds=[100,100:100x100}\n" +
        "    NlComponent{tag=<CheckBox>, bounds=[300,300:20x20}\n" +
        "    NlComponent{tag=<TextView>, bounds=[300,600:20x20}",
      NlTreeDumper.dumpTree(model.components)
    )

    format(model.file)
    assertEquals(
      "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
        "    android:id=\"@id/root\"\n" +
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
        "        android:layout_marginTop=\"50dp\"\n" +
        "        android:layout_marginLeft=\"50dp\"\n" +
        "        android:layout_marginStart=\"50dp\" />\n" +
        "\n" +
        "    <CheckBox\n" +
        "        android:id=\"@id/checkbox\"\n" +
        "        android:layout_width=\"20dp\"\n" +
        "        android:layout_height=\"20dp\"\n" +
        "        android:layout_below=\"@id/button\"\n" +
        "        android:layout_toRightOf=\"@id/button\"\n" +
        "        android:layout_marginLeft=\"50dp\"\n" +
        "        android:layout_marginTop=\"50dp\" />\n" +
        "\n" +
        "    <TextView\n" +
        "        android:layout_width=\"20dp\"\n" +
        "        android:layout_height=\"20dp\"\n" +
        "        android:layout_marginLeft=\"50dp\"\n" +
        "        android:layout_marginTop=\"50dp\" />\n" +
        "\n" +
        "</RelativeLayout>\n",
      model.file.text
    )
    return builder
  }
}
