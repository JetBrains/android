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
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;

public class SceneDisplayListSortedTest extends SceneTest {
  @Override
  @NotNull
  public ModelBuilder createModel() {
    return model("constraint.xml",
                 component(CONSTRAINT_LAYOUT.defaultName())
                   .id("@+id/root")
                   .withBounds(0, 0, 2000, 2000)
                   .width("1000dp")
                   .height("1000dp")
                   .withAttribute("android:padding", "20dp")
                   .children(
                     component(LINEAR_LAYOUT)
                       .id("@+id/linear")
                       .withBounds(20, 20, 1980, 40)
                       .width("980dp")
                       .height("20dp")
                       .children(
                         component(TEXT_VIEW)
                           .id("@+id/button1")
                           .withBounds(20, 20, 1980, 40)
                           .width("100dp")
                           .height("20dp")
                       ),
                     component(LINEAR_LAYOUT)
                       .id("@+id/linear2")
                       .withBounds(20, 200, 1980, 40)
                       .width("980dp")
                       .height("20dp")
                       .children(
                         component(TEXT_VIEW)
                           .id("@+id/button2")
                           .withBounds(20, 200, 1980, 40)
                           .width("100dp")
                           .height("20dp")
                       )
                   ));
  }

  public void testBasicScene() {
    String simpleList = "DrawNlComponentFrame,0,0,1000,1000,1,1000,1000\n" +
                        "Clip,0,0,1000,1000\n" +
                        "DrawLinearLayout,10,10,990,20,1\n" +
                        "DrawNlComponentFrame,10,10,990,20,1,20,20\n" +
                        "Clip,10,10,990,20\n" +
                        "DrawComponentBackground,10,10,990,20,1\n" +
                        "DrawTextRegion,10,10,990,20,0,16,false,false,4,5,28,1.0,\"TextView\"\n" +
                        "DrawNlComponentFrame,10,10,990,20,1,20,20\n" +
                        "UNClip\n" +
                        "DrawLinearLayout,10,100,990,20,1\n" +
                        "DrawNlComponentFrame,10,100,990,20,1,20,20\n" +
                        "Clip,10,100,990,20\n" +
                        "DrawComponentBackground,10,100,990,20,1\n" +
                        "DrawTextRegion,10,100,990,20,0,16,false,false,4,5,28,1.0,\"TextView\"\n" +
                        "DrawNlComponentFrame,10,100,990,20,1,20,20\n" +
                        "UNClip\n" +
                        "UNClip\n";

    assertEquals(simpleList, myInteraction.getDisplayList().serialize());
    DisplayList disp = DisplayList.getDisplayList(simpleList);
    assertEquals(simpleList, DisplayList.getDisplayList(simpleList).serialize());
    //noinspection UndesirableClassUsage
    BufferedImage img = new BufferedImage(2000, 2000, BufferedImage.TYPE_INT_ARGB);
    disp.paint(img.createGraphics(), SceneContext.get());
    assertEquals(17, disp.getCommands().size());
    String result = disp.generateSortedDisplayList();
    String sorted = "DrawNlComponentFrame,0,0,1000,1000,1,1000,1000\n" +
                    "Clip,0,0,1000,1000\n" +
                    "DrawLinearLayout,10,10,990,20,1\n" +
                    "DrawNlComponentFrame,10,10,990,20,1,20,20\n" +
                    "Clip,10,10,990,20\n" +
                    "DrawComponentBackground,10,10,990,20,1\n" +
                    "DrawTextRegion,10,10,990,20,0,16,false,false,4,5,28,1.0,\"TextView\"\n" +
                    "DrawNlComponentFrame,10,10,990,20,1,20,20\n" +
                    "UNClip\n" +
                    "\n" +
                    "DrawLinearLayout,10,100,990,20,1\n" +
                    "DrawNlComponentFrame,10,100,990,20,1,20,20\n" +
                    "Clip,10,100,990,20\n" +
                    "DrawComponentBackground,10,100,990,20,1\n" +
                    "DrawTextRegion,10,100,990,20,0,16,false,false,4,5,28,1.0,\"TextView\"\n" +
                    "DrawNlComponentFrame,10,100,990,20,1,20,20\n" +
                    "UNClip\n" +
                    "\n" +
                    "UNClip\n" +
                    "\n";
    assertEquals(sorted, result);
    disp.clear();
  }
}