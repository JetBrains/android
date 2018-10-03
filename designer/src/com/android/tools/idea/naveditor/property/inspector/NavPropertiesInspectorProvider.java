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
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.common.property.inspector.InspectorComponent;
import com.android.tools.idea.common.property.inspector.InspectorProvider;
import com.android.tools.idea.naveditor.property.NavPropertiesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates the properties inspector for navigation Destinations.
 */
public abstract class NavPropertiesInspectorProvider implements InspectorProvider<NavPropertiesManager> {

  private final Map<String, InspectorComponent<NavPropertiesManager>> myInspectors = new HashMap<>();

  private final Map<String, String> myPropertyNameUiNameMap;
  private final String myTitle;

  protected NavPropertiesInspectorProvider(@NotNull Map<String, String> uiNamePropertyNameMap, @Nullable String title) {
    myPropertyNameUiNameMap = uiNamePropertyNameMap;
    myTitle = title;
  }

  @Override
  public boolean isApplicable(@NotNull List<NlComponent> components,
                              @NotNull Map<String, NlProperty> properties,
                              @NotNull NavPropertiesManager propertiesManager) {
    if (components.isEmpty()) {
      return false;
    }
    String tagName = components.get(0).getTag().getName();
    if (properties.keySet().stream().noneMatch(name -> myPropertyNameUiNameMap.containsKey(name))) {
      return false;
    }
    if (myInspectors.containsKey(tagName)) {
      return true;
    }
    myInspectors.put(tagName, new NavigationInspectorComponent(properties, propertiesManager, myPropertyNameUiNameMap, myTitle));
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
}
