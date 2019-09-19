/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.property2.action;

import com.android.tools.idea.uibuilder.handlers.motion.property2.MotionLayoutAttributesModel;
import com.android.tools.idea.uibuilder.property2.NeleNewPropertyItem;
import com.android.tools.idea.uibuilder.property2.NelePropertyItem;
import com.android.tools.property.panel.api.FilteredPTableModel;
import com.android.tools.property.panel.api.PropertiesTable;
import com.android.tools.property.panel.api.TableLineModel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class AddMotionFieldAction extends AnAction {
  private final FilteredPTableModel<NelePropertyItem> myTableModel;
  private final NeleNewPropertyItem myNewProperty;
  private TableLineModel myLineModel;

  public AddMotionFieldAction(@NotNull MotionLayoutAttributesModel model,
                              @NotNull FilteredPTableModel<NelePropertyItem> tableModel,
                              @NotNull PropertiesTable<NelePropertyItem> properties) {
    super(null, "Add Attribute", AllIcons.General.Add);
    myTableModel = tableModel;
    myNewProperty = new NeleNewPropertyItem(model, properties, (item) -> item.getRawValue() == null, (delegate) -> null);
  }

  public void setLineModel(@NotNull TableLineModel lineModel) {
    myLineModel = lineModel;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    NelePropertyItem nextItem = myTableModel.addNewItem(myNewProperty);
    myLineModel.requestFocus(nextItem);
  }
}
