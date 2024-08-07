/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.base.scope.OutputSink;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.BlazeUserSettings.FocusBehavior;
import com.google.idea.blaze.base.ui.problems.BuildTasksProblemsView;
import com.intellij.openapi.project.Project;

/** Shows the compiler output. */
public class ProblemsViewScope implements BlazeScope, OutputSink<IssueOutput> {

  private final Project project;
  private final FocusBehavior problemsViewFocusBehavior;
  private final boolean resetProblemsContext;

  public ProblemsViewScope(Project project, FocusBehavior problemsViewFocusBehavior) {
    this(project, problemsViewFocusBehavior, true);
  }

  public ProblemsViewScope(
      Project project, FocusBehavior problemsViewFocusBehavior, boolean resetProblemsContext) {
    this.project = project;
    this.problemsViewFocusBehavior = problemsViewFocusBehavior;
    this.resetProblemsContext = resetProblemsContext;
  }

  @Override
  public void onScopeBegin(BlazeContext context) {
    context.addOutputSink(IssueOutput.class, this);
    if (resetProblemsContext) {
        BuildTasksProblemsView.getInstance(project).newProblemsContext(problemsViewFocusBehavior);
    }
  }

  @Override
  public Propagation onOutput(IssueOutput output) {
      BuildTasksProblemsView.getInstance(project).addMessage(output, null);
    return Propagation.Continue;
  }
}
