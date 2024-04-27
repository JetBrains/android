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

import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.NlComponent;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.awt.event.InputEvent;
import java.util.List;

import static com.android.AndroidXConstants.CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;

/**
 * Test selecting widgets
 */
public class SceneSelectionTest extends SceneTest {

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
                                       .withAttribute("tools:layout_editor_absoluteY", "500dp"),
                                     component(LINEAR_LAYOUT)
                                       .id("@id/linear")
                                       .withBounds(1200, 1200, 500, 500)
                                     .width("250dp")
                                     .height("250dp")
                                       .withAttribute("tools:layout_editor_absoluteX", "600dp")
                                       .withAttribute("tools:layout_editor_absoluteY", "600dp")
                                      .children(
                                        component(TEXT_VIEW)
                                          .id("@id/textView3")
                                          .withBounds(1200, 1200, 200, 200)
                                          .width("100dp")
                                          .height("100dp")
                                      )
                                   ));
    return builder;
  }

  public void testSelection() {
    List<NlComponent> componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(0, componentList.size());
    myInteraction.mouseDown(25, 25);
    myInteraction.mouseRelease(25, 25);
    componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(1, componentList.size());
    assertEquals(myScene.getSceneComponent("root").getNlComponent(), componentList.get(0));
    myInteraction.mouseDown("button");
    myInteraction.mouseRelease("button");
    componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(1, componentList.size());
    assertEquals(myScene.getSceneComponent("button").getNlComponent(), componentList.get(0));
    myInteraction.mouseDown("button2");
    myInteraction.mouseRelease("button2");
    componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(1, componentList.size());
    assertEquals(myScene.getSceneComponent("button2").getNlComponent(), componentList.get(0));
  }

  public void testLassoSelection() {
    List<NlComponent> componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(0, componentList.size());
    myInteraction.mouseDown(0, 0);
    myInteraction.mouseRelease(50, 50);
    componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(0, componentList.size());
    myInteraction.mouseDown(25, 25);
    myInteraction.mouseRelease(25, 25);
    componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(1, componentList.size());
    assertEquals(myScene.getSceneComponent("root").getNlComponent(), componentList.get(0));
    myInteraction.mouseDown(80, 190);
    myInteraction.mouseRelease(110, 210);
    componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(1, componentList.size());
    assertEquals(myScene.getSceneComponent("button").getNlComponent(), componentList.get(0));
    myInteraction.mouseDown(0, 0);
    myInteraction.mouseRelease(50, 50);
    componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(0, componentList.size());
    myInteraction.mouseDown(80, 190);
    myInteraction.mouseRelease(110, 510);
    componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(2, componentList.size());
    assertTrue(componentList.contains(myScene.getSceneComponent("button").getNlComponent()));
    assertTrue(componentList.contains(myScene.getSceneComponent("button2").getNlComponent()));
  }

  public void testFindComponent() {
    assertEquals(myScene.getSceneComponent(myScreen.findById("@id/button").getComponent()),
                 myScene.findComponent(myScreen.getScreen().getContext(), 101, 201));
    assertEquals(myScene.getSceneComponent(myScreen.findById("@id/button2").getComponent()),
                 myScene.findComponent(myScreen.getScreen().getContext(), 101, 501));
    assertEquals(myScene.getSceneComponent(myScreen.findById("@id/root").getComponent()),
                 myScene.findComponent(myScreen.getScreen().getContext(), 101, 101));
  }

  public void testFindNestedComponent() {
    assertEquals(myScene.getSceneComponent(myScreen.findById("@id/textView3").getComponent()),
                 myScene.findComponent(myScreen.getScreen().getContext(), 601, 601));
    assertEquals(myScene.getSceneComponent(myScreen.findById("@id/linear").getComponent()),
                 myScene.findComponent(myScreen.getScreen().getContext(), 801, 801));
  }

  public void testSelectionAfterDragging() {
    List<NlComponent> componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(0, componentList.size());

    SceneComponent button = myScene.getSceneComponent("button");
    SceneComponent button2 = myScene.getSceneComponent("button2");
    myScene.select(ImmutableList.of(button, button2));
    myInteraction.repaint();
    componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(2, componentList.size());

    myInteraction.mouseDown("button");
    myInteraction.mouseRelease(210, 410);
    myInteraction.repaint();
    componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(2, componentList.size());
  }
}
