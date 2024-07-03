/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.property;

import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.android.tools.property.panel.api.PropertiesModel;
import com.android.tools.property.panel.api.PropertiesModelListener;
import com.android.tools.property.panel.api.PropertiesTable;
import org.jetbrains.annotations.NotNull;

/**
 * Support Sub tags in the Attributes Panel
 */
public class SubTagAttributesModel implements PropertiesModel<NlPropertyItem> {
  private final MotionLayoutAttributesModel myModel;
  private final String mySubTagName;

  public SubTagAttributesModel(@NotNull MotionLayoutAttributesModel model, @NotNull String subTagName) {
    myModel = model;
    mySubTagName = subTagName;
  }

  @NotNull
  @Override
  public PropertiesTable<NlPropertyItem> getProperties() {
    PropertiesTable<NlPropertyItem> properties = myModel.getAllProperties().get(mySubTagName);
    if (properties == null) {
      properties = PropertiesTable.Companion.emptyTable();
    }
    return properties;
  }

  @Override
  public void deactivate() {
  }

  @Override
  public void addListener(@NotNull PropertiesModelListener<NlPropertyItem> listener) {
  }

  @Override
  public void removeListener(@NotNull PropertiesModelListener<NlPropertyItem> listener) {
  }
}
