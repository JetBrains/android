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

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.android.tools.idea.uibuilder.property.PropertyTestCase;
import com.android.tools.idea.uibuilder.property.editors.NlComponentEditor;
import com.google.common.collect.ImmutableMap;
import icons.AndroidIcons;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.property.NlProperties.STARRED_PROP;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class FavoritesInspectorProviderTest extends PropertyTestCase {
  private FavoritesInspectorProvider myProvider;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myProvider = new FavoritesInspectorProvider();
  }

  public void testIsApplicable() {
    NlPropertyItem elevation = createFrom(myTextView, ATTR_ELEVATION);
    NlPropertyItem visibility = createFrom(myTextView, ATTR_VISIBILITY);

    // If no starred properties are available for the current selection then this provider is not applicable:
    assertThat(myProvider.isApplicable(Collections.emptyList(),
                                       Collections.emptyMap(),
                                       myPropertiesManager)).isFalse();
    assertThat(myProvider.isApplicable(Collections.emptyList(),
                                       Collections.singletonMap(ATTR_ELEVATION, elevation),
                                       myPropertiesManager)).isFalse();
    assertThat(myProvider.isApplicable(Collections.emptyList(),
                                       Collections.singletonMap(ATTR_VISIBILITY, visibility),
                                       myPropertiesManager)).isTrue();
  }

  public void testIsApplicableChangesWithStarSettings() {
    NlPropertyItem elevation = createFrom(myTextView, ATTR_ELEVATION);
    NlPropertyItem visibility = createFrom(myTextView, ATTR_VISIBILITY);

    assertThat(myProvider.isApplicable(Collections.emptyList(),
                                       Collections.singletonMap(ATTR_ELEVATION, elevation),
                                       myPropertiesManager)).isFalse();
    assertThat(myProvider.isApplicable(Collections.emptyList(),
                                       Collections.singletonMap(ATTR_VISIBILITY, visibility),
                                       myPropertiesManager)).isTrue();

    myPropertiesComponent.setValue(STARRED_PROP, ATTR_ELEVATION);

    assertThat(myProvider.isApplicable(Collections.emptyList(),
                                       Collections.singletonMap(ATTR_ELEVATION, elevation),
                                       myPropertiesManager)).isTrue();
    assertThat(myProvider.isApplicable(Collections.emptyList(),
                                       Collections.singletonMap(ATTR_VISIBILITY, visibility),
                                       myPropertiesManager)).isFalse();
  }


  public void testInspectorComponent() {
    NlPropertyItem elevation = createFrom(myTextView, ATTR_ELEVATION);
    NlPropertyItem visibility = createFrom(myTextView, ATTR_VISIBILITY);
    NlPropertyItem text = createFrom(myTextView, ATTR_TEXT);

    myPropertiesComponent.setValue(STARRED_PROP, TOOLS_NS_NAME_PREFIX + ATTR_ELEVATION + ";" + ATTR_VISIBILITY);
    List<NlComponent> components = Collections.singletonList(myTextView);
    Map<String, NlProperty> properties = ImmutableMap.of(ATTR_TEXT, text, ATTR_ELEVATION, elevation, ATTR_VISIBILITY, visibility);
    assertThat(myProvider.isApplicable(components, properties, myPropertiesManager)).isTrue();

    InspectorComponent inspector = myProvider.createCustomInspector(components, properties, myPropertiesManager);
    List<NlComponentEditor> editors = inspector.getEditors();
    assertThat(editors.size()).isEqualTo(2);
    assertThat(inspector.getMaxNumberOfRows()).isEqualTo(3);

    InspectorPanel panel = mock(InspectorPanel.class);
    when(panel.addComponent(anyString(), anyString(), any())).thenAnswer(invocation -> new JLabel());
    inspector.attachToInspector(panel);

    verify(panel).addTitle(eq("Favorite Attributes"));
    NlComponentEditor visibilityEditor = editors.get(1);
    assertThat(visibilityEditor.getProperty()).isSameAs(visibility);
    verify(panel).addComponent(eq(visibility.getName()), eq(visibility.getTooltipText()), eq(visibilityEditor.getComponent()));
    JLabel visibilityLabel = visibilityEditor.getLabel();
    assertThat(visibilityLabel).isNotNull();
    assertThat(visibilityLabel.getIcon()).isNull();

    NlComponentEditor elevationEditor = editors.get(0);
    NlProperty elevationDesignProperty = elevationEditor.getProperty();
    assertThat(elevationDesignProperty.getName()).isEqualTo(elevation.getName());
    assertThat(elevationDesignProperty.getDefinition()).isSameAs(elevation.getDefinition());
    assertThat(elevationDesignProperty.getNamespace()).isEqualTo(TOOLS_URI);
    verify(panel).addComponent(eq(elevationDesignProperty.getName()),
                               eq(elevationDesignProperty.getTooltipText()),
                               eq(elevationEditor.getComponent()));
    JLabel elevationLabel = elevationEditor.getLabel();
    assertThat(elevationLabel).isNotNull();
    assertThat(elevationLabel.getIcon()).isSameAs(AndroidIcons.NeleIcons.DesignProperty);
  }
}
