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
package com.android.tools.idea.uibuilder.handlers.motion.property.action;

import com.android.tools.idea.uibuilder.handlers.motion.property.MotionLayoutAttributesModel;
import com.android.tools.idea.uibuilder.handlers.motion.property.MotionSelection;
import com.android.tools.idea.uibuilder.handlers.motion.property.NewCustomAttributePanel;
import com.android.tools.property.panel.api.TableLineModel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class AddCustomFieldAction extends AnAction {
  private static final String ADD_CUSTOM_ATTRIBUTE = "Add custom attribute";
  private final MotionLayoutAttributesModel myModel;
  private final MotionSelection mySelection;
  private TableLineModel myLineModel;
  private NewCustomAttributePanel myDialog;

  public AddCustomFieldAction(@NotNull MotionLayoutAttributesModel model, @NotNull MotionSelection selection) {
    super(ADD_CUSTOM_ATTRIBUTE, ADD_CUSTOM_ATTRIBUTE, AllIcons.General.Add);
    myModel = model;
    mySelection = selection;
  }

  public void setLineModel(@NotNull TableLineModel lineModel) {
    myLineModel = lineModel;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    if (myDialog == null || myDialog.isDisposed()) {
      myDialog = new NewCustomAttributePanel(myModel, mySelection, myLineModel);
    }
    myDialog.show();
  }
}
