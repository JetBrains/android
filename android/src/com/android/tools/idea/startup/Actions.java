/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.startup;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Constraints;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.EmptyAction;
import com.intellij.openapi.actionSystem.EmptyActionGroup;
import org.jetbrains.annotations.NotNull;

public final class Actions {
  private Actions() {
  }

  public static void hideAction(@NotNull ActionManager actionManager, @NotNull String actionId) {
    if (actionManager.getActionOrStub(actionId) == null) {
      return; // Action not found.
    }
    var emptyReplacement = actionManager.isGroup(actionId) ? new EmptyActionGroup() : new EmptyAction();
    actionManager.replaceAction(actionId, emptyReplacement);
  }

  public static void replaceAction(@NotNull ActionManager actionManager, @NotNull String actionId, @NotNull AnAction newAction) {
    AnAction oldAction = actionManager.getAction(actionId);
    if (oldAction != null) {
      newAction.getTemplatePresentation().setIcon(oldAction.getTemplatePresentation().getIcon());
      actionManager.replaceAction(actionId, newAction);
    }
    else {
      actionManager.registerAction(actionId, newAction);
    }
  }

  public static void moveAction(
      @NotNull ActionManager actionManager,
      @NotNull String actionId,
      @NotNull String oldGroupId,
      @NotNull String groupId,
      @NotNull Constraints constraints) {
    AnAction action = actionManager.getActionOrStub(actionId);
    AnAction group = actionManager.getAction(groupId);
    AnAction oldGroup = actionManager.getAction(oldGroupId);
    if (action != null && oldGroup instanceof DefaultActionGroup && group instanceof DefaultActionGroup) {
      ((DefaultActionGroup)oldGroup).remove(action, actionManager);
      ((DefaultActionGroup)group).add(action, constraints, actionManager);
    }
  }
}
