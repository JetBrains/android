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

import com.android.annotations.concurrency.UiThread;
import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ActionPlaces;
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults;
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultsKt;
import com.android.tools.idea.testartifacts.instrumented.testsuite.logging.AndroidTestSuiteLogger;
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.StudioIcons;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Displays detailed results of an instrumentation test case. The test case may be executed by
 * multiple devices and this view can show them all. It has a device selector list view at left
 * side and the test result of the selected device is displayed at the right side.
 *
 * <p>Use {@link AndroidTestSuiteDetailsViewListener} to receive events.
 *
 * Note: This view is under development and most of methods are not implemented yet.
 */
public class AndroidTestSuiteDetailsView {

  /**
   * An interface to listen events occurred in AndroidTestSuiteDetailsView.
   */
  public interface AndroidTestSuiteDetailsViewListener {
    /**
     * Invoked when the close button is clicked.
     */
    void onAndroidTestSuiteDetailsViewCloseButtonClicked();
  }

  // Those properties are initialized by IntelliJ form editor before the constructor using reflection.
  private JPanel myRootPanel;
  private JPanel myHeaderPanel;
  private JBLabel myTitleText;
  private CommonButton myChangeOrientationButton;
  private CommonButton myCloseButton;
  private JPanel myContentPanel;

  private final JBSplitter myComponentsSplitter;
  private final DetailsViewDeviceSelectorListView myDeviceSelectorListView;
  private final DetailsViewContentView myContentView;
  private final ConsoleViewImpl myRawTestLogConsoleView;
  private final NonOpaquePanel myRawTestLogConsoleViewWithVerticalToolbar;

  @Nullable private AndroidTestResults myTestResults;
  @Nullable private AndroidDevice mySelectedDevice;

