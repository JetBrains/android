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

import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.property2.MotionLayoutAttributesModel;
import com.android.tools.idea.uibuilder.handlers.motion.property2.MotionSelection;
import com.android.tools.idea.uibuilder.property2.NelePropertyItem;
import com.android.tools.property.panel.api.TableLineModel;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

public class DeleteCustomFieldAction extends AnAction {
  private TableLineModel myLineModel;

  public DeleteCustomFieldAction() {
    super(null, "Remove selected attribute", StudioIcons.Common.REMOVE);
  }

  public void setLineModel(@NotNull TableLineModel lineModel) {
    myLineModel = lineModel;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    if (myLineModel == null) {
      return;
    }
    NelePropertyItem property = (NelePropertyItem)myLineModel.getSelectedItem();
    if (property == null) {
      return;
    }
    MotionSelection selection = MotionLayoutAttributesModel.getMotionSelection(property);
    if (selection == null) {
      return;
    }
    MotionSceneTag tag = selection.getMotionSceneTag();
    if (tag == null) {
      return;
    }
    MTag customTag = MotionLayoutAttributesModel.findCustomTag(tag, property.getName());
    if (customTag == null) {
      return;
    }
    MTag.TagWriter tagWriter = customTag.getTagWriter();
    tagWriter.deleteTag();
    tagWriter.commit("Delete " + property.getName());
  }
}
