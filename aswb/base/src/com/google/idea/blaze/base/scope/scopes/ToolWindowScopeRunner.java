/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.scope.scopes;

import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.toolwindow.Task;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;

/**
 * A utility class to run a background action that needs the Bazel output tool window.
 */
public class ToolWindowScopeRunner {
  private static final BoolExperiment showWindowOnAutomaticSyncErrors =
    new BoolExperiment("querysync.autosync.show.console.on.error", true);

  public static boolean runTaskWithToolWindow(Project project,
                                              String title,
                                              String subTitle,
                                              QuerySyncManager.TaskOrigin taskOrigin,
                                              BlazeUserSettings userSettings,
                                              QuerySyncManager.ThrowingScopedOperation operation) {
    return Scope.root(
      context -> {
        Task task = new Task(project, subTitle, Task.Type.SYNC);
        BlazeScope scope =
          new ToolWindowScope.Builder(project, task)
            .showSummaryOutput()
            .setPopupBehavior(
              taskOrigin == QuerySyncManager.TaskOrigin.AUTOMATIC
              ? showWindowOnAutomaticSyncErrors.getValue()
                ? BlazeUserSettings.FocusBehavior.ON_ERROR
                : BlazeUserSettings.FocusBehavior.NEVER
              : userSettings.getShowBlazeConsoleOnSync())
            .setIssueParsers(
              BlazeIssueParser.defaultIssueParsers(
                project,
                WorkspaceRoot.fromProject(project),
                BlazeInvocationContext.ContextType.Sync))
            .build();
        context
          .push(scope)
          .push(
            new ProblemsViewScope(
              project, userSettings.getShowProblemsViewOnSync()))
          .push(new IdeaLogScope());
        try {
          operation.execute(context);
        }
        catch (Exception e) {
          context.handleException(title + " failed", e);
        }
        return !context.hasErrors();
      });
  }
}
