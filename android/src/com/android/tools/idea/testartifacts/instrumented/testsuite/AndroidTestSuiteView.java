/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite;

import com.android.annotations.concurrency.AnyThread;
import com.android.annotations.concurrency.UiThread;
import com.android.tools.idea.testartifacts.instrumented.testsuite.AndroidTestSuiteDetailsView.AndroidTestSuiteDetailsViewListener;
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults;
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice;
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase;
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.components.JBLabel;
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

  private final ThreeComponentsSplitter myComponentsSplitter;
  private final AndroidTestResultsTable myTable;
  private final AndroidTestSuiteDetailsView myDetailsView;

  private int scheduledTestCases = 0;
  private int passedTestCases = 0;
  private int failedTestCases = 0;
  private int skippedTestCases = 0;

  /**
   * Constructs AndroidTestSuiteView.
   *
   * @param parentDisposable a parent disposable which this view's lifespan is tied with.
   */
  @UiThread
  public AndroidTestSuiteView(@NotNull Disposable parentDisposable) {
    myTable = new AndroidTestResultsTable(this);
    myTableViewContainer.add(myTable.getComponent());

    myComponentsSplitter = new ThreeComponentsSplitter(/*vertical=*/true,
                                                       /*onePixelDividers=*/true);
    myComponentsSplitter.setOpaque(false);
    myComponentsSplitter.setMinSize(MIN_COMPONENT_HEIGHT_IN_SPLITTER);
    Disposer.register(this, myComponentsSplitter);
    myComponentsSplitter.setFirstComponent(myRootPanel);

    myDetailsView = new AndroidTestSuiteDetailsView(parentDisposable, this);
    myDetailsView.getRootPanel().setVisible(false);
    myComponentsSplitter.setLastComponent(myDetailsView.getRootPanel());

    updateProgress();

    Disposer.register(parentDisposable, this);
  }

  @UiThread
  private void updateProgress() {
    int completedTestCases = passedTestCases + failedTestCases + skippedTestCases;
    if (scheduledTestCases == 0) {
      myProgressBar.setIndeterminate(true);
    } else {
      float progress = (float) completedTestCases / scheduledTestCases;
      myProgressBar.setValue(Math.round((myProgressBar.getMaximum() - myProgressBar.getMinimum()) * progress));
      myProgressBar.setIndeterminate(false);
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
    AppUIUtil.invokeOnEdt(() -> myTable.addTestCase(device, testCase));
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
    });
  }

  @Override
  @AnyThread
  public void onTestSuiteFinished(@NotNull AndroidDevice device, @NotNull AndroidTestSuite testSuite) {
    AppUIUtil.invokeOnEdt(() -> myTable.refreshTable());
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
    processHandler.putCopyableUserData(AndroidTestSuiteConstantsKt.ANDROID_TEST_RESULT_LISTENER_KEY, this);
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
  public AndroidTestResultsTable getTableForTesting() {
    return myTable;
  }

  @VisibleForTesting
  public AndroidTestSuiteDetailsView getDetailsViewForTesting() {
    return myDetailsView;
  }
}
