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
import org.jetbrains.annotations.NotNull;

/**
 * Test dragging a widget
 */
public class MotionDragTest extends SceneTest {

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
                                       .withBounds(200, 400, 200, 40)
                                       .width("100dp")
                                       .height("20dp")
                                       .withAttribute("tools:layout_editor_absoluteX", "100dp")
                                       .withAttribute("tools:layout_editor_absoluteY", "200dp"),
                                     component(TEXT_VIEW)
                                       .id("@id/button2")
                                       .withBounds(200, 1000, 10, 10)
                                       .width("5dp")
                                       .height("5dp")
                                       .withAttribute("tools:layout_editor_absoluteX", "100dp")
                                       .withAttribute("tools:layout_editor_absoluteY", "500dp")
                                   ));
    return builder;
  }

  public void testDragRight() {
    myInteraction.mouseDown("button");
    myInteraction.mouseRelease(800, 400);
    myScreen.get("@id/button")
      .expectXml("<TextView\n" +
                 "        android:id=\"@id/button\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        tools:layout_editor_absoluteX=\"750dp\"\n" +
                 "        tools:layout_editor_absoluteY=\"390dp\" />");
  }

  public void testDragTooSmall() {
    // if the drag is too small, do not move anything if its not selected
    myInteraction.mouseDown("button");
    myInteraction.mouseRelease("button", 2, 2);
    myScreen.get("@id/button")
      .expectXml("<TextView\n" +
                 "    android:id=\"@id/button\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "    tools:layout_editor_absoluteX=\"100dp\"\n" +
                 "    tools:layout_editor_absoluteY=\"200dp\"/>");
  }

  public void testDragTooSmallWidget() {
    // click on the center, offset by (6, 6) -- so this should be outside of
    // the widget bounds, unless we correctly expanded the target.
    myInteraction.mouseDown("button2", 6, 6);
    myInteraction.mouseRelease(100, 200);
    myScreen.get("@id/button2")
      .expectXml("<TextView\n" +
                 "        android:id=\"@id/button2\"\n" +
                 "        android:layout_width=\"5dp\"\n" +
                 "        android:layout_height=\"5dp\"\n" +
                 "        tools:layout_editor_absoluteX=\"92dp\"\n" +
                 "        tools:layout_editor_absoluteY=\"192dp\" />");
  }

  public void testDragLeft() {
    myInteraction.mouseDown("button");
    myInteraction.mouseRelease(30, 400);
    myScreen.get("@id/button")
      .expectXml("<TextView\n" +
                 "        android:id=\"@id/button\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        tools:layout_editor_absoluteX=\"-20dp\"\n" +
                 "        tools:layout_editor_absoluteY=\"390dp\" />");
  }

  public void testDragCancel() {
    myInteraction.mouseDown("button");
    myInteraction.mouseCancel(100, 200);
    myScreen.get("@id/button")
      .expectXml("<TextView\n" +
                 "    android:id=\"@id/button\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "    tools:layout_editor_absoluteX=\"100dp\"\n" +
                 "    tools:layout_editor_absoluteY=\"200dp\"/>");
  }
}
