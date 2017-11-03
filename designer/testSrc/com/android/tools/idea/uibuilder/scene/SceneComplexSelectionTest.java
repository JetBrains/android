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

import static com.android.SdkConstants.CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;

/**
 * Test selecting widgets in nested hierarchy
 */
public class SceneComplexSelectionTest extends SceneTest {

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
                                     component(LINEAR_LAYOUT)
                                       .id("@id/linearlayout")
                                       .withAttribute("android:orientation", "vertical")
                                       .withBounds(100, 100, 100, 20)
                                       .width("100dp")
                                       .height("20dp")
                                       .withAttribute("tools:layout_editor_absoluteX", "100dp")
                                       .withAttribute("tools:layout_editor_absoluteY", "200dp").children(
                                       component(TEXT_VIEW)
                                         .id("@id/button")
                                         .withBounds(100, 100, 100, 20)
                                         .width("100dp")
                                         .height("20dp")
                                     ),
                                     component(TEXT_VIEW)
                                       .id("@id/button2")
                                       .withBounds(500, 500, 100, 20)
                                       .width("100dp")
                                       .height("20dp")
                                       .withAttribute("tools:layout_editor_absoluteX", "500dp")
                                       .withAttribute("tools:layout_editor_absoluteY", "500dp")
                                   ));
    return builder;
  }

  public void testSelection() {
    List<NlComponent> componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(0, componentList.size());
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
    myInteraction.mouseDown("button");
    myInteraction.mouseRelease("button", 100, 100);
    componentList = myScreen.getScreen().getSelectionModel().getSelection();
    assertEquals(1, componentList.size());
    assertEquals(myScene.getSceneComponent("linearlayout").getNlComponent(), componentList.get(0));
  }

}
