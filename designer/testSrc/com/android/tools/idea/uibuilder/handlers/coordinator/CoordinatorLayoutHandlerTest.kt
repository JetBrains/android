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

import com.android.AndroidXConstants
import com.android.SdkConstants
import com.android.tools.idea.common.LayoutTestUtilities
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.util.NlTreeDumper
import com.android.tools.idea.common.util.XmlTagUtil
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.scene.SceneTest
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget
import com.android.tools.idea.uibuilder.util.MockNlComponent
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.xml.XmlTag
import com.intellij.util.ui.UIUtil
import org.mockito.Mockito.mock

class CoordinatorLayoutHandlerTest : SceneTest() {

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
          "        android:layout_height=\"30dp\" />"
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
          "        android:layout_height=\"30dp\" />"
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
          "        android:layout_height=\"80dp\" />"
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
          "        android:layout_height=\"80dp\" />"
      )
  }

  fun testDragComponentToLeftTopSide() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox")
    myInteraction.mouseRelease(51f, 51f)
    myScreen
      .get("@id/checkbox")
      .expectXml(
        "<CheckBox\n" +
          "        android:id=\"@id/checkbox\"\n" +
          "        android:layout_width=\"20dp\"\n" +
          "        android:layout_height=\"20dp\"\n" +
          "        app:layout_anchor=\"@+id/button\"\n" +
          "        app:layout_anchorGravity=\"start|top\" />"
      )
  }

  fun testDragComponentToRightBottomSide() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox")
    myInteraction.mouseRelease(99f, 99f)
    myScreen
      .get("@id/checkbox")
      .expectXml(
        "<CheckBox\n" +
          "        android:id=\"@id/checkbox\"\n" +
          "        android:layout_width=\"20dp\"\n" +
          "        android:layout_height=\"20dp\"\n" +
          "        app:layout_anchor=\"@+id/button\"\n" +
          "        app:layout_anchorGravity=\"end|bottom\" />"
      )
  }

  fun testAddBottomAppBar() {
    val bottomAppBar =
      MockNlComponent.create(
        ApplicationManager.getApplication().runReadAction<XmlTag> {
          XmlTagUtil.createTag(
              project,
              "<${SdkConstants.BOTTOM_APP_BAR} android:id=\"@+id/bottomAppBar\"/>",
            )
            .apply { putUserData(ModuleUtilCore.KEY_MODULE, myModule) }
        }
      )
    val handler = CoordinatorLayoutHandler()
    val editor = mock(ViewEditor::class.java)
    handler.onChildInserted(myModel.treeReader.components.first(), bottomAppBar, InsertType.CREATE)
    UIUtil.dispatchAllInvocationEvents()

    val fab = myModel.treeReader.find("fab")!!
    assertThat(fab.getAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_LAYOUT_ANCHOR))
      .isEqualTo("@id/bottomAppBar")
    assertThat(fab.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_GRAVITY))
      .isNull()
    assertThat(fab.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN)).isNull()
  }

  fun testRemovingComponentWillRemoveAnchorAttribute() {
    // Move linear layout to frame layout. Button2 should remove anchor attributes.
    myInteraction.select("linear", true)
    myInteraction.mouseDown("linear")
    myInteraction.mouseRelease("frame", 30f, 30f)
    val p = myScreen.get("@id/button2").component.parent
    myScreen
      .get("@id/button2")
      .expectXml(
        "<Button\n" +
          "    android:id=\"@id/button2\"\n" +
          "    android:layout_width=\"100dp\"\n" +
          "    android:layout_height=\"100dp\" />"
      )
  }

  override fun createModel(): ModelBuilder {
    val builder =
      model(
        "coordinator.xml",
        component(AndroidXConstants.COORDINATOR_LAYOUT.newName())
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
              .height("20dp"),
            component(AndroidXConstants.FLOATING_ACTION_BUTTON.newName())
              .withBounds(200, 400, 64, 64)
              .id("@id/fab")
              .width("64dp")
              .height("64dp")
              .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_GRAVITY, "bottom")
              .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN, "15dp"),
            component(SdkConstants.LINEAR_LAYOUT)
              .withBounds(900, 900, 100, 100)
              .id("@id/linear")
              .width("100dp")
              .height("100dp"),
            component(SdkConstants.BUTTON)
              .withBounds(900, 900, 100, 100)
              .id("@id/button2")
              .width("100dp")
              .height("100dp")
              .withAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_LAYOUT_ANCHOR, "@id/linear")
              .withAttribute(
                SdkConstants.AUTO_URI,
                SdkConstants.ATTR_LAYOUT_ANCHOR_GRAVITY,
                "bottom|end",
              ),
            component(SdkConstants.FRAME_LAYOUT)
              .withBounds(500, 500, 400, 400)
              .id("@id/frame")
              .width("400dp")
              .height("400dp"),
          ),
      )
    val model = builder.build()
    assertEquals(1, model.treeReader.components.size)
    assertEquals(
      "NlComponent{tag=<androidx.coordinatorlayout.widget.CoordinatorLayout>, bounds=[0,0:1000x1000}\n" +
        "    NlComponent{tag=<Button>, bounds=[100,100:100x100}\n" +
        "    NlComponent{tag=<CheckBox>, bounds=[300,300:20x20}\n" +
        "    NlComponent{tag=<com.google.android.material.floatingactionbutton.FloatingActionButton>, bounds=[200,400:64x64}\n" +
        "    NlComponent{tag=<LinearLayout>, bounds=[900,900:100x100}\n" +
        "    NlComponent{tag=<Button>, bounds=[900,900:100x100}\n" +
        "    NlComponent{tag=<FrameLayout>, bounds=[500,500:400x400}",
      NlTreeDumper.dumpTree(model.treeReader.components),
    )

    format(model.file)
    assertEquals(
      "<androidx.coordinatorlayout.widget.CoordinatorLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
        "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
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
        "    <com.google.android.material.floatingactionbutton.FloatingActionButton\n" +
        "        android:id=\"@id/fab\"\n" +
        "        android:layout_width=\"64dp\"\n" +
        "        android:layout_height=\"64dp\"\n" +
        "        android:layout_gravity=\"bottom\"\n" +
        "        android:layout_margin=\"15dp\" />\n" +
        "\n" +
        "    <LinearLayout\n" +
        "        android:id=\"@id/linear\"\n" +
        "        android:layout_width=\"100dp\"\n" +
        "        android:layout_height=\"100dp\" />\n" +
        "\n" +
        "    <Button\n" +
        "        android:id=\"@id/button2\"\n" +
        "        android:layout_width=\"100dp\"\n" +
        "        android:layout_height=\"100dp\"\n" +
        "        app:layout_anchor=\"@id/linear\"\n" +
        "        app:layout_anchorGravity=\"bottom|end\" />\n" +
        "\n" +
        "    <FrameLayout\n" +
        "        android:id=\"@id/frame\"\n" +
        "        android:layout_width=\"400dp\"\n" +
        "        android:layout_height=\"400dp\" />\n" +
        "\n" +
        "</androidx.coordinatorlayout.widget.CoordinatorLayout>\n",
      model.file.text,
    )
    return builder
  }
}
