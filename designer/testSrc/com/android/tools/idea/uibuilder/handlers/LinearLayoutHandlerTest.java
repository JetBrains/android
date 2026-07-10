/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers;

import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.actions.DirectViewAction;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.api.actions.ViewAction;
import com.android.tools.idea.uibuilder.handlers.linear.LinearLayoutHandler;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.scene.SceneTest;
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EmptyStackException;

import static com.android.SdkConstants.*;

public class LinearLayoutHandlerTest extends SceneTest {

  public void testDragNothing() {
    myInteraction.select("myText1", true);
    // Mouse down on the bottom-right corner (at coordinate 200, 200)
    myInteraction.mouseDown("myText1", ResizeBaseTarget.Type.RIGHT_BOTTOM);
    // Releasing the mouse without crossing any threshold leaves the element at its initial size.
    myInteraction.mouseRelease(150f, 150f);
    myScreen.get("@id/myText1").expectWidth("100dp").expectHeight("100dp");
  }

  public void testCancel() {
    myInteraction.select("myText1", true);
    // Mouse down on the bottom-right corner (at coordinate 200, 200)
    myInteraction.mouseDown("myText1", ResizeBaseTarget.Type.RIGHT_BOTTOM);
    // Dragging to a new coordinate but canceling reverts the element to its initial size.
    myInteraction.mouseCancel(110f, 115f);
    myScreen.get("@id/myText1").expectWidth("100dp").expectHeight("100dp");
  }

  public void testDragBottomRight() {
    myInteraction.select("myText1", true);
    // Mouse down on the bottom-right corner (at coordinate 200, 200)
    myInteraction.mouseDown("myText1", ResizeBaseTarget.Type.RIGHT_BOTTOM);
    // Dragging to (110f, 115f) is constrained by minimum dimension bounds, resulting in width=60dp, height=65dp.
    myInteraction.mouseRelease(110f, 115f);
    myScreen.get("@id/myText1").expectWidth("60dp").expectHeight("65dp");
  }

  public void testResizeTopLeft() {
    NlComponent text1 = myScene.getSceneComponent("myText1").getNlComponent();
    // We set gravity to right|bottom so that the bottom-right of the element is anchored,
    // which allows the top-left corner to be resized.
    text1.setAttribute(ANDROID_URI, ATTR_LAYOUT_GRAVITY, "right|bottom");
    buildScene();
    myInteraction.select("myText1", true);
    // Mouse down on the top-left corner (at coordinate 100, 100)
    myInteraction.mouseDown("myText1", ResizeBaseTarget.Type.LEFT_TOP);
    // Dragging top-left to (120f, 130f) shrinks the size on the left & top sides by 20dp and 30dp respectively,
    // which results in width=20dp, height=30dp due to the Swing-to-Android transform mapping.
    myInteraction.mouseRelease(120f, 130f);
    myScreen.get("@id/myText1").expectWidth("20dp").expectHeight("30dp");
  }

  public void testDrag() {
    myInteraction.select("myText1", true);
    // Mouse down on the center to drag.
    myInteraction.mouseDown("myText1");
    // Dragging a child inside a LinearLayout does not change its dimensions.
    myInteraction.mouseRelease(110f, 115f);
    myScreen.get("@id/myText1")
      .expectWidth("100dp")
      .expectHeight("100dp")
      .expectXml("<TextView\n" +
                 "        android:id=\"@id/myText1\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"100dp\" />");
  }

  @NotNull
  @Override
  public ModelBuilder createModel() {
    // Layout Structure & Dimension Mappings:
    //
    // +------------------------------------------------------------+
    // | LinearLayout (0, 0, 1000, 1000)                            |
    // |                                                            |
    // |  (100, 100)                                                |
    // |  +-----------------------+                                 |
    // |  | TextView (myText1)    |                                 |
    // |  | Initial Size:         |                                 |
    // |  | width = 100dp         |                                 |
    // |  | height = 100dp        |                                 |
    // |  +-----------------------+ (200, 200)                      |
    // |                                                            |
    // |  (100, 200)                                                |
    // |  +-----------------------+                                 |
    // |  | Button (myText2)      |                                 |
    // |  | width = 100dp         |                                 |
    // |  | height = 100dp        |                                 |
    // |  +-----------------------+ (200, 300)                      |
    // +------------------------------------------------------------+
    //
    return model("linear.xml",
                 component(LINEAR_LAYOUT)
                   .withBounds(0, 0, 1000, 1000)
                   .matchParentWidth()
                   .matchParentHeight()
                   .children(
                     component(TEXT_VIEW)
                       .withBounds(100, 100, 100, 100)
                       .id("@id/myText1")
                       .width("100dp")
                       .height("100dp"),
                     component(BUTTON)
                       .withBounds(100, 200, 100, 100)
                       .id("@id/myText2")
                       .width("100dp")
                       .height("100dp")
                       .withAttribute("android:layout_weight", "1.0")
                   ));
  }

  /**
   * Check if the LinearLayoutHandler's actions can handle a DelegatingViewHandler which delegate to a LinearLayout.
   *
   * This is a check for b/37946463
   */
  public void testDelegatedHandler() {
    ModelBuilder builder = model("linear.xml",
                                 component(LINEAR_LAYOUT)
                                   .id("@id/root")
                                   .withBounds(0, 0, 1000, 1000)
                                   .matchParentWidth()
                                   .matchParentHeight()
                                   .children(
                                     component(TEXT_VIEW)
                                       .withBounds(100, 100, 100, 100)
                                       .id("@id/myText1")
                                       .width("100dp")
                                       .height("100dp")
                                   ));

    SyncNlModel model = builder.build();
    LinearLayoutHandler handler = new LinearLayoutHandler();
    ArrayList<ViewAction> actions = new ArrayList<>();
    handler.addToolbarActions(actions);
    ViewEditor editor = editor(screen(model).getScreen());
    DelegatingViewGroupHandler delegatingViewGroupHandler = new DelegatingViewGroupHandler(handler);
    NlComponent component = model.getTreeReader().find("root");
    assertNotNull(component);
    assertNoException(EmptyStackException.class, () -> {
        actions.stream()
          .filter(action -> action instanceof DirectViewAction)
          .map(action -> (DirectViewAction)action)
          .forEach(action -> action.perform(editor, delegatingViewGroupHandler, component, ImmutableList.of(), 0));
      }
    );
  }
}