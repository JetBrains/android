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

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.NlComponentEditor;
import com.android.tools.idea.uibuilder.property.inspector.InspectorComponent;
import com.android.tools.idea.uibuilder.property.inspector.InspectorPanel;
import com.android.tools.idea.uibuilder.property.inspector.InspectorProvider;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.TOOLS_NS_NAME_PREFIX;

/**
 * Creates the properties inspector for navigation Actions.
 *
 * TODO: merge with NavigationInspectorProvider?
 */
public class ActionInspectorProvider implements InspectorProvider {
  private InspectorComponent myComponent;
  private static final String[] ACTION_PROPERTIES = {NavigationSchema.ATTR_DESTINATION};

  @Override
  public boolean isApplicable(@NotNull List<NlComponent> components,
                              @NotNull Map<String, NlProperty> properties,
                              @NotNull NlPropertiesManager propertiesManager) {
    if (components.size() == 1) {
      return components.get(0).getTagName().equals(NavigationSchema.TAG_ACTION);
    }
    return false;
  }

  @NotNull
  @Override
  public InspectorComponent createCustomInspector(@NotNull List<NlComponent> components,
                                                  @NotNull Map<String, NlProperty> properties,
                                                  @NotNull NlPropertiesManager propertiesManager) {
    if (myComponent == null) {
      myComponent = new ActionInspectorComponent(properties, propertiesManager);
    }
    myComponent.updateProperties(components, properties, propertiesManager);
    return myComponent;
  }

  @Override
  public void resetCache() {

  }

  private static class ActionInspectorComponent implements InspectorComponent {

    private final List<NlComponentEditor> myEditors = new ArrayList<>();

    public ActionInspectorComponent(@NotNull Map<String, NlProperty> properties,
                                    @NotNull NlPropertiesManager propertiesManager) {
      createEditors(properties, propertiesManager);
    }

    private void createEditors(@NotNull Map<String, NlProperty> properties, @NotNull NlPropertiesManager propertiesManager) {
      for (String propertyName : ACTION_PROPERTIES) {
        boolean designPropertyRequired = propertyName.startsWith(TOOLS_NS_NAME_PREFIX);
        propertyName = StringUtil.trimStart(propertyName, TOOLS_NS_NAME_PREFIX);
        NlProperty property = properties.get(propertyName);
        if (property != null) {
          if (designPropertyRequired) {
            property = property.getDesignTimeProperty();
          }
          NlComponentEditor editor = propertiesManager.getPropertyEditors().create(property);
          editor.setProperty(property);
          myEditors.add(editor);
        }
      }
    }

    @Override
    public void updateProperties(@NotNull List<NlComponent> components,
                                 @NotNull Map<String, NlProperty> properties,
                                 @NotNull NlPropertiesManager propertiesManager) {
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
      inspector.addTitle("Action");
      for (NlComponentEditor editor : myEditors) {
        NlProperty property = editor.getProperty();
        String propertyName = property.getName();
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
