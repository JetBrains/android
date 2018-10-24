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
package com.android.tools.idea.run.tasks;

import com.android.tools.deployer.DeployerException;
import com.android.tools.idea.run.ui.ApplyChangesAction;
import com.android.tools.idea.run.ui.CodeSwapAction;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.ui.playback.commands.ActionCommand;
import java.util.List;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class DeploymentErrorHandler {
  @NotNull
  static final String APPLY_CHANGES_LINK = "apply_changes";
  @VisibleForTesting
  static final String APPLY_CHANGES_OPTION = "<a href='" + APPLY_CHANGES_LINK + "'>Apply Changes</a>";
  @NotNull
  static final String RERUN_LINK = "rerun";
  @VisibleForTesting
  static final String RERUN_OPTION = "<a href='" + RERUN_LINK + "'>Rerun</a>";

  @NotNull
  private final String myFormattedErrorString;
  @Nullable
  private final NotificationListener myNotificationListener;

  DeploymentErrorHandler(@NotNull String simpleErrorString) {
    myFormattedErrorString = simpleErrorString;
    myNotificationListener = null;
  }

  DeploymentErrorHandler(@NotNull UnifiedDeployTask.DeployType type,
                         DeployerException.Error error,
                         @NotNull String errorsString) {
    myFormattedErrorString = formatDeploymentErrors(type, error, errorsString);
    myNotificationListener = new DeploymentErrorNotificationListener();
  }

  @NotNull
  String getFormattedErrorString() {
    return myFormattedErrorString;
  }

  @Nullable
  NotificationListener getNotificationListener() {
    return myNotificationListener;
  }

  @NotNull
  private String formatDeploymentErrors(@NotNull UnifiedDeployTask.DeployType type,
                                        DeployerException.Error error,
                                        @NotNull String errorsString) {
    List<String> errors = Lists.newArrayList(Splitter.on('\n').split(errorsString));
    errors.sort(String::compareTo); // Sort the errors so at least they're consistent over runs.

    StringBuilder builder = new StringBuilder();

    if (errors.isEmpty() || type == UnifiedDeployTask.DeployType.INSTALL) {
      builder.append("Incompatible changes.");
    }
    else {
      builder.append("Recent changes are incompatible with ");
      //noinspection EnumSwitchStatementWhichMissesCases
      switch (type) {
        case CODE_SWAP:
          builder.append(CodeSwapAction.NAME);
          break;
        case FULL_SWAP:
          builder.append(ApplyChangesAction.NAME);
          break;
      }
      builder.append(".\n");
    }

    // TODO(b/117673388): Add "Learn More" hyperlink when we finally have the webpage up.
    if (type == UnifiedDeployTask.DeployType.CODE_SWAP && errors.size() == 1 && DeployerException.Error.CANNOT_SWAP_RESOURCE.equals(error)) {
      builder.append(APPLY_CHANGES_OPTION);
      builder.append(" | ");
    }
    builder.append(RERUN_OPTION);

    return builder.toString();
  }

  private static class DeploymentErrorNotificationListener implements NotificationListener {
    @Override
    public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
      if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
        return;
      }
      ActionManager manager = ActionManager.getInstance();
      String actionId = null;
      switch (event.getDescription()) {
        case APPLY_CHANGES_LINK:
          actionId = ApplyChangesAction.ID;
          break;
        case RERUN_LINK:
          actionId = IdeActions.ACTION_DEFAULT_RUNNER;
          break;
      }
      if (actionId == null) {
        return;
      }
      AnAction action = manager.getAction(actionId);
      if (action == null) {
        return;
      }
      manager.tryToExecute(action, ActionCommand.getInputEvent(ApplyChangesAction.ID), null, ActionPlaces.UNKNOWN, true);
    }
  }
}
