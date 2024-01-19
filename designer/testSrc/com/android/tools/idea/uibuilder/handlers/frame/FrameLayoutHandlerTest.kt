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
package com.android.tools.idea.uibuilder.handlers.frame

import com.android.SdkConstants.*
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.util.NlTreeDumper
import com.android.tools.idea.uibuilder.model.SegmentType.*
import com.android.tools.idea.uibuilder.scene.SceneTest
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget

class FrameLayoutHandlerTest : SceneTest() {

  // needs to be rewritten for the Target architecture
  fun ignore_testDragNothing() {
    screen(myModel)
      .get("@id/myText1")
      .resize(TOP, RIGHT)
      .drag(0, 0)
      .release()
      .expectWidth("100dp")
      .expectHeight("100dp")
  }

  // needs to be rewritten for the Target architecture
  fun ignore_testCancel() {
    screen(myModel)
      .get("@id/myText1")
      .resize(TOP)
      .drag(20, 30)
      .cancel()
      .expectWidth("100dp")
      .expectHeight("100dp")
  }

  fun testResize() {
    myInteraction.select("myText1", true)
    myInteraction.mouseDown("myText1", ResizeBaseTarget.Type.RIGHT_BOTTOM)
    myInteraction.mouseRelease(220f, 230f)
    myScreen
      .get("@id/myText1")
      .expectXml(
        "<TextView\n" +
          "        android:id=\"@id/myText1\"\n" +
          "        android:layout_width=\"170dp\"\n" +
          "        android:layout_height=\"180dp\" />"
      )
  }

  fun testDrag() {
    myInteraction.select("myText1", true)
    myInteraction.mouseDown("myText1")
    myInteraction.mouseRelease(-100f, -100f)
    myScreen
      .get("@id/myText1")
      .expectXml(
        "<TextView\n" +
          "        android:id=\"@id/myText1\"\n" +
          "        android:layout_width=\"100dp\"\n" +
          "        android:layout_height=\"100dp\" />"
      )
  }

  override fun createModel(): ModelBuilder {
    val builder =
      model(
        "frame.xml",
        component(FRAME_LAYOUT)
          .withBounds(0, 0, 1000, 1000)
          .matchParentWidth()
          .matchParentHeight()
          .children(
            component(TEXT_VIEW)
              .withBounds(100, 100, 100, 100)
              .id("@id/myText1")
              .width("100dp")
              .height("100dp")
          ),
      )

    val model = builder.build()
    assertEquals(1, model.components.size)
    assertEquals(
      "NlComponent{tag=<FrameLayout>, bounds=[0,0:1000x1000}\n" +
        "    NlComponent{tag=<TextView>, bounds=[100,100:100x100}",
      NlTreeDumper.dumpTree(model.components),
    )
    format(model.file)

    return builder
  }
}
