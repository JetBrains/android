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

import org.jetbrains.annotations.NotNull;

/**
 * Interface for generating the UI components for a specific inspector.
 */
public interface InspectorComponent {
  /**
   * Return the maximum number of rows that attachToInspector may generate.
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
}
