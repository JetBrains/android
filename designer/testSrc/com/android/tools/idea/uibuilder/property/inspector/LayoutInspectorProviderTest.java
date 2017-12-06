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
package com.android.tools.idea.uibuilder.property.inspector;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.property.inspector.InspectorComponent;
import com.android.tools.idea.common.property.inspector.InspectorPanel;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.android.tools.idea.uibuilder.property.PropertyTestCase;
import com.android.tools.idea.common.property.editors.NlComponentEditor;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.XmlName;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

public class LayoutInspectorProviderTest extends PropertyTestCase {

  private NlComponent myCoordinatorLayout;
  private NlComponent myAppBarLayout;
  private NlComponent myCollapsingToolbarLayout;
  private NlComponent myToolbar;
  private NlComponent myFakeImageView;
  private LayoutInspectorProvider myProvider;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myCoordinatorLayout = newFakeNlComponent(COORDINATOR_LAYOUT.defaultName());
    myAppBarLayout = newFakeNlComponent(APP_BAR_LAYOUT.defaultName());
    myCollapsingToolbarLayout = newFakeNlComponent(COLLAPSING_TOOLBAR_LAYOUT.defaultName());
    myToolbar = newFakeNlComponent(TOOLBAR_V7.defaultName());
    myFakeImageView = newFakeNlComponent(IMAGE_VIEW);
    myProvider = new LayoutInspectorProvider(getProject());
    initFakeHierarchy();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      // Null out all fields, since otherwise they're retained for the lifetime of the suite (which can be long if e.g. you're running many
      // tests through IJ)
      myCoordinatorLayout = null;
      myAppBarLayout = null;
      myCollapsingToolbarLayout = null;
      myToolbar = null;
      myFakeImageView = null;
      myProvider = null;
    }
    finally {
      super.tearDown();
    }
  }

  public void testIsApplicable() {
    assertThat(isApplicable(myProvider, myMerge)).isFalse();
    assertThat(isApplicable(myProvider, myTextView)).isFalse();
    assertThat(isApplicable(myProvider, myCheckBox1)).isFalse();
    assertThat(isApplicable(myProvider, myButton)).isFalse();
    assertThat(isApplicable(myProvider, myToolbar)).isTrue();
    assertThat(isApplicable(myProvider, myCollapsingToolbarLayout)).isTrue();
    assertThat(isApplicable(myProvider, myAppBarLayout)).isTrue();
    assertThat(isApplicable(myProvider, myToolbar, myFakeImageView)).isTrue();
  }

  public void testInspectorForCollapsingToolbarLayout() {
    assertInspectorPanel(myToolbar, "CollapsingToolbarLayout_layout", ATTR_LAYOUT_COLLAPSE_MODE, ATTR_COLLAPSE_PARALLAX_MULTIPLIER);
  }

  public void testInspectorForAppBarLayout() {
    assertInspectorPanel(myCollapsingToolbarLayout, "AppBarLayout_layout", ATTR_LAYOUT_SCROLL_FLAGS);
  }

  public void testInspectorForCoordinatorLayout() {
    assertInspectorPanel(myAppBarLayout, "CoordinatorLayout_layout", ATTR_LAYOUT_BEHAVIOR);
  }

  private void assertInspectorPanel(@NotNull NlComponent component, @NotNull String expectedTitle, @NotNull String... expectedProperties) {
    List<NlComponent> components = Collections.singletonList(component);
    Map<String, NlProperty> properties = getFakePropertyMap(components);
    assertThat(myProvider.isApplicable(components, properties, myPropertiesManager)).isTrue();
    InspectorComponent<NlPropertiesManager> inspector = myProvider.createCustomInspector(components, properties, myPropertiesManager);

    InspectorPanel panel = mock(InspectorPanel.class);
    when(panel.addComponent(anyString(), anyString(), any())).thenAnswer(invocation -> new JLabel());
    inspector.attachToInspector(panel);

    List<NlComponentEditor> editors = inspector.getEditors();
    assertThat(editors.size()).isEqualTo(expectedProperties.length);
    assertThat(inspector.getMaxNumberOfRows()).isEqualTo(expectedProperties.length + 1);
    verify(panel).addTitle(expectedTitle);

    int editorIndex = 0;
    for (String expectedPropertyName : expectedProperties) {
      verify(panel).addComponent(eq(expectedPropertyName), eq(expectedPropertyName), eq(editors.get(editorIndex++).getComponent()));
    }
  }

  // We do not have access to the design library in this type of test. Fake the hierarchy:
  private void initFakeHierarchy() {
    myCoordinatorLayout.addChild(myAppBarLayout);
    myAppBarLayout.addChild(myCollapsingToolbarLayout);
    myCollapsingToolbarLayout.addChild(myToolbar);
    myCollapsingToolbarLayout.addChild(myFakeImageView);
  }

  // We do not have access to the design library in this type of test. Fake the creation of the NlComponents:
  private NlComponent newFakeNlComponent(@NotNull String tagName) {
    XmlTag tag = mock(XmlTag.class);
    when(tag.getName()).thenReturn(tagName);
    when(tag.isValid()).thenReturn(true);
    when(tag.getProject()).thenReturn(myModel.getProject());

    return myModel.createComponent(tag);
  }

  // We do not have access to the design library in this type of test. Fake the properties:
  @NotNull
  private Map<String, NlProperty> getFakePropertyMap(@NotNull List<NlComponent> components) {
    Map<String, NlProperty> map = new HashMap<>();
    addFakeProperty(map, ATTR_LAYOUT_BEHAVIOR, components);
    addFakeProperty(map, ATTR_LAYOUT_SCROLL_FLAGS, components);
    addFakeProperty(map, ATTR_LAYOUT_COLLAPSE_MODE, components);
    addFakeProperty(map, ATTR_COLLAPSE_PARALLAX_MULTIPLIER, components);
    return map;
  }

  private void addFakeProperty(@NotNull Map<String, NlProperty> map, @NotNull String propertyName, @NotNull List<NlComponent> components) {
    AttributeDefinition definition = new AttributeDefinition(propertyName, null, null, Collections.emptyList());
    NlProperty property = NlPropertyItem.create(new XmlName(propertyName), definition, components, myPropertiesManager);
    map.put(propertyName, property);
  }
}
