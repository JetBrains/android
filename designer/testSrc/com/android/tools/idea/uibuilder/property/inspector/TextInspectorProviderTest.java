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
import com.android.tools.idea.common.property.inspector.InspectorPanel;
import com.android.tools.idea.uibuilder.model.PreferenceUtils;
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.uibuilder.property.PropertyTestCase;
import com.android.tools.idea.common.property.editors.BaseComponentEditor;
import com.android.tools.idea.uibuilder.property.editors.NlBooleanIconEditor;
import com.android.tools.idea.common.property.editors.NlComponentEditor;
import com.android.tools.idea.uibuilder.property.editors.NlEnumEditor;
import com.android.tools.idea.uibuilder.property.inspector.TextInspectorProvider.TextInspectorComponent;
import com.google.common.collect.ImmutableList;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.mockito.ArgumentCaptor;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.android.SdkConstants.*;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class TextInspectorProviderTest extends PropertyTestCase {
  private TextInspectorProvider myProvider;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myProvider = new TextInspectorProvider();
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
    assertThat(isApplicable(myProvider, myMerge)).isFalse();
    assertThat(isApplicable(myProvider, myTextView)).isTrue();
    assertThat(isApplicable(myProvider, myCheckBox1)).isTrue();
    assertThat(isApplicable(myProvider, myProgressBar)).isFalse();
    assertThat(isApplicable(myProvider, myTextView, myCheckBox1, mySwitch)).isTrue();
    assertThat(isApplicable(myProvider, myTextView, myCheckBox1, mySwitch, myMerge)).isFalse();
  }

  public void testIsApplicableRequiresAllTextAttributesPresent() {
    List<NlComponent> components = ImmutableList.of(myTextView);
    Map<String, NlProperty> properties = getPropertyMap(components);

    assertThat(myProvider.isApplicable(components, properties, myPropertiesManager)).isTrue();
    for (String propertyName : TextInspectorProvider.REQUIRED_TEXT_PROPERTIES) {
      Map<String, NlProperty> props = new HashMap<>(properties);
      props.remove(propertyName);
      assertThat(myProvider.isApplicable(components, props, myPropertiesManager)).isFalse();
    }
  }

  public void testIsNotApplicableForPreferenceComponents() {
    Map<String, NlProperty> properties = getPropertyMap(ImmutableList.of(myTextView));

    for (String tagName : PreferenceUtils.VALUES) {
      NlComponent component = mock(NlComponent.class);
      when(component.getTagName()).thenReturn(tagName);
      assertThat(myProvider.isApplicable(ImmutableList.of(component), properties, myPropertiesManager)).isFalse();
    }
  }

  public void testInspectorComponent() {
    List<NlComponent> components = ImmutableList.of(myTextView);
    Map<String, NlProperty> properties = getPropertyMap(components);
    assertThat(myProvider.isApplicable(components, properties, myPropertiesManager)).isTrue();
    TextInspectorComponent inspector = myProvider.createCustomInspector(components, properties, myPropertiesManager);
    List<NlComponentEditor> editors = inspector.getEditors();
    assertThat(editors.size()).isEqualTo(9);
    assertThat(inspector.getMaxNumberOfRows()).isEqualTo(12);

    InspectorPanel panel = mock(InspectorPanel.class);
    when(panel.addComponent(anyString(), anyString(), any())).thenAnswer(invocation -> new JLabel());
    inspector.attachToInspector(panel);

    verify(panel).addTitle(eq(TEXT_VIEW));
    for (NlComponentEditor editor : editors) {
      NlProperty property = editor.getProperty();
      assertThat(property).isNotNull();
      if (property.getName().equals(ATTR_TEXT_APPEARANCE)) {
        assertThat(editor).isInstanceOf(NlEnumEditor.class);
        NlEnumEditor enumEditor = (NlEnumEditor)editor;
        verify(panel).addExpandableComponent(
          eq(property.getName()),
          eq(property.getTooltipText()),
          eq(editor.getComponent()),
          eq(enumEditor.getKeySource()));
      }
      else {
        verify(panel).addComponent(
          eq(property.getName()),
          eq(property.getTooltipText()),
          eq(editor.getComponent()));
      }
    }

    propertyPanelHasAllComponents(panel, ATTR_TEXT_STYLE, properties, inspector.getTextStyleEditors());
    propertyPanelHasAllComponents(panel, ATTR_TEXT_ALIGNMENT, properties, inspector.getTextAlignmentEditors());
  }

  public void testTextAppearanceResetsOtherStyles() {
    List<NlComponent> components = ImmutableList.of(myTextView);
    Map<String, NlProperty> properties = getPropertyMap(components);
    properties.get(ATTR_FONT_FAMILY).setValue("serif");
    properties.get(ATTR_TYPEFACE).setValue("sans");
    properties.get(ATTR_TEXT_SIZE).setValue("24sp");
    properties.get(ATTR_LINE_SPACING_EXTRA).setValue("24sp");
    properties.get(ATTR_TEXT_COLOR).setValue("@android:color/holo_blue_dark");
    properties.get(ATTR_TEXT_STYLE).setValue("bold|italic");
    properties.get(ATTR_TEXT_ALL_CAPS).setValue("true");
    properties.get(ATTR_TEXT_ALIGNMENT).setValue("textStart");

    assertThat(myProvider.isApplicable(components, properties, myPropertiesManager)).isTrue();
    TextInspectorComponent inspector = myProvider.createCustomInspector(components, properties, myPropertiesManager);
    inspector.refresh();

    Optional<NlComponentEditor> textAppearanceEditor = inspector.getEditors().stream()
      .filter(editor -> editor.getProperty().getName().equals(ATTR_TEXT_APPEARANCE))
      .findFirst();
    assertThat(textAppearanceEditor.isPresent()).isTrue();
    assert textAppearanceEditor.isPresent();
    assertThat(textAppearanceEditor.get()).isInstanceOf(BaseComponentEditor.class);
    BaseComponentEditor editor = (BaseComponentEditor)textAppearanceEditor.get();
    editor.stopEditing("Material.Display1");
    UIUtil.dispatchAllInvocationEvents();

    assertThat(properties.get(ATTR_TEXT_APPEARANCE).getValue()).isEqualTo("Material.Display1");
    assertThat(properties.get(ATTR_FONT_FAMILY).getValue()).isNull();
    assertThat(properties.get(ATTR_TYPEFACE).getValue()).isNull();
    assertThat(properties.get(ATTR_TEXT_SIZE).getValue()).isNull();
    assertThat(properties.get(ATTR_LINE_SPACING_EXTRA).getValue()).isNull();
    assertThat(properties.get(ATTR_TEXT_COLOR).getValue()).isNull();
    assertThat(properties.get(ATTR_TEXT_STYLE).getValue()).isNull();
    assertThat(properties.get(ATTR_TEXT_ALL_CAPS).getValue()).isNull();
    assertThat(properties.get(ATTR_TEXT_ALIGNMENT).getValue()).isNull();
  }


  private static void propertyPanelHasAllComponents(@NotNull InspectorPanel panel,
                                                    @NotNull String propertyName,
                                                    @NotNull Map<String, NlProperty> properties,
                                                    @NotNull List<NlBooleanIconEditor> editors) {
    List<Component> components = editors.stream().map(NlBooleanIconEditor::getComponent).collect(Collectors.toList());
    ArgumentCaptor<JPanel> panelCaptor = ArgumentCaptor.forClass(JPanel.class);
    NlProperty property = properties.get(propertyName);
    verify(panel).addComponent(eq(propertyName), eq(property.getTooltipText()), panelCaptor.capture());
    JPanel propertyPanel = panelCaptor.getValue();
    assertThat(propertyPanel.getComponents()).asList().containsAllIn(components);
  }
}
