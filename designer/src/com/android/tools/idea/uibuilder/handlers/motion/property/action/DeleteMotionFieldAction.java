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
package com.android.tools.idea.uibuilder.handlers.motion.property.action;

import static com.intellij.openapi.actionSystem.IdeActions.ACTION_DELETE;

import com.android.tools.idea.uibuilder.property.NelePropertyItem;
import com.android.tools.property.panel.api.TableLineModel;
import com.android.tools.property.ptable2.PTableItem;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import icons.StudioIcons;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeleteMotionFieldAction extends AnAction {
  private TableLineModel myLineModel;

  public DeleteMotionFieldAction() {
    super(null, "Remove selected attribute", StudioIcons.Common.REMOVE);
    ActionManager manager = ActionManager.getInstance();
    setShortcutSet(manager.getAction(ACTION_DELETE).getShortcutSet());
  }

  public void setLineModel(@NotNull TableLineModel lineModel) {
    myLineModel = lineModel;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    boolean enabled = myLineModel != null && !myLineModel.getTableModel().getItems().isEmpty();
    event.getPresentation().setEnabled(enabled);

    // Hack: the FocusableActionButton will update when the state of the template presentation is updated:
    getTemplatePresentation().setEnabled(enabled);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    NelePropertyItem property = getSelectedOrFirstItem(myLineModel);
    if (property != null) {
      myLineModel.removeItem(property);
    }
  }

  @Nullable
  public static NelePropertyItem getSelectedOrFirstItem(@Nullable TableLineModel lineModel) {
    if (lineModel == null) {
      return null;
    }
    NelePropertyItem property = (NelePropertyItem)lineModel.getSelectedItem();
    if (property == null) {
      List<PTableItem> items = lineModel.getTableModel().getItems();
      if (items.isEmpty()) {
        return null;
      }
      property = (NelePropertyItem)items.get(0);
    }
    return property;
  }
}
