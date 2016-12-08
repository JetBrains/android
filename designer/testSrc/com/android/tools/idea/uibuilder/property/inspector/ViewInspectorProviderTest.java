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

import com.android.tools.idea.uibuilder.handlers.SwitchHandler;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.android.tools.idea.uibuilder.property.PropertyTestCase;
import com.android.tools.idea.uibuilder.property.editors.NlComponentEditor;
import com.google.common.collect.ImmutableList;
import icons.AndroidIcons;
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
    myProvider = new ViewInspectorProvider(getProject());
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

    // Component with ViewHandler but no inspector properties:
    assertThat(myProvider.isApplicable(ImmutableList.of(myConstraintLayout), Collections.emptyMap(), myPropertiesManager)).isFalse();

    // Component with ViewHandler with inspector properties are applicable:
    assertThat(myProvider.isApplicable(ImmutableList.of(myCheckBox1), Collections.emptyMap(), myPropertiesManager)).isTrue();
  }

  public void testInspectorComponent() {
    List<NlComponent> components = ImmutableList.of(mySwitch);
    Map<String, NlProperty> properties = getPropertyMap(components);
    assertThat(myProvider.isApplicable(components, properties, myPropertiesManager)).isTrue();
    InspectorComponent inspector = myProvider.createCustomInspector(components, properties, myPropertiesManager);
    List<NlComponentEditor> editors = inspector.getEditors();
    assertThat(editors.size()).isEqualTo(14);
    assertThat(inspector.getMaxNumberOfRows()).isEqualTo(editors.size() + 1);

    Set<String> inspectorProperties = new HashSet<>(new SwitchHandler().getInspectorProperties());

    InspectorPanel panel = mock(InspectorPanel.class);
    when(panel.addComponent(anyString(), anyString(), any())).thenAnswer(invocation -> new JLabel());
    inspector.attachToInspector(panel);

    verify(panel).addTitle(SWITCH);
    for (NlComponentEditor editor : editors) {
      NlProperty property = editor.getProperty();
      assertThat(property).isNotNull();
      verify(panel).addComponent(eq(property.getName()), eq(property.getTooltipText()), eq(editor.getComponent()));
      assertThat(editor.getLabel()).isNotNull();
      if (TOOLS_URI.equals(property.getNamespace())) {
        assertThat(editor.getLabel().getIcon()).isEqualTo(AndroidIcons.NeleIcons.DesignProperty);
        if (!inspectorProperties.remove(TOOLS_NS_NAME_PREFIX + property.getName())) {
          assertThat(inspectorProperties.remove(TOOLS_NS_NAME_PREFIX + property.getName())).named(property.getName()).isTrue();
        }
      }
      else {
        assertThat(editor.getLabel().getIcon()).isNull();
        assertThat(inspectorProperties.remove(property.getName())).named(property.getName()).isTrue();
      }
    }
    assertThat(inspectorProperties).isEmpty();
  }

  public void testUpdateProperties() {
    List<NlComponent> components = ImmutableList.of(myImageView);
    Map<String, NlProperty> properties = getPropertyMap(components);
    assertThat(myProvider.isApplicable(components, properties, myPropertiesManager)).isTrue();
    InspectorComponent inspector = myProvider.createCustomInspector(components, properties, myPropertiesManager);
    inspector.updateProperties(components, properties, myPropertiesManager);

    NlComponentEditor srcEditor = inspector.getEditors().get(0);
    assertThat(srcEditor.getProperty()).isNotNull();
    assertThat(srcEditor.getProperty().getName()).isEqualTo(ATTR_SRC);

    // Simulate the addition of appcompat library:
    AttributeDefinition srcCompatDefinition = new AttributeDefinition(ATTR_SRC_COMPAT);
    properties.put(ATTR_SRC_COMPAT, new NlPropertyItem(components, myPropertiesManager, ANDROID_URI, srcCompatDefinition));

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
