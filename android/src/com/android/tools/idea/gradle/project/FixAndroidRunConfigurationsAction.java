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
package com.android.tools.idea.gradle.project;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenUrlHyperlink;
import com.android.tools.idea.gradle.run.MakeBeforeRunTaskProviderUtil;
import com.android.tools.idea.project.AndroidNotification;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.commands.ActionCommand;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

import static com.android.tools.idea.project.AndroidNotification.BALLOON_GROUP;
import static com.intellij.notification.NotificationType.WARNING;

public class FixAndroidRunConfigurationsAction extends DumbAwareAction {
  public final static String ID = "Android.FixAndroidRunConfigurations";

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(FixAndroidRunConfigurationsAction.class);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(StudioFlags.FIX_ANDROID_RUN_CONFIGURATIONS_ENABLED.get());
    e.getPresentation().setVisible(false);
  }

  public static void perform(@Nullable Project project) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (project == null || project.isDisposed()) {
      return;
    }
    executeAction(ID);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null || project.isDisposed()) {
      return;
    }
    if (!StudioFlags.FIX_ANDROID_RUN_CONFIGURATIONS_ENABLED.get()) {
      return;
    }

    List<RunConfiguration> fixedConfigs = MakeBeforeRunTaskProviderUtil.fixConfigurationsMissingBeforeRunTask(project);
    if (fixedConfigs.isEmpty()) {
      return;
    }

    String configs = fixedConfigs.stream().map(x -> "\"" + x.getName() + "\"").collect(Collectors.joining(", "));
    String message = String.format("The IDE updated the following %s: %s.",
                                   StringUtil.pluralize("run configuration", fixedConfigs.size()),
                                   configs);
    getLogger().warn(message);
    AndroidNotification.getInstance(project).showBalloon(
      "Android Run Configuration",
      message,
      WARNING,
      BALLOON_GROUP,
      new OpenUrlHyperlink("https://d.android.com/r/studio-ui/gradle-aware-make-fix.html", "Learn more"));
  }

  private static void executeAction(@SuppressWarnings("SameParameterValue") @NotNull String actionId) {
    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    AnAction action = actionManager.getAction(actionId);
    if (action == null) {
      return;
    }
    actionManager.tryToExecute(action, ActionCommand.getInputEvent(actionId), null, ActionPlaces.UNKNOWN, true);
  }
}
