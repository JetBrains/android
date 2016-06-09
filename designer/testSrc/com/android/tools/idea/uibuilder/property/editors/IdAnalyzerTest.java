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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.LayoutTestUtilities;
import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.google.common.collect.ImmutableList;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.SdkConstants.*;

public class IdAnalyzerTest extends LayoutTestCase {

  public void testConstraintLayout() {
    ModelBuilder modelBuilder = createConstraintLayout();
    NlModel model = modelBuilder.build();
    NlComponent button2 = findById(model, "button3");
    NlProperty property = new NlPropertyItem(ImmutableList.of(button2), AUTO_URI, new AttributeDefinition(ATTR_LAYOUT_TOP_TO_TOP_OF));
    List<String> ids = IdAnalyzer.findIdsForProperty(property);
    assertEquals(ImmutableList.of("button4", "button5"), ids);
  }

  public void testRelativeLayout() {
    ModelBuilder modelBuilder = createRelativeLayout();
    NlModel model = modelBuilder.build();
    NlComponent button2 = findById(model, "button3");
    NlProperty property = new NlPropertyItem(ImmutableList.of(button2), ANDROID_URI, new AttributeDefinition(ATTR_LAYOUT_ALIGN_START));
    List<String> ids = IdAnalyzer.findIdsForProperty(property);
    assertEquals(ImmutableList.of("button4", "button5", "group1"), ids);
  }

  public void testRadioGroup() {
    ModelBuilder modelBuilder = createRelativeLayout();
    NlModel model = modelBuilder.build();
    NlComponent group = findById(model, "group1");
    NlProperty property = new NlPropertyItem(ImmutableList.of(group), ANDROID_URI, new AttributeDefinition(ATTR_CHECKED_BUTTON));
    List<String> ids = IdAnalyzer.findIdsForProperty(property);
    assertEquals(ImmutableList.of("radio_button1", "radio_button2", "radio_button3"), ids);
  }

  public void testDefaultHandler() {
    ModelBuilder modelBuilder = createRelativeLayout();
    NlModel model = modelBuilder.build();
    NlComponent button1 = findById(model, "button1");
    NlProperty labelFor = new NlPropertyItem(ImmutableList.of(button1), ANDROID_URI, new AttributeDefinition(ATTR_LABEL_FOR));
    List<String> ids = IdAnalyzer.findIdsForProperty(labelFor);
    assertEquals(ImmutableList.of("button2",
                                  "button3",
                                  "button4",
                                  "button5",
                                  "group1",
                                  "radio_button1",
                                  "radio_button2",
                                  "radio_button3",
                                  "text_view1"), ids);

    NlComponent button2 = findById(model, "button2");
    NlProperty accessibility =
      new NlPropertyItem(ImmutableList.of(button1, button2), ANDROID_URI, new AttributeDefinition(ATTR_ACCESSIBILITY_TRAVERSAL_BEFORE));
    ids = IdAnalyzer.findIdsForProperty(accessibility);
    assertEquals(ImmutableList.of("button3",
                                  "button4",
                                  "button5",
                                  "group1",
                                  "radio_button1",
                                  "radio_button2",
                                  "radio_button3",
                                  "text_view1"), ids);
  }

  private ModelBuilder createConstraintLayout() {
    return model("constraint.xml", component(CONSTRAINT_LAYOUT)
      .withBounds(0, 0, 1000, 1000)
      .matchParentWidth()
      .matchParentHeight()
      .children(
        component(BUTTON)
          .withBounds(0, 0, 100, 100)
          .wrapContentWidth()
          .wrapContentHeight()
          .withAttribute(AUTO_URI, ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, "@id/button1"),
        component(BUTTON)
          .withBounds(100, 100, 100, 100)
          .id("@+id/button1")
          .wrapContentWidth()
          .wrapContentHeight()
          .withAttribute(AUTO_URI, ATTR_LAYOUT_BOTTOM_TO_TOP_OF, "@id/button2"),
        component(BUTTON)
          .withBounds(100, 200, 100, 100)
          .id("@+id/button2")
          .wrapContentWidth()
          .wrapContentHeight()
          .withAttribute(AUTO_URI, ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, "@id/button3"),
        component(BUTTON)
          .withBounds(100, 300, 100, 100)
          .id("@+id/button3")
          .wrapContentWidth()
          .wrapContentHeight()
          .withAttribute(AUTO_URI, ATTR_LAYOUT_BOTTOM_TO_TOP_OF, "@id/button4"),
        component(BUTTON)
          .withBounds(100, 400, 100, 100)
          .id("@+id/button4")
          .wrapContentWidth()
          .wrapContentHeight(),
        component(BUTTON)
          .withBounds(100, 500, 100, 100)
          .id("@+id/button5")
          .wrapContentWidth()
          .wrapContentHeight()
      ));
  }

  @Override
  protected void tearDown() throws Exception {
    LayoutTestUtilities.resetComponentTestIds();
    super.tearDown();
  }

  private ModelBuilder createRelativeLayout() {
    return model("relative.xml", component(RELATIVE_LAYOUT)
      .withBounds(0, 0, 1000, 1000)
      .matchParentWidth()
      .matchParentHeight()
      .children(
        component(BUTTON)
          .withBounds(100, 0, 100, 100)
          .wrapContentWidth()
          .wrapContentHeight()
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_TO_LEFT_OF, "@id/button1"),
        component(BUTTON)
          .withBounds(100, 100, 100, 100)
          .id("@+id/button1")
          .wrapContentWidth()
          .wrapContentHeight()
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_TO_LEFT_OF, "@id/button2"),
        component(BUTTON)
          .withBounds(100, 200, 100, 100)
          .id("@+id/button2")
          .wrapContentWidth()
          .wrapContentHeight()
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW, "@id/button3"),
        component(BUTTON)
          .withBounds(100, 300, 100, 100)
          .id("@+id/button3")
          .wrapContentWidth()
          .wrapContentHeight()
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_TO_LEFT_OF, "@id/button4"),
        component(BUTTON)
          .withBounds(100, 400, 100, 100)
          .id("@+id/button4")
          .wrapContentWidth()
          .wrapContentHeight(),
        component(BUTTON)
          .withBounds(100, 500, 100, 100)
          .id("@+id/button5")
          .wrapContentWidth()
          .wrapContentHeight(),
        component(RADIO_GROUP)
          .withBounds(100, 600, 100, 400)
          .id("@+id/group1")
          .wrapContentWidth()
          .wrapContentHeight()
          .children(
            component(RADIO_BUTTON)
              .withBounds(100, 600, 100, 100)
              .id("@+id/radio_button1")
              .wrapContentWidth()
              .wrapContentHeight(),
            component(RADIO_BUTTON)
              .withBounds(100, 700, 100, 100)
              .id("@+id/radio_button2")
              .wrapContentWidth()
              .wrapContentHeight(),
            component(TEXT_VIEW)
              .withBounds(100, 800, 100, 100)
              .id("@+id/text_view1")
              .wrapContentWidth()
              .wrapContentHeight(),
            component(RADIO_BUTTON)
              .withBounds(100, 900, 100, 100)
              .id("@+id/radio_button3")
              .wrapContentWidth()
              .wrapContentHeight())
      ));
  }

  @NotNull
  private static NlComponent findById(@NotNull NlModel model, @NotNull String id) {
    return model.flattenComponents()
      .filter(component -> id.equals(component.getId()))
      .findFirst()
      .orElseThrow(() -> new RuntimeException("Id not found: " + id));
  }
}
