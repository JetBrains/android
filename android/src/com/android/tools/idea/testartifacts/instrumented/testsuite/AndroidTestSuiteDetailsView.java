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
package com.android.tools.idea.testartifacts.instrumented.testsuite;

import com.android.annotations.concurrency.UiThread;
import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults;
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice;
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.DetailsViewContentView;
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.DetailsViewDeviceSelectorListView;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import icons.StudioIcons;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * Displays detailed results of an instrumentation test case. The test case may be executed by
 * multiple devices and this view can show them all. It has a device selector list view at left
 * side and the test result of the selected device is displayed at the right side.
 *
 * <p>Use {@link AndroidTestSuiteDetailsViewListener} to receive events.
 *
 * Note: This view is under development and most of methods are not implemented yet.
 */
public class AndroidTestSuiteDetailsView implements DetailsViewDeviceSelectorListView.DetailsViewDeviceSelectorListViewListener {

  /**
   * An interface to listen events occurred in AndroidTestSuiteDetailsView.
   */
  public interface AndroidTestSuiteDetailsViewListener {
    /**
     * Invoked when the close button is clicked.
     */
    void onAndroidTestSuiteDetailsViewCloseButtonClicked();
  }

  /**
   * Minimum width of the device list swing component in pixel.
   */
  private static final int MIN_DEVICE_LIST_WIDTH = 64;
  private static final int DEFAULT_DEVICE_LIST_WIDTH = 128;

  // Those properties are initialized by IntelliJ form editor before the constructor using reflection.
  private JPanel myRootPanel;
  private JPanel myHeaderPanel;
  private JBLabel myTitleText;
  private CommonButton myCloseButton;
  private JPanel myContentPanel;

  private final DetailsViewDeviceSelectorListView myDeviceSelectorListView;

  @UiThread
  public AndroidTestSuiteDetailsView(@NotNull Disposable parentDisposable,
                                     @NotNull AndroidTestSuiteDetailsViewListener listener) {
    myHeaderPanel.setBorder(new SideBorder(UIUtil.getBoundsColor(), SideBorder.BOTTOM));

    myCloseButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        listener.onAndroidTestSuiteDetailsViewCloseButtonClicked();
      }
    });

    ThreeComponentsSplitter componentsSplitter = new ThreeComponentsSplitter(/*vertical=*/false, /*onePixelDividers=*/true);
    componentsSplitter.setOpaque(false);
    componentsSplitter.setMinSize(MIN_DEVICE_LIST_WIDTH);
    Disposer.register(parentDisposable, componentsSplitter);

    myDeviceSelectorListView = new DetailsViewDeviceSelectorListView(this);
    componentsSplitter.setFirstComponent(myDeviceSelectorListView.getRootPanel());
    componentsSplitter.setFirstSize(DEFAULT_DEVICE_LIST_WIDTH);

    DetailsViewContentView contentView = new DetailsViewContentView();
    componentsSplitter.setLastComponent(contentView.getRootPanel());

    myContentPanel.add(componentsSplitter);
  }

  /**
   * Creates and initializes custom view components. This method is called by IntelliJ form editor runtime
   * before the constructor is called.
   */
  private void createUIComponents() {
    myCloseButton = new CommonButton(StudioIcons.Common.CLOSE);
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
    setTitle(results.getTestCaseName());
  }

  @UiThread
  private void setTitle(@NotNull String title) {
    myTitleText.setText(title);
  }

  /**
   * Adds a given Android device to the device selector list in the details view.
   */
  @UiThread
  public void addDevice(@NotNull AndroidDevice device) {
    myDeviceSelectorListView.addDevice(device);
  }

  @Override
  @UiThread
  public void onDeviceSelected(@NotNull AndroidDevice selectedDevice) {
    // TODO: implement this.
  }

  @VisibleForTesting
  public JBLabel getTitleTextViewForTesting() {
    return myTitleText;
  }

  @VisibleForTesting
  public CommonButton getCloseButtonForTesting() {
    return myCloseButton;
  }
}
