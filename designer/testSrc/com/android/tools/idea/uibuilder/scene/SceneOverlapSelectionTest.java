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
import com.android.tools.idea.common.model.NlComponent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.SdkConstants.BUTTON;
import static com.android.AndroidXConstants.CONSTRAINT_LAYOUT;

/**
 * Test selecting overlapping widgets
 */
public class SceneOverlapSelectionTest extends SceneTest {

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
                                     component(BUTTON)
                                       .id("@id/button1")
                                       .withBounds(200, 400, 200, 200)
                                       .width("100dp")
                                       .height("100dp")
                                       .withAttribute("tools:layout_editor_absoluteX", "100dp")
                                       .withAttribute("tools:layout_editor_absoluteY", "200dp"),
                                     component(BUTTON)
                                       .id("@id/button2")
                                       .withBounds(250, 330, 200, 200)
                                       .width("100dp")
                                       .height("100dp")
                                       .withAttribute("tools:layout_editor_absoluteX", "125dp")
                                       .withAttribute("tools:layout_editor_absoluteY", "165dp"),
                                     component(BUTTON)
                                       .id("@id/button3")
                                       .withBounds(290, 460, 200, 200)
                                       .width("100dp")
                                       .height("100dp")
                                       .withAttribute("tools:layout_editor_absoluteX", "145dp")
                                       .withAttribute("tools:layout_editor_absoluteY", "230dp")
                                   ));
    return builder;
  }

  public void testSelection() {
    List<NlComponent> componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(0, componentList.size());
    myInteraction.mouseDown(120, 280);
    myInteraction.mouseRelease(120, 280);
    componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(1, componentList.size());
    assertEquals(myScene.getSceneComponent("button1").getNlComponent(), componentList.get(0));
    myInteraction.select("button1", false);
    componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(0, componentList.size());
    myInteraction.mouseDown(140, 215);
    myInteraction.mouseRelease(140, 215);
    componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(1, componentList.size());
    assertEquals(myScene.getSceneComponent("button2").getNlComponent(), componentList.get(0));
    myInteraction.select("button2", false);
    myInteraction.mouseDown(160, 245);
    myInteraction.mouseRelease(160, 245);
    componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(1, componentList.size());
    assertEquals(myScene.getSceneComponent("button3").getNlComponent(), componentList.get(0));
  }

  public void testDragSelection() {
    myInteraction.select("button1", true);
    List<NlComponent> componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(1, componentList.size());
    assertEquals(myScene.getSceneComponent("button1").getNlComponent(), componentList.get(0));
    assertTrue(myScene.getSceneComponent("button1").isSelected());
    myInteraction.mouseDown(160, 245);
    myInteraction.mouseRelease(600, 600);
    componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(1, componentList.size());
    assertEquals(myScene.getSceneComponent("button1").getNlComponent(), componentList.get(0));
    myScreen.get("@id/button1")
      .expectXml("<Button\n" +
                 "        android:id=\"@id/button1\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"100dp\"\n" +
                 "        tools:layout_editor_absoluteX=\"540dp\"\n" +
                 "        tools:layout_editor_absoluteY=\"555dp\" />");
    myScreen.get("@id/button2")
      .expectXml("<Button\n" +
                 "    android:id=\"@id/button2\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"100dp\"\n" +
                 "    tools:layout_editor_absoluteX=\"125dp\"\n" +
                 "    tools:layout_editor_absoluteY=\"165dp\"/>");
    myScreen.get("@id/button3")
      .expectXml("<Button\n" +
                 "    android:id=\"@id/button3\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"100dp\"\n" +
                 "    tools:layout_editor_absoluteX=\"145dp\"\n" +
                 "    tools:layout_editor_absoluteY=\"230dp\"/>");
  }

  public void testDragSelection2() {
    myInteraction.select("button2", true);
    List<NlComponent> componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(1, componentList.size());
    assertEquals(myScene.getSceneComponent("button2").getNlComponent(), componentList.get(0));
    assertTrue(myScene.getSceneComponent("button2").isSelected());
    myInteraction.mouseDown(160, 245);
    myInteraction.mouseRelease(600, 600);
    componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(1, componentList.size());
    assertEquals(myScene.getSceneComponent("button2").getNlComponent(), componentList.get(0));
    myScreen.get("@id/button1")
      .expectXml("<Button\n" +
                 "    android:id=\"@id/button1\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"100dp\"\n" +
                 "    tools:layout_editor_absoluteX=\"100dp\"\n" +
                 "    tools:layout_editor_absoluteY=\"200dp\"/>");
    myScreen.get("@id/button2")
      .expectXml("<Button\n" +
                 "        android:id=\"@id/button2\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"100dp\"\n" +
                 "        tools:layout_editor_absoluteX=\"565dp\"\n" +
                 "        tools:layout_editor_absoluteY=\"520dp\" />");
    myScreen.get("@id/button3")
      .expectXml("<Button\n" +
                 "    android:id=\"@id/button3\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"100dp\"\n" +
                 "    tools:layout_editor_absoluteX=\"145dp\"\n" +
                 "    tools:layout_editor_absoluteY=\"230dp\"/>");
  }

  public void testDragSelection3() {
    myInteraction.select("button3", true);
    List<NlComponent> componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(1, componentList.size());
    assertEquals(myScene.getSceneComponent("button3").getNlComponent(), componentList.get(0));
    assertTrue(myScene.getSceneComponent("button3").isSelected());
    myInteraction.mouseDown(160, 245);
    myInteraction.mouseRelease(600, 600);
    componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(1, componentList.size());
    assertEquals(myScene.getSceneComponent("button3").getNlComponent(), componentList.get(0));
    myScreen.get("@id/button1")
      .expectXml("<Button\n" +
                 "    android:id=\"@id/button1\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"100dp\"\n" +
                 "    tools:layout_editor_absoluteX=\"100dp\"\n" +
                 "    tools:layout_editor_absoluteY=\"200dp\"/>");
    myScreen.get("@id/button2")
      .expectXml("<Button\n" +
                 "    android:id=\"@id/button2\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"100dp\"\n" +
                 "    tools:layout_editor_absoluteX=\"125dp\"\n" +
                 "    tools:layout_editor_absoluteY=\"165dp\"/>");
    myScreen.get("@id/button3")
      .expectXml("<Button\n" +
                 "        android:id=\"@id/button3\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"100dp\"\n" +
                 "        tools:layout_editor_absoluteX=\"585dp\"\n" +
                 "        tools:layout_editor_absoluteY=\"585dp\" />");
  }

  public void testDragSelection4() {
    myInteraction.select("button1", true);
    List<NlComponent> componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(1, componentList.size());
    assertEquals(myScene.getSceneComponent("button1").getNlComponent(), componentList.get(0));
    assertTrue(myScene.getSceneComponent("button1").isSelected());
    myInteraction.mouseDown(130, 210);
    myInteraction.mouseRelease(130, 210);
    componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(1, componentList.size());
    assertEquals(myScene.getSceneComponent("button2").getNlComponent(), componentList.get(0));
    myScreen.get("@id/button1")
      .expectXml("<Button\n" +
                 "    android:id=\"@id/button1\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"100dp\"\n" +
                 "    tools:layout_editor_absoluteX=\"100dp\"\n" +
                 "    tools:layout_editor_absoluteY=\"200dp\"/>");
    myScreen.get("@id/button2")
      .expectXml("<Button\n" +
                 "    android:id=\"@id/button2\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"100dp\"\n" +
                 "    tools:layout_editor_absoluteX=\"125dp\"\n" +
                 "    tools:layout_editor_absoluteY=\"165dp\"/>");
    myScreen.get("@id/button3")
      .expectXml("<Button\n" +
                 "        android:id=\"@id/button3\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"100dp\"\n" +
                 "        tools:layout_editor_absoluteX=\"145dp\"\n" +
                 "        tools:layout_editor_absoluteY=\"230dp\" />");
  }
}
