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

import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.actions.DirectViewAction;
import com.android.tools.idea.uibuilder.api.actions.ViewAction;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.handlers.linear.LinearLayoutHandler;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.util.NlTreeDumper;
import com.google.common.collect.ImmutableList;
import com.intellij.testFramework.exceptionCases.EmptyStackExceptionCase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EmptyStackException;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.model.SegmentType.*;

public class LinearLayoutHandlerTest extends LayoutTestCase {

  public void testDragNothing() throws Exception {
    screen(createModel())
      .get("@id/myText1")
      .resize(TOP, RIGHT)
      .drag(0, 0)
      .release()
      .expectWidth("100dp")
      .expectHeight("100dp");
  }

  public void testCancel() throws Exception {
    screen(createModel())
      .get("@id/myText1")
      .resize(TOP)
      .drag(20, 30)
      .cancel()
      .expectWidth("100dp")
      .expectHeight("100dp");
  }

  // needs to be rewritten for the Target architecture
  public void ignore_testDragBottomRight() throws Exception {
    screen(createModel())
      .get("@id/myText1")
      .resize(BOTTOM, RIGHT)
      .drag(20, 30)
      .release()
      .expectWidth("60dp")
      .expectHeight("65dp");
  }

  // needs to be rewritten for the Target architecture
  public void ignore_testResizeTopLeft() throws Exception {
    screen(createModel())
      .get("@id/myText1")
      .resize(TOP, LEFT)
      .drag(-20, -30)
      .release()
      .expectWidth("60dp")
      .expectHeight("65dp");
  }

  public void testDrag() throws Exception {
    screen(createModel())
      .get("@id/myText1")
      .drag()
      .drag(20, 30)
      .release()
      .primary()
      .expectWidth("100dp")
      .expectHeight("100dp")
      .expectXml("<TextView\n" +
                 "        android:id=\"@id/myText1\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"100dp\" />");
  }

  @NotNull
  private SyncNlModel createModel() {
    ModelBuilder builder = model("linear.xml",
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
    final SyncNlModel model = builder.build();
    assertEquals(1, model.getComponents().size());
    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100}",
                 NlTreeDumper.dumpTree(model.getComponents()));
    format(model.getFile());
    return model;
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
    NlComponent component = model.find("root");
    assertNotNull(component);
    assertNoException(new EmptyStackExceptionCase() {
      @Override
      public void tryClosure() throws EmptyStackException {
        actions.stream()
          .filter(action -> action instanceof DirectViewAction)
          .map(action -> (DirectViewAction)action)
          .forEach(action -> action.perform(editor, delegatingViewGroupHandler, component, ImmutableList.of(), 0));
      }
    });
  }
}