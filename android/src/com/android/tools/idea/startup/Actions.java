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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.ui.customization.ActionUrl;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Constraints;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.EmptyAction;
import com.intellij.openapi.util.Pair;
import java.util.ArrayList;
import java.util.Arrays;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Actions {
  private Actions() {
  }

  public static void hideProgrammaticallyAddedAction(
    @NotNull CustomActionsSchema schema, @NotNull String actionId, int absolutePosition, @NotNull String... groupPaths) {
    ActionUrl actionUrl = createActionUrl(actionId, null, ActionUrl.DELETED, absolutePosition, groupPaths);
    if (!schema.getActions().contains(actionUrl)) {
      schema.addAction(actionUrl);
    }
  }

  @NotNull
  @VisibleForTesting
  static ActionUrl createActionUrl(
    @NotNull String actionId, @Nullable Icon actionIcon, int actionType, int absolutePosition, @NotNull String[] groupPaths) {
    // For a list of types that the component property of ActionUrl can hold, please refer to MyTreeCellRenderer#customizeCellRenderer in
    // CustomizableActionsPanel.java.
    Object component;
    if (actionIcon == null) {
      component = actionId;
    }
    else {
      component = Pair.create(actionId, actionIcon);
    }

    ActionUrl actionUrl = new ActionUrl();
    actionUrl.setComponent(component);
    actionUrl.setActionType(actionType);
    actionUrl.setGroupPath(new ArrayList<>(Arrays.asList(groupPaths)));
    actionUrl.setAbsolutePosition(absolutePosition);
    return actionUrl;
  }

  public static void hideAction(@NotNull String actionId) {
    AnAction oldAction = ActionManager.getInstance().getAction(actionId);
    if (oldAction != null) {
      replaceAction(actionId, new EmptyAction());
    }
  }

  public static void replaceAction(@NotNull String actionId, @NotNull AnAction newAction) {
    ActionManager actionManager = ActionManager.getInstance();
    AnAction oldAction = actionManager.getAction(actionId);
    if (oldAction != null) {
      newAction.getTemplatePresentation().setIcon(oldAction.getTemplatePresentation().getIcon());
      actionManager.replaceAction(actionId, newAction);
    }
    else {
      actionManager.registerAction(actionId, newAction);
    }
  }

  public static void moveAction(@NotNull String actionId, @NotNull String oldGroupId, @NotNull String groupId, @NotNull Constraints constraints) {
    ActionManager actionManager = ActionManager.getInstance();
    AnAction action = actionManager.getActionOrStub(actionId);
    AnAction group = actionManager.getAction(groupId);
    AnAction oldGroup = actionManager.getAction(oldGroupId);
    if (action != null && oldGroup instanceof DefaultActionGroup && group instanceof DefaultActionGroup) {
      ((DefaultActionGroup)oldGroup).remove(action);
      ((DefaultActionGroup)group).add(action, constraints);
    }
  }
}