  @UiThread
  public AndroidTestSuiteDetailsView(@NotNull Disposable parentDisposable,
                                     @NotNull AndroidTestSuiteViewController controller,
                                     @NotNull AndroidTestSuiteDetailsViewListener listener,
                                     @NotNull Project project,
                                     @NotNull AndroidTestSuiteLogger logger) {
    myHeaderPanel.setBorder(new SideBorder(UIUtil.getBoundsColor(), SideBorder.BOTTOM));

    myCloseButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        listener.onAndroidTestSuiteDetailsViewCloseButtonClicked();
      }
    });
    myChangeOrientationButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        switch(controller.getOrientation()) {
          case VERTICAL:
            myChangeOrientationButton.setIcon(AllIcons.General.ArrowDown);
            controller.setOrientation(AndroidTestSuiteViewController.Orientation.HORIZONTAL);
            break;
          case HORIZONTAL:
            myChangeOrientationButton.setIcon(AllIcons.General.ArrowRight);
            controller.setOrientation(AndroidTestSuiteViewController.Orientation.VERTICAL);
            break;
        }
      }
    });

    myContentView = new DetailsViewContentView(parentDisposable, project, logger);
    myRawTestLogConsoleView = new ConsoleViewImpl(project, /*viewer=*/true);
    Disposer.register(parentDisposable, myRawTestLogConsoleView);
    myRawTestLogConsoleViewWithVerticalToolbar = new NonOpaquePanel(new BorderLayout());
    myRawTestLogConsoleViewWithVerticalToolbar.add(myRawTestLogConsoleView.getComponent(), BorderLayout.CENTER);
    ActionToolbar rawTestLogToolbar = ActionManager.getInstance().createActionToolbar(
      ActionPlaces.ANDROID_TEST_SUITE_RAW_LOG, new DefaultActionGroup(myRawTestLogConsoleView.createConsoleActions()), false);
    myRawTestLogConsoleViewWithVerticalToolbar.add(rawTestLogToolbar.getComponent(), BorderLayout.EAST);

    myComponentsSplitter = new JBSplitter();
    myComponentsSplitter.setHonorComponentsMinimumSize(false);
    myComponentsSplitter.setDividerWidth(1);
    myComponentsSplitter.getDivider().setBackground(UIUtil.CONTRAST_BORDER_COLOR);

    myDeviceSelectorListView = new DetailsViewDeviceSelectorListView(
      new DetailsViewDeviceSelectorListView.DetailsViewDeviceSelectorListViewListener() {
        @Override
        public void onDeviceSelected(@NotNull AndroidDevice selectedDevice) {
          mySelectedDevice = selectedDevice;
          reloadAndroidTestResults();
          myComponentsSplitter.setSecondComponent(myContentView.getRootPanel());
        }

        @Override
        public void onRawOutputSelected() {
          myComponentsSplitter.setSecondComponent(myRawTestLogConsoleViewWithVerticalToolbar);
        }
      });
    myComponentsSplitter.setFirstComponent(myDeviceSelectorListView.getRootPanel());
    myComponentsSplitter.setProportion(0.3f);

    myComponentsSplitter.setSecondComponent(myRawTestLogConsoleViewWithVerticalToolbar);
    myContentPanel.add(myComponentsSplitter);

    myTitleText.setBorder(JBUI.Borders.empty(0, 10));
    myRootPanel.setMinimumSize(new Dimension());
  }

  /**
   * Creates and initializes custom view components. This method is called by IntelliJ form editor runtime
   * before the constructor is called.
   */
  private void createUIComponents() {
    myCloseButton = new CommonButton(StudioIcons.Common.CLOSE);
    myChangeOrientationButton = new CommonButton(AllIcons.General.ArrowDown);
  }

  /**
   * Returns the root panel of AndroidTestSuiteDetailsView.
   */
  @NotNull
  public JPanel getRootPanel() {
    return myRootPanel;
  }

  /**
   * Updates the view with a given AndroidTestResults.
   */
  @UiThread
  public void setAndroidTestResults(@NotNull AndroidTestResults results) {
    myTestResults = results;
    reloadAndroidTestResults();
  }

  /**
   * Reload AndroidTestResults set by {@link #setAndroidTestResults(AndroidTestResults)}.
   */
  @UiThread
  public void reloadAndroidTestResults() {
    if (myTestResults == null) {
      return;
    }
    if (!Strings.isNullOrEmpty(myTestResults.getMethodName())) {
      myTitleText.setText(AndroidTestResultsKt.getFullTestCaseName(myTestResults));
    } else if (!Strings.isNullOrEmpty(myTestResults.getClassName())) {
      myTitleText.setText(AndroidTestResultsKt.getFullTestClassName(myTestResults));
    } else {
      myTitleText.setText("Test Results");
    }
    myTitleText.setIcon(AndroidTestResultsTableViewKt.getIconFor(myTestResults.getTestResultSummary()));
    myTitleText.setMinimumSize(new Dimension());

    if (AndroidTestResultsKt.isRootAggregationResult(myTestResults)) {
      myDeviceSelectorListView.setShowRawOutputItem(true);
    } else {
      myDeviceSelectorListView.setShowRawOutputItem(false);
      if (mySelectedDevice != null) {
        selectDevice(mySelectedDevice);
      }
    }
    myDeviceSelectorListView.setAndroidTestResults(myTestResults);

    myContentView.setPackageName(myTestResults.getPackageName());
    if (mySelectedDevice != null) {
      myContentView.setAndroidDevice(mySelectedDevice);
      myContentView.setAndroidTestCaseResult(myTestResults.getTestCaseResult(mySelectedDevice));
      myContentView.setLogcat(myTestResults.getLogcat(mySelectedDevice));
      myContentView.setErrorStackTrace(myTestResults.getErrorStackTrace(mySelectedDevice));
      myContentView.setBenchmarkText(myTestResults.getBenchmark(mySelectedDevice));
      myContentView.setRetentionSnapshot(myTestResults.getRetentionSnapshot(mySelectedDevice));
    }
  }

  /**
   * Adds a given Android device to the device selector list in the details view.
   */
  @UiThread
  public void addDevice(@NotNull AndroidDevice device) {
    myDeviceSelectorListView.addDevice(device);

    // If a user hasn't select a device yet, set the first come device as default.
    if (mySelectedDevice == null) {
      mySelectedDevice = device;
    }
  }

  /**
   * Selects a given device and display test results specifically to the device.
   */
  @UiThread
  public void selectDevice(@NotNull AndroidDevice device) {
    myDeviceSelectorListView.selectDevice(device);
  }

  /**
   * Select the raw output item to be displayed in the content area.
   */
  @UiThread
  public void selectRawOutput() {
    myDeviceSelectorListView.selectRawOutputItem();
  }

  @UiThread
  public ConsoleView getRawTestLogConsoleView() {
    return myRawTestLogConsoleView;
  }

  @VisibleForTesting
  public JBLabel getTitleTextViewForTesting() {
    return myTitleText;
  }

  @VisibleForTesting
  public CommonButton getCloseButtonForTesting() {
    return myCloseButton;
  }

  @VisibleForTesting
  @Nullable
  public AndroidDevice getSelectedDeviceForTesting() {
    return mySelectedDevice;
  }

  @VisibleForTesting
  public DetailsViewContentView getContentViewForTesting() {
    return myContentView;
  }
}
