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
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.property.*;
import com.google.common.collect.Table;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.android.SdkConstants.*;

public abstract class InspectorProviderTestCase extends LayoutTestCase {
  private static final String UNKNOWN_TAG = "UnknownTagName";

  protected NlComponent myTextBox;
  protected NlComponent myProgressBar;
  protected NlComponent myCheckBox1;
  protected NlComponent myCheckBox2;
  protected NlComponent mySwitch;
  protected NlComponent myUnknown;
  protected NlComponent myLayout;
  protected NlComponent myButton;
  protected NlComponent myImageView;
  protected NlPropertiesManager myPropertiesManager;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    NlModel model = createModel();
    myTextBox = model.findLeafAt(100, 100, false);
    myProgressBar = model.findLeafAt(100, 200, false);
    myCheckBox1 = model.findLeafAt(100, 300, false);
    myCheckBox2 = model.findLeafAt(100, 400, false);
    mySwitch = model.findLeafAt(100, 500, false);
    myImageView = model.findLeafAt(100, 600, false);
    myUnknown = model.findLeafAt(100, 700, false);
    myButton = model.findLeafAt(400, 100, false);
    myLayout = myTextBox.getParent();
    myPropertiesManager = new NlPropertiesManager(getProject(), null);
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
                                     component(IMAGE_VIEW)
                                       .withBounds(100, 600, 100, 100)
                                       .id("@id/imageview")
                                       .width("wrap_content")
                                       .height("wrap_content"),
                                     component(UNKNOWN_TAG)
                                       .withBounds(100, 700, 100, 100)
                                       .id("@id/unknown")
                                       .width("wrap_content")
                                       .height("wrap_content"),
                                     component(CONSTRAINT_LAYOUT)
                                       .withBounds(300, 0, 700, 1000)
                                       .width("700dp")
                                       .height("1000dp")
                                       .children(
                                         component(BUTTON)
                                           .withBounds(400, 100, 100, 100)
                                           .id("@id/button")
                                           .width("wrap_content")
                                           .height("wrap_content")
                                           .text("SomeButton")
                                       )));
    return builder.build();
  }

  @NotNull
  protected static Map<String, NlProperty> getPropertyMap(@NotNull List<NlComponent> components) {
    if (components.isEmpty()) {
      return Collections.emptyMap();
    }
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

  protected boolean isApplicable(@NotNull InspectorProvider provider, @NotNull NlComponent... componentArray) {
    return provider.isApplicable(Arrays.asList(componentArray), getPropertyMap(Arrays.asList(componentArray)), myPropertiesManager);
  }
}
