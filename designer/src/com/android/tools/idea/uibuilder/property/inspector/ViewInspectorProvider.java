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

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.*;
import com.intellij.openapi.project.Project;
import icons.AndroidIcons;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

import static com.android.tools.idea.uibuilder.property.editors.NlEditingListener.DEFAULT_LISTENER;

public class ViewInspectorProvider implements InspectorProvider {
  private final Project myProject;
  private final ViewHandlerManager myViewHandlerManager;
  private final Map<String, InspectorComponent> myInspectors;

  public ViewInspectorProvider(@NotNull Project project) {
    myProject = project;
    myViewHandlerManager = ViewHandlerManager.get(project);
    myInspectors = new HashMap<>();
  }

  @Override
  public boolean isApplicable(@NotNull List<NlComponent> components, @NotNull Map<String, NlProperty> properties) {
    if (components.size() != 1) {
      return false;
    }
    String tagName = components.get(0).getTagName();
    if (myInspectors.containsKey(tagName)) {
      return true;
    }
    ViewHandler handler = myViewHandlerManager.getHandler(tagName);
    if (handler == null || handler.getInspectorProperties().isEmpty()) {
      return false;
    }
    myInspectors.put(tagName, new ViewInspectorComponent(myProject, tagName, properties, handler.getInspectorProperties()));
    return true;
  }

  @NotNull
  @Override
  public InspectorComponent createCustomInspector(@NotNull List<NlComponent> components,
                                                  @NotNull Map<String, NlProperty> properties,
                                                  @NotNull NlPropertiesManager propertiesManager) {
    assert components.size() == 1;
    String tagName = components.get(0).getTagName();
    InspectorComponent inspector = myInspectors.get(tagName);
    assert inspector != null;
    return inspector;
  }

  private static class ViewInspectorComponent implements InspectorComponent {
    private final String myComponentName;
    private final List<String> myPropertyNames;
    private final Map<String, NlComponentEditor> myEditors;
    private Map<String, NlProperty> myProperties;

    public ViewInspectorComponent(@NotNull Project project,
                                  @NotNull String tagName,
                                  @NotNull Map<String, NlProperty> properties,
                                  @NotNull List<String> propertyNames) {
      myComponentName = tagName.substring(tagName.lastIndexOf('.') + 1);
      myPropertyNames = propertyNames;
      myEditors = new HashMap<>(propertyNames.size());
      myProperties = properties;
      for (String propertyName : propertyNames) {
        NlProperty property = properties.get(propertyName);
        if (property == null) {
          continue;
        }
        AttributeDefinition definition = property.getDefinition();
        Set<AttributeFormat> formats = definition != null ? definition.getFormats() : Collections.emptySet();
        if (formats.contains(AttributeFormat.Boolean)) {
          myEditors.put(propertyName, NlBooleanEditor.createForInspector(DEFAULT_LISTENER));
        }
        else if (formats.contains(AttributeFormat.Enum)) {
          myEditors.put(propertyName, NlEnumEditor.createForInspector(NlEnumEditor.getDefaultListener()));
        }
        else if (formats.contains(AttributeFormat.Flag)) {
          myEditors.put(propertyName, NlFlagsEditor.create());
        }
        else {
          myEditors.put(propertyName, NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER));
        }
      }
    }

    @Override
    public void updateProperties(@NotNull List<NlComponent> components, @NotNull Map<String, NlProperty> properties) {
      myProperties = properties;
    }

    @Override
    public int getMaxNumberOfRows() {
      return 2 + myEditors.size();
    }

    @Override
    public void attachToInspector(@NotNull InspectorPanel inspector) {
      inspector.addSeparator();
      inspector.addTitle(myComponentName);
      for (String propertyName : myPropertyNames) {
        if (myProperties.containsKey(propertyName)) {
          NlProperty property = myProperties.get(propertyName);
          JLabel label = inspector.addComponent(propertyName, property.getTooltipText(), myEditors.get(propertyName).getComponent());
          if (SdkConstants.TOOLS_URI.equals(property.getNamespace())) {
            label.setIcon(AndroidIcons.NeleIcons.DesignProperty);
          }
        }
      }
      refresh();
    }

    @Override
    public void refresh() {
      for (String propertyName : myPropertyNames) {
        if (myProperties.containsKey(propertyName)) {
          myEditors.get(propertyName).setProperty(myProperties.get(propertyName));
        }
      }
    }
  }
}
