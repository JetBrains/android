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
import com.android.tools.idea.uibuilder.property.PropertyTestCase;
import com.android.tools.idea.common.property.editors.BaseComponentEditor;
import com.android.tools.idea.common.property.editors.NlComponentEditor;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.android.SdkConstants.*;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class ProgressBarInspectorProviderTest extends PropertyTestCase {
  private ProgressBarInspectorProvider myProvider;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myProvider = new ProgressBarInspectorProvider();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      // Null out all fields, since otherwise they're retained for the lifetime of the suite (which can be long if e.g. you're running many
      // tests through IJ)
      myProvider = null;
    }
    finally {
      super.tearDown();
    }
  }

  public void testIsApplicable() {
    assertThat(isApplicable(myProvider, myProgressBar)).isTrue();
    assertThat(isApplicable(myProvider, myMerge)).isFalse();
    assertThat(isApplicable(myProvider, myTextView)).isFalse();
    assertThat(isApplicable(myProvider, myCheckBox1)).isFalse();
  }

  public void testInspectorComponent() {
    List<NlComponent> components = ImmutableList.of(myProgressBar);
    Map<String, NlProperty> properties = getPropertyMap(components);
    assertThat(myProvider.isApplicable(components, properties, myPropertiesManager)).isTrue();

    InspectorComponent<NlPropertiesManager> inspector = myProvider.createCustomInspector(components, properties, myPropertiesManager);
    List<NlComponentEditor> editors = inspector.getEditors();
    assertThat(editors.size()).isEqualTo(10);
    assertThat(inspector.getMaxNumberOfRows()).isEqualTo(11);

    InspectorPanel panel = mock(InspectorPanel.class);
    when(panel.addComponent(anyString(), anyString(), any())).thenAnswer(invocation -> new JLabel());
    inspector.attachToInspector(panel);

    verify(panel).addTitle(eq(PROGRESS_BAR));
    for (NlComponentEditor editor : editors) {
      NlProperty property = editor.getProperty();
      assertThat(property).isNotNull();
      verify(panel).addComponent(
        eq(property.getName()),
        eq(property.getTooltipText()),
        eq(editor.getComponent()));
    }
  }

  public void testChangingIndeterminateChangesVisibilityOfEditors() {
    List<NlComponent> components = ImmutableList.of(myProgressBar);
    Map<String, NlProperty> properties = getPropertyMap(components);
    assertThat(myProvider.isApplicable(components, properties, myPropertiesManager)).isTrue();
    InspectorComponent<NlPropertiesManager> inspector = myProvider.createCustomInspector(components, properties, myPropertiesManager);
    inspector.refresh();

    assertThat(isEditorVisible(inspector, ATTR_STYLE, "")).isTrue();
    assertThat(isEditorVisible(inspector, ATTR_PROGRESS_DRAWABLE)).isTrue();
    assertThat(isEditorVisible(inspector, ATTR_INDETERMINATE_DRAWABLE)).isFalse();
    assertThat(isEditorVisible(inspector, ATTR_PROGRESS_TINT)).isTrue();
    assertThat(isEditorVisible(inspector, ATTR_INDETERMINATE_TINT)).isFalse();
    assertThat(isEditorVisible(inspector, ATTR_MAXIMUM)).isTrue();
    assertThat(isEditorVisible(inspector, ATTR_PROGRESS)).isTrue();
    assertThat(isEditorVisible(inspector, ATTR_VISIBILITY)).isTrue();
    assertThat(isEditorVisible(inspector, ATTR_VISIBILITY, TOOLS_URI)).isTrue();
    assertThat(isEditorVisible(inspector, ATTR_INDETERMINATE)).isTrue();

    BaseComponentEditor indeterminateEditor = (BaseComponentEditor)getEditor(inspector, ATTR_INDETERMINATE, ANDROID_URI);
    indeterminateEditor.stopEditing("true");

    assertThat(isEditorVisible(inspector, ATTR_STYLE, "")).isTrue();
    assertThat(isEditorVisible(inspector, ATTR_PROGRESS_DRAWABLE)).isFalse();
    assertThat(isEditorVisible(inspector, ATTR_INDETERMINATE_DRAWABLE)).isTrue();
    assertThat(isEditorVisible(inspector, ATTR_PROGRESS_TINT)).isFalse();
    assertThat(isEditorVisible(inspector, ATTR_INDETERMINATE_TINT)).isTrue();
    assertThat(isEditorVisible(inspector, ATTR_MAXIMUM)).isFalse();
    assertThat(isEditorVisible(inspector, ATTR_PROGRESS)).isFalse();
    assertThat(isEditorVisible(inspector, ATTR_VISIBILITY)).isTrue();
    assertThat(isEditorVisible(inspector, ATTR_VISIBILITY, TOOLS_URI)).isTrue();
    assertThat(isEditorVisible(inspector, ATTR_INDETERMINATE)).isTrue();

    indeterminateEditor.stopEditing("false");

    assertThat(isEditorVisible(inspector, ATTR_STYLE, "")).isTrue();
    assertThat(isEditorVisible(inspector, ATTR_PROGRESS_DRAWABLE)).isTrue();
    assertThat(isEditorVisible(inspector, ATTR_INDETERMINATE_DRAWABLE)).isFalse();
    assertThat(isEditorVisible(inspector, ATTR_PROGRESS_TINT)).isTrue();
    assertThat(isEditorVisible(inspector, ATTR_INDETERMINATE_TINT)).isFalse();
    assertThat(isEditorVisible(inspector, ATTR_MAXIMUM)).isTrue();
    assertThat(isEditorVisible(inspector, ATTR_PROGRESS)).isTrue();
    assertThat(isEditorVisible(inspector, ATTR_VISIBILITY)).isTrue();
    assertThat(isEditorVisible(inspector, ATTR_VISIBILITY, TOOLS_URI)).isTrue();
    assertThat(isEditorVisible(inspector, ATTR_INDETERMINATE)).isTrue();
  }

  private static boolean isEditorVisible(@NotNull InspectorComponent<NlPropertiesManager> inspector, @NotNull String attributeName) {
    return isEditorVisible(inspector, attributeName, ANDROID_URI);
  }

  private static boolean isEditorVisible(@NotNull InspectorComponent<NlPropertiesManager> inspector,
                                         @NotNull String attributeName,
                                         @NotNull String namespace) {
    return getEditor(inspector, attributeName, namespace).getComponent().isVisible();
  }

  private static NlComponentEditor getEditor(@NotNull InspectorComponent<NlPropertiesManager> inspector,
                                             @NotNull String attributeName,
                                             @NotNull String namespace) {
    Optional<NlComponentEditor> attributeEditor =
      inspector.getEditors().stream().filter(editor -> hasProperty(editor, attributeName, namespace)).findFirst();
    assertThat(attributeEditor.isPresent()).named("Editor not found for: " + attributeName).isTrue();
    assert attributeEditor.isPresent();
    return attributeEditor.get();
  }

  private static boolean hasProperty(@NotNull NlComponentEditor editor, @NotNull String attributeName, @NotNull String namespace) {
    NlProperty property = editor.getProperty();
    assertThat(property).isNotNull();
    return property.getName().equals(attributeName) && namespace.equals(property.getNamespace());
  }
}
