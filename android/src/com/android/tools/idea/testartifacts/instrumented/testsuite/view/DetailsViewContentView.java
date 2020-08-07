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

import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ActionPlaces;
import com.android.tools.idea.testartifacts.instrumented.testsuite.logging.AndroidTestSuiteLogger;
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice;
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceKt;
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.ParallelAndroidTestReportUiEvent;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.largeFilesEditor.GuiUtils;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.JBTabsFactory;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.io.File;
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
  @Nullable private File myRetentionSnapshot = null;

  @VisibleForTesting final ConsoleViewImpl myLogsView;

  @VisibleForTesting final TabInfo myBenchmarkTab;
  @VisibleForTesting final ConsoleViewImpl myBenchmarkView;

  @VisibleForTesting final AndroidDeviceInfoTableView myDeviceInfoTableView;
  @VisibleForTesting final RetentionView myRetentionView;
  @VisibleForTesting final TabInfo myRetentionTab;

  public DetailsViewContentView(@NotNull Disposable parentDisposable,
                                @NotNull Project project,
                                @NotNull AndroidTestSuiteLogger logger) {
    myTestResultLabel.setBorder(JBUI.Borders.empty(10));

    JBTabs tabs = JBTabsFactory.createTabs(project, parentDisposable);

    // Create logcat tab.
    myLogsView = new ConsoleViewImpl(project, /*viewer=*/true);
    Disposer.register(parentDisposable, myLogsView);
    logger.addImpressionWhenDisplayed(
      myLogsView.getComponent(), ParallelAndroidTestReportUiEvent.UiElement.TEST_SUITE_LOG_VIEW);
    NonOpaquePanel logsViewWithVerticalToolbar = new NonOpaquePanel(new BorderLayout());
    logsViewWithVerticalToolbar.add(myLogsView.getComponent(), BorderLayout.CENTER);
    ActionToolbar logViewToolbar = ActionManager.getInstance().createActionToolbar(
      ActionPlaces.ANDROID_TEST_SUITE_DETAILS_VIEW_LOG, new DefaultActionGroup(myLogsView.createConsoleActions()), false);
    logsViewWithVerticalToolbar.add(logViewToolbar.getComponent(), BorderLayout.EAST);
    TabInfo logsTab = new TabInfo(logsViewWithVerticalToolbar);
    logsTab.setText("Logs");
    logsTab.setTooltipText("Show logcat output");
    tabs.addTab(logsTab);

    // Create benchmark tab.
    myBenchmarkView = new ConsoleViewImpl(project, /*viewer=*/true);
    Disposer.register(parentDisposable, myBenchmarkView);
    NonOpaquePanel benchmarkViewWithVerticalToolbar = new NonOpaquePanel(new BorderLayout());
    benchmarkViewWithVerticalToolbar.add(myBenchmarkView.getComponent(), BorderLayout.CENTER);
    ActionToolbar benchmarkViewToolbar = ActionManager.getInstance().createActionToolbar(
      ActionPlaces.ANDROID_TEST_SUITE_DETAILS_VIEW_BENCHMARK, new DefaultActionGroup(myBenchmarkView.createConsoleActions()), false);
    benchmarkViewWithVerticalToolbar.add(benchmarkViewToolbar.getComponent(), BorderLayout.EAST);
    myBenchmarkTab = new TabInfo(benchmarkViewWithVerticalToolbar);
    myBenchmarkTab.setText("Benchmark");
    myBenchmarkTab.setTooltipText("Show benchmark results");
    myBenchmarkTab.setHidden(true);
    tabs.addTab(myBenchmarkTab);

    // Device info tab.
    myDeviceInfoTableView = new AndroidDeviceInfoTableView();
    logger.addImpressionWhenDisplayed(
      myDeviceInfoTableView.getComponent(), ParallelAndroidTestReportUiEvent.UiElement.TEST_SUITE_DEVICE_INFO_VIEW);
    TabInfo deviceInfoTab = new TabInfo(myDeviceInfoTableView.getComponent());
    deviceInfoTab.setText("Device Info");
    deviceInfoTab.setTooltipText("Show device information");
    tabs.addTab(deviceInfoTab);

    // Android Test Retention tab.
    myRetentionView = new RetentionView();
    myRetentionTab = new TabInfo(myRetentionView.getRootPanel());
    myRetentionTab.setText("Retention");
    myRetentionTab.setTooltipText("Show emulator snapshots of failed tests");
    tabs.addTab(myRetentionTab);
    myRetentionTab.setHidden(true);

    // Wrap JBTabs to draw a border on top.
    JPanel tabsWrapper = new JPanel(new BorderLayout());
    tabsWrapper.add(tabs.getComponent());
    GuiUtils.setStandardLineBorderToPanel(tabsWrapper, 1, 0, 0, 0);

    myContentPanel.add(tabsWrapper);
    myRootPanel.setMinimumSize(new Dimension());
  }

  /**
   * Returns the root panel.
   */
  @NotNull
  public JPanel getRootPanel() {
    return myRootPanel;
  }

  public void setPackageName(@NotNull String packageName) {
    myRetentionView.setPackageName(packageName);
  }

  public void setAndroidDevice(@NotNull AndroidDevice androidDevice) {
    myAndroidDevice = androidDevice;
    refreshTestResultLabel();
    myDeviceInfoTableView.setAndroidDevice(androidDevice);
    myRetentionView.setAndroidDevice(androidDevice);
  }

  public void setAndroidTestCaseResult(@Nullable AndroidTestCaseResult result) {
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

  public void setBenchmarkText(@NotNull String benchmarkText) {
    myBenchmarkView.clear();
    myBenchmarkView.print(benchmarkText, ConsoleViewContentType.NORMAL_OUTPUT);
    myBenchmarkTab.setHidden(StringUtil.isEmpty(benchmarkText));
  }

  public void setRetentionSnapshot(@Nullable File rententionSnapshot) {
    myRetentionSnapshot = rententionSnapshot;
    refreshRetentionView();
  }

  private void refreshRetentionView() {
    myRetentionTab.setHidden(myRetentionSnapshot == null);
    myRetentionView.setSnapshotFile(myRetentionSnapshot);
  }

  private void refreshTestResultLabel() {
    if (myAndroidDevice == null) {
      myTestResultLabel.setText("No test status available");
      return;
    }
    if (myAndroidTestCaseResult == null) {
      myTestResultLabel.setText("No test status available on " + AndroidDeviceKt.getName(myAndroidDevice));
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
            AndroidDeviceKt.getName(myAndroidDevice)));
          break;

        case FAILED: {
          String errorMessage = Arrays.stream(StringUtil.splitByLines(myErrorStackTrace)).findFirst().orElse("");
          if (StringUtil.isEmptyOrSpaces(errorMessage)) {
            myTestResultLabel.setText(String.format(
              Locale.US,
              "<html><font color='%s'>Failed</font> on %s</html>",
              ColorUtil.toHtmlColor(statusColor),
              AndroidDeviceKt.getName(myAndroidDevice)));
          } else {
            myTestResultLabel.setText(String.format(
              Locale.US,
              "<html><font size='+1'>%s</font><br><font color='%s'>Failed</font> on %s</html>",
              errorMessage,
              ColorUtil.toHtmlColor(statusColor),
              AndroidDeviceKt.getName(myAndroidDevice)));
          }
          break;
        }

        case SKIPPED:
          myTestResultLabel.setText(String.format(
            Locale.US,
            "<html><font color='%s'>Skipped</font> on %s</html>",
            ColorUtil.toHtmlColor(statusColor),
            AndroidDeviceKt.getName(myAndroidDevice)));
          break;

        case CANCELLED:
          myTestResultLabel.setText(String.format(
            Locale.US,
            "<html><font color='%s'>Cancelled</font> on %s</html>",
            ColorUtil.toHtmlColor(statusColor),
            AndroidDeviceKt.getName(myAndroidDevice)));
          break;

        default:
          myTestResultLabel.setText("");
          Logger.getInstance(getClass()).warn(
            String.format(Locale.US, "Unexpected result type: %s", myAndroidTestCaseResult));
      }
    } else {
      myTestResultLabel.setText(String.format(Locale.US, "Running on %s", AndroidDeviceKt.getName(myAndroidDevice)));
    }
  }

  private void refreshLogsView() {
    myLogsView.clear();
    if (StringUtil.isEmptyOrSpaces(myLogcat) && StringUtil.isEmptyOrSpaces(myErrorStackTrace)) {
      myLogsView.print("No logcat output for this device.", ConsoleViewContentType.NORMAL_OUTPUT);
      return;
    }
    myLogsView.print(myLogcat, ConsoleViewContentType.NORMAL_OUTPUT);
    myLogsView.print("\n", ConsoleViewContentType.NORMAL_OUTPUT);
    myLogsView.print(myErrorStackTrace, ConsoleViewContentType.ERROR_OUTPUT);
  }
}
