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

import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.handlers.SwitchHandler;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.property.*;
import com.android.tools.idea.uibuilder.property.editors.NlComponentEditor;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.mockito.stubbing.Answer;

import javax.swing.*;
import java.util.*;

import static com.android.SdkConstants.*;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class ViewInspectorProviderTest extends LayoutTestCase {
  private static final String UNKNOWN_TAG = "UnknownTagName";

  private NlComponent myTextBox;
  private NlComponent myProgressBar;
  private NlComponent myCheckBox1;
  private NlComponent myCheckBox2;
  private NlComponent mySwitch;
  private NlComponent myUnknown;
  private NlComponent myLayout;
  private ViewInspectorProvider myProvider;
  private NlPropertiesManager myPropertiesManager;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    NlModel model = createModel();
    myTextBox = model.findLeafAt(100, 100, false);
    myProgressBar = model.findLeafAt(100, 200, false);
    myCheckBox1 = model.findLeafAt(100, 300, false);
    myCheckBox2 = model.findLeafAt(100, 400, false);
    mySwitch = model.findLeafAt(100, 500, false);
    myUnknown = model.findLeafAt(100, 600, false);
    myLayout = myTextBox.getParent();
    myProvider = ViewInspectorProvider.getInstance(getProject());
    myPropertiesManager = new NlPropertiesManager(getProject(), null);
  }

  public void testIsApplicable() {
    // There must be exactly 1 element selected:
    assertThat(myProvider.isApplicable(Collections.emptyList(), Collections.emptyMap(), myPropertiesManager)).isFalse();
    assertThat(myProvider.isApplicable(ImmutableList.of(myCheckBox1, myCheckBox2), Collections.emptyMap(), myPropertiesManager)).isFalse();

    // TextBox and ProgressBar are handled by a different provider:
    assertThat(myProvider.isApplicable(ImmutableList.of(myTextBox), Collections.emptyMap(), myPropertiesManager)).isFalse();
    assertThat(myProvider.isApplicable(ImmutableList.of(myProgressBar), Collections.emptyMap(), myPropertiesManager)).isFalse();

    // Component with no ViewHandler:
    assertThat(myProvider.isApplicable(ImmutableList.of(myUnknown), Collections.emptyMap(), myPropertiesManager)).isFalse();

    // Component with ViewHandler but no inspector properties:
    assertThat(myProvider.isApplicable(ImmutableList.of(myLayout), Collections.emptyMap(), myPropertiesManager)).isFalse();

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
    when(panel.addComponent(anyString(), anyString(), any())).thenAnswer((Answer<JLabel>)invocation -> new JLabel());
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

  private NlModel createModel() {
    ModelBuilder builder = model("absolute.xml",
                                 component(ABSOLUTE_LAYOUT)
                                   .withBounds(0, 0, 1000, 1000)
                                   .matchParentWidth()
                                   .matchParentHeight()
                                   .children(
                                     component(TEXT_VIEW)
                                       .withBounds(100, 100, 100, 100)
                                       .id("@id/textView")
                                       .width("wrap_content")
                                       .height("wrap_content")
                                       .text("SomeText"),
                                     component(PROGRESS_BAR)
                                       .withBounds(100, 200, 100, 100)
                                       .id("@id/progress")
                                       .width("wrap_content")
                                       .height("wrap_content"),
                                     component(CHECK_BOX)
                                       .withBounds(100, 300, 100, 100)
                                       .id("@id/checkBox1")
                                       .width("wrap_content")
                                       .height("wrap_content")
                                       .text("Enable Wifi"),
                                     component(CHECK_BOX)
                                       .withBounds(100, 400, 100, 100)
                                       .id("@id/checkBox1")
                                       .width("wrap_content")
                                       .height("wrap_content")
                                       .text("Enable Roaming"),
                                     component(SWITCH)
                                       .withBounds(100, 500, 100, 100)
                                       .id("@id/switch")
                                       .width("wrap_content")
                                       .height("wrap_content")
                                       .text("Enable Roaming"),
                                     component(UNKNOWN_TAG)
                                       .withBounds(100, 600, 100, 100)
                                       .id("@id/unknown")
                                       .width("wrap_content")
                                       .height("wrap_content")
                                   ));
    return builder.build();
  }

  @NotNull
  private static Map<String, NlProperty> getPropertyMap(@NotNull List<NlComponent> components) {
    NlProperties propertiesProvider = NlProperties.getInstance();
    Table<String, String, NlPropertyItem> properties = propertiesProvider.getProperties(components);
    Map<String, NlProperty> propertiesByName = new HashMap<>();
    for (NlProperty property : properties.row(ANDROID_URI).values()) {
      propertiesByName.put(property.getName(), property);
    }
    for (NlProperty property : properties.row(AUTO_URI).values()) {
      propertiesByName.put(property.getName(), property);
    }
    for (NlProperty property : properties.row("").values()) {
      propertiesByName.put(property.getName(), property);
    }
    // Add access to known design properties
    NlDesignProperties designProperties = new NlDesignProperties();
    for (NlProperty property : designProperties.getKnownProperties(components)) {
      propertiesByName.putIfAbsent(property.getName(), property);
    }
    return propertiesByName;
  }
}
