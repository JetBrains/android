/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run.editor;

import com.android.annotations.Nullable;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdEditWizard;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.run.*;
import com.google.common.base.Predicate;
import com.google.common.collect.Sets;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SortedListModel;
import com.intellij.ui.components.JBList;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import gnu.trove.TIntArrayList;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class DevicePicker implements AndroidDebugBridge.IDebugBridgeChangeListener, AndroidDebugBridge.IDeviceChangeListener, Disposable,
                                     ActionListener {
  private static final Logger LOG = Logger.getInstance(DevicePicker.class);
  private final static int UPDATE_DELAY_MILLIS = 250;

  private JPanel myPanel;
  private JButton myCreateEmulatorButton;
  private HyperlinkLabel myHelpHyperlink;
  private JScrollPane myScrollPane;
  private JBList myDevicesList;

  @NotNull private final AndroidFacet myFacet;
  @NotNull private final SortedListModel<AndroidDevice> myModel;
  @NotNull private final MergingUpdateQueue myUpdateQueue;
  private List<AvdInfo> myAvdInfos;

  public DevicePicker(@NotNull Disposable parent, @NotNull final AndroidFacet facet) {
    myFacet = facet;

    myHelpHyperlink.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        BrowserUtil.browse("http://developer.android.com/tools/device.html", facet.getModule().getProject());
      }
    });

    myModel = SortedListModel.create(new AndroidDeviceComparator());
    myDevicesList.setModel(myModel);
    myDevicesList.setCellRenderer(new AndroidDeviceRenderer(createChecker(facet))); // TODO: need to show serial number in some cases
    myDevicesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myCreateEmulatorButton.addActionListener(this);

    // the device change notifications from adb can sometimes be noisy (esp. when a device is [dis]connected)
    // we use this merging queue to collapse multiple updates to one
    myUpdateQueue = new MergingUpdateQueue("android.device.chooser", UPDATE_DELAY_MILLIS, true, null, this, null,
                                           Alarm.ThreadToUse.POOLED_THREAD);

    AndroidDebugBridge.addDebugBridgeChangeListener(this);
    AndroidDebugBridge.addDeviceChangeListener(this);

    postUpdate();
    refreshAvds();

    Disposer.register(parent, this);
  }

  private static LaunchCompatibiltyChecker createChecker(@NotNull AndroidFacet facet) {
    AndroidVersion minSdkVersion = AndroidModuleInfo.get(facet).getRuntimeMinSdkVersion();

    AndroidPlatform platform = facet.getConfiguration().getAndroidPlatform();
    if (platform == null) {
      throw new IllegalStateException("Android platform not set for module: " + facet.getModule().getName());
    }

    // Currently, we only look at whether the device supports the watch feature.
    // We may not want to search the device for every possible feature, but only a small subset of important
    // features, starting with hardware type watch.
    EnumSet<IDevice.HardwareFeature> requiredHardwareFeatures;
    if (LaunchUtils.isWatchFeatureRequired(facet)) {
      requiredHardwareFeatures = EnumSet.of(IDevice.HardwareFeature.WATCH);
    }
    else {
      requiredHardwareFeatures = EnumSet.noneOf(IDevice.HardwareFeature.class);
    }

    return new LaunchCompatibiltyChecker(minSdkVersion, platform.getTarget(), requiredHardwareFeatures);
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myDevicesList;
  }

  private void createUIComponents() {
    myDevicesList = new JBList();
    myScrollPane = ScrollPaneFactory.createScrollPane(myDevicesList);
    myHelpHyperlink = new HyperlinkLabel("Don't see your device?");
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (e.getSource() == myCreateEmulatorButton) {
      AvdEditWizard wizard = new AvdEditWizard(myPanel, myFacet.getModule().getProject(), myFacet.getModule(), null, false);
      wizard.init();
      wizard.showAndGet();
      refreshAvds();
    }
  }

  @Override
  public void bridgeChanged(@NotNull AndroidDebugBridge bridge) {
    postUpdate();
  }

  @Override
  public void deviceConnected(@NotNull IDevice device) {
    postUpdate();
  }

  @Override
  public void deviceDisconnected(@NotNull final IDevice device) {
    postUpdate();
  }

  @Override
  public void deviceChanged(IDevice device, int changeMask) {
    postUpdate();
  }

  private void refreshAvds() {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        final List<AvdInfo> avdInfos = AvdManagerConnection.getDefaultAvdManagerConnection().getAvds(true);
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            myAvdInfos = avdInfos;
            updateModel();
          }
        });
      }
    });
  }

  private void postUpdate() {
    myUpdateQueue.queue(new Update("updateDevicePickerModel") {
      @Override
      public void run() {
        updateModel();
      }

      @Override
      public boolean canEat(Update update) {
        return true;
      }
    });
  }

  private void updateModel() {
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          updateModel();
        }
      });
      return;
    }

    Set<String> selectedSerials = getSelectedSerials(myDevicesList.getSelectedValues());

    Set<String> runningAvdNames = Sets.newHashSet();
    myModel.clear();

    for (IDevice device : AndroidDebugBridge.getBridge().getDevices()) {
      myModel.add(new ConnectedAndroidDevice(device, myAvdInfos));

      if (device.isEmulator()) {
        String avdName = device.getAvdName();
        if (avdName != null) {
          runningAvdNames.add(avdName);
        }
      }
    }

    if (myAvdInfos != null) {
      for (AvdInfo avdInfo : myAvdInfos) {
        if (!runningAvdNames.contains(avdInfo.getName())) {
          myModel.add(new LaunchableAndroidDevice(avdInfo));
        }
      }
    }

    myDevicesList.setSelectedIndices(getIndices(myModel.getItems(), selectedSerials));
  }

  @NotNull
  private static int[] getIndices(@NotNull List<AndroidDevice> items, @NotNull Set<String> selectedSerials) {
    TIntArrayList list = new TIntArrayList(selectedSerials.size());

    for (int i = 0; i < items.size(); i++) {
      if (selectedSerials.contains(items.get(i).getSerial())) {
        list.add(i);
      }
    }

    return list.toNativeArray();
  }

  @NotNull
  private static Set<String> getSelectedSerials(@NotNull Object[] selectedValues) {
    Set<String> selection = Sets.newHashSet();

    for (Object o : selectedValues) {
      if (o instanceof AndroidDevice) {
        selection.add(((AndroidDevice)o).getSerial());
      }
    }

    return selection;
  }

  @Override
  public void dispose() {
    AndroidDebugBridge.removeDebugBridgeChangeListener(this);
    AndroidDebugBridge.removeDeviceChangeListener(this);
  }

  public ValidationInfo validate() {
    if (getSelectedDevice() == null) {
      return new ValidationInfo("No device selected", myDevicesList);
    }

    return null;
  }

  @Nullable
  public AndroidDevice getSelectedDevice() {
    return (AndroidDevice)myDevicesList.getSelectedValue();
  }

  @NotNull
  public DeviceTarget getSelectedTarget(ProcessHandlerConsolePrinter printer) {
    AndroidDevice selectedDevice = getSelectedDevice();
    if (selectedDevice == null) {
      throw new IllegalStateException("Incorrect validation? No target was selected in device picker.");
    }

    if (selectedDevice instanceof ConnectedAndroidDevice) {
      IDevice device = ((ConnectedAndroidDevice)selectedDevice).getDevice();
      return DeviceTarget.forDevices(Collections.singletonList(device));
    }
    else if (selectedDevice instanceof LaunchableAndroidDevice) {
      final AvdInfo avdInfo = ((LaunchableAndroidDevice)selectedDevice).getAvdInfo();
      AvdManagerConnection.getDefaultAvdManagerConnection().startAvd(myFacet.getModule().getProject(), avdInfo);

      // Wait for an AVD to come up with name matching the one we just launched.
      final String avdName = avdInfo.getName();
      Predicate<IDevice> avdNameFilter = new Predicate<IDevice>() {
        @Override
        public boolean apply(IDevice device) {
          return device.isEmulator() && avdName.equals(device.getAvdName());
        }
      };

      return DeviceTarget.forFuture(DeviceReadyListener.getReadyDevice(avdNameFilter, printer));
    }
    else {
      throw new IllegalStateException("Unsupported ");
    }
  }
}
