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
public class ScoutArrangeConnectTest extends SceneTest {

  public ScoutArrangeConnectTest() {
    super(false);
  }

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
                       .withBounds(100, 750, 200, 40)
                       .width("100dp")
                       .height("40dp"),
                     component(TEXT_VIEW)
                       .id("@+id/textview2")
                       .withBounds(400, 1050, 200, 30)
                       .width("200dp")
                       .height("30dp"),
                     component(TEXT_VIEW)
                       .id("@+id/textview3")
                       .withBounds(750, 1050, 200, 50)
                       .width("200dp")
                       .height("50dp")
                   ));
  }

  public void testConnectTop() {
    myScreen.get("@+id/textview2")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textview2\"\n" +
                 "    android:layout_width=\"200dp\"\n" +
                 "    android:layout_height=\"30dp\"/>");
    List<NlComponent> list = myModel.getTreeReader().getComponents().get(0).getChildren();
    Scout.arrangeWidgetsAndCommit(Scout.Arrange.ConnectTop, list, false);
    myScreen.get("@+id/textview2")
      .expectXml("<TextView\n" +
                 "        android:id=\"@+id/textview2\"\n" +
                 "        android:layout_width=\"200dp\"\n" +
                 "        android:layout_height=\"30dp\"\n" +
                 "        android:layout_marginTop=\"525dp\"\n" +
                 "        app:layout_constraintTop_toTopOf=\"parent\"\n" +
                 "        tools:layout_editor_absoluteX=\"200dp\" />");
  }

  public void testConnectBottom() {
    myScreen.get("@+id/textview2")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textview2\"\n" +
                 "    android:layout_width=\"200dp\"\n" +
                 "    android:layout_height=\"30dp\"/>");
    List<NlComponent> list = myModel.getTreeReader().getComponents().get(0).getChildren();
    System.out.println("list size" + list.size());
    Scout.arrangeWidgetsAndCommit(Scout.Arrange.ConnectBottom, list, false);
    myScreen.get("@+id/textview2")
      .expectXml("<TextView\n" +
                 "        android:id=\"@+id/textview2\"\n" +
                 "        android:layout_width=\"200dp\"\n" +
                 "        android:layout_height=\"30dp\"\n" +
                 "        android:layout_marginBottom=\"460dp\"\n" +
                 "        app:layout_constraintBottom_toBottomOf=\"parent\"\n" +
                 "        tools:layout_editor_absoluteX=\"200dp\" />");
  }

  public void testConnectStart() {
    myScreen.get("@+id/textview2")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textview2\"\n" +
                 "    android:layout_width=\"200dp\"\n" +
                 "    android:layout_height=\"30dp\"/>");
    List<NlComponent> list = myModel.getTreeReader().getComponents().get(0).getChildren();
    Scout.arrangeWidgetsAndCommit(Scout.Arrange.ConnectStart, list, false);
    myScreen.get("@+id/textview2")
      .expectXml("<TextView\n" +
                 "        android:id=\"@+id/textview2\"\n" +
                 "        android:layout_width=\"200dp\"\n" +
                 "        android:layout_height=\"30dp\"\n" +
                 "        android:layout_marginStart=\"200dp\"\n" +
                 "        android:layout_marginLeft=\"200dp\"\n" +
                 "        app:layout_constraintStart_toStartOf=\"parent\"\n" +
                 "        tools:layout_editor_absoluteY=\"525dp\" />");
  }

  public void testConnectEnd() {
    myScreen.get("@+id/textview2")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textview2\"\n" +
                 "    android:layout_width=\"200dp\"\n" +
                 "    android:layout_height=\"30dp\"/>");
    List<NlComponent> list = myModel.getTreeReader().getComponents().get(0).getChildren();
    Scout.arrangeWidgetsAndCommit(Scout.Arrange.ConnectEnd, list, false);
    myScreen.get("@+id/textview2")
      .expectXml("<TextView\n" +
                 "        android:id=\"@+id/textview2\"\n" +
                 "        android:layout_width=\"200dp\"\n" +
                 "        android:layout_height=\"30dp\"\n" +
                 "        android:layout_marginEnd=\"75dp\"\n" +
                 "        android:layout_marginRight=\"75dp\"\n" +
                 "        app:layout_constraintEnd_toStartOf=\"@+id/textview3\"\n" +
                 "        tools:layout_editor_absoluteY=\"525dp\" />");
  }

  public void testConnectEndGap() {
    myScreen.get("@+id/textview2")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textview2\"\n" +
                 "    android:layout_width=\"200dp\"\n" +
                 "    android:layout_height=\"30dp\"/>");
    List<NlComponent> list = myModel.getTreeReader().getComponents().get(0).getChildren();
    Scout.arrangeWidgetsAndCommit(Scout.Arrange.ConnectEnd, list, true);
    myScreen.get("@+id/textview2")
      .expectXml("<TextView\n" +
                 "        android:id=\"@+id/textview2\"\n" +
                 "        android:layout_width=\"200dp\"\n" +
                 "        android:layout_height=\"30dp\"\n" +
                 "        app:layout_constraintEnd_toStartOf=\"@+id/textview3\"\n" +
                 "        tools:layout_editor_absoluteY=\"525dp\" />");
  }
}