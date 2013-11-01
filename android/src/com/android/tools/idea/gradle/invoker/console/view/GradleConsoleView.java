/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.invoker.console.view;

import com.intellij.codeEditor.printing.PrintAction;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.ide.actions.NextOccurenceToolbarAction;
import com.intellij.ide.actions.PreviousOccurenceToolbarAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class GradleConsoleView implements Disposable {
  private static final Class<?>[] IGNORED_CONSOLE_ACTION_TYPES =
    {PreviousOccurenceToolbarAction.class, NextOccurenceToolbarAction.class, ConsoleViewImpl.ClearAllAction.class, PrintAction.class};

  @NotNull private final Project myProject;
  @NotNull private final ConsoleViewImpl myConsoleView;

  private JPanel myConsolePanel;

  public GradleConsoleView(@NotNull Project project) {
    myProject = project;
    myConsoleView = new ConsoleViewImpl(myProject, false);
    Disposer.register(this, myConsoleView);
  }

  public static GradleConsoleView getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleConsoleView.class);
  }

  public void createToolWindowContent(@NotNull ToolWindow toolWindow) {
    //Create runner UI layout
    RunnerLayoutUi.Factory factory = RunnerLayoutUi.Factory.getInstance(myProject);
    RunnerLayoutUi layoutUi = factory.create("", "", "session", myProject);

    // Adding actions
    DefaultActionGroup group = new DefaultActionGroup();
    layoutUi.getOptions().setLeftToolbar(group, ActionPlaces.UNKNOWN);

    Content console = layoutUi.createContent(GradleConsoleToolWindowFactory.ID, myConsoleView.getComponent(), "", null, null);
    AnAction[] consoleActions = myConsoleView.createConsoleActions();
    for (AnAction action : consoleActions) {
      if (!shouldIgnoreAction(action)) {
        group.add(action);
      }
    }
    layoutUi.addContent(console, 0, PlaceInGrid.right, false);

    JComponent layoutComponent = layoutUi.getComponent();
    myConsolePanel.add(layoutComponent, BorderLayout.CENTER);

    //noinspection ConstantConditions
    Content content = ContentFactory.SERVICE.getInstance().createContent(layoutComponent, null, true);
    toolWindow.getContentManager().addContent(content);
  }

  private static boolean shouldIgnoreAction(@NotNull AnAction action) {
    for (Class<?> actionType : IGNORED_CONSOLE_ACTION_TYPES) {
      if (actionType.isInstance(action)) {
        return true;
      }
    }
    return false;
  }

  public void clear() {
    myConsoleView.clear();
  }

  public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
    myConsoleView.print(text, contentType);
  }

  @Override
  public void dispose() {
  }
}
