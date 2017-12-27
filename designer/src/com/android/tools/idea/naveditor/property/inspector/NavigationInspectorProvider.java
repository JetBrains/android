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
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.NlComponentEditor;
import com.android.tools.idea.uibuilder.property.inspector.InspectorComponent;
import com.android.tools.idea.uibuilder.property.inspector.InspectorPanel;
import com.android.tools.idea.uibuilder.property.inspector.InspectorProvider;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates the properties inspector for navigation Destinations.
 *
 * TODO: merge with ActionInspectorProvider?
 */
public class NavigationInspectorProvider implements InspectorProvider {
  private static final String[] NAVIGATION_PROPERTIES = {
    SdkConstants.ATTR_NAME, SdkConstants.ATTR_ID, SdkConstants.ATTR_LABEL, NavigationSchema.ATTR_NAV_TYPE
  };

  private final Map<String, InspectorComponent> myInspectors = new HashMap<>();

  @Override
  public boolean isApplicable(@NotNull List<NlComponent> components,
                              @NotNull Map<String, NlProperty> properties,
                              @NotNull NlPropertiesManager propertiesManager) {
    if (components.size() != 1) {
      return false;
    }
    XmlTag tag = components.get(0).getTag();
    NavigationSchema schema = NavigationSchema.getOrCreateSchema(propertiesManager.getFacet());
    if (schema.getDestinationClassByTag(tag.getName()) == null) {
      return false;
    }
    String tagName = tag.getName();
    if (myInspectors.containsKey(tagName)) {
      return true;
    }
    myInspectors.put(tagName, new NavigationInspectorComponent(tagName, properties, propertiesManager));
    return true;
  }

  @NotNull
  @Override
  public InspectorComponent createCustomInspector(@NotNull List<NlComponent> components,
                                                  @NotNull Map<String, NlProperty> properties,
                                                  @NotNull NlPropertiesManager propertiesManager) {
    String tagName = components.get(0).getTagName();
    InspectorComponent inspector = myInspectors.get(tagName);
    assert inspector != null;
    inspector.updateProperties(components, properties, propertiesManager);
    return inspector;
  }

  @Override
  public void resetCache() {
    myInspectors.clear();
  }

  private static class NavigationInspectorComponent implements InspectorComponent {
    private final String myComponentName;
    private final List<NlComponentEditor> myEditors;

    public NavigationInspectorComponent(@NotNull String tagName,
                                        @NotNull Map<String, NlProperty> properties,
                                        @NotNull NlPropertiesManager propertiesManager) {
      myComponentName = tagName.substring(tagName.lastIndexOf('.') + 1);
      myEditors = new ArrayList<>(NAVIGATION_PROPERTIES.length);
      createEditors(properties, propertiesManager);
    }

    private void createEditors(@NotNull Map<String, NlProperty> properties, @NotNull NlPropertiesManager propertiesManager) {
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
      inspector.addTitle(myComponentName);
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
