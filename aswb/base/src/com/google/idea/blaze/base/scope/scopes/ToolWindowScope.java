/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser;
import com.google.idea.blaze.base.issueparser.ToolWindowTaskIssueOutputFilter;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.base.scope.OutputSink;
import com.google.idea.blaze.base.scope.OutputSink.Propagation;
import com.google.idea.blaze.base.scope.output.StateUpdate;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.scope.output.SummaryOutput;
import com.google.idea.blaze.base.settings.BlazeUserSettings.FocusBehavior;
import com.google.idea.blaze.base.toolwindow.Task;
import com.google.idea.blaze.base.toolwindow.Task.Status;
import com.google.idea.blaze.base.toolwindow.TasksToolWindowService;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.PrintOutput.OutputType;
import com.intellij.execution.filters.Filter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import java.util.Set;
import javax.annotation.Nullable;

/** ToolWindowScope passes print outputs to the Blaze Outputs Tool Window. */
public final class ToolWindowScope implements BlazeScope {

  /** Builder for the {@link ToolWindowScope} */
  public static final class Builder {
    private final Project project;
    private final Task task;
    private ProgressIndicator progressIndicator;
    private boolean startTaskOnScopeBegin = true;
    private boolean finishTaskOnScopeEnd = true;
    private boolean showSummaryOutput = false;
    private FocusBehavior popupBehavior = FocusBehavior.ON_ERROR;
    private ImmutableList<BlazeIssueParser.Parser> parsers = ImmutableList.of();

    public Builder(Project project, Task task) {
      this.project = project;
      this.task = task;
    }

    @CanIgnoreReturnValue
    public Builder setProgressIndicator(ProgressIndicator progressIndicator) {
      this.progressIndicator = progressIndicator;
      return this;
    }

    /**
     * Makes the scope to start or not start the task when scope begins. The default behaviour is to
     * start the task.
     */
    @CanIgnoreReturnValue
    public Builder setStartTaskOnScopeBegin(boolean startTaskOnScopeBegin) {
      this.startTaskOnScopeBegin = startTaskOnScopeBegin;
      return this;
    }

    /**
     * Makes the scope to stop or not stop the task when the scope ends. The default behaviour is to
     * stop the task.
     */
    @CanIgnoreReturnValue
    public Builder setFinishTaskOnScopeEnd(boolean finishTaskOnScopeEnd) {
      this.finishTaskOnScopeEnd = finishTaskOnScopeEnd;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setIssueParsers(ImmutableList<BlazeIssueParser.Parser> parsers) {
      this.parsers = parsers;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setPopupBehavior(FocusBehavior popupBehavior) {
      this.popupBehavior = popupBehavior;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder showSummaryOutput() {
      this.showSummaryOutput = true;
      return this;
    }

    public BlazeScope build() {
      return new ToolWindowScope(
          project,
          task,
          progressIndicator,
          startTaskOnScopeBegin,
          finishTaskOnScopeEnd,
          showSummaryOutput,
          popupBehavior,
          parsers.isEmpty() || !startTaskOnScopeBegin
              ? ImmutableList.of()
              : ImmutableList.of(
                  ToolWindowTaskIssueOutputFilter.createWithLinkToTask(project, parsers, task)));
    }
  }

  private final Task task;
  @Nullable private final ProgressIndicator progressIndicator;
  private final boolean startTaskOnScopeBegin;
  private final FocusBehavior popupBehavior;
  private final ImmutableList<Filter> consoleFilters;
  private final TasksToolWindowService tasksToolWindowController;
  private final OutputSink<PrintOutput> printSink;
  private final OutputSink<StatusOutput> statusSink;
  private final OutputSink<StateUpdate> stateSink;
  @Nullable private final OutputSink<SummaryOutput> summarySink;

  private boolean finishTaskOnScopeEnd;
  private boolean activated;
  private final Set<String> dedupedSummaryOutput = Sets.newHashSet();

  private ToolWindowScope(
      Project project,
      Task task,
      @Nullable ProgressIndicator progressIndicator,
      boolean startTaskOnScopeBegin,
      boolean finishTaskOnScopeEnd,
      boolean showSummaryOutput,
      FocusBehavior popupBehavior,
      ImmutableList<Filter> consoleFilters) {
    this.task = task;
    this.progressIndicator = progressIndicator;
    this.startTaskOnScopeBegin = startTaskOnScopeBegin;
    this.finishTaskOnScopeEnd = finishTaskOnScopeEnd;
    this.popupBehavior = popupBehavior;
    this.consoleFilters = consoleFilters;
    tasksToolWindowController = TasksToolWindowService.getInstance(project);
    printSink =
        (output) -> {
          tasksToolWindowController.output(task, output);
          activateIfNeeded(output.getOutputType());
          return Propagation.Stop;
        };
    statusSink =
        (output) -> {
          tasksToolWindowController.status(task, output);
          activateIfNeeded(OutputType.NORMAL);
          return Propagation.Stop;
        };
    stateSink =
        (output) -> {
          tasksToolWindowController.state(task, output);
          return Propagation.Stop;
        };
    if (showSummaryOutput) {
      summarySink =
          (output) -> {
            if (task.getParent().isPresent()) {
              return Propagation.Continue;
            }
            if (!output.shouldDedupe() || dedupedSummaryOutput.add(output.getRawText())) {
              return printSink.onOutput(output.toPrintOutput());
            } else {
              return Propagation.Stop;
            }
          };
    } else {
      summarySink = null;
    }
  }

  @Override
  public void onScopeBegin(BlazeContext context) {
    context.addOutputSink(PrintOutput.class, printSink);
    context.addOutputSink(StatusOutput.class, statusSink);
    context.addOutputSink(StateUpdate.class, stateSink);
    if (summarySink != null) {
      context.addOutputSink(SummaryOutput.class, summarySink);
    }
    context.addCancellationHandler(
        () -> {
          if (progressIndicator != null) {
            progressIndicator.cancel();
          }
        });
    if (startTaskOnScopeBegin) {
      tasksToolWindowController.startTask(task, consoleFilters);
    }
    tasksToolWindowController.setStopHandler(
        task,
        () -> {
          context.setCancelled();
        });
  }

  private static Task.Status statusForContext(BlazeContext context) {
    if (context.hasErrors()) {
      return Status.ERROR;
    } else if (context.isCancelled()) {
      return Status.CANCELLED;
    } else if (context.hasWarnings()) {
      return Status.FINISHED_WITH_WARNINGS;
    } else {
      return Status.FINISHED;
    }
  }

  @Override
  public void onScopeEnd(BlazeContext context) {
    if (finishTaskOnScopeEnd) {
      tasksToolWindowController.finishTask(task, statusForContext(context));
    }
    tasksToolWindowController.removeStopHandler(task);
  }

  public Task getTask() {
    return task;
  }

  public void setFinishTaskOnScopeEnd(boolean finishTaskOnScopeEnd) {
    this.finishTaskOnScopeEnd = finishTaskOnScopeEnd;
  }

  private void activateIfNeeded(OutputType outputType) {
    if (activated) {
      return;
    }
    boolean activate =
        popupBehavior == FocusBehavior.ALWAYS
            || (popupBehavior == FocusBehavior.ON_ERROR && outputType == OutputType.ERROR);
    if (activate) {
      activated = true;
      ApplicationManager.getApplication().invokeLater(tasksToolWindowController::activate);
    }
  }
}
