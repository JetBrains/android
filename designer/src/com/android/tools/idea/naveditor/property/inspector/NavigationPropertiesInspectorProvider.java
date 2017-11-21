/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.property.inspector;

import com.android.SdkConstants;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.common.property.editors.NlComponentEditor;
import com.android.tools.idea.common.property.inspector.InspectorComponent;
import com.android.tools.idea.common.property.inspector.InspectorPanel;
import com.android.tools.idea.common.property.inspector.InspectorProvider;
import com.android.tools.idea.naveditor.property.NavPropertiesManager;
import com.google.common.collect.ImmutableMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.naveditor.property.NavComponentTypePropertyKt.TYPE_EDITOR_PROPERTY_LABEL;

/**
 * Creates the properties inspector for navigation Destinations.
 */
public class NavigationPropertiesInspectorProvider implements InspectorProvider<NavPropertiesManager> {
  private static final String[] NAVIGATION_PROPERTIES = {
    TYPE_EDITOR_PROPERTY_LABEL,
    SdkConstants.ATTR_ID,
    SdkConstants.ATTR_LABEL,
    SdkConstants.ATTR_NAME,
    NavigationSchema.ATTR_START_DESTINATION,
    NavigationSchema.ATTR_DESTINATION,
    NavigationSchema.ATTR_SINGLE_TOP,
    NavigationSchema.ATTR_DOCUMENT,
    NavigationSchema.ATTR_CLEAR_TASK
  };

  private static final Map<String, String> PROPERTY_NAME_UI_NAME_MAP = new ContainerUtil.ImmutableMapBuilder()
    .put(SdkConstants.ATTR_LABEL, "Title")
    .put(SdkConstants.ATTR_ID, "ID")
    .put(SdkConstants.ATTR_NAME, "Class")
    .put(NavigationSchema.ATTR_START_DESTINATION, "Start Destination")
    .put(NavigationSchema.ATTR_DESTINATION, "Destination")
    .put(NavigationSchema.ATTR_SINGLE_TOP, "Single Top")
    .put(NavigationSchema.ATTR_DOCUMENT, "Document")
    .put(NavigationSchema.ATTR_CLEAR_TASK, "Clear Task").build();

  private final Map<String, InspectorComponent<NavPropertiesManager>> myInspectors = new HashMap<>();

  @Override
  public boolean isApplicable(@NotNull List<NlComponent> components,
                              @NotNull Map<String, NlProperty> properties,
                              @NotNull NavPropertiesManager propertiesManager) {
    if (components.isEmpty()) {
      return false;
    }
    String tagName = components.get(0).getTag().getName();
    if (myInspectors.containsKey(tagName)) {
      return true;
    }
    myInspectors.put(tagName, new NavigationInspectorComponent(properties, propertiesManager));
    return true;
  }

  @NotNull
  @Override
  public InspectorComponent<NavPropertiesManager> createCustomInspector(@NotNull List<NlComponent> components,
                                                                        @NotNull Map<String, NlProperty> properties,
                                                                        @NotNull NavPropertiesManager propertiesManager) {
    String tagName = components.get(0).getTagName();
    InspectorComponent<NavPropertiesManager> inspector = myInspectors.get(tagName);
    assert inspector != null;
    inspector.updateProperties(components, properties, propertiesManager);
    return inspector;
  }

  @Override
  public void resetCache() {
    myInspectors.clear();
  }

  private static class NavigationInspectorComponent implements InspectorComponent<NavPropertiesManager> {
    private final List<NlComponentEditor> myEditors;

    public NavigationInspectorComponent(@NotNull Map<String, NlProperty> properties,
                                        @NotNull NavPropertiesManager propertiesManager) {
      myEditors = new ArrayList<>(NAVIGATION_PROPERTIES.length);
      createEditors(properties, propertiesManager);
    }

    private void createEditors(@NotNull Map<String, NlProperty> properties, @NotNull NavPropertiesManager propertiesManager) {
      for (String propertyName : NAVIGATION_PROPERTIES) {
        NlProperty property = properties.get(propertyName);
        if (property != null) {
          NlComponentEditor editor = propertiesManager.getPropertyEditors().create(property);
          editor.setProperty(property);
          myEditors.add(editor);
        }
      }
    }

    @Override
    public void updateProperties(@NotNull List<NlComponent> components,
                                 @NotNull Map<String, NlProperty> properties,
                                 @NotNull NavPropertiesManager propertiesManager) {
      myEditors.clear();
      createEditors(properties, propertiesManager);
    }

    @Override
    @NotNull
    public List<NlComponentEditor> getEditors() {
      return myEditors;
    }

    @Override
    public int getMaxNumberOfRows() {
      return 1 + myEditors.size();
    }

    @Override
    public void attachToInspector(@NotNull InspectorPanel inspector) {
      refresh();
      for (NlComponentEditor editor : myEditors) {
        NlProperty property = editor.getProperty();
        JLabel existing = editor.getLabel();
        String propertyName = existing != null ? existing.getText() : PROPERTY_NAME_UI_NAME_MAP.get(property.getName());
        if (propertyName == null) {
          propertyName = property.getName();
        }
        JLabel label = inspector.addComponent(propertyName, property.getTooltipText(), editor.getComponent());
        editor.setLabel(label);
      }
    }

    @Override
    public void refresh() {
      myEditors.forEach(NlComponentEditor::refresh);
    }
  }
}
