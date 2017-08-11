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
import com.android.tools.idea.uibuilder.model.PreferenceUtils;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.PropertyTestCase;
import com.android.tools.idea.uibuilder.property.editors.NlComponentEditor;
import com.android.tools.idea.uibuilder.property.inspector.IdInspectorProvider.IdInspectorComponent;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

public class IdInspectorProviderTest extends PropertyTestCase {
  private IdInspectorProvider myProvider;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myProvider = new IdInspectorProvider();
  }

  public void testIsApplicable() {
    assertThat(isApplicable(myProvider, myMerge)).isTrue();
    assertThat(isApplicable(myProvider, myTextView)).isTrue();
    assertThat(isApplicable(myProvider, myCheckBox1)).isTrue();
    assertThat(isApplicable(myProvider, myProgressBar)).isTrue();
    assertThat(isApplicable(myProvider, myTextView, myCheckBox1, mySwitch)).isTrue();
    assertThat(isApplicable(myProvider, myTextView, myCheckBox1, mySwitch, myMerge)).isTrue();
  }

  public void testIsNotApplicableForPreferenceAndMenuComponents() {
    Map<String, NlProperty> properties = getPropertyMap(ImmutableList.of(myTextView));
    for (String tagName : PreferenceUtils.VALUES) {
      assertThat(myProvider.isApplicable(ImmutableList.of(mockComponentWithTag(tagName)), properties, myPropertiesManager)).isFalse();
    }
    assertThat(myProvider.isApplicable(ImmutableList.of(mockComponentWithTag(TAG_GROUP)), properties, myPropertiesManager)).isFalse();
    assertThat(myProvider.isApplicable(ImmutableList.of(mockComponentWithTag(TAG_ITEM)), properties, myPropertiesManager)).isFalse();
    assertThat(myProvider.isApplicable(ImmutableList.of(mockComponentWithTag(TAG_MENU)), properties, myPropertiesManager)).isFalse();
  }

  public void testInspectorComponent() {
    List<NlComponent> components = ImmutableList.of(myTextView);
    Map<String, NlProperty> properties = getPropertyMap(components);
    assertThat(myProvider.isApplicable(components, properties, myPropertiesManager)).isTrue();
    IdInspectorComponent inspector = myProvider.createCustomInspector(components, properties, myPropertiesManager);

    List<NlComponentEditor> editors = inspector.getEditors();
    assertThat(editors.size()).isEqualTo(3);
    assertThat(inspector.getMaxNumberOfRows()).isEqualTo(4);

    InspectorPanel panel = mock(InspectorPanel.class);
    when(panel.addComponent(anyString(), nullable(String.class), any())).thenReturn(new JLabel());
    inspector.attachToInspector(panel);

    for (NlComponentEditor editor : editors) {
      NlProperty property = editor.getProperty();
      assertThat(property).isNotNull();
      String propertyName = property.getName();
      if (propertyName.equals(ATTR_ID)) {
        propertyName = "ID";
      }
      verify(panel).addComponent(eq(propertyName), eq(null), eq(editor.getComponent()));
    }
    verify(panel, never()).addPanel(inspector.getConstraintPanel());
  }

  public void testUpdateProperties() {
    List<NlComponent> components = ImmutableList.of(myTextView);
    Map<String, NlProperty> properties = getPropertyMap(components);
    assertThat(myProvider.isApplicable(components, properties, myPropertiesManager)).isTrue();
    IdInspectorComponent inspector = myProvider.createCustomInspector(components, properties, myPropertiesManager);

    inspector.updateProperties(ImmutableList.of(), properties, myPropertiesManager);

    components = ImmutableList.of(myButtonInConstraintLayout);
    properties = getPropertyMap(components);
    inspector.updateProperties(components, properties, myPropertiesManager);
  }

  private static NlComponent mockComponentWithTag(@NotNull String tagName) {
    NlComponent component = mock(NlComponent.class);
    when(component.getTagName()).thenReturn(tagName);
    return component;
  }
}
