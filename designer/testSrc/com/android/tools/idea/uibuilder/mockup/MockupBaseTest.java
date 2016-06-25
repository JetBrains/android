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
package com.android.tools.idea.uibuilder.mockup;

import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.LayoutTestUtilities;
import com.android.tools.idea.uibuilder.fixtures.ComponentDescriptor;
import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.model.NlModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.*;

public abstract class MockupBaseTest extends LayoutTestCase {

  public static final String DEFAULT_TEST_POSITION = "20 20 60 60 10 10 60 60";
  public static final String MOCKUP_PSD = "mockup/mockup.psd";

  protected NlModel createModel0Mockup() {
    ModelBuilder builder = model("relative.xml",
                                 component(RELATIVE_LAYOUT)
                                   .withBounds(0, 0, 1000, 1000)
                                   .matchParentWidth()
                                   .matchParentHeight());
    final NlModel model = builder.build();
    assertEquals(1, model.getComponents().size());
    return model;
  }

  protected NlModel createModel1Mockup(@NotNull String mockupFile, @Nullable String mockupPosition, @Nullable String opacity) {
    final ComponentDescriptor root = component(RELATIVE_LAYOUT)
      .withBounds(0, 0, 1000, 1000)
      .matchParentWidth()
      .matchParentHeight()
      .withAttribute("xmlns:tools", TOOLS_URI)
      .withAttribute(TOOLS_URI, ATTR_MOCKUP, mockupFile);

    if(mockupPosition != null) {
      root.withAttribute(TOOLS_URI, ATTR_MOCKUP_POSITION, mockupPosition);
    }

    if (opacity != null) {
      root.withAttribute(TOOLS_URI, ATTR_MOCKUP_OPACITY, opacity);
    }

    ModelBuilder builder = model("relative.xml", root);

    final NlModel model = builder.build();
    assertEquals(1, model.getComponents().size());
    return model;
  }

  protected NlModel createModel1Mockup(@NotNull String mockupFile, @Nullable String mockupPosition) {
    return createModel1Mockup(mockupFile, mockupPosition, null);
  }

  @NotNull
  protected NlModel createModel2Mockup(String mockupFile, @NotNull String mockupPosition) {
    ModelBuilder builder = model("relative.xml",
                                 component(RELATIVE_LAYOUT)
                                   .withBounds(0, 0, 1000, 1000)
                                   .matchParentWidth()
                                   .matchParentHeight()
                                   .withAttribute("xmlns:tools", TOOLS_URI)
                                   .withAttribute(TOOLS_URI, ATTR_MOCKUP, mockupFile)
                                   .withAttribute(TOOLS_URI, ATTR_MOCKUP_POSITION, mockupPosition)
                                   .children(
                                     component(LINEAR_LAYOUT)
                                       .withBounds(0, 0, 200, 200)
                                       .wrapContentWidth()
                                       .wrapContentHeight()
                                       .withAttribute("xmlns:tools", TOOLS_URI)
                                       .withAttribute(TOOLS_URI, ATTR_MOCKUP, mockupFile)
                                       .withAttribute(TOOLS_URI, ATTR_MOCKUP_POSITION, mockupPosition)
                                       .children(
                                         component(BUTTON)
                                           .withBounds(0, 0, 100, 100)
                                           .id("@+id/myButton")
                                           .width("100dp")
                                           .height("100dp")),
                                     component(TEXT_VIEW)
                                       .withBounds(0, 200, 100, 100)
                                       .id("@+id/myText")
                                       .width("100dp")
                                       .height("100dp"),
                                     component(ABSOLUTE_LAYOUT)
                                       .withBounds(0, 300, 400, 500)
                                       .width("400dp")
                                       .height("500dp")));
    final NlModel model = builder.build();
    assertEquals(1, model.getComponents().size());
    assertEquals(3, model.getComponents().get(0).getChildCount());

    assertEquals("NlComponent{tag=<RelativeLayout>, bounds=[0,0:1000x1000}\n" +
                 "    NlComponent{tag=<LinearLayout>, bounds=[0,0:200x200}\n" +
                 "        NlComponent{tag=<Button>, bounds=[0,0:100x100}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[0,200:100x100}\n" +
                 "    NlComponent{tag=<AbsoluteLayout>, bounds=[0,300:400x500}",
                 LayoutTestUtilities.toTree(model.getComponents()));
    return model;
  }
}
