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

import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.android.tools.property.panel.api.TableLineModel;
import com.android.tools.property.ptable.PTableItem;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeleteMotionFieldAction extends AnAction {
  private static final String REMOVE_ATTRIBUTE = "Remove selected attribute";
  private TableLineModel myLineModel;

  public DeleteMotionFieldAction() {
    super(REMOVE_ATTRIBUTE, REMOVE_ATTRIBUTE, AllIcons.General.Remove);
    ActionManager manager = ActionManager.getInstance();
    setShortcutSet(manager.getAction(IdeActions.ACTION_DELETE).getShortcutSet());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    // Updated in the EDT because of the myLinesModel model access.
    return ActionUpdateThread.EDT;
  }

  public void setLineModel(@NotNull TableLineModel lineModel) {
    myLineModel = lineModel;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    boolean enabled = myLineModel != null && !myLineModel.getTableModel().getItems().isEmpty();
    event.getPresentation().setEnabled(enabled);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    NlPropertyItem property = getSelectedOrFirstItem(myLineModel);
    if (property != null) {
      myLineModel.removeItem(property);
    }
  }

  @Nullable
  public static NlPropertyItem getSelectedOrFirstItem(@Nullable TableLineModel lineModel) {
    if (lineModel == null) {
      return null;
    }
    NlPropertyItem property = (NlPropertyItem)lineModel.getSelectedItem();
    if (property == null) {
      List<PTableItem> items = lineModel.getTableModel().getItems();
      if (items.isEmpty()) {
        return null;
      }
      property = (NlPropertyItem)items.get(0);
    }
    return property;
  }
}
