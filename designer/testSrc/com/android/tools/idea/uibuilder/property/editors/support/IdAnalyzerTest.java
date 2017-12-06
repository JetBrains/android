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
package com.android.tools.idea.uibuilder.property.editors.support;

import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.property.PropertiesManager;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.google.common.collect.ImmutableList;
import com.intellij.util.xml.XmlName;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

public class IdAnalyzerTest extends LayoutTestCase {

  private PropertiesManager myPropertiesManager;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myPropertiesManager = mock(PropertiesManager.class);
  }

  public void testConstraintLayout() {
    ModelBuilder modelBuilder = createConstraintLayout();
    NlModel model = modelBuilder.build();
    NlComponent button2 = findById(model, "button3");
    NlProperty property = createFrom(ATTR_LAYOUT_TOP_TO_TOP_OF, AUTO_URI, button2);
    IdAnalyzer analyzer = new IdAnalyzer(property);
    List<String> ids = analyzer.findIds();
    assertEquals(ImmutableList.of("button4", "button5"), ids);
  }

  public void testOnRoot() {
    ModelBuilder modelBuilder = createConstraintLayout();
    NlModel model = modelBuilder.build();
    NlComponent constraint = findById(model, "constraint");
    NlProperty property = createFrom(ATTR_LAYOUT_ALIGN_LEFT, AUTO_URI, constraint);
    IdAnalyzer analyzer = new IdAnalyzer(property);
    List<String> ids = analyzer.findIds();
    assertThat(ids).isEmpty();
  }

  public void testRelativeLayout() {
    ModelBuilder modelBuilder = createRelativeLayout();
    NlModel model = modelBuilder.build();
    NlComponent button2 = findById(model, "button3");
    NlProperty property = createFrom(ATTR_LAYOUT_ALIGN_START, button2);
    IdAnalyzer analyzer = new IdAnalyzer(property);
    List<String> ids = analyzer.findIds();
    assertEquals(ImmutableList.of("button4", "button5", "group1"), ids);
  }

  public void testRadioGroup() {
    ModelBuilder modelBuilder = createRelativeLayout();
    NlModel model = modelBuilder.build();
    NlComponent group = findById(model, "group1");
    NlProperty property = createFrom(ATTR_CHECKED_BUTTON, group);
    IdAnalyzer analyzer = new IdAnalyzer(property);
    List<String> ids = analyzer.findIds();
    assertEquals(ImmutableList.of("radio_button1", "radio_button2", "radio_button3"), ids);
  }

  public void testButton1() {
    ModelBuilder modelBuilder = createRelativeLayout();
    NlModel model = modelBuilder.build();
    NlComponent button1 = findById(model, "button1");
    NlProperty labelFor = createFrom(ATTR_LABEL_FOR, button1);
    IdAnalyzer analyzer = new IdAnalyzer(labelFor);
    List<String> ids = analyzer.findIds();
    assertEquals(ImmutableList.of("button2",
                                  "button3",
                                  "button4",
                                  "button5",
                                  "group1",
                                  "radio_button1",
                                  "radio_button2",
                                  "radio_button3",
                                  "text_view1"), ids);
  }

  public void testButton1And2() {
    ModelBuilder modelBuilder = createRelativeLayout();
    NlModel model = modelBuilder.build();
    NlComponent button1 = findById(model, "button1");
    NlComponent button2 = findById(model, "button2");
    NlProperty accessibility = createFrom(ATTR_ACCESSIBILITY_TRAVERSAL_BEFORE, button1, button2);
    IdAnalyzer analyzer = new IdAnalyzer(accessibility);
    List<String> ids = analyzer.findIds();
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
    return model("constraint.xml", component(CONSTRAINT_LAYOUT.defaultName())
      .withBounds(0, 0, 1000, 1000)
      .id("@+id/constraint")
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

  @NotNull
  private NlProperty createFrom(@NotNull String attributeName, @NotNull NlComponent... components) {
    return createFrom(attributeName, ANDROID_URI, components);
  }

  @NotNull
  private NlProperty createFrom(@NotNull String attributeName, @NotNull String namespace, @NotNull NlComponent... components) {
    return NlPropertyItem.create(new XmlName(attributeName, namespace),
                                 new AttributeDefinition(attributeName),
                                 Arrays.asList(components),
                                 myPropertiesManager);
  }
}
