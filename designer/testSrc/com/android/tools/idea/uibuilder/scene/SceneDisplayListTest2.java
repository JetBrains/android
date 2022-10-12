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

import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.scene.draw.DisplayList;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;

import static com.android.AndroidXConstants.CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;

public class SceneDisplayListTest2 extends SceneTest {
  @Override
  @NotNull
  public ModelBuilder createModel() {
    return model("constraint.xml",
                 component(CONSTRAINT_LAYOUT.defaultName())
                   .id("@id/root")
                   .withBounds(0, 0, 2000, 2000)
                   .width("1000dp")
                   .height("1000dp")
                   .withAttribute("android:padding", "20dp")
                   .children(
                     component(TEXT_VIEW)
                       .id("@id/button")
                       .withBounds(900, 980, 200, 40)
                       .width("100dp")
                       .height("20dp")
                       .withAttribute("app:layout_constraintLeft_toLeftOf", "parent")
                       .withAttribute("app:layout_constraintRight_toRightOf", "parent")
                       .withAttribute("app:layout_constraintTop_toTopOf", "parent")
                       .withAttribute("app:layout_constraintBottom_toBottomOf", "parent")
                   ));
  }

  public void testBasicScene() {
    myScreen.get("@id/button")
      .expectXml("<TextView\n" +
                 "    android:id=\"@id/button\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "    app:layout_constraintLeft_toLeftOf=\"parent\"\n" +
                 "    app:layout_constraintRight_toRightOf=\"parent\"\n" +
                 "    app:layout_constraintTop_toTopOf=\"parent\"\n" +
                 "    app:layout_constraintBottom_toBottomOf=\"parent\"/>");

    String simpleList = "DrawNlComponentFrame,0,0,1000,1000,1,1000,1000\n" +
                        "Clip,0,0,1000,1000\n" +
                        "DrawComponentBackground,450,490,100,20,1\n" +
                        "DrawTextRegion,450,490,100,20,0,16,false,false,4,5,28,1.0,\"TextView\"\n" +
                        "DrawNlComponentFrame,450,490,100,20,1,20,20\n" +
                        "DrawConnection,2,450x490x100x20,0,0x0x1000x1000,0,1,false,0,0,false,0.5,0,0,0\n" +
                        "DrawConnection,2,450x490x100x20,1,0x0x1000x1000,1,1,false,0,0,false,0.5,0,0,0\n" +
                        "DrawConnection,2,450x490x100x20,2,0x0x1000x1000,2,1,false,0,0,false,0.5,0,0,0\n" +
                        "DrawConnection,2,450x490x100x20,3,0x0x1000x1000,3,1,false,0,0,false,0.5,0,0,0\n" +
                        "UNClip\n";

    assertEquals(simpleList, myInteraction.getDisplayList().serialize());
    DisplayList disp = DisplayList.getDisplayList(simpleList);
    assertEquals(simpleList, DisplayList.getDisplayList(simpleList).serialize());
    //noinspection UndesirableClassUsage
    BufferedImage img = new BufferedImage(2000, 2000,BufferedImage.TYPE_INT_ARGB);
    disp.paint(img.createGraphics(), SceneContext.get());
    assertEquals(10, disp.getCommands().size());
    disp.clear();
  }
}