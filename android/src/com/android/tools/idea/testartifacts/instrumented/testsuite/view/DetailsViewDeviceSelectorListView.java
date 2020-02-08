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
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.components.JBList;
import javax.swing.DefaultListModel;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.jetbrains.annotations.NotNull;

/**
 * Shows a list of devices. This view is intended to be used in Android test suite details page
 * in conjunction with {@link DetailsViewContentView}.
 * A user can click on a device item to look up detailed test results for a selected device.
 */
public class DetailsViewDeviceSelectorListView {

  /**
   * Interface to listen events occurred in {@link DetailsViewDeviceSelectorListView}.
   */
  public interface DetailsViewDeviceSelectorListViewListener {
    /**
     * Called when a user selects a device for looking up test results for the device.
     */
    @UiThread
    void onDeviceSelected(@NotNull AndroidDevice selectedDevice);
  }

  // Those properties are initialized by IntelliJ form editor before the constructor using reflection.
  private JPanel myRootPanel;
  private JBList<AndroidDevice> myDeviceList;
  private DefaultListModel<AndroidDevice> myDeviceListModel;

  public DetailsViewDeviceSelectorListView(@NotNull DetailsViewDeviceSelectorListViewListener listener) {
    myDeviceList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent event) {
        AndroidDevice selectedItem = myDeviceList.getSelectedValue();
        if (selectedItem != null) {
          listener.onDeviceSelected(selectedItem);
        }
      }
    });
  }

  /**
   * Creates and initializes custom view components. This method is called by IntelliJ form editor runtime
   * before the constructor is called.
   */
  private void createUIComponents() {
    myDeviceListModel = new DefaultListModel<>();
    myDeviceList = new JBList<>(myDeviceListModel);
    myDeviceList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
  }

  /**
   * Returns the root panel.
   */
  @NotNull
  public JPanel getRootPanel() {
    return myRootPanel;
  }

  /**
   * Adds a given device to the list.
   */
  @UiThread
  public void addDevice(@NotNull AndroidDevice device) {
    myDeviceListModel.addElement(device);
  }

  @VisibleForTesting
  public JBList<AndroidDevice> getDeviceListForTesting() {
    return myDeviceList;
  }
}
