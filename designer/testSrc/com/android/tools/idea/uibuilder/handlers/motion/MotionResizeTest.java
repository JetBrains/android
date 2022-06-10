/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion;

import static com.android.AndroidXConstants.MOTION_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;

import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.scene.SceneTest;
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget;
import org.jetbrains.annotations.NotNull;

/**
 * Test dragging a widget
 */
public class MotionResizeTest extends SceneTest {

  @Override
  @NotNull
  public ModelBuilder createModel() {
    ModelBuilder builder = model("constraint.xml",
                                 component(MOTION_LAYOUT.defaultName())
                                   .id("@id/root")
                                   .withBounds(0, 0, 2000, 2000)
                                   .width("1000dp")
                                   .height("1000dp")
                                   .withAttribute("android:padding", "20dp")
                                   .children(
                                     component(TEXT_VIEW)
                                       .id("@id/button")
                                       .withBounds(1000, 1000, 200, 40)
                                       .width("100dp")
                                       .height("20dp")
                                       .withAttribute("tools:layout_editor_absoluteX", "500dp")
                                       .withAttribute("tools:layout_editor_absoluteY", "500dp")
                                   ));
    return builder;
  }

  public void testResizeLeftTop() {
    myInteraction.select("button", true);
    myInteraction.mouseDown("button", ResizeBaseTarget.Type.LEFT_TOP);
    myInteraction.mouseRelease(300, 300);
    myScreen.get("@id/button")
      .expectXml("<TextView\n" +
                 "        android:id=\"@id/button\"\n" +
                 "        android:layout_width=\"300dp\"\n" +
                 "        android:layout_height=\"220dp\"\n" +
                 "        tools:layout_editor_absoluteX=\"300dp\"\n" +
                 "        tools:layout_editor_absoluteY=\"300dp\" />");
  }

  public void testResizeRightTop() {
    myInteraction.select("button", true);
    myInteraction.mouseDown("button", ResizeBaseTarget.Type.RIGHT_TOP);
    myInteraction.mouseRelease(900, 300);
    myScreen.get("@id/button")
      .expectXml("<TextView\n" +
                 "        android:id=\"@id/button\"\n" +
                 "        android:layout_width=\"400dp\"\n" +
                 "        android:layout_height=\"220dp\"\n" +
                 "        tools:layout_editor_absoluteX=\"500dp\"\n" +
                 "        tools:layout_editor_absoluteY=\"300dp\" />");
  }

  public void testResizeLeftBottom() {
    myInteraction.select("button", true);
    myInteraction.mouseDown("button", ResizeBaseTarget.Type.LEFT_BOTTOM);
    myInteraction.mouseRelease(300, 700);
    myScreen.get("@id/button")
      .expectXml("<TextView\n" +
                 "        android:id=\"@id/button\"\n" +
                 "        android:layout_width=\"300dp\"\n" +
                 "        android:layout_height=\"200dp\"\n" +
                 "        tools:layout_editor_absoluteX=\"300dp\"\n" +
                 "        tools:layout_editor_absoluteY=\"500dp\" />");
  }

  public void testResizeRightBottom() {
    myInteraction.select("button", true);
    myInteraction.mouseDown("button", ResizeBaseTarget.Type.RIGHT_BOTTOM);
    myInteraction.mouseRelease(900, 700);
    myScreen.get("@id/button")
      .expectXml("<TextView\n" +
                 "        android:id=\"@id/button\"\n" +
                 "        android:layout_width=\"400dp\"\n" +
                 "        android:layout_height=\"200dp\"\n" +
                 "        tools:layout_editor_absoluteX=\"500dp\"\n" +
                 "        tools:layout_editor_absoluteY=\"500dp\" />");
  }

  public void testResizeCancel() {
    myInteraction.select("button", true);
    myInteraction.mouseDown("button", ResizeBaseTarget.Type.LEFT_TOP);
    myInteraction.mouseCancel(300, 300);
    myScreen.get("@id/button")
      .expectXml("<TextView\n" +
                 "    android:id=\"@id/button\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "    tools:layout_editor_absoluteX=\"500dp\"\n" +
                 "    tools:layout_editor_absoluteY=\"500dp\"/>");
  }
}
