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
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.common.property.inspector.InspectorComponent;
import com.android.tools.idea.common.property.inspector.InspectorPanel;
import com.android.tools.idea.uibuilder.handlers.SwitchHandler;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.android.tools.idea.uibuilder.property.PropertyTestCase;
import com.android.tools.idea.common.property.editors.NlComponentEditor;
import com.google.common.collect.ImmutableList;
import com.intellij.util.xml.XmlName;
import icons.StudioIcons;
import org.jetbrains.android.dom.attrs.AttributeDefinition;

import javax.swing.*;
import java.util.*;

import static com.android.SdkConstants.*;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class ViewInspectorProviderTest extends PropertyTestCase {
  private ViewInspectorProvider myProvider;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myProvider = new ViewInspectorProvider();
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
    // There must be exactly 1 element selected:
    assertThat(myProvider.isApplicable(Collections.emptyList(), Collections.emptyMap(), myPropertiesManager)).isFalse();
    assertThat(myProvider.isApplicable(ImmutableList.of(myCheckBox1, myCheckBox2), Collections.emptyMap(), myPropertiesManager)).isFalse();

    // TextBox and ProgressBar are handled by a different provider:
    assertThat(myProvider.isApplicable(ImmutableList.of(myTextView), Collections.emptyMap(), myPropertiesManager)).isFalse();
    assertThat(myProvider.isApplicable(ImmutableList.of(myProgressBar), Collections.emptyMap(), myPropertiesManager)).isFalse();

    // Component with no ViewHandler:
    assertThat(myProvider.isApplicable(ImmutableList.of(myUnknown), Collections.emptyMap(), myPropertiesManager)).isFalse();

    // Component with ViewHandler with inspector properties are applicable:
    assertThat(myProvider.isApplicable(ImmutableList.of(myCheckBox1), Collections.emptyMap(), myPropertiesManager)).isTrue();
  }

  public void testInspectorComponent() {
    List<NlComponent> components = ImmutableList.of(mySwitch);
    Map<String, NlProperty> properties = getPropertyMap(components);
    assertThat(myProvider.isApplicable(components, properties, myPropertiesManager)).isTrue();
    InspectorComponent<NlPropertiesManager> inspector = myProvider.createCustomInspector(components, properties, myPropertiesManager);
    List<NlComponentEditor> editors = inspector.getEditors();
    assertThat(editors.size()).isEqualTo(13);
    assertThat(inspector.getMaxNumberOfRows()).isEqualTo(editors.size() + 1);

    Set<String> inspectorProperties = new HashSet<>(new SwitchHandler().getInspectorProperties());

    @SuppressWarnings("unchecked")
    InspectorPanel<NlPropertiesManager> panel = mock(InspectorPanel.class);
    when(panel.addComponent(anyString(), anyString(), any())).thenAnswer(invocation -> new JLabel());
    inspector.attachToInspector(panel);

    verify(panel).addTitle(SWITCH);
    for (NlComponentEditor editor : editors) {
      NlProperty property = editor.getProperty();
      assertThat(property).isNotNull();
      verify(panel).addComponent(eq(property.getName()), eq(property.getTooltipText()), eq(editor.getComponent()));
      assertThat(editor.getLabel()).isNotNull();
      if (TOOLS_URI.equals(property.getNamespace())) {
        assertThat(editor.getLabel().getIcon()).isEqualTo(StudioIcons.LayoutEditor.Properties.DESIGN_PROPERTY);
        if (!inspectorProperties.remove(TOOLS_NS_NAME_PREFIX + property.getName())) {
          assertThat(inspectorProperties.remove(TOOLS_NS_NAME_PREFIX + property.getName())).named(property.getName()).isTrue();
        }
      }
      else {
        assertThat(editor.getLabel().getIcon()).isNull();
        assertThat(inspectorProperties.remove(property.getName())).named(property.getName()).isTrue();
      }
    }
    assertThat(inspectorProperties).contains("trackTint"); // trackTint was added in API 23 / default minAPI is 22
  }

  public void testUpdateProperties() {
    List<NlComponent> components = ImmutableList.of(myImageView);
    Map<String, NlProperty> properties = getPropertyMap(components);
    assertThat(myProvider.isApplicable(components, properties, myPropertiesManager)).isTrue();
    InspectorComponent<NlPropertiesManager> inspector = myProvider.createCustomInspector(components, properties, myPropertiesManager);
    inspector.updateProperties(components, properties, myPropertiesManager);

    NlComponentEditor srcEditor = inspector.getEditors().get(0);
    assertThat(srcEditor.getProperty()).isNotNull();
    assertThat(srcEditor.getProperty().getName()).isEqualTo(ATTR_SRC);

    // Simulate the addition of appcompat library:
    AttributeDefinition srcCompatDefinition = new AttributeDefinition(ATTR_SRC_COMPAT);
    properties.put(ATTR_SRC_COMPAT,
                   NlPropertyItem.create(new XmlName(ATTR_SRC_COMPAT, AUTO_URI), srcCompatDefinition, components, myPropertiesManager));

    // Check that an update will replace the ATTR_SRC with ATTR_SRC_COMPAT
    inspector.updateProperties(components, properties, myPropertiesManager);
    srcEditor = inspector.getEditors().get(0);
    assertThat(srcEditor.getProperty()).isNotNull();
    assertThat(srcEditor.getProperty().getName()).isEqualTo(ATTR_SRC_COMPAT);

    // Simulate the removal of appcompat library:
    properties.remove(ATTR_SRC_COMPAT);

    // Check that an update will replace the ATTR_SRC with ATTR_SRC_COMPAT
    inspector.updateProperties(components, properties, myPropertiesManager);
    srcEditor = inspector.getEditors().get(0);
    assertThat(srcEditor.getProperty()).isNotNull();
    assertThat(srcEditor.getProperty().getName()).isEqualTo(ATTR_SRC);
  }
}
