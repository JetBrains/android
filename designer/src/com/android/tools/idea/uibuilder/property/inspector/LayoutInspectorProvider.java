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

import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.NlComponentEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.TOOLS_NS_NAME_PREFIX;
import static com.android.SdkConstants.TOOLS_URI;

/**
 * {@link InspectorProvider} for showing editors for child views of a specific component.
 * Override {@link ViewHandler#getLayoutInspectorProperties()} when you want to show
 * editors in the properties inspector when the child view is focused in the layout editor.
 */
public class LayoutInspectorProvider implements InspectorProvider {

  private final ViewHandlerManager myViewHandlerManager;
  private final Map<String, InspectorComponent> myParentInspectors;

  public LayoutInspectorProvider(@NotNull Project project) {
    myViewHandlerManager = ViewHandlerManager.get(project);
    myParentInspectors = new HashMap<>();
  }

  @Override
  public boolean isApplicable(@NotNull List<NlComponent> components,
                              @NotNull Map<String, NlProperty> properties,
                              @NotNull NlPropertiesManager propertiesManager) {
    assert components.size() >= 1;
    String parentTagName = getParentTagName(components);
    if (parentTagName == null) {
      return false;
    }
    String tagName = parentTagName.substring(parentTagName.lastIndexOf('.') + 1) + "_layout";
    if (myParentInspectors.containsKey(tagName)) {
      return true;
    }

    ViewHandler handler = myViewHandlerManager.getHandler(parentTagName);
    if (handler == null || handler.getLayoutInspectorProperties().isEmpty()) {
      return false;
    }
    myParentInspectors.put(parentTagName, new LayoutInspectorComponent(tagName, properties, propertiesManager,
                                                                       handler.getLayoutInspectorProperties()));
    return true;
  }

  @NotNull
  @Override
  public InspectorComponent createCustomInspector(@NotNull List<NlComponent> components,
                                                  @NotNull Map<String, NlProperty> properties,
                                                  @NotNull NlPropertiesManager propertiesManager) {
    assert components.size() >= 1;
    String parentTagName = getParentTagName(components);
    InspectorComponent inspector = myParentInspectors.get(parentTagName);
    assert inspector != null;
    inspector.updateProperties(components, properties, propertiesManager);
    return inspector;
  }

  @Override
  public void resetCache() {
    myParentInspectors.clear();
  }

  private static class LayoutInspectorComponent implements InspectorComponent {

    private final String myTagName;
    private final List<String> myPropertyNames;
    private final List<NlComponentEditor> myEditors;

    public LayoutInspectorComponent(@NotNull String tagName,
                                    @NotNull Map<String, NlProperty> properties,
                                    @NotNull NlPropertiesManager propertiesManager,
                                    @NotNull List<String> propertyNames) {
      myPropertyNames = propertyNames;
      myTagName = tagName;
      myEditors = new ArrayList<>(propertyNames.size());
      createEditors(properties, propertiesManager);
    }

    private void createEditors(@NotNull Map<String, NlProperty> properties, @NotNull NlPropertiesManager propertiesManager) {
      for (String propertyName : myPropertyNames) {
        propertyName = StringUtil.trimStart(propertyName, TOOLS_NS_NAME_PREFIX);
        NlProperty property = properties.get(propertyName);
        if (property == null) {
          continue;
        }
        NlComponentEditor editor = propertiesManager.getPropertyEditors().create(property);
        editor.setProperty(property);
        myEditors.add(editor);
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
      inspector.addTitle(myTagName);
      for (NlComponentEditor editor : myEditors) {
        NlProperty property = editor.getProperty();
        String propertyName = property.getName();
        JLabel label = inspector.addComponent(propertyName, property.getTooltipText(), editor.getComponent());
        if (TOOLS_URI.equals(property.getNamespace())) {
          label.setIcon(AndroidIcons.NeleIcons.DesignProperty);
        }
        editor.setLabel(label);
      }
    }

    @Override
    public void refresh() {
      myEditors.forEach(NlComponentEditor::refresh);
    }
  }

  @Nullable
  private static String getParentTagName(@NotNull List<NlComponent> components) {
    String parentTagName = null;
    for (NlComponent component : components) {
      if (component.getParent() == null) {
        return null;
      }
      if (parentTagName != null && !component.getParent().getTagName().equals(parentTagName)) {
        return null;
      }
      parentTagName = component.getParent().getTagName();
    }
    return parentTagName;
  }
}
