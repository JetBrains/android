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
import com.android.tools.idea.uibuilder.handlers.constraint.targets.ChainCycleTarget;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.BUTTON;
import static com.android.SdkConstants.CONSTRAINT_LAYOUT;

/**
 * Test cycling a chain
 */
public class SceneChainCycleTest extends SceneTest {

  @Override
  @NotNull
  public ModelBuilder createModel() {
    ModelBuilder builder = model("constraint.xml",
                                 component(CONSTRAINT_LAYOUT.defaultName())
                                   .id("@id/root")
                                   .withBounds(0, 0, 1000, 1000)
                                   .width("1000dp")
                                   .height("1000dp")
                                   .withAttribute("android:padding", "20dp")
                                   .children(
                                     component(BUTTON)
                                       .id("@id/button")
                                       .withBounds(300, 200, 100, 20)
                                       .width("100dp")
                                       .height("20dp")
                                       .withAttribute("app:layout_constraintRight_toRightOf", "parent")
                                       .withAttribute("app:layout_constraintLeft_toRightOf", "@+id/button2")
                                       .withAttribute("tools:layout_editor_absoluteY", "200dp"),
                                     component(BUTTON)
                                       .id("@id/button2")
                                       .withBounds(100, 200, 100, 20)
                                       .width("100dp")
                                       .height("20dp")
                                       .withAttribute("app:layout_constraintRight_toLeftOf", "@+id/button")
                                       .withAttribute("app:layout_constraintLeft_toLeftOf", "parent")
                                       .withAttribute("tools:layout_editor_absoluteY", "200dp"),
                                     component(BUTTON)
                                       .id("@id/button3")
                                       .withBounds(800, 300, 100, 20)
                                       .width("100dp")
                                       .height("20dp")
                                       .withAttribute("app:layout_constraintBottom_toBottomOf", "parent")
                                       .withAttribute("app:layout_constraintTop_toBottomOf", "@+id/button4")
                                       .withAttribute("tools:layout_editor_absoluteX", "800dp"),
                                     component(BUTTON)
                                       .id("@id/button4")
                                       .withBounds(800, 600, 100, 20)
                                       .width("100dp")
                                       .height("20dp")
                                       .withAttribute("app:layout_constraintBottom_toTopOf", "@+id/button3")
                                       .withAttribute("app:layout_constraintTop_toTopOf", "parent")
                                       .withAttribute("tools:layout_editor_absoluteX", "800dp")
                                   ));
    return builder;
  }

  public void testHorizontalCycle() {
    myInteraction.select("button", true);
    myInteraction.mouseDown("button", ChainCycleTarget.class, 0);
    myInteraction.mouseRelease("button", ChainCycleTarget.class, 0);
    myScreen.get("@id/button2")
      .expectXml("<Button\n" +
                 "        android:id=\"@id/button2\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        app:layout_constraintHorizontal_chainStyle=\"spread_inside\"\n" +
                 "        app:layout_constraintLeft_toLeftOf=\"parent\"\n" +
                 "        app:layout_constraintRight_toLeftOf=\"@+id/button\"\n" +
                 "        tools:layout_editor_absoluteY=\"200dp\" />");
    myInteraction.mouseDown("button", ChainCycleTarget.class, 0);
    myInteraction.mouseRelease("button", ChainCycleTarget.class, 0);
    myScreen.get("@id/button2")
      .expectXml("<Button\n" +
                 "        android:id=\"@id/button2\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        app:layout_constraintHorizontal_chainStyle=\"packed\"\n" +
                 "        app:layout_constraintLeft_toLeftOf=\"parent\"\n" +
                 "        app:layout_constraintRight_toLeftOf=\"@+id/button\"\n" +
                 "        tools:layout_editor_absoluteY=\"200dp\" />");
    myInteraction.mouseDown("button", ChainCycleTarget.class, 0);
    myInteraction.mouseRelease("button", ChainCycleTarget.class, 0);
    myScreen.get("@id/button2")
      .expectXml("<Button\n" +
                 "        android:id=\"@id/button2\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        app:layout_constraintHorizontal_chainStyle=\"spread\"\n" +
                 "        app:layout_constraintLeft_toLeftOf=\"parent\"\n" +
                 "        app:layout_constraintRight_toLeftOf=\"@+id/button\"\n" +
                 "        tools:layout_editor_absoluteY=\"200dp\" />");
    myInteraction.mouseDown("button", ChainCycleTarget.class, 0);
    myInteraction.mouseRelease("button", ChainCycleTarget.class, 0);
    myScreen.get("@id/button2")
      .expectXml("<Button\n" +
                 "        android:id=\"@id/button2\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        app:layout_constraintHorizontal_chainStyle=\"spread_inside\"\n" +
                 "        app:layout_constraintLeft_toLeftOf=\"parent\"\n" +
                 "        app:layout_constraintRight_toLeftOf=\"@+id/button\"\n" +
                 "        tools:layout_editor_absoluteY=\"200dp\" />");
  }

  public void testVerticalCycle() {
    myInteraction.select("button3", true);
    myInteraction.mouseDown("button3", ChainCycleTarget.class, 0);
    myInteraction.mouseRelease("button3", ChainCycleTarget.class, 0);
    myScreen.get("@id/button4")
      .expectXml("<Button\n" +
                 "        android:id=\"@id/button4\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        app:layout_constraintBottom_toTopOf=\"@+id/button3\"\n" +
                 "        app:layout_constraintTop_toTopOf=\"parent\"\n" +
                 "        app:layout_constraintVertical_chainStyle=\"spread_inside\"\n" +
                 "        tools:layout_editor_absoluteX=\"800dp\" />");
    myInteraction.mouseDown("button3", ChainCycleTarget.class, 0);
    myInteraction.mouseRelease("button3", ChainCycleTarget.class, 0);
    myScreen.get("@id/button4")
      .expectXml("<Button\n" +
                 "        android:id=\"@id/button4\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        app:layout_constraintBottom_toTopOf=\"@+id/button3\"\n" +
                 "        app:layout_constraintTop_toTopOf=\"parent\"\n" +
                 "        app:layout_constraintVertical_chainStyle=\"packed\"\n" +
                 "        tools:layout_editor_absoluteX=\"800dp\" />");
    myInteraction.mouseDown("button3", ChainCycleTarget.class, 0);
    myInteraction.mouseRelease("button3", ChainCycleTarget.class, 0);
    myScreen.get("@id/button4")
      .expectXml("<Button\n" +
                 "        android:id=\"@id/button4\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        app:layout_constraintBottom_toTopOf=\"@+id/button3\"\n" +
                 "        app:layout_constraintTop_toTopOf=\"parent\"\n" +
                 "        app:layout_constraintVertical_chainStyle=\"spread\"\n" +
                 "        tools:layout_editor_absoluteX=\"800dp\" />");
    myInteraction.mouseDown("button3", ChainCycleTarget.class, 0);
    myInteraction.mouseRelease("button3", ChainCycleTarget.class, 0);
    myScreen.get("@id/button4")
      .expectXml("<Button\n" +
                 "        android:id=\"@id/button4\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        app:layout_constraintBottom_toTopOf=\"@+id/button3\"\n" +
                 "        app:layout_constraintTop_toTopOf=\"parent\"\n" +
                 "        app:layout_constraintVertical_chainStyle=\"spread_inside\"\n" +
                 "        tools:layout_editor_absoluteX=\"800dp\" />");
  }
}