/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.*;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;

/**
 * {@link com.android.tools.idea.run.ExternalToolRunner} allows running the given
 * {@link com.intellij.execution.configurations.GeneralCommandLine} in an external process while attaching a console to it. The console
 * shows up inside the Run tool window with the given title. By default, a single stop action is provided in the toolbar attached to
 * the console that allows for killing the running command.
 *
 * This class is patterned after {@link com.intellij.execution.runners.AbstractConsoleRunnerWithHistory}.
 */
public class ExternalToolRunner {
  private final Project myProject;
  private final String myTitle;
  private final GeneralCommandLine myCommandLine;

  private ProcessHandler myProcessHandler;

  public ExternalToolRunner(@NotNull Project project, @NotNull String consoleTitle, @NotNull GeneralCommandLine commandLine) {
    myProject = project;
    myTitle = consoleTitle;
    myCommandLine = commandLine;
  }

  public ProcessHandler start() throws ExecutionException {
    final Process process = createProcess();
    myProcessHandler = createProcessHandler(process, myCommandLine);

    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        initConsoleUi();
      }
    });

    return myProcessHandler;
  }

  @NotNull
  protected Process createProcess() throws ExecutionException {
    return myCommandLine.createProcess();
  }

  @NotNull
  protected ProcessHandler createProcessHandler(Process process, @NotNull GeneralCommandLine commandLine) {
    return new OSProcessHandler(process, commandLine.getCommandLineString());
  }

  protected ConsoleView initConsoleUi() {
    ConsoleView consoleView = createConsoleView();
    consoleView.print(myCommandLine.getCommandLineString() + '\n', ConsoleViewContentType.USER_INPUT);
    consoleView.attachToProcess(myProcessHandler);

    JPanel panel = new JPanel(new BorderLayout());

    DefaultActionGroup toolbarActions = new DefaultActionGroup();
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions, false);
    panel.add(actionToolbar.getComponent(), BorderLayout.WEST);
    panel.add(consoleView.getComponent(), BorderLayout.CENTER);
    actionToolbar.setTargetComponent(panel);

    toolbarActions.addAll(consoleView.createConsoleActions());
    fillToolBarActions(toolbarActions);
    panel.updateUI();

    Executor defaultExecutor = DefaultRunExecutor.getRunExecutorInstance();
    final RunContentDescriptor contentDescriptor = new RunContentDescriptor(consoleView, myProcessHandler, panel, myTitle);
    showConsole(defaultExecutor, contentDescriptor);

    myProcessHandler.addProcessListener(new ConsoleListener(myProject, defaultExecutor, myProcessHandler));
    myProcessHandler.startNotify();
    return consoleView;
  }

  protected ConsoleView createConsoleView() {
    TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(myProject);
    builder.setViewer(true);
    return builder.getConsole();
  }

  protected void fillToolBarActions(DefaultActionGroup toolbarActions) {
    toolbarActions.addAction(ActionManager.getInstance().getAction(IdeActions.ACTION_STOP_PROGRAM));
  }

  protected void showConsole(Executor defaultExecutor, RunContentDescriptor myDescriptor) {
    // Show in run tool window
    ExecutionManager.getInstance(myProject).getContentManager().showRunContent(defaultExecutor, myDescriptor);
  }

  // Listens to console output and fronts the console in case of errors
  private static class ConsoleListener extends ProcessAdapter {
    private final Project myProject;
    private final Executor myExecutor;
    private final ProcessHandler myProcessHandler;

    public ConsoleListener(@NotNull Project project,
                           @NotNull Executor executor,
                           @NotNull ProcessHandler processHandler) {
      myProject = project;
      myExecutor = executor;
      myProcessHandler = processHandler;
    }

    @Override
    public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
      myProcessHandler.removeProcessListener(this);
    }

    @Override
    public void onTextAvailable(ProcessEvent event, Key outputType) {
      String text = event.getText();
      boolean hasError = text != null && text.toLowerCase(Locale.US).contains("error");
      if (hasError || ProcessOutputTypes.STDERR.equals(outputType)) {
        ExecutionManager.getInstance(myProject).getContentManager().toFrontRunContent(myExecutor, myProcessHandler);
      }
    }
  }

  public static class ProcessOutputCollector extends ProcessAdapter {
    private final StringBuilder sb = new StringBuilder();

    @Override
    public void onTextAvailable(ProcessEvent event, Key outputType) {
      sb.append(event.getText());
    }

    public String getText() {
      return sb.toString();
    }
  }
}
