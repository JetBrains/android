/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.filters;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_USER_REQUEST_RERUN_WITH_ADDITIONAL_OPTIONS;

import com.android.tools.idea.gradle.actions.ExplainSyncOrBuildOutput;
import com.android.tools.idea.gradle.project.build.output.ExplainBuildErrorFilter;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.studiobot.StudioBot;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemResolveProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.GradleExecutionConsoleManager;
import org.jetbrains.plugins.gradle.execution.filters.GradleReRunBuildFilter;
import org.jetbrains.plugins.gradle.util.GradleConstants;

public class AndroidGradleExecutionConsoleManager extends GradleExecutionConsoleManager {
  public static final Key<String[]> EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY = Key.create("extra.gradle.command.line.options");

  @Override
  public boolean isApplicableFor(@NotNull ExternalSystemTask task) {
    return GradleConstants.SYSTEM_ID.equals(task.getId().getProjectSystemId()) &&
           task instanceof ExternalSystemResolveProjectTask;
  }

  @Nullable
  @Override
  public ExecutionConsole attachExecutionConsole(@NotNull Project project,
                                                 @NotNull ExternalSystemTask task,
                                                 @Nullable ExecutionEnvironment env,
                                                 @Nullable ProcessHandler processHandler) {
    // Register AndroidReRunSyncFilter for Gradle Sync tasks.
    // The default filter calls refreshProject from ExternalSystemUtil, which corresponds to GradleSyncInvoker in Android Studio.
    if (task instanceof ExternalSystemResolveProjectTask) {
      ConsoleView executionConsole = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
      executionConsole.attachToProcess(processHandler);
      executionConsole.addMessageFilter(new AndroidReRunSyncFilter(((ExternalSystemResolveProjectTask)task).getExternalProjectPath()));
      return executionConsole;
    }
    return super.attachExecutionConsole(project, task, env, processHandler);
  }

  /** Converts console output items injected by Studio Bot into hyperlinks. */
  @Override
  public Filter[] getCustomExecutionFilters(@NotNull Project project,
                                            @NotNull ExternalSystemTask task,
                                            @Nullable ExecutionEnvironment env) {
    Filter[] filters = super.getCustomExecutionFilters(project, task, env);
    StudioBot studioBot = StudioBot.Companion.getInstance();
    if (studioBot == null || !studioBot.isAvailable()) {
      return filters;
    }
    Filter[] customFilters = new Filter[filters.length + 1];
    System.arraycopy(filters, 0, customFilters, 0, filters.length);
    customFilters[filters.length] = new ExplainBuildErrorFilter();
    return customFilters;
  }

  @SuppressWarnings("UnstableApiUsage")
  @Override
  public AnAction[] getCustomContextActions(@NotNull Project project,
                                            @NotNull ExternalSystemTask task,
                                            @Nullable ExecutionEnvironment env) {
    // adds a Gemini popup menu item to the sync tree view
    AnAction[] contextActions = super.getCustomContextActions(project, task, env);
    StudioBot studioBot = StudioBot.Companion.getInstance();
    if (!studioBot.isAvailable()) {
      return contextActions;
    }
    AnAction[] extendedActions = new AnAction[contextActions.length + 1];
    System.arraycopy(contextActions, 0, extendedActions, 0, contextActions.length);
    extendedActions[contextActions.length] = new ExplainSyncOrBuildOutput();
    return extendedActions;
  }

  static class AndroidReRunSyncFilter extends GradleReRunBuildFilter {
    AndroidReRunSyncFilter(@NotNull String projectPath) {
      super(projectPath);
    }

    @NotNull
    @Override
    protected HyperlinkInfo getHyperLinkInfo(@NotNull List<String> options) {
      // Create a hyperlink that when clicked invoking Gradle Sync with the given command line options.
      return (project) -> {
        project.putUserData(EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY, options.toArray(new String[0]));
        GradleSyncInvoker.getInstance()
          .requestProjectSync(project, new GradleSyncInvoker.Request(TRIGGER_USER_REQUEST_RERUN_WITH_ADDITIONAL_OPTIONS), null);
      };
    }
  }
}