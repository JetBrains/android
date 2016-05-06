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
package com.android.tools.idea.uibuilder.handlers.constraint;

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.inspector.InspectorComponent;
import com.android.tools.idea.uibuilder.property.inspector.InspectorPanel;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * This is the builder for the Constraint panel
 */
public class ConstraintInspectorComponent implements InspectorComponent {
  private List<NlComponent>  mComponents;

  @Override
  public void updateProperties(@NotNull List<NlComponent> components, @NotNull Map<String, NlProperty> properties) {
    // TODO: Support multiple components
    mComponents = components;
  }

  @Override
  public int getMaxNumberOfRows() {
    return 2;
  }

  @Override
  public void attachToInspector(@NotNull InspectorPanel inspector) {
    inspector.addPanel(new WidgetConstraintPanel(mComponents));
    inspector.addSeparator();

    refresh();
  }

  @Override
  public void refresh() {

  }
}
