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
package com.android.tools.idea.uibuilder.scene;

import com.android.tools.idea.common.fixtures.ModelBuilder;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;

/**
 * Test dragging a widget connected top-left
 */
public class SceneDragMarginFromWidgetTest extends SceneTest {

  @Override
  @NotNull
  public ModelBuilder createModel() {
    ModelBuilder builder = model("constraint.xml",
                                 component(CONSTRAINT_LAYOUT.defaultName())
                                   .id("@id/root")
                                   .withBounds(0, 0, 2000, 2000)
                                   .width("1000dp")
                                   .height("1000dp")
                                   .withAttribute("android:padding", "20dp")
                                   .children(
                                     component(TEXT_VIEW)
                                       .id("@id/button1")
                                       .withBounds(200, 400, 200, 40)
                                       .width("100dp")
                                       .height("20dp")
                                       .withAttribute("tools:layout_editor_absoluteX", "100dp")
                                       .withAttribute("tools:layout_editor_absoluteY", "200dp"),
                                     component(TEXT_VIEW)
                                       .id("@id/button2")
                                       .withBounds(400, 400, 200, 40)
                                       .width("100dp")
                                       .height("20dp")
                                       .withAttribute("app:layout_constraintLeft_toRightOf", "@+id/button1")
                                       .withAttribute("tools:layout_editor_absoluteY", "200dp")
                                   ));
    return builder;
  }

  public void testDragRight() {
    myInteraction.mouseDown("button2");
    myInteraction.mouseRelease(800, 210);
    myScreen.get("@id/button2")
      .expectXml("<TextView\n" +
                 "        android:id=\"@id/button2\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_marginLeft=\"548dp\"\n" +
                 "        app:layout_constraintLeft_toRightOf=\"@+id/button1\"\n" +
                 "        tools:layout_editor_absoluteY=\"200dp\" />");
  }

}
