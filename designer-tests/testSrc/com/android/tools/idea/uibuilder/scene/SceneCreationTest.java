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

import com.android.tools.idea.uibuilder.fixtures.ComponentDescriptor;
import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Basic tests for creating and updating a Scene out of a NlModel
 */
public class SceneCreationTest extends SceneTest {

  public void testBasicScene() {
    myScreen.get("@id/button")
      .expectXml("<TextView\n" +
                 "    android:id=\"@id/button\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "    tools:layout_editor_absoluteX=\"100dp\"\n" +
                 "    tools:layout_editor_absoluteY=\"200dp\"/>");

    assertTrue(myInteraction.getDisplayList().getCommands().size() > 0);
    SceneComponent component = myScene.getSceneComponent("button");
    assertEquals(component.getScene(), myScene);
    NlComponent nlComponent = component.getNlComponent();
    assertEquals(component, myScene.getSceneComponent(nlComponent));
    assertEquals(myScene.pxToDp(myScene.dpToPx(100)), 100);
    myScene.setDpiFactor(3.5f);
    assertEquals(myScene.pxToDp(myScene.dpToPx(100)), 100);
  }

  public void testSceneCreation() {
    ModelBuilder builder = createModel();
    NlModel model = builder.build();
    Scene scene = Scene.createScene(model);
    scene.setDpiFactor(1);
    scene.setAnimate(false);
    assertEquals(scene.getRoot().getChildren().size(), 1);
    ComponentDescriptor parent = builder.findByPath(CONSTRAINT_LAYOUT);
    ComponentDescriptor textView = builder.findByPath(CONSTRAINT_LAYOUT, TEXT_VIEW);
    ComponentDescriptor editText = parent.addChild(component(EDIT_TEXT)
                                                     .withBounds(110, 220, 200, 30)
                                                     .width("200dp")
                                                     .height("30dp"), textView);
    builder.updateModel(model, false);
    scene.updateFrom(model);
    assertEquals(2, scene.getRoot().getChildren().size());
    List<SceneComponent> children = scene.getRoot().getChildren();
    SceneComponent sceneTextView = children.get(0);
    assertEquals(100, sceneTextView.getDrawX());
    assertEquals(200, sceneTextView.getDrawY());
    assertEquals(100, sceneTextView.getDrawWidth());
    assertEquals(20, sceneTextView.getDrawHeight());
    SceneComponent sceneEditText = children.get(1);
    assertEquals(110, sceneEditText.getDrawX());
    assertEquals(220, sceneEditText.getDrawY());
    assertEquals(200, sceneEditText.getDrawWidth());
    assertEquals(30, sceneEditText.getDrawHeight());
    parent.removeChild(textView);
    builder.updateModel(model, false);
    scene.updateFrom(model);
    assertEquals(1, scene.getRoot().getChildren().size());
    sceneTextView = scene.getRoot().getChildren().get(0);
    assertEquals(110, sceneTextView.getDrawX());
    assertEquals(220, sceneTextView.getDrawY());
    assertEquals(200, sceneTextView.getDrawWidth());
    assertEquals(30, sceneTextView.getDrawHeight());
  }


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
}