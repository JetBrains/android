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
import com.android.tools.idea.uibuilder.property.NlProperties;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.NlComponentEditor;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

import static com.android.SdkConstants.TOOLS_NS_NAME_PREFIX;
import static com.android.SdkConstants.TOOLS_URI;

public class FavoritesInspectorProvider implements InspectorProvider {
  private final List<String> myStarredPropertyNames;
  private FavoritesInspectorComponent myInspectorComponent;
  private String myLastStarredPropertyListValue;

  public FavoritesInspectorProvider() {
    myStarredPropertyNames = new ArrayList<>();
  }

  @Override
  public boolean isApplicable(@NotNull List<NlComponent> components,
                              @NotNull Map<String, NlProperty> properties,
                              @NotNull NlPropertiesManager propertiesManager) {
    loadStarredPropertiesIfNeeded();
    for (String propertyName : myStarredPropertyNames) {
      if (properties.containsKey(removePropertyPrefix(propertyName))) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  @Override
  public InspectorComponent createCustomInspector(@NotNull List<NlComponent> components,
                                                  @NotNull Map<String, NlProperty> properties,
                                                  @NotNull NlPropertiesManager propertiesManager) {
    if (myInspectorComponent == null) {
      myInspectorComponent = new FavoritesInspectorComponent(myStarredPropertyNames);
    }
    myInspectorComponent.updateProperties(components, properties, propertiesManager);
    return myInspectorComponent;
  }

  @Override
  public void resetCache() {
    myLastStarredPropertyListValue = null;
    myStarredPropertyNames.clear();
    myInspectorComponent = null;
  }

  private void loadStarredPropertiesIfNeeded() {
    if (!Objects.equals(myLastStarredPropertyListValue, NlProperties.getStarredPropertiesAsString())) {
      loadStarredProperties();
    }
  }

  private void loadStarredProperties() {
    myLastStarredPropertyListValue = NlProperties.getStarredPropertiesAsString();
    myStarredPropertyNames.clear();
    for (String propertyName : NlProperties.getStarredProperties()) {
      if (!propertyName.startsWith(TOOLS_NS_NAME_PREFIX)) {
        propertyName = removePropertyPrefix(propertyName);
      }
      myStarredPropertyNames.add(propertyName);
    }
    Collections.sort(myStarredPropertyNames);
  }

  @NotNull
  private static String removePropertyPrefix(@NotNull String propertyName) {
    return propertyName.substring(propertyName.indexOf(':') + 1);
  }

  private static class FavoritesInspectorComponent implements InspectorComponent {
    private final List<String> myStarredPropertyNames;
    private final Map<String, NlComponentEditor> myEditorMap;

    private FavoritesInspectorComponent(@NotNull List<String> starredPropertyNames) {
      myStarredPropertyNames = starredPropertyNames;
      myEditorMap = new HashMap<>(starredPropertyNames.size());
    }

    @Override
    public void updateProperties(@NotNull List<NlComponent> components,
                                 @NotNull Map<String, NlProperty> properties,
                                 @NotNull NlPropertiesManager propertiesManager) {
      myEditorMap.clear();
      for (String propertyName : myStarredPropertyNames) {
        NlProperty property = findProperty(propertyName, properties);
        NlComponentEditor editor = myEditorMap.get(propertyName);
        if (property == null) {
          if (editor != null) {
            myEditorMap.remove(propertyName);
          }
        }
        else {
          if (editor == null) {
            editor = propertiesManager.getPropertyEditors().create(property);
            myEditorMap.put(propertyName, editor);
          }
          editor.setProperty(property);
        }
      }
    }

    @Nullable
    private static NlProperty findProperty(@NotNull String propertyName, @NotNull Map<String, NlProperty> properties) {
      boolean designPropertyRequired = propertyName.startsWith(TOOLS_NS_NAME_PREFIX);
      propertyName = removePropertyPrefix(propertyName);
      NlProperty property = properties.get(propertyName);
      if (property == null) {
        return null;
      }
      if (designPropertyRequired) {
        property = property.getDesignTimeProperty();
      }
      return property;
    }

    @Override
    public int getMaxNumberOfRows() {
      return 1 + myStarredPropertyNames.size();
    }

    @Override
    public void attachToInspector(@NotNull InspectorPanel inspector) {
      inspector.addTitle("Favorite Attributes");
      for (String propertyName : myStarredPropertyNames) {
        NlComponentEditor editor = myEditorMap.get(propertyName);
        if (editor != null) {
          NlProperty property = editor.getProperty();
          JLabel label = inspector.addComponent(property.getName(), property.getTooltipText(), editor.getComponent());
          if (TOOLS_URI.equals(property.getNamespace())) {
            label.setIcon(AndroidIcons.NeleIcons.DesignProperty);
          }
          editor.setLabel(label);
        }
      }
    }

    @Override
    public void refresh() {
      for (String propertyName : myStarredPropertyNames) {
        NlComponentEditor editor = myEditorMap.get(propertyName);
        if (editor != null) {
          editor.refresh();
        }
      }
    }

    @NotNull
    @Override
    public List<NlComponentEditor> getEditors() {
      List<NlComponentEditor> editors = new ArrayList<>(myStarredPropertyNames.size());
      for (String propertyName : myStarredPropertyNames) {
        NlComponentEditor editor = myEditorMap.get(propertyName);
        if (editor != null) {
          editors.add(editor);
        }
      }
      return editors;
    }
  }
}
