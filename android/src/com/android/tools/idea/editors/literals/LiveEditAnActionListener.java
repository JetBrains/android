/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors.literals;

import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration;
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
import org.jetbrains.annotations.Nullable;

public class LiveEditAnActionListener implements AnActionListener {

  private static final String SAVEALL_NO_SHORTCUT_MSG = "[SaveAll shortcut]";

  @Override
  public void beforeShortcutTriggered(@NotNull Shortcut shortcut, @NotNull List<AnAction> actions, @NotNull DataContext dataContext) {
    Project project = dataContext.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    if (Arrays.asList(getLiveEditTriggerShortCut()).contains(shortcut)) {
      triggerLiveEdit(project);
    }
  }

  public static void triggerLiveEdit(@NotNull Project project) {
    if (!LiveEditApplicationConfiguration.getInstance().isLiveEdit()) {
      return;
    }

    if (!LiveEditService.Companion.isLeTriggerManual()) {
      return;
    }

    LiveEditService.getInstance(project).triggerLiveEdit();
  }

  @Nullable
  private static Shortcut[] getLiveEditTriggerShortCut() {
    AnAction saveAction = ActionManager.getInstance().getAction(LiveEditService.getPIGGYBACK_ACTION_ID());
    if (saveAction == null) {
      return null;
    }

    Shortcut[] shortcuts = saveAction.getShortcutSet().getShortcuts();
    if (shortcuts.length == 0) {
      return new Shortcut[0];
    }
    return shortcuts;
  }

  public static String getLiveEditTriggerShortCutString() {
    Shortcut[] shortcuts = getLiveEditTriggerShortCut();
    if (shortcuts == null || shortcuts.length == 0) {
      return SAVEALL_NO_SHORTCUT_MSG ;
    }

    return KeymapUtil.getShortcutText(shortcuts[0]);
  }
}
