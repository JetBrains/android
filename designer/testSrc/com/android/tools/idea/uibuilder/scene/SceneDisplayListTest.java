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

import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;

import static com.android.SdkConstants.CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;

public class SceneDisplayListTest extends SceneTest {
  @Override
  @NotNull
  public ModelBuilder createModel() {
    ModelBuilder builder = model("constraint.xml",
                                 component(CONSTRAINT_LAYOUT)
                                   .id("@id/root")
                                   .withBounds(0, 0, 1000, 1000)
                                   .width("1000dp")
                                   .height("1000dp")
                                   .withAttribute("android:padding", "20dp")
                                   .children(
                                     component(TEXT_VIEW)
                                       .id("@id/button")
                                       .withBounds(100, 200, 100, 20)
                                       .width("100dp")
                                       .height("20dp")
                                       .withAttribute("tools:layout_editor_absoluteX", "100dp")
                                       .withAttribute("tools:layout_editor_absoluteY", "200dp")
                                   ));
    return builder;
  }

  public void testBasicScene() {
    myScreen.get("@id/button")
      .expectXml("<TextView\n" +
                 "    android:id=\"@id/button\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "    tools:layout_editor_absoluteX=\"100dp\"\n" +
                 "    tools:layout_editor_absoluteY=\"200dp\"/>");

    String simpleList = "Rect,0,0,1000,1000,ffff0000\n" +
                        "Clip,0,0,1000,1000\n" +
                        "Rect,100,200,100,20,ff00ffff\n" +
                        "DrawComponent,100,200,100,20,0\n" +
                        "DrawComponent,100,200,100,20,0\n" +
                        "UNClip\n" +
                        "Rect,100,200,100,20,ff00ffff\n" +
                        "DrawComponent,100,200,100,20,0\n";

    assertEquals(myInteraction.getDisplayList().serialize(),simpleList );
    DisplayList disp = DisplayList.getDisplayList(simpleList);
    assertEquals(simpleList, DisplayList.getDisplayList(simpleList).serialize());
    //noinspection UndesirableClassUsage
    BufferedImage img = new BufferedImage(1000, 1000,BufferedImage.TYPE_INT_ARGB);
    disp.paint(img.createGraphics(), SceneContext.get());
  }
}