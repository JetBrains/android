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

import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice;
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.largeFilesEditor.GuiUtils;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.JBTabsFactory;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.Arrays;
import java.util.Locale;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shows detailed tests results for a selected device.
 */
public class DetailsViewContentView {

  // Those properties are initialized by IntelliJ form editor before the constructor using reflection.
  private JPanel myRootPanel;
  private JPanel myContentPanel;
  @VisibleForTesting JBLabel myTestResultLabel;

  @Nullable private AndroidDevice myAndroidDevice;
  @Nullable private AndroidTestCaseResult myAndroidTestCaseResult;
  @NotNull private String myLogcat = "";
  @NotNull private String myErrorStackTrace = "";

  @VisibleForTesting final ConsoleViewImpl myLogsView;
  @VisibleForTesting final AndroidDeviceInfoTableView myDeviceInfoTableView;

  public DetailsViewContentView(@NotNull Disposable parentDisposable,
                                @NotNull Project project) {
    myTestResultLabel.setBorder(JBUI.Borders.empty(10));

    JBTabs tabs = JBTabsFactory.createTabs(project, parentDisposable);

    // Create logcat tab.
    myLogsView = new ConsoleViewImpl(project, /*viewer=*/true);
    Disposer.register(parentDisposable, myLogsView);
    TabInfo logsTab = new TabInfo(myLogsView.getComponent());
    logsTab.setText("Logs");
    tabs.addTab(logsTab);

    // Device info tab.
    myDeviceInfoTableView = new AndroidDeviceInfoTableView();
    TabInfo deviceInfoTab = new TabInfo(myDeviceInfoTableView.getComponent());
    deviceInfoTab.setText("Device Info");
    tabs.addTab(deviceInfoTab);

    // Wrap JBTabs to draw a border on top.
    JPanel tabsWrapper = new JPanel(new BorderLayout());
    tabsWrapper.add(tabs.getComponent());
    GuiUtils.setStandardLineBorderToPanel(tabsWrapper, 1, 0, 0, 0);

    myContentPanel.add(tabsWrapper);
  }

  /**
   * Returns the root panel.
   */
  @NotNull
  public JPanel getRootPanel() {
    return myRootPanel;
  }

  public void setAndroidDevice(@NotNull AndroidDevice androidDevice) {
    myAndroidDevice = androidDevice;
    refreshTestResultLabel();
    myDeviceInfoTableView.setAndroidDevice(androidDevice);
  }

  public void setAndroidTestCaseResult(@NotNull AndroidTestCaseResult result) {
    myAndroidTestCaseResult = result;
    refreshTestResultLabel();
  }

  public void setLogcat(@NotNull String logcat) {
    myLogcat = logcat;
    refreshLogsView();
  }

  public void setErrorStackTrace(@NotNull String errorStackTrace) {
    myErrorStackTrace = errorStackTrace;
    refreshTestResultLabel();
    refreshLogsView();
  }

  private void refreshTestResultLabel() {
    if (myAndroidTestCaseResult == null || myAndroidDevice == null) {
      return;
    }
    if (myAndroidTestCaseResult.isTerminalState()) {
      Color statusColor = AndroidTestResultsTableViewKt.getColorFor(myAndroidTestCaseResult);
      if (statusColor == null) {
        statusColor = UIUtil.getActiveTextColor();
      }
      switch (myAndroidTestCaseResult) {
        case PASSED:
          myTestResultLabel.setText(String.format(
            Locale.US,
            "<html><font color='%s'>Passed</font> on %s</html>",
            ColorUtil.toHtmlColor(statusColor),
            myAndroidDevice.getName()));
          break;

        case FAILED:
          myTestResultLabel.setText(String.format(
            Locale.US,
            "<html><font size='+1'>%s</font><br><font color='%s'>Failed</font> on %s</html>",
            Arrays.stream(StringUtil.splitByLines(myErrorStackTrace)).findFirst().orElse(""),
            ColorUtil.toHtmlColor(statusColor),
            myAndroidDevice.getName()));
          break;

        case SKIPPED:
          myTestResultLabel.setText(String.format(
            Locale.US,
            "<html><font color='%s'>Skipped</font> on %s</html>",
            ColorUtil.toHtmlColor(statusColor),
            myAndroidDevice.getName()));
          break;

        default:
          Logger.getInstance(getClass()).warn(
            String.format(Locale.US, "Unexpected result type: %s", myAndroidTestCaseResult));
      }
    } else {
      myTestResultLabel.setText(String.format(Locale.US, "Running on %s", myAndroidDevice.getName()));
    }
  }

  private void refreshLogsView() {
    myLogsView.clear();
    myLogsView.print(myLogcat, ConsoleViewContentType.NORMAL_OUTPUT);
    myLogsView.print("\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myLogsView.print(myErrorStackTrace, ConsoleViewContentType.ERROR_OUTPUT);
  }
}
