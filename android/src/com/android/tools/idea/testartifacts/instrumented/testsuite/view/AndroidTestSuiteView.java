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

import static com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestSuiteConstantsKt.ANDROID_TEST_RESULT_LISTENER_KEY;

import com.android.annotations.concurrency.AnyThread;
import com.android.annotations.concurrency.UiThread;
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultListener;
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults;
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice;
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase;
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite;
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteDetailsView.AndroidTestSuiteDetailsViewListener;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.largeFilesEditor.GuiUtils;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.progress.util.ColorProgressBar;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A console view to display a test execution and result of Android instrumentation tests.
 *
 * Note: This view is under development and most of methods are not implemented yet.
 */
public class AndroidTestSuiteView implements ConsoleView, AndroidTestResultListener, AndroidTestResultsTableListener,
                                             AndroidTestSuiteDetailsViewListener {

  /**
   * Minimum height of swing components in ThreeComponentsSplitter container in pixel.
   */
  private static final int MIN_COMPONENT_HEIGHT_IN_SPLITTER = 48;

  // Those properties are initialized by IntelliJ form editor before the constructor using reflection.
  private JPanel myRootPanel;
  private JProgressBar myProgressBar;
  private JBLabel myStatusText;
  private JBLabel myStatusBreakdownText;
  private JPanel myTableViewContainer;
  private JPanel myStatusPanel;
  private MyStatusPanelItemSeparator mySeparator;

  private final ThreeComponentsSplitter myComponentsSplitter;
  private final AndroidTestResultsTableView myTable;
  private final AndroidTestSuiteDetailsView myDetailsView;

  private int scheduledTestCases = 0;
  private int passedTestCases = 0;
  private int failedTestCases = 0;
  private int skippedTestCases = 0;

  /**
   * This method is invoked before the constructor by IntelliJ form editor runtime.
   */
  private void createUIComponents() {
    mySeparator = new MyStatusPanelItemSeparator();
  }

  /**
   * Constructs AndroidTestSuiteView.
   *
   * @param parentDisposable a parent disposable which this view's lifespan is tied with.
   * @param project a project which this test suite view belongs to, or null.
   */
  @UiThread
  public AndroidTestSuiteView(@NotNull Disposable parentDisposable, @NotNull Project project) {
    GuiUtils.setStandardLineBorderToPanel(myStatusPanel, 0, 0, 1, 0);
    GuiUtils.setStandardLineBorderToPanel(myTableViewContainer, 0, 0, 1, 0);

    myTable = new AndroidTestResultsTableView(this);
    myTableViewContainer.add(myTable.getComponent());

    myComponentsSplitter = new ThreeComponentsSplitter(/*vertical=*/true,
                                                       /*onePixelDividers=*/true);
    myComponentsSplitter.setOpaque(false);
    myComponentsSplitter.setMinSize(MIN_COMPONENT_HEIGHT_IN_SPLITTER);
    Disposer.register(this, myComponentsSplitter);
    myComponentsSplitter.setFirstComponent(myRootPanel);

    myDetailsView = new AndroidTestSuiteDetailsView(parentDisposable, this, project);
    myDetailsView.getRootPanel().setVisible(false);
    myComponentsSplitter.setLastComponent(myDetailsView.getRootPanel());

    updateProgress();

    Disposer.register(parentDisposable, this);
  }

  @UiThread
  private void updateProgress() {
    int completedTestCases = passedTestCases + failedTestCases + skippedTestCases;
    if (scheduledTestCases == 0) {
      myProgressBar.setValue(0);
      myProgressBar.setIndeterminate(false);
      myProgressBar.setForeground(ColorProgressBar.BLUE);
    } else {
      myProgressBar.setMaximum(scheduledTestCases);
      myProgressBar.setValue(completedTestCases);
      myProgressBar.setIndeterminate(false);
      if (failedTestCases > 0) {
        myProgressBar.setForeground(ColorProgressBar.RED);
      } else if (completedTestCases == scheduledTestCases) {
        myProgressBar.setForeground(ColorProgressBar.GREEN);
      }
    }

    myStatusText.setText(AndroidBundle.message("android.testartifacts.instrumented.testsuite.status.summary", completedTestCases));
    myStatusBreakdownText.setText(AndroidBundle.message("android.testartifacts.instrumented.testsuite.status.breakdown",
                                                        failedTestCases, passedTestCases, skippedTestCases, /*errorTestCases=*/0));
  }

  @Override
  @AnyThread
  public void onTestSuiteScheduled(@NotNull AndroidDevice device) {
    AppUIUtil.invokeOnEdt(() -> {
      myTable.addDevice(device);
      myDetailsView.addDevice(device);
    });
  }

  @Override
  @AnyThread
  public void onTestSuiteStarted(@NotNull AndroidDevice device, @NotNull AndroidTestSuite testSuite) {
    AppUIUtil.invokeOnEdt(() -> {
      scheduledTestCases += testSuite.getTestCaseCount();
      updateProgress();
    });
  }

  @Override
  @AnyThread
  public void onTestCaseStarted(@NotNull AndroidDevice device, @NotNull AndroidTestSuite testSuite, @NotNull AndroidTestCase testCase) {
    AppUIUtil.invokeOnEdt(() -> {
      myTable.addTestCase(device, testCase);
      myDetailsView.reloadAndroidTestResults();
    });
  }

  @Override
  @AnyThread
  public void onTestCaseFinished(@NotNull AndroidDevice device,
                                 @NotNull AndroidTestSuite testSuite,
                                 @NotNull AndroidTestCase testCase) {
    AppUIUtil.invokeOnEdt(() -> {
      switch (Preconditions.checkNotNull(testCase.getResult())) {
        case PASSED:
          passedTestCases++;
          break;
        case FAILED:
          failedTestCases++;
          break;
        case SKIPPED:
          skippedTestCases++;
          break;
      }
      updateProgress();
      myTable.refreshTable();
      myDetailsView.reloadAndroidTestResults();
    });
  }

  @Override
  @AnyThread
  public void onTestSuiteFinished(@NotNull AndroidDevice device, @NotNull AndroidTestSuite testSuite) {
    AppUIUtil.invokeOnEdt(() -> {
      myTable.refreshTable();
      myDetailsView.reloadAndroidTestResults();
    });
  }

  @Override
  @UiThread
  public void onAndroidTestResultsRowSelected(@NotNull AndroidTestResults selectedResults) {
    openAndroidTestSuiteDetailsView(selectedResults);
  }

  @Override
  @UiThread
  public void onAndroidTestSuiteDetailsViewCloseButtonClicked() {
    closeAndroidTestSuiteDetailsView();
  }

  @UiThread
  private void openAndroidTestSuiteDetailsView(@NotNull AndroidTestResults results) {
    myDetailsView.setAndroidTestResults(results);
    myDetailsView.getRootPanel().setVisible(true);

    // ComponentsSplitter doesn't update child components' size automatically when you change its visibility.
    // So we have to update them manually here otherwise the size can be invalid (e.g. smaller than the min size).
    myComponentsSplitter.setFirstSize(myComponentsSplitter.getFirstSize());
    myComponentsSplitter.setLastSize(myComponentsSplitter.getLastSize());
  }

  @UiThread
  private void closeAndroidTestSuiteDetailsView() {
    myDetailsView.getRootPanel().setVisible(false);

    // ComponentsSplitter doesn't update child components' size automatically when you change its visibility.
    // So we have to update them manually here otherwise the size can be invalid (e.g. smaller than the min size).
    myComponentsSplitter.setFirstSize(myComponentsSplitter.getFirstSize());
    myComponentsSplitter.setLastSize(myComponentsSplitter.getLastSize());
  }

  @Override
  public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) { }

  @Override
  public void clear() { }

  @Override
  public void scrollTo(int offset) { }

  @Override
  public void attachToProcess(ProcessHandler processHandler) {
    // Put this test suite view to the process handler as AndroidTestResultListener so the view
    // is notified the test results and to be updated.
    processHandler.putCopyableUserData(ANDROID_TEST_RESULT_LISTENER_KEY, this);
  }

  @Override
  public void setOutputPaused(boolean value) { }

  @Override
  public boolean isOutputPaused() {
    return false;
  }

  @Override
  public boolean hasDeferredOutput() {
    return false;
  }

  @Override
  public void performWhenNoDeferredOutput(@NotNull Runnable runnable) {
    runnable.run();
  }

  @Override
  public void setHelpId(@NotNull String helpId) { }

  @Override
  public void addMessageFilter(@NotNull Filter filter) { }

  @Override
  public void printHyperlink(@NotNull String hyperlinkText, @Nullable HyperlinkInfo info) { }

  @Override
  public int getContentSize() {
    return 0;
  }

  @Override
  public boolean canPause() {
    return false;
  }

  @NotNull
  @Override
  public AnAction[] createConsoleActions() {
    return AnAction.EMPTY_ARRAY;
  }

  @Override
  public void allowHeavyFilters() { }

  @Override
  public JComponent getComponent() {
    return myComponentsSplitter;
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return myRootPanel;
  }

  @Override
  public void dispose() { }

  @VisibleForTesting
  public AndroidTestResultsTableView getTableForTesting() {
    return myTable;
  }

  @VisibleForTesting
  public AndroidTestSuiteDetailsView getDetailsViewForTesting() {
    return myDetailsView;
  }

  public final class MyStatusPanelItemSeparator extends JComponent {
    @Override
    public Dimension getPreferredSize() {
      int gap = JBUIScale.scale(2);
      int center = JBUIScale.scale(3);
      int width = gap * 2 + center;
      int height = JBUIScale.scale(24);

      return new JBDimension(width, height, /*preScaled=*/true);
    }

    @Override
    protected void paintComponent(final Graphics g) {
      if (getParent() == null) return;

      int gap = JBUIScale.scale(2);
      int center = JBUIScale.scale(3);

      g.setColor(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground());
      int y2 = myStatusPanel.getHeight() - gap * 2;
      LinePainter2D.paint((Graphics2D)g, center, gap, center, y2);
    }
  }
}
