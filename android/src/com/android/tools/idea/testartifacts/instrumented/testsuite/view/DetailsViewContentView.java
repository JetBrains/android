/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view;

import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.SingleHeightTabs;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * Shows detailed tests results for a selected device.
 */
public class DetailsViewContentView {

  // Those properties are initialized by IntelliJ form editor before the constructor using reflection.
  private JPanel myRootPanel;
  private JPanel myContentPanel;
  @VisibleForTesting JBLabel myTestResultLabel;

  @VisibleForTesting final ConsoleViewImpl myLogcatView;

  public DetailsViewContentView(@NotNull Disposable parentDisposable,
                                @NotNull Project project) {
    SingleHeightTabs tabs =
      new SingleHeightTabs(project, ActionManager.getInstance(), IdeFocusManager.getInstance(project), parentDisposable);

    // Create logcat tab.
    myLogcatView = new ConsoleViewImpl(project, /*viewer=*/true);
    Disposer.register(parentDisposable, myLogcatView);
    TabInfo logcatTab = new TabInfo(myLogcatView.getComponent());
    logcatTab.setText("Logs");
    tabs.addTab(logcatTab);

    myContentPanel.add(tabs);
  }

  /**
   * Returns the root panel.
   */
  @NotNull
  public JPanel getRootPanel() {
    return myRootPanel;
  }

  public void setAndroidTestCaseResult(@NotNull AndroidTestCaseResult result) {
    myTestResultLabel.setText(result.name());
  }

  public void setLogcat(@NotNull String logcat) {
    myLogcatView.clear();
    myLogcatView.print(logcat, ConsoleViewContentType.NORMAL_OUTPUT);
  }
}
