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
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.NlComponentEditor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Interface for generating the UI components for a specific inspector.
 */
public interface InspectorComponent {

  /**
   * Update the properties used
   * @param components the components selected
   * @param properties that are now available
   * @param propertiesManager the current properties manager
   */
  void updateProperties(@NotNull List<NlComponent> components,
                        @NotNull Map<String, NlProperty> properties,
                        @NotNull NlPropertiesManager propertiesManager);

  /**
   * Return the maximum number of rows that attachToInspector may generate.
   * A row is either a title, separator, or a component row
   */
  int getMaxNumberOfRows();

  /**
   * Add rows of controls to the inspector panel for this inspector.
   */
  void attachToInspector(@NotNull InspectorPanel inspector);

  /**
   * Refresh the values shown in this inspector.
   */
  void refresh();

  /**
   * Get the editors created by this inspector.
   */
  @NotNull
  List<NlComponentEditor> getEditors();

  /**
   * If customized handling of field visibility is required, use this method
   * to set the visibility of the fields.
   * Filtering in the inspector will change any visibility settings done by this
   * {@link InspectorComponent}. Use this method to override the visibility when
   * there is no filter active. When a filter is active this method should not
   * do anything.
   */
  default void updateVisibility() {}
}
