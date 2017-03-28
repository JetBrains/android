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
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.NlComponentEditor;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

import static com.android.SdkConstants.*;

public class ViewInspectorProvider implements InspectorProvider {
  private static final Set<String> TAG_EXCEPTIONS = ImmutableSet.of(TEXT_VIEW, PROGRESS_BAR);
  private final ViewHandlerManager myViewHandlerManager;
  private final Map<String, InspectorComponent> myInspectors;

  public ViewInspectorProvider(@NotNull Project project) {
    myViewHandlerManager = ViewHandlerManager.get(project);
    myInspectors = new HashMap<>();
  }

  @Override
  public boolean isApplicable(@NotNull List<NlComponent> components,
                              @NotNull Map<String, NlProperty> properties,
                              @NotNull NlPropertiesManager propertiesManager) {
    if (components.size() != 1) {
      return false;
    }
    String tagName = components.get(0).getTagName();
    if (TAG_EXCEPTIONS.contains(tagName)) {
      return false;
    }
    if (myInspectors.containsKey(tagName)) {
      return true;
    }
    ViewHandler handler = myViewHandlerManager.getHandler(tagName);
    if (handler == null || handler.getInspectorProperties().isEmpty()) {
      return false;
    }
    myInspectors.put(tagName, new ViewInspectorComponent(tagName, properties, propertiesManager, handler.getInspectorProperties()));
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
    inspector.updateProperties(components, properties, propertiesManager);
    return inspector;
  }

  @Override
  public void resetCache() {
    myInspectors.clear();
  }

  private static class ViewInspectorComponent implements InspectorComponent {
    // These layout properties should be shown for all child components of layouts that have the property.
    // Which we can do by simply ask if the property is present.
    private static final List<String> LAYOUT_PROPERTIES = ImmutableList.of(
      ATTR_LAYOUT_SCROLL_FLAGS,
      ATTR_LAYOUT_COLLAPSE_MODE,
      ATTR_COLLAPSE_PARALLAX_MULTIPLIER,
      ATTR_LAYOUT_BEHAVIOR);

    private final String myComponentName;
    private final List<String> myPropertyNames;
    private final List<NlComponentEditor> myEditors;
    private final int mySrcPropertyIndex;

    public ViewInspectorComponent(@NotNull String tagName,
                                  @NotNull Map<String, NlProperty> properties,
                                  @NotNull NlPropertiesManager propertiesManager,
                                  @NotNull List<String> propertyNames) {
      myComponentName = tagName.substring(tagName.lastIndexOf('.') + 1);
      myPropertyNames = combineLists(propertyNames, LAYOUT_PROPERTIES);
      mySrcPropertyIndex = myPropertyNames.indexOf(ATTR_SRC);
      myEditors = new ArrayList<>(myPropertyNames.size());
      createEditors(properties, propertiesManager);
    }

    private void createEditors(@NotNull Map<String, NlProperty> properties, @NotNull NlPropertiesManager propertiesManager) {
      for (String propertyName : myPropertyNames) {
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
      // TODO: Update the properties in the editors instead of recreating the editors
      useSrcCompatIfExist(properties);
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
        if (property != null) {
          String propertyName = property.getName();
          JLabel label = inspector.addComponent(propertyName, property.getTooltipText(), editor.getComponent());
          if (TOOLS_URI.equals(property.getNamespace())) {
            label.setIcon(AndroidIcons.NeleIcons.DesignProperty);
          }
          editor.setLabel(label);
        }
      }
    }

    @Override
    public void refresh() {
      myEditors.forEach(NlComponentEditor::refresh);
    }

    @NotNull
    private static List<String> combineLists(@NotNull List<String> list1, @NotNull List<String> list2) {
      List<String> combined = new ArrayList<>(list1.size() + list2.size());
      combined.addAll(list1);
      combined.addAll(list2);
      return combined;
    }

    private void useSrcCompatIfExist(@NotNull Map<String, NlProperty> properties) {
      if (mySrcPropertyIndex < 0) {
        return;
      }
      myPropertyNames.set(mySrcPropertyIndex, properties.containsKey(ATTR_SRC_COMPAT) ? ATTR_SRC_COMPAT : ATTR_SRC);
    }
  }
}
