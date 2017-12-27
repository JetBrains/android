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
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.android.tools.idea.uibuilder.property.PropertyTestCase;
import com.android.tools.idea.uibuilder.property.editors.NlComponentEditor;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
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

  public void testInspectorComponentCreation() {
    myPropertiesComponent.setValue(STARRED_PROP, TOOLS_NS_NAME_PREFIX + ATTR_ELEVATION + ";" + ATTR_VISIBILITY);
    InspectorComponent inspector = createInspector(myTextView);
    List<NlComponentEditor> editors = inspector.getEditors();
    assertThat(editors.size()).isEqualTo(2);
    assertThat(inspector.getMaxNumberOfRows()).isEqualTo(3);

    NlComponentEditor elevationEditor = editors.get(0);
    NlProperty elevation = elevationEditor.getProperty();
    assertThat(elevation.getName()).isEqualTo(ATTR_ELEVATION);
    assertThat(elevation.getNamespace()).isEqualTo(TOOLS_URI);
    assertThat(elevation.getComponents()).containsExactly(myTextView);

    NlComponentEditor visibilityEditor = editors.get(1);
    NlProperty visibility = visibilityEditor.getProperty();
    assertThat(visibility.getName()).isEqualTo(ATTR_VISIBILITY);
    assertThat(visibility.getNamespace()).isEqualTo(ANDROID_URI);
    assertThat(visibility.getComponents()).containsExactly(myTextView);
  }

  public void testInspectorComponentAdjustToNewlySelectedComponents() {
    myPropertiesComponent.setValue(STARRED_PROP, TOOLS_NS_NAME_PREFIX + ATTR_ELEVATION + ";" + ATTR_VISIBILITY);
    InspectorComponent inspector1 = createInspector(myTextView);
    InspectorComponent inspector2 = createInspector(myProgressBar);

    assertThat(inspector1).isSameAs(inspector2);
    List<NlComponentEditor> editors = inspector2.getEditors();
    NlComponentEditor elevationEditor = editors.get(0);
    NlProperty elevation = elevationEditor.getProperty();
    assertThat(elevation.getName()).isEqualTo(ATTR_ELEVATION);
    assertThat(elevation.getNamespace()).isEqualTo(TOOLS_URI);
    assertThat(elevation.getComponents()).containsExactly(myProgressBar);

    NlComponentEditor visibilityEditor = editors.get(1);
    NlProperty visibility = visibilityEditor.getProperty();
    assertThat(visibility.getName()).isEqualTo(ATTR_VISIBILITY);
    assertThat(visibility.getNamespace()).isEqualTo(ANDROID_URI);
    assertThat(visibility.getComponents()).containsExactly(myProgressBar);
  }

  public void testAttachToInspectorPanel() {
    myPropertiesComponent.setValue(STARRED_PROP, TOOLS_NS_NAME_PREFIX + ATTR_ELEVATION + ";" + ATTR_VISIBILITY);
    InspectorComponent inspector = createInspector(myTextView);

    InspectorPanel panel = mock(InspectorPanel.class);
    when(panel.addComponent(anyString(), anyString(), any())).thenAnswer(invocation -> new JLabel());
    inspector.attachToInspector(panel);

    verify(panel).addTitle(eq("Favorite Attributes"));
    List<NlComponentEditor> editors = inspector.getEditors();
    NlComponentEditor visibilityEditor = editors.get(1);
    NlProperty visibility = visibilityEditor.getProperty();
    assertThat(visibility.getName()).isEqualTo(ATTR_VISIBILITY);
    verify(panel).addComponent(eq(ATTR_VISIBILITY),
                               eq(visibility.getTooltipText()),
                               eq(visibilityEditor.getComponent()));
    JLabel visibilityLabel = visibilityEditor.getLabel();
    assertThat(visibilityLabel).isNotNull();
    assertThat(visibilityLabel.getIcon()).isNull();

    NlComponentEditor elevationEditor = editors.get(0);
    NlProperty elevation = elevationEditor.getProperty();
    assertThat(elevation.getName()).isEqualTo(ATTR_ELEVATION);
    verify(panel).addComponent(eq(elevation.getName()),
                               eq(elevation.getTooltipText()),
                               eq(elevationEditor.getComponent()));
    JLabel elevationLabel = elevationEditor.getLabel();
    assertThat(elevationLabel).isNotNull();
    assertThat(elevationLabel.getIcon()).isSameAs(AndroidIcons.NeleIcons.DesignProperty);
  }

  @NotNull
  private InspectorComponent createInspector(@NotNull NlComponent... componentArray) {
    List<NlComponent> components = Arrays.asList(componentArray);
    Map<String, NlProperty> properties = getPropertyMap(components);
    assertThat(myProvider.isApplicable(components, properties, myPropertiesManager)).isTrue();
    return myProvider.createCustomInspector(components, properties, myPropertiesManager);
  }
}
