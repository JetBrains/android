/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.stats;

import static com.android.tools.idea.stats.StatisticsViewerListener.register;

import com.android.tools.analytics.AnalyticsSettings;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBColor;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * A non-modal dialog displaying statistics as they're logged.
 */
public class StatisticsViewer extends JPanel implements Disposable {
  public static final String ID = "StatisticsViewer";
  public static final String TITLE = "Android Studio Usage Statistics";
  private final ConsoleViewImpl myConsoleView;

  private DialogWrapper myDialog;

  StatisticsViewer() {
    super(new BorderLayout(0, 0));
    Disposer.register(ApplicationManager.getApplication(), this);
    Project project = ProjectManager.getInstance().getDefaultProject();

    // Use the ConsoleView from IntelliJ to render log entries as it makes for easy browsing, copy/paste & searching.
    myConsoleView = new ConsoleViewImpl(project, true);
    register(this, this::processEvent);
    layoutConsoleView();
    createDialog(project);
  }

  private Unit processEvent(AndroidStudioEvent.Builder builder) {
    myConsoleView.print("===\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsoleView.print(AnalyticsSettings.getDateProvider().now().toString() + "\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myConsoleView.print(builder.build().toString(), ConsoleViewContentType.NORMAL_OUTPUT);
    return Unit.INSTANCE;
  }

  // Create a non-modal dialog with this panel so the user can interact with Android Studio while looking at metrics on another screen.
  private void createDialog(final Project project) {
    myDialog = new DialogWrapper(project, true) {
      {
        init();
      }

      @Override
      protected JComponent createCenterPanel() {
        Disposer.register(getDisposable(), StatisticsViewer.this);
        return StatisticsViewer.this;
      }


      @Override
      protected String getDimensionServiceKey() {
        return ID;
      }

      @NotNull
      @Override
      protected Action[] createActions() {
        return new Action[]{new AbstractAction("Close") {
          @Override
          public void actionPerformed(ActionEvent e) {
            doOKAction();
          }
        }};
      }
    };
    myDialog.setModal(false);
    myDialog.setTitle(TITLE);
    myDialog.setResizable(true);
    myDialog.show();
  }

  /**
   * Code to initialize the console window and its toolbar.
   */
  private void layoutConsoleView() {
    //
    final RunContentDescriptor descriptor = new RunContentDescriptor(myConsoleView, null, this, TITLE);
    Disposer.register(this, descriptor);

    // must call getComponent before createConsoleActions()
    final JComponent consoleViewComponent = myConsoleView.getComponent();

    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.addAll(myConsoleView.createConsoleActions());

    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actionGroup, false);
    toolbar.setTargetComponent(consoleViewComponent);

    final JComponent ui = descriptor.getComponent();
    ui.add(consoleViewComponent, BorderLayout.CENTER);
    ui.add(toolbar.getComponent(), BorderLayout.WEST);

    // Add a border to make things look nicer.
    consoleViewComponent.setBorder(BorderFactory.createLineBorder(JBColor.GRAY));
  }

  @Override
  public void show() {
    myDialog.getPeer().getWindow().toFront();
  }

  @Override
  public void dispose() {
  }
}
