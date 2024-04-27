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
package com.android.tools.idea.uibuilder.scene;

import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.scout.Scout;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.AndroidXConstants.CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;

/**
 * Check that connections to parent if referenced by an id still works, also check the display list sorted result.
 */
public class ScoutArrangeTest extends SceneTest {
  @Override
  @NotNull
  public ModelBuilder createModel() {
    return model("constraint.xml",
                 component(CONSTRAINT_LAYOUT.defaultName())
                   .id("@+id/content_main")
                   .withBounds(0, 0, 2000, 2000)
                   .width("1000dp")
                   .height("1000dp")
                   .children(
                     component(TEXT_VIEW)
                       .id("@+id/textview1")
                       .withBounds(900, 980, 200, 40)
                       .width("100dp")
                       .height("20dp"),
                     component(TEXT_VIEW)
                       .id("@+id/textview2")
                       .withBounds(900, 1280, 300, 30)
                       .width("200dp")
                       .height("40dp")
                   ));
  }

  public void testCenter() {
    myScreen.get("@+id/textview1")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textview1\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"/>");
    List<NlComponent> list = myModel.getComponents().get(0).getChildren();
    Scout.arrangeWidgetsAndCommit(Scout.Arrange.CenterHorizontally, list,true);
    Scout.arrangeWidgetsAndCommit(Scout.Arrange.CenterVertically, list,true);
    myScreen.get("@+id/textview1")
      .expectXml("<TextView\n" +
                 "        android:id=\"@+id/textview1\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        app:layout_constraintBottom_toTopOf=\"@+id/textview2\"\n" +
                 "        app:layout_constraintEnd_toEndOf=\"parent\"\n" +
                 "        app:layout_constraintHorizontal_bias=\"0.5\"\n" +
                 "        app:layout_constraintStart_toStartOf=\"parent\"\n" +
                 "        app:layout_constraintTop_toTopOf=\"parent\"\n" +
                 "        app:layout_constraintVertical_bias=\"0.5\" />");

    buildScene();
    String simpleList = "DrawNlComponentFrame,0,0,1000,1000,1,1000,1000\n" +
                        "Clip,0,0,1000,1000\n" +
                        "DrawComponentBackground,450,490,100,20,1\n" +
                        "DrawTextRegion,450,490,100,20,0,16,false,false,4,5,28,1.0,\"TextView\"\n" +
                        "DrawNlComponentFrame,450,490,100,20,1,20,20\n" +
                        "DrawConnection,2,450x490x100x20,0,0x0x1000x1000,0,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,450x490x100x20,1,0x0x1000x1000,1,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,450x490x100x20,2,0x0x1000x1000,2,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,3,450x490x100x20,3,450x640x150x15,2,0,true,0,0,false,0.5,2,0,0\n" +
                        "DrawComponentBackground,450,640,150,15,1\n" +
                        "DrawTextRegion,450,640,150,15,0,12,false,false,4,5,28,1.0,\"TextView\"\n" +
                        "DrawNlComponentFrame,450,640,150,15,1,40,40\n" +
                        "DrawConnection,2,450x640x150x15,0,0x0x1000x1000,0,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,450x640x150x15,1,0x0x1000x1000,1,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,3,450x640x150x15,2,450x490x100x20,3,0,true,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,450x640x150x15,3,0x0x1000x1000,3,1,false,0,0,false,0.5,2,0,0\n" +
                        "UNClip\n";

    assertEquals(simpleList, myInteraction.getDisplayList().serialize());
  }

  public void testCenterHorizontallyInParent() {
    myScreen.get("@+id/textview1")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textview1\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"/>");
    List<NlComponent> list = myModel.getComponents().get(0).getChildren();
    Scout.arrangeWidgetsAndCommit(Scout.Arrange.CenterHorizontallyInParent, list,true);
    Scout.arrangeWidgetsAndCommit(Scout.Arrange.CenterVerticallyInParent, list,true);
    myScreen.get("@+id/textview1")
      .expectXml("<TextView\n" +
                 "        android:id=\"@+id/textview1\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        app:layout_constraintBottom_toBottomOf=\"parent\"\n" +
                 "        app:layout_constraintEnd_toEndOf=\"parent\"\n" +
                 "        app:layout_constraintHorizontal_bias=\"0.5\"\n" +
                 "        app:layout_constraintStart_toStartOf=\"parent\"\n" +
                 "        app:layout_constraintTop_toTopOf=\"parent\"\n" +
                 "        app:layout_constraintVertical_bias=\"0.5\" />");

    buildScene();
    String simpleList = "DrawNlComponentFrame,0,0,1000,1000,1,1000,1000\n" +
                        "Clip,0,0,1000,1000\n" +
                        "DrawComponentBackground,450,490,100,20,1\n" +
                        "DrawTextRegion,450,490,100,20,0,16,false,false,4,5,28,1.0,\"TextView\"\n" +
                        "DrawNlComponentFrame,450,490,100,20,1,20,20\n" +
                        "DrawConnection,2,450x490x100x20,0,0x0x1000x1000,0,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,450x490x100x20,1,0x0x1000x1000,1,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,450x490x100x20,2,0x0x1000x1000,2,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,450x490x100x20,3,0x0x1000x1000,3,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawComponentBackground,450,640,150,15,1\n" +
                        "DrawTextRegion,450,640,150,15,0,12,false,false,4,5,28,1.0,\"TextView\"\n" +
                        "DrawNlComponentFrame,450,640,150,15,1,40,40\n" +
                        "DrawConnection,2,450x640x150x15,0,0x0x1000x1000,0,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,450x640x150x15,1,0x0x1000x1000,1,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,450x640x150x15,2,0x0x1000x1000,2,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,450x640x150x15,3,0x0x1000x1000,3,1,false,0,0,false,0.5,2,0,0\n" +
                        "UNClip\n";

    assertEquals(simpleList, myInteraction.getDisplayList().serialize());
  }

  public void testAlignHorizontallyCenter() {
    myScreen.get("@+id/textview1")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textview1\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"/>");
    List<NlComponent> list = myModel.getComponents().get(0).getChildren();
    Scout.arrangeWidgetsAndCommit(Scout.Arrange.AlignHorizontallyCenter, list,true);
    myScreen.get("@+id/textview1")
      .expectXml("<TextView\n" +
                 "        android:id=\"@+id/textview1\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        app:layout_constraintEnd_toEndOf=\"@+id/textview2\"\n" +
                 "        app:layout_constraintStart_toStartOf=\"@+id/textview2\"\n" +
                 "        tools:layout_editor_absoluteY=\"490dp\" />");

    buildScene();
    String simpleList = "DrawNlComponentFrame,0,0,1000,1000,1,1000,1000\n" +
                        "Clip,0,0,1000,1000\n" +
                        "DrawComponentBackground,450,490,100,20,1\n" +
                        "DrawTextRegion,450,490,100,20,0,16,false,false,4,5,28,1.0,\"TextView\"\n" +
                        "DrawNlComponentFrame,450,490,100,20,1,20,20\n" +
                        "DrawConnection,6,450x490x100x20,0,450x640x150x15,0,0,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,6,450x490x100x20,1,450x640x150x15,1,0,false,0,0,false,0.5,2,0,0\n" +
                        "DrawComponentBackground,450,640,150,15,1\n" +
                        "DrawTextRegion,450,640,150,15,0,12,false,false,4,5,28,1.0,\"TextView\"\n" +
                        "DrawNlComponentFrame,450,640,150,15,1,40,40\n" +
                        "UNClip\n";

    assertEquals(simpleList, myInteraction.getDisplayList().serialize());
  }

}