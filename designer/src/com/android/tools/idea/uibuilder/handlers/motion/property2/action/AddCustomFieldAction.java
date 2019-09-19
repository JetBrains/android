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

import com.android.tools.idea.uibuilder.handlers.motion.attributeEditor.NewCustomAttributePanel;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneTag;
import com.android.tools.idea.uibuilder.handlers.motion.property2.MotionLayoutAttributesModel;
import com.android.tools.idea.uibuilder.handlers.motion.property2.MotionLayoutPropertyProvider;
import com.android.tools.idea.uibuilder.handlers.motion.property2.MotionSelection;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.MotionSceneModel;
import com.android.tools.idea.uibuilder.property2.NelePropertyItem;
import com.android.tools.property.panel.api.FilteredPTableModel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.text.StringUtil;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

public class AddCustomFieldAction extends AnAction {
  private final FilteredPTableModel<NelePropertyItem> myTableModel;
  private final NelePropertyItem myProperty;
  private final MotionLayoutAttributesModel myModel;

  public AddCustomFieldAction(@NotNull FilteredPTableModel<NelePropertyItem> tableModel, @NotNull NelePropertyItem property) {
    super(null, "Add Custom Attribute", AllIcons.General.Add);
    myTableModel = tableModel;
    myProperty = property;
    myModel = (MotionLayoutAttributesModel)myProperty.getModel();
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    NewCustomAttributePanel newAttributePanel = new NewCustomAttributePanel();
    newAttributePanel.show();
    if (!newAttributePanel.isOK()) {
      return;
    }
    String attributeName = newAttributePanel.getAttributeName();
    String value = newAttributePanel.getInitialValue();
    MotionSceneModel.CustomAttributes.Type type = newAttributePanel.getType();
    if (StringUtil.isEmpty(attributeName)) {
      return;
    }
    MotionSelection selection = MotionLayoutAttributesModel.getMotionSelection(myProperty);
    if (selection == null) {
      return;
    }
    Consumer<MotionSceneTag> applyToModel = newCustomTag -> {
      NelePropertyItem newProperty = MotionLayoutPropertyProvider.createCustomProperty(
        attributeName, type.getTagName(), selection, myProperty.getModel());
      myTableModel.addNewItem(newProperty);
    };

    myModel.createCustomXmlTag(selection, attributeName, value, type, applyToModel);
  }
}
