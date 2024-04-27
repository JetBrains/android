/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.editors.liveedit;

import static com.android.tools.idea.editors.liveedit.LiveEditService.Companion.LiveEditTriggerMode.ON_SAVE;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Monitor shortcut invocation and detect if they match "Save All". If that is the case, we trigger LiveEdit if it is in ON_SAVE mode.
 **/
public class LiveEditAnActionListener implements AnActionListener {

  private static final String SAVEALL_NO_SHORTCUT_MSG = "[SaveAll shortcut]";

  @Override
  public void beforeShortcutTriggered(@NotNull Shortcut shortcut, @NotNull List<AnAction> actions, @NotNull DataContext dataContext) {
    Project project = dataContext.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    if (LiveEditApplicationConfiguration.getInstance().getLeTriggerMode() != ON_SAVE) {
      return;
    }
    if (Arrays.asList(getLiveEditTriggerShortCut()).contains(shortcut)) {
      LiveEditService.manualLiveEdit(project);
    }
  }

  @NotNull
  private static Shortcut[] getLiveEditTriggerShortCut() {
    AnAction saveAction = ActionManager.getInstance().getAction(LiveEditService.getPIGGYBACK_ACTION_ID());
    if (saveAction == null) {
      return new Shortcut[0];
    }

    Shortcut[] shortcuts = saveAction.getShortcutSet().getShortcuts();
    if (shortcuts.length == 0) {
      return new Shortcut[0];
    }
    return shortcuts;
  }

  public static String getLiveEditTriggerShortCutString() {
    Shortcut[] shortcuts = getLiveEditTriggerShortCut();
    if (shortcuts.length == 0) {
      return SAVEALL_NO_SHORTCUT_MSG ;
    }

    return KeymapUtil.getShortcutText(shortcuts[0]);
  }
}
