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
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.projectsystem.TestArtifactSearchScopes;
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ActionPlaces;
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultListener;
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults;
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultsKt;
import com.android.tools.idea.testartifacts.instrumented.testsuite.logging.AndroidTestSuiteLogger;
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice;
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceKt;
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase;
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult;
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite;
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteDetailsView.AndroidTestSuiteDetailsViewListener;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.wireless.android.sdk.stats.ParallelAndroidTestReportUiEvent;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.largeFilesEditor.GuiUtils;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.PlatformEditorBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.util.ColorProgressBar;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.SortedComboBoxModel;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A console view to display a test execution and result of Android instrumentation tests.
 */
public class AndroidTestSuiteView implements ConsoleView, AndroidTestResultListener, AndroidTestResultsTableListener,
                                             AndroidTestSuiteDetailsViewListener, AndroidTestSuiteViewController {
  /**
   * Minimum width or height of swing components in ThreeComponentsSplitter container in pixel.
   */
  private static final int MIN_COMPONENT_SIZE_IN_SPLITTER = 48;

  /**
   * A ComboBox item to be used in API level filter ComboBox.
   */
  @VisibleForTesting
  static final class ApiLevelFilterComboBoxItem implements Comparable<ApiLevelFilterComboBoxItem> {
    @NotNull final Comparator<AndroidVersion> myComparator = Comparator.nullsFirst(Comparator.naturalOrder());
    @Nullable final AndroidVersion myVersion;

    /**
     * @param androidVersion an Android version to be shown or null for "All API versions".
     */
    ApiLevelFilterComboBoxItem(@Nullable AndroidVersion androidVersion) {
      myVersion = androidVersion;
    }

    @Override
    public int compareTo(@NotNull ApiLevelFilterComboBoxItem o) {
      return Objects.compare(myVersion, o.myVersion, myComparator);
    }

    @Override
    public String toString() {
      if (myVersion == null) {
        return "All API levels";
      } else {
        return String.format(Locale.US, "API %s", myVersion.getApiString());
      }
    }

    @Override
    public int hashCode() {
      return Objects.hash(myVersion);
    }
  }

  /**
   * A ComboBox item to be used in device filter ComboBox.
   */
  @VisibleForTesting
  static final class DeviceFilterComboBoxItem implements Comparable<DeviceFilterComboBoxItem> {
    @NotNull final Comparator<String> myComparator = Comparator.nullsFirst(Comparator.naturalOrder());
    @Nullable final AndroidDevice myDevice;

    /**
     * @param androidDevice an Android device to be shown or null for "All devices".
     */
    DeviceFilterComboBoxItem(@Nullable AndroidDevice androidDevice) {
      myDevice = androidDevice;
    }

    @Override
    public int compareTo(@NotNull DeviceFilterComboBoxItem o) {
      String lhs = null;
      if (myDevice != null) {
        lhs = AndroidDeviceKt.getName(myDevice);
      }
      String rhs = null;
      if (o.myDevice != null) {
        rhs = AndroidDeviceKt.getName(o.myDevice);
      }
      return Objects.compare(lhs, rhs, myComparator);
    }

    @Override
    public String toString() {
      if (myDevice == null) {
        return "All devices";
      } else {
        return AndroidDeviceKt.getName(myDevice);
      }
    }

    @Override
    public int hashCode() {
      return Objects.hash(myDevice);
    }
  }

  // Those properties are initialized by IntelliJ form editor before the constructor using reflection.
  private JPanel myRootPanel;
  @VisibleForTesting JProgressBar myProgressBar;
  private JBLabel myStatusText;
  private JBLabel myStatusBreakdownText;
  private JPanel myTableViewContainer;
  private JPanel myStatusPanel;
  private MyItemSeparator myStatusSeparator;
  private JPanel myFilterPanel;
  private MyItemSeparator myStatusFilterSeparator;
  private ComboBox<DeviceFilterComboBoxItem> myDeviceFilterComboBox;
  @VisibleForTesting SortedComboBoxModel<DeviceFilterComboBoxItem> myDeviceFilterComboBoxModel;
  private MyItemSeparator myDeviceFilterSeparator;
  private ComboBox<ApiLevelFilterComboBoxItem> myApiLevelFilterComboBox;
  private JPanel myTestStatusFilterPanel;
  @VisibleForTesting SortedComboBoxModel<ApiLevelFilterComboBoxItem> myApiLevelFilterComboBoxModel;
  @VisibleForTesting MyToggleAction myFailedToggleButton;
  @VisibleForTesting MyToggleAction myPassedToggleButton;
  @VisibleForTesting MyToggleAction mySkippedToggleButton;
  @VisibleForTesting MyToggleAction myInProgressToggleButton;
  @VisibleForTesting MyToggleAction mySortByNameToggleButton;
  @VisibleForTesting MyToggleAction mySortByDurationToggleButton;

  private static final String FAILED_TOGGLE_BUTTON_STATE_KEY = "AndroidTestSuiteView.myFailedToggleButton";
  private static final String PASSED_TOGGLE_BUTTON_STATE_KEY = "AndroidTestSuiteView.myPassedToggleButton";
  private static final String SKIPPED_TOGGLE_BUTTON_STATE_KEY = "AndroidTestSuiteView.mySkippedToggleButton";
  private static final String IN_PROGRESS_TOGGLE_BUTTON_STATE_KEY = "AndroidTestSuiteView.myInProgressToggleButton";
  private static final String SORT_BY_NAME_TOGGLE_BUTTON_STATE_KEY = "AndroidTestSuiteView.mySortByNameToggleButton";
  private static final String SORT_BY_DURATION_TOGGLE_BUTTON_STATE_KEY = "AndroidTestSuiteView.mySortByDurationToggleButton";

  private final Project myProject;
  @VisibleForTesting final AndroidTestSuiteLogger myLogger;

  private final JBSplitter myComponentsSplitter;
  private final AndroidTestResultsTableView myTable;
  private final AndroidTestSuiteDetailsView myDetailsView;
  private final Map<AndroidTestResults, Integer> myInsertionOrderMap;

  // Number of devices which we will run tests against.
  private int myScheduledDevices = 0;
  // Number of devices which we have started running tests on.
  private int myStartedDevices = 0;

  private int scheduledTestCases = 0;
  private int passedTestCases = 0;
  private int failedTestCases = 0;
  private int skippedTestCases = 0;

  /**
   * This method is invoked before the constructor by IntelliJ form editor runtime.
   *
   * @see <a href="https://www.jetbrains.com/help/idea/creating-form-initialization-code.html">Initialization Code</a>
   */
  private void createUIComponents() {
    myStatusSeparator = new MyItemSeparator();
    myStatusFilterSeparator = new MyItemSeparator();
    myDeviceFilterSeparator = new MyItemSeparator();

    myFailedToggleButton = new MyToggleAction(
      "Show failed tests", AndroidTestResultsTableViewKt.getIconFor(AndroidTestCaseResult.FAILED, false),
      FAILED_TOGGLE_BUTTON_STATE_KEY, true);
    myPassedToggleButton = new MyToggleAction(
      "Show passed tests", AndroidTestResultsTableViewKt.getIconFor(AndroidTestCaseResult.PASSED, false),
      PASSED_TOGGLE_BUTTON_STATE_KEY, true);
    mySkippedToggleButton = new MyToggleAction(
      "Show skipped tests", AndroidTestResultsTableViewKt.getIconFor(AndroidTestCaseResult.SKIPPED, false),
      SKIPPED_TOGGLE_BUTTON_STATE_KEY, true);
    myInProgressToggleButton = new MyToggleAction(
      "Show running tests", AndroidTestResultsTableViewKt.getIconFor(AndroidTestCaseResult.IN_PROGRESS, false),
      IN_PROGRESS_TOGGLE_BUTTON_STATE_KEY, true);
    mySortByNameToggleButton = new MyToggleAction(
      PlatformEditorBundle.message("action.sort.alphabetically"), AllIcons.ObjectBrowser.Sorted,
      SORT_BY_NAME_TOGGLE_BUTTON_STATE_KEY, false);
    mySortByDurationToggleButton = new MyToggleAction(
      ExecutionBundle.message("junit.runing.info.sort.by.statistics.action.name"), AllIcons.RunConfigurations.SortbyDuration,
      SORT_BY_DURATION_TOGGLE_BUTTON_STATE_KEY, false);

    myDeviceFilterComboBoxModel = new SortedComboBoxModel<>(Comparator.naturalOrder());
    DeviceFilterComboBoxItem allDevicesItem = new DeviceFilterComboBoxItem(null);
    myDeviceFilterComboBoxModel.add(allDevicesItem);
    myDeviceFilterComboBoxModel.setSelectedItem(allDevicesItem);
    myDeviceFilterComboBox = new ComboBox<>(myDeviceFilterComboBoxModel);

    myApiLevelFilterComboBoxModel = new SortedComboBoxModel<>(Comparator.naturalOrder());
    ApiLevelFilterComboBoxItem allApiLevelsItem = new ApiLevelFilterComboBoxItem(null);
    myApiLevelFilterComboBoxModel.add(allApiLevelsItem);
    myApiLevelFilterComboBoxModel.setSelectedItem(allApiLevelsItem);
    myApiLevelFilterComboBox = new ComboBox<>(myApiLevelFilterComboBoxModel);
  }

  /**
   * Constructs AndroidTestSuiteView.
   *
   * @param parentDisposable a parent disposable which this view's lifespan is tied with.
   * @param project a project which this test suite view belongs to.
   * @param module a module which this test suite view belongs to. If null is given, some functions such as source code lookup
   *               will be disabled in this view.
   */
  @UiThread
  public AndroidTestSuiteView(@NotNull Disposable parentDisposable, @NotNull Project project, @Nullable Module module) {
    myProject = project;  // Note: this field assignment is called before createUIComponents().
    // Note: private final properties need to be initialized here instead of initialization value. You will hit
    //       NPE otherwise. This restriction comes from the IntelliJ form editor runtime.
    myLogger = new AndroidTestSuiteLogger();
    myInsertionOrderMap = new HashMap<>();

    GuiUtils.setStandardLineBorderToPanel(myStatusPanel, 0, 0, 1, 0);
    GuiUtils.setStandardLineBorderToPanel(myFilterPanel, 0, 0, 1, 0);

    DefaultActionGroup testFilterAndSorterActionGroup = new DefaultActionGroup();
    testFilterAndSorterActionGroup.addAll(
      myFailedToggleButton, myPassedToggleButton, mySkippedToggleButton, myInProgressToggleButton,
      Separator.getInstance(), mySortByNameToggleButton, mySortByDurationToggleButton);
    myTestStatusFilterPanel.add(
      ActionManager.getInstance().createActionToolbar(
        ActionPlaces.ANDROID_TEST_SUITE_TABLE,
        testFilterAndSorterActionGroup, true).getComponent());

    TestArtifactSearchScopes testArtifactSearchScopes = null;
    if (module != null) {
      testArtifactSearchScopes = TestArtifactSearchScopes.getInstance(module);
    }
    myTable = new AndroidTestResultsTableView(this, JavaPsiFacade.getInstance(project), testArtifactSearchScopes, myLogger);
    myTable.setRowFilter(testResults -> {
      if (AndroidTestResultsKt.isRootAggregationResult(testResults)) {
        return true;
      }
      if (myFailedToggleButton.isSelected()
          && testResults.getTestResultSummary() == AndroidTestCaseResult.FAILED) {
        return true;
      }
      if (myPassedToggleButton.isSelected()
          && testResults.getTestResultSummary() == AndroidTestCaseResult.PASSED) {
        return true;
      }
      if (mySkippedToggleButton.isSelected()
          && testResults.getTestResultSummary() == AndroidTestCaseResult.SKIPPED) {
        return true;
      }
      if (myInProgressToggleButton.isSelected()
          && testResults.getTestResultSummary() == AndroidTestCaseResult.IN_PROGRESS) {
        return true;
      }
      return false;
    });
    myTable.setColumnFilter(androidDevice -> {
      DeviceFilterComboBoxItem deviceItem = myDeviceFilterComboBoxModel.getSelectedItem();
      if (deviceItem != null && deviceItem.myDevice != null) {
        if (!androidDevice.getId().equals(deviceItem.myDevice.getId())) {
          return false;
        }
      }

      ApiLevelFilterComboBoxItem apiItem = myApiLevelFilterComboBoxModel.getSelectedItem();
      if (apiItem != null && apiItem.myVersion != null) {
        if (!androidDevice.getVersion().equals(apiItem.myVersion)) {
          return false;
        }
      }
      return true;
    });
    myTable.setRowComparator(new Comparator<AndroidTestResults>() {
      @Override
      public int compare(AndroidTestResults o1, AndroidTestResults o2) {
        if (mySortByNameToggleButton.isSelected) {
          int result = AndroidTestResultsTableViewKt.getTEST_NAME_COMPARATOR().compare(o1, o2);
          if (result != 0) {
            return result;
          }
        }
        if (mySortByDurationToggleButton.isSelected) {
          int result = AndroidTestResultsTableViewKt.getTEST_DURATION_COMPARATOR().compare(o1, o2) * -1;
          if (result != 0) {
            return result;
          }
        }
        return Integer.compare(myInsertionOrderMap.getOrDefault(o1, Integer.MAX_VALUE),
                               myInsertionOrderMap.getOrDefault(o2, Integer.MAX_VALUE));
      }
    });
    ItemListener tableUpdater = new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        myTable.refreshTable();
      }
    };
    myDeviceFilterComboBox.addItemListener(tableUpdater);
    myApiLevelFilterComboBox.addItemListener(tableUpdater);
    myTableViewContainer.add(myTable.getComponent());

    myComponentsSplitter = new JBSplitter();
    myComponentsSplitter.setHonorComponentsMinimumSize(false);
    myComponentsSplitter.setDividerWidth(1);
    myComponentsSplitter.getDivider().setBackground(UIUtil.CONTRAST_BORDER_COLOR);

    myRootPanel.setMinimumSize(new Dimension());
    myComponentsSplitter.setFirstComponent(myRootPanel);

    myDetailsView = new AndroidTestSuiteDetailsView(parentDisposable, this, this, project, myLogger);
    myDetailsView.getRootPanel().setVisible(false);
    myDetailsView.getRootPanel().setMinimumSize(new Dimension());
    myComponentsSplitter.setSecondComponent(myDetailsView.getRootPanel());

    myTable.selectRootItem();

    updateProgress();

    myLogger.addImpressions(ParallelAndroidTestReportUiEvent.UiElement.TEST_SUITE_VIEW,
                            ParallelAndroidTestReportUiEvent.UiElement.TEST_SUITE_VIEW_TABLE_ROW);

    Disposer.register(parentDisposable, this);
  }

  @VisibleForTesting class MyToggleAction extends ToggleAction {
    private final String myPropertiesComponentKey;
    private final boolean myDefaultState;
    private boolean isSelected;

    MyToggleAction(@NotNull String actionText,
                   @Nullable Icon actionIcon,
                   @NotNull String propertiesComponentKey,
                   boolean defaultState) {
      super(() -> actionText, actionIcon);
      myPropertiesComponentKey = propertiesComponentKey;
      myDefaultState = defaultState;
      isSelected = PropertiesComponent.getInstance(myProject).getBoolean(propertiesComponentKey, defaultState);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return isSelected;
    }

    public boolean isSelected() {
      return isSelected;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      isSelected = state;
      PropertiesComponent.getInstance(myProject).setValue(myPropertiesComponentKey, isSelected, myDefaultState);
      myTable.refreshTable();
    }

    public void setSelected(boolean state) {
      isSelected = state;
      PropertiesComponent.getInstance(myProject).setValue(myPropertiesComponentKey, isSelected, myDefaultState);
      myTable.refreshTable();
    }
  }

  @UiThread
  private void updateProgress() {
    int completedTestCases = passedTestCases + failedTestCases + skippedTestCases;
    if (scheduledTestCases == 0) {
      myProgressBar.setValue(0);
      myProgressBar.setIndeterminate(false);
      myProgressBar.setForeground(ColorProgressBar.BLUE);
    } else {
      myProgressBar.setMaximum(scheduledTestCases * myScheduledDevices);
      myProgressBar.setValue(completedTestCases * myStartedDevices);
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
      DeviceFilterComboBoxItem deviceFilterItem = new DeviceFilterComboBoxItem(device);
      if (myDeviceFilterComboBoxModel.indexOf(deviceFilterItem) == -1) {
        myDeviceFilterComboBoxModel.add(deviceFilterItem);
      }

      ApiLevelFilterComboBoxItem apiLevelFilterItem = new ApiLevelFilterComboBoxItem(device.getVersion());
      if (myApiLevelFilterComboBoxModel.indexOf(apiLevelFilterItem) == -1) {
        myApiLevelFilterComboBoxModel.add(apiLevelFilterItem);
      }

      myScheduledDevices++;
      if (myScheduledDevices == 1) {
        myTable.showTestDuration(device);
      } else {
        myTable.showTestDuration(null);
      }
      myTable.addDevice(device);
      myDetailsView.addDevice(device);
      updateProgress();
    });
  }

  @Override
  @AnyThread
  public void onTestSuiteStarted(@NotNull AndroidDevice device, @NotNull AndroidTestSuite testSuite) {
    AppUIUtil.invokeOnEdt(() -> {
      scheduledTestCases += testSuite.getTestCaseCount();
      myStartedDevices++;
      updateProgress();
    });
  }

  @Override
  @AnyThread
  public void onTestCaseStarted(@NotNull AndroidDevice device, @NotNull AndroidTestSuite testSuite, @NotNull AndroidTestCase testCase) {
    AppUIUtil.invokeOnEdt(() -> {
      AndroidTestResults results = myTable.addTestCase(device, testCase);
      myInsertionOrderMap.computeIfAbsent(results, (unused) -> myInsertionOrderMap.size());
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
  public void onAndroidTestResultsRowSelected(@NotNull AndroidTestResults selectedResults,
                                              @Nullable AndroidDevice selectedDevice) {
    openAndroidTestSuiteDetailsView(selectedResults, selectedDevice);
  }

  @Override
  @UiThread
  public void onAndroidTestSuiteDetailsViewCloseButtonClicked() {
    closeAndroidTestSuiteDetailsView();
  }

  @UiThread
  private void openAndroidTestSuiteDetailsView(@NotNull AndroidTestResults results,
                                               @Nullable AndroidDevice selectedDevice) {
    myLogger.addImpression(
      getOrientation() == Orientation.HORIZONTAL
      ? ParallelAndroidTestReportUiEvent.UiElement.TEST_SUITE_DETAILS_HORIZONTAL_VIEW
      : ParallelAndroidTestReportUiEvent.UiElement.TEST_SUITE_DETAILS_VERTICAL_VIEW);

    myDetailsView.setAndroidTestResults(results);
    if (selectedDevice != null) {
      myDetailsView.selectDevice(selectedDevice);
    } else if (AndroidTestResultsKt.isRootAggregationResult(results)) {
      myDetailsView.selectRawOutput();
    }

    if (!myDetailsView.getRootPanel().isVisible()) {
      myDetailsView.getRootPanel().setVisible(true);
    }
  }

  @UiThread
  private void closeAndroidTestSuiteDetailsView() {
    myDetailsView.getRootPanel().setVisible(false);
    myTable.clearSelection();
  }

  @Override
  @UiThread
  public Orientation getOrientation() {
    boolean isVertical = myComponentsSplitter.getOrientation();
    return isVertical ? Orientation.VERTICAL : Orientation.HORIZONTAL;
  }

  @Override
  @UiThread
  public void setOrientation(Orientation orientation) {
    myComponentsSplitter.setOrientation(orientation == Orientation.VERTICAL);
  }

  @Override
  public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
    myDetailsView.getRawTestLogConsoleView().print(text, contentType);
  }

  @Override
  public void clear() {
    myDetailsView.getRawTestLogConsoleView().clear();
  }

  @Override
  public void scrollTo(int offset) {
    myDetailsView.getRawTestLogConsoleView().scrollTo(offset);
  }

  @Override
  public void attachToProcess(ProcessHandler processHandler) {
    // Put this test suite view to the process handler as AndroidTestResultListener so the view
    // is notified the test results and to be updated.
    processHandler.putCopyableUserData(ANDROID_TEST_RESULT_LISTENER_KEY, this);
    myDetailsView.getRawTestLogConsoleView().attachToProcess(processHandler);
  }

  @Override
  public void setOutputPaused(boolean value) {
    myDetailsView.getRawTestLogConsoleView().setOutputPaused(value);
  }

  @Override
  public boolean isOutputPaused() {
    return myDetailsView.getRawTestLogConsoleView().isOutputPaused();
  }

  @Override
  public boolean hasDeferredOutput() {
    return myDetailsView.getRawTestLogConsoleView().hasDeferredOutput();
  }

  @Override
  public void performWhenNoDeferredOutput(@NotNull Runnable runnable) {
    myDetailsView.getRawTestLogConsoleView().performWhenNoDeferredOutput(runnable);
  }

  @Override
  public void setHelpId(@NotNull String helpId) {
    myDetailsView.getRawTestLogConsoleView().setHelpId(helpId);
  }

  @Override
  public void addMessageFilter(@NotNull Filter filter) {
    myDetailsView.getRawTestLogConsoleView().addMessageFilter(filter);
  }

  @Override
  public void printHyperlink(@NotNull String hyperlinkText, @Nullable HyperlinkInfo info) {
    myDetailsView.getRawTestLogConsoleView().printHyperlink(hyperlinkText, info);
  }

  @Override
  public int getContentSize() {
    return myDetailsView.getRawTestLogConsoleView().getContentSize();
  }

  @Override
  public boolean canPause() {
    return myDetailsView.getRawTestLogConsoleView().canPause();
  }

  @NotNull
  @Override
  public AnAction[] createConsoleActions() {
    return AnAction.EMPTY_ARRAY;
  }

  @Override
  public void allowHeavyFilters() {
    myDetailsView.getRawTestLogConsoleView().allowHeavyFilters();
  }

  @Override
  public JComponent getComponent() {
    return myComponentsSplitter;
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return myTable.getPreferredFocusableComponent();
  }

  @Override
  public void dispose() {
    myLogger.reportImpressions();
  }

  @VisibleForTesting
  public AndroidTestResultsTableView getTableForTesting() {
    return myTable;
  }

  @VisibleForTesting
  public AndroidTestSuiteDetailsView getDetailsViewForTesting() {
    return myDetailsView;
  }

  public final class MyItemSeparator extends JComponent {
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
