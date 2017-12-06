/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.common.command.NlWriteCommandAction;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.scout.Scout;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.android.SdkConstants.CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;

/**
 * Check that connections to parent if referenced by an id still works, also check the display list sorted result.
 */
public class ScoutGuidelineTest extends SceneTest {
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
                       .withBounds(200, 200, 200, 50)
                       .width("100dp")
                       .height("50dp"),
                     component(TEXT_VIEW)
                       .id("@+id/textview2")
                       .withBounds(500, 400, 200, 50)
                       .width("200dp")
                       .height("50dp"),
                     component(TEXT_VIEW)
                       .id("@+id/textview3")
                       .withBounds(800, 600, 200, 50)
                       .width("200dp")
                       .height("50dp"),
                     component("android.support.constraint.Guideline").id("@+id/guidelineVert")
                       .withBounds(600, 0, 1, 1990)
                       .withAttribute("android:orientation", "vertical")
                       .withAttribute("app:layout_constraintGuide_begin", "250dp")
                       .width("1dp")
                       .height("1000dp"),
                     component("android.support.constraint.Guideline").id("@+id/guidelineHor")
                       .withBounds(0, 440, 1990, 1)
                       .withAttribute("android:orientation", "horizontal")
                       .withAttribute("app:layout_constraintGuide_begin", "250dp")
                       .width("1000dp")
                       .height("1dp")
                   ));
  }

  public void testGuidelinConect1() {
    myScreen.get("@+id/textview1")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textview1\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"50dp\"/>");
    final List<NlComponent> list = Arrays.asList(myScreen.get("@+id/textview1").getComponent());
    Scout.arrangeWidgets(Scout.Arrange.ConnectTop, list, true);
    Scout.arrangeWidgets(Scout.Arrange.ConnectBottom, list, true);
    Scout.arrangeWidgets(Scout.Arrange.ConnectStart, list, true);
    Scout.arrangeWidgets(Scout.Arrange.ConnectEnd, list, true);

    NlWriteCommandAction
      .run(list, Scout.Arrange.ConnectTop.toString(), () -> list.forEach(component -> component.startAttributeTransaction().commit()));

    myScreen.get("@+id/textview1")
      .expectXml("<TextView\n" +
                 "        android:id=\"@+id/textview1\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"50dp\"\n" +
                 "        app:layout_constraintBottom_toTopOf=\"@+id/guidelineHor\"\n" +
                 "        app:layout_constraintEnd_toStartOf=\"@+id/guidelineVert\"\n" +
                 "        app:layout_constraintStart_toStartOf=\"parent\"\n" +
                 "        app:layout_constraintTop_toTopOf=\"parent\" />");
  }

  public void testGuidelinConect2() {
    myScreen.get("@+id/textview2")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textview2\"\n" +
                 "    android:layout_width=\"200dp\"\n" +
                 "    android:layout_height=\"50dp\"/>");
    final List<NlComponent> list = Arrays.asList(myScreen.get("@+id/textview2").getComponent());

    Scout.arrangeWidgets(Scout.Arrange.ConnectTop, list, true);
    Scout.arrangeWidgets(Scout.Arrange.ConnectBottom, list, true);
    Scout.arrangeWidgets(Scout.Arrange.ConnectStart, list, true);
    Scout.arrangeWidgets(Scout.Arrange.ConnectEnd, list, true);

    NlWriteCommandAction
      .run(list, Scout.Arrange.ConnectTop.toString(), () -> list.forEach(component -> component.startAttributeTransaction().commit()));
    myScreen.get("@+id/guidelineVert").expectXml("<android.support.constraint.Guideline\n" +
                                                 "    android:id=\"@+id/guidelineVert\"\n" +
                                                 "    android:orientation=\"vertical\"\n" +
                                                 "    app:layout_constraintGuide_begin=\"250dp\"\n" +
                                                 "    android:layout_width=\"1dp\"\n" +
                                                 "    android:layout_height=\"1000dp\"/>");
    myScreen.get("@+id/textview2")
      .expectXml("<TextView\n" +
                 "        android:id=\"@+id/textview2\"\n" +
                 "        android:layout_width=\"200dp\"\n" +
                 "        android:layout_height=\"50dp\"\n" +
                 "        app:layout_constraintBottom_toBottomOf=\"parent\"\n" +
                 "        app:layout_constraintEnd_toEndOf=\"parent\"\n" +
                 "        app:layout_constraintStart_toStartOf=\"parent\"\n" +
                 "        app:layout_constraintTop_toTopOf=\"parent\" />");
  }

  public void testGuidelinConect3() {
    myScreen.get("@+id/textview3")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textview3\"\n" +
                 "    android:layout_width=\"200dp\"\n" +
                 "    android:layout_height=\"50dp\"/>");
    final List<NlComponent> list = Arrays.asList(myScreen.get("@+id/textview3").getComponent());

    Scout.arrangeWidgets(Scout.Arrange.ConnectTop, list, true);
    Scout.arrangeWidgets(Scout.Arrange.ConnectBottom, list, true);
    Scout.arrangeWidgets(Scout.Arrange.ConnectStart, list, true);
    Scout.arrangeWidgets(Scout.Arrange.ConnectEnd, list, true);

    NlWriteCommandAction
      .run(list, Scout.Arrange.ConnectTop.toString(), () -> list.forEach(component -> component.startAttributeTransaction().commit()));

    myScreen.get("@+id/textview3")
      .expectXml("<TextView\n" +
                 "        android:id=\"@+id/textview3\"\n" +
                 "        android:layout_width=\"200dp\"\n" +
                 "        android:layout_height=\"50dp\"\n" +
                 "        app:layout_constraintBottom_toBottomOf=\"parent\"\n" +
                 "        app:layout_constraintEnd_toEndOf=\"parent\"\n" +
                 "        app:layout_constraintStart_toEndOf=\"@+id/guidelineVert\"\n" +
                 "        app:layout_constraintTop_toBottomOf=\"@+id/guidelineHor\" />");
  }
}