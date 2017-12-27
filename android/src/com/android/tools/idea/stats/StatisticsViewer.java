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

import com.android.annotations.NonNull;
import com.android.tools.analytics.UsageTracker;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.wireless.android.play.playlog.proto.ClientAnalytics;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * A non-modal dialog displaying statistics as they're logged.
 */
public class StatisticsViewer extends JPanel implements Disposable {
  public static final String ID = "StatisticsViewer";
  public static final String TITLE = "Android Studio Usage Statistics";
  private final ConsoleViewImpl myConsoleView;

  private DialogWrapper myDialog;
  private UsageTracker myOriginalUsageTracker;

  StatisticsViewer() {
    super(new BorderLayout(0, 0));
    Disposer.register(Disposer.get("ui"), this);
    Project project = ProjectManager.getInstance().getDefaultProject();

    // Use the ConsoleView from IntelliJ to render log entries as it makes for easy browsing, copy/paste & searching.
    myConsoleView = new ConsoleViewImpl(project, true);
    hookUsageTracker();
    layoutConsoleView();
    createDialog(project);
  }

  private void hookUsageTracker() {
    // Use the setInstanceForTest API to temporary wrap the UsageTracker in our own usage tracker that logs to our console.
    myOriginalUsageTracker = UsageTracker.getInstance();
    UsageTracker.setInstanceForTest(new UsageTracker(myOriginalUsageTracker.getAnalyticsSettings(), myOriginalUsageTracker.getScheduler()) {
      @Override
      public void logDetails(@NonNull ClientAnalytics.LogEvent.Builder logEvent) {
        myOriginalUsageTracker.logDetails(logEvent);
        try {
          // LogEvent contains a serialized AndroidStudioEvent, we're interested in pretty printed version of this, so we have to parse it.
          AndroidStudioEvent androidStudioEvent = AndroidStudioEvent.parseFrom(logEvent.getSourceExtension());
          // we still want the other fields of LogEvent pretty printed, so empty out the binary blob.
          logEvent.clearSourceExtension();
          // Marker to easily visually distinguish between events.
          myConsoleView.print("===\n", ConsoleViewContentType.NORMAL_OUTPUT);
          myConsoleView.print(logEvent.build().toString(), ConsoleViewContentType.NORMAL_OUTPUT);
          myConsoleView.print(androidStudioEvent.toString(), ConsoleViewContentType.NORMAL_OUTPUT);
        }
        catch (InvalidProtocolBufferException e) {
          // This should not be happening as the server side expects an AndroidStudioEvent, so log an error.
          myConsoleView.print("Unable to parse AndroidStudioEvent from LogEvent: " + logEvent.build().toString(), ConsoleViewContentType.ERROR_OUTPUT);
        }
      }

      @Override
      public void close() throws Exception {
        myOriginalUsageTracker.close();
      }
    });
    UsageTracker.getInstance().setVersion(myOriginalUsageTracker.getVersion());
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
        return new Action[] {new AbstractAction("Close") {
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
    // Undo the wrapping of the UsageTracker
    UsageTracker.setInstanceForTest(myOriginalUsageTracker);
  }
}
