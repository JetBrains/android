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
import com.android.tools.idea.avdmanager.*;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.run.*;
import com.android.tools.idea.run.util.LaunchUtils;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.Alarm;
import com.intellij.util.SmartList;
import com.intellij.util.ThreeState;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class DevicePicker implements AndroidDebugBridge.IDebugBridgeChangeListener, AndroidDebugBridge.IDeviceChangeListener, Disposable,
                                     ActionListener, ListSelectionListener {
  private static final int UPDATE_DELAY_MILLIS = 250;
  private static final String DEVICE_PICKER_LAST_SELECTION = "device.picker.selection";
  private static final TIntObjectHashMap<Set<String>> ourSelectionsPerConfig = new TIntObjectHashMap<Set<String>>();

  private JPanel myPanel;
  private JButton myCreateEmulatorButton;
  private HyperlinkLabel myHelpHyperlink;
  @SuppressWarnings("unused") // custom create
  private JScrollPane myScrollPane;
  private JPanel myNotificationPanel;
  private JBList myDevicesList;
  private int myDeviceCount;
  private int myErrorGen;

  @NotNull private final AndroidFacet myFacet;
  private final int myRunContextId;
  @NotNull private final DevicePickerListModel myModel;
  @NotNull private final MergingUpdateQueue myUpdateQueue;
  private final LaunchCompatibilityChecker myCompatibilityChecker;
  private final ListSpeedSearch mySpeedSearch;

  private List<AvdInfo> myAvdInfos = Lists.newArrayList();

  public DevicePicker(@NotNull Disposable parent,
                      int runContextId,
                      @NotNull final AndroidFacet facet,
                      @NotNull DeviceCount deviceCount,
                      @NotNull LaunchCompatibilityChecker compatibilityChecker) {
    myRunContextId = runContextId;
    myFacet = facet;

    myHelpHyperlink.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        launchDiagnostics(facet.getModule().getProject());
      }
    });

    myCompatibilityChecker = compatibilityChecker;

    mySpeedSearch = new ListSpeedSearch(myDevicesList) {
      @Override
      protected String getElementText(Object element) {
        if (element instanceof DevicePickerEntry) {
          DevicePickerEntry entry = (DevicePickerEntry)element;
          if (!entry.isMarker()) {
            AndroidDevice device = entry.getAndroidDevice();
            return device.getName();
          }
        }
        return "";
      }
    };

    myModel = new DevicePickerListModel();
    myDevicesList.setModel(myModel);
    myDevicesList.setCellRenderer(new AndroidDeviceRenderer(myCompatibilityChecker, mySpeedSearch));
    myDevicesList.setSelectionMode(getListSelectionMode(deviceCount));
    myDevicesList.addKeyListener(new MyListKeyListener(mySpeedSearch));
    myDevicesList.addListSelectionListener(this);

    myNotificationPanel.setLayout(new BoxLayout(myNotificationPanel, 1));

    myCreateEmulatorButton.addActionListener(this);

    // the device change notifications from adb can sometimes be noisy (esp. when a device is [dis]connected)
    // we use this merging queue to collapse multiple updates to one
    myUpdateQueue = new MergingUpdateQueue("android.device.chooser", UPDATE_DELAY_MILLIS, true, null, this, null,
                                           Alarm.ThreadToUse.POOLED_THREAD);

    AndroidDebugBridge.addDebugBridgeChangeListener(this);
    AndroidDebugBridge.addDeviceChangeListener(this);

    postUpdate();
    refreshAvds(null);

    Disposer.register(parent, this);
  }

  @Override
  public void dispose() {
    AndroidDebugBridge.removeDebugBridgeChangeListener(this);
    AndroidDebugBridge.removeDeviceChangeListener(this);
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
      AvdOptionsModel avdOptionsModel = new AvdOptionsModel(null);
      ModelWizardDialog dialog = AvdWizardUtils.createAvdWizard(myPanel,myFacet.getModule().getProject(), avdOptionsModel);
      if (dialog.showAndGet()) {
        AvdInfo createdAvd = avdOptionsModel.getCreatedAvd();
        refreshAvds(createdAvd);
      }
    }
  }

  @Override
  public void valueChanged(ListSelectionEvent e) {
    if (e.getSource() == myDevicesList) {
      Set<String> selectedSerials = getSelectedSerials(myDevicesList.getSelectedValues());
      ourSelectionsPerConfig.put(myRunContextId, selectedSerials);
      saveSelectionForProject(myFacet.getModule().getProject(), selectedSerials);
    }
  }

  @Override
  public void bridgeChanged(@Nullable AndroidDebugBridge bridge) {
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

  public void refreshAvds(@Nullable final AvdInfo avdToSelect) {
    myDevicesList.setPaintBusy(true);
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        final List<AvdInfo> avdInfos = AvdManagerConnection.getDefaultAvdManagerConnection().getAvds(true);
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            myAvdInfos = avdInfos;
            updateModel();
            myDevicesList.setPaintBusy(false);

            if (avdToSelect != null) {
              selectAvd(avdToSelect);
            }
          }
        });
      }
    });
  }

  private void updateErrorCheck() {
    myErrorGen++;
    myNotificationPanel.removeAll();
    if (myDeviceCount == 0) {
      EditorNotificationPanel panel = new EditorNotificationPanel();
      panel.setText("No USB devices or running emulators detected");
      panel.createActionLabel("Troubleshoot", new Runnable() {
        @Override
        public void run() {
          launchDiagnostics(null);
        }
      });

      myNotificationPanel.add(panel);
    }
    if (!myAvdInfos.isEmpty()) {
      final int currentErrorGen = myErrorGen;
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          final AccelerationErrorCode error = AvdManagerConnection.getDefaultAvdManagerConnection().checkAcceration();
          if (error != AccelerationErrorCode.ALREADY_INSTALLED) {
            UIUtil.invokeLaterIfNeeded(new Runnable() {
              @Override
              public void run() {
                if (myErrorGen != currentErrorGen) {
                  // The notification panel has been reset since we started this update.
                  // Ignore this error, there is another request coming.
                  return;
                }
                myNotificationPanel.add(new AccelerationErrorNotificationPanel(error, myFacet.getModule().getProject(), new Runnable() {
                  @Override
                  public void run() {
                    updateErrorCheck();
                  }
                }));
                myPanel.revalidate();
                myPanel.repaint();
              }
            });
          }
        }
      });
    }
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

  private void selectAvd(@NotNull AvdInfo avdToSelect) {
    String serial = new LaunchableAndroidDevice(avdToSelect).getSerial();

    List<DevicePickerEntry> items = myModel.getItems();
    for (int i = 0; i < items.size(); i++) {
      DevicePickerEntry entry = items.get(i);
      if (entry.isMarker()) {
        continue;
      }

      AndroidDevice device = entry.getAndroidDevice();
      assert device != null : "Non marker entry cannot be null";
      if (serial.equals(device.getSerial())) {
        myDevicesList.setSelectedIndex(i);
        return;
      }
    }
  }

  private void updateModel() {
    AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();
    if (bridge == null || !bridge.isConnected()) {
      return;
    }

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

    List<IDevice> connectedDevices = Lists.newArrayList(bridge.getDevices());
    myModel.reset(connectedDevices, myAvdInfos);
    myDeviceCount = connectedDevices.size();

    if (selectedSerials.isEmpty()) {
      selectedSerials = getDefaultSelection();
    }
    myDevicesList.setSelectedIndices(getIndices(myModel.getItems(), selectedSerials));

    // The help hyper link is shown only when there is no inline troubleshoot link
    myHelpHyperlink.setVisible(!connectedDevices.isEmpty());

    updateErrorCheck();
  }

  @NotNull
  private static int[] getIndices(@NotNull List<DevicePickerEntry> items, @NotNull Set<String> selectedSerials) {
    TIntArrayList list = new TIntArrayList(selectedSerials.size());

    for (int i = 0; i < items.size(); i++) {
      DevicePickerEntry entry = items.get(i);
      if (entry.isMarker()) {
        continue;
      }

      AndroidDevice androidDevice = entry.getAndroidDevice();
      assert androidDevice != null : "An entry in the device picker must be either a marker or an AndroidDevice, got null";

      if (selectedSerials.contains(androidDevice.getSerial())) {
        list.add(i);
      }
    }

    return list.toNativeArray();
  }

  @NotNull
  private static Set<String> getSelectedSerials(@NotNull Object[] selectedValues) {
    Set<String> selection = Sets.newHashSet();

    for (Object o : selectedValues) {
      if (o instanceof DevicePickerEntry) {
        AndroidDevice device = ((DevicePickerEntry)o).getAndroidDevice();
        if (device != null) {
          selection.add(device.getSerial());
        }
      }
    }

    return selection;
  }

  @NotNull
  private Set<String> getDefaultSelection() {
    // first use the last selection for this config
    Set<String> lastSelection = ourSelectionsPerConfig.get(myRunContextId);

    // if this is the first time launching the dialog, pick up the previous selections from saved state
    if (lastSelection == null || lastSelection.isEmpty()) {
      lastSelection = getLastSelectionForProject(myFacet.getModule().getProject());
    }

    if (!lastSelection.isEmpty()) {
      // check if any of them actually present right now
      int[] indices = getIndices(myModel.getItems(), lastSelection);
      if (indices.length > 0) {
        return lastSelection;
      }
    }

    for (DevicePickerEntry entry : myModel.getItems()) {
      if (entry.isMarker()) {
        continue;
      }

      AndroidDevice androidDevice = entry.getAndroidDevice();
      assert androidDevice != null : "Non marker entry in the device picker doesn't contain an android device";
      if (myCompatibilityChecker.validate(androidDevice).isCompatible() != ThreeState.NO) {
        return ImmutableSet.of(androidDevice.getSerial());
      }
    }

    return Collections.emptySet();
  }

  private static int getListSelectionMode(@NotNull DeviceCount deviceCount) {
    return deviceCount.isMultiple() ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION;
  }

  public ValidationInfo validate() {
    List<AndroidDevice> devices = getSelectedDevices();
    if (devices.isEmpty()) {
      return new ValidationInfo("No device selected", myDevicesList);
    }

    for (AndroidDevice device : devices) {
      LaunchCompatibility compatibility = myCompatibilityChecker.validate(device);
      if (compatibility.isCompatible() == ThreeState.NO) {
        String reason = StringUtil.notNullize(compatibility.getReason(), "Incompatible");
        if (devices.size() > 1) {
          reason = device.getName() + ": " + reason;
        }

        return new ValidationInfo(reason, myDevicesList);
      }
    }

    return null;
  }

  @NotNull
  public List<AndroidDevice> getSelectedDevices() {
    SmartList<AndroidDevice> devices = new SmartList<AndroidDevice>();

    for (Object value: myDevicesList.getSelectedValues()) {
      if (value instanceof DevicePickerEntry) {
        AndroidDevice device = ((DevicePickerEntry)value).getAndroidDevice();
        if (device != null) {
          devices.add(device);
        }
      }
    }

    return devices;
  }

  // TODO: this needs to become a diagnostics dialog
  public static void launchDiagnostics(@Nullable Project project) {
    BrowserUtil.browse("https://developer.android.com/r/studio-ui/devicechooser.html", project);
  }

  public void installDoubleClickListener(@NotNull DoubleClickListener listener) {
    listener.installOn(myDevicesList);
  }

  private static Set<String> getLastSelectionForProject(@NotNull Project project) {
    String s = PropertiesComponent.getInstance(project).getValue(DEVICE_PICKER_LAST_SELECTION);
    return s == null ? Collections.<String>emptySet() : Sets.newHashSet(s.split(" "));
  }

  private static void saveSelectionForProject(@NotNull Project project, @NotNull Set<String> selectedSerials) {
    PropertiesComponent.getInstance(project).setValue(DEVICE_PICKER_LAST_SELECTION, Joiner.on(' ').join(selectedSerials));
  }

  /** {@link MyListKeyListener} provides a custom key listener that makes sure that up/down key events don't end up selecting a marker. */
  private static class MyListKeyListener extends KeyAdapter {
    private final ListSpeedSearch mySpeedSearch;

    private MyListKeyListener(@NotNull ListSpeedSearch speedSearch) {
      mySpeedSearch = speedSearch;
    }

    @Override
    public void keyPressed(KeyEvent e) {
      if (mySpeedSearch.isPopupActive()) {
        return;
      }

      JList list = (JList)e.getSource();
      int startIndex = list.getSelectedIndex();

      int keyCode = e.getKeyCode();
      switch (keyCode) {
        case KeyEvent.VK_DOWN:
          ListScrollingUtil.moveDown(list, e.getModifiersEx());
          break;
        case KeyEvent.VK_PAGE_DOWN:
          ListScrollingUtil.movePageDown(list);
          break;
        case KeyEvent.VK_UP:
          ListScrollingUtil.moveUp(list, e.getModifiersEx());
          break;
        case KeyEvent.VK_PAGE_UP:
          ListScrollingUtil.movePageUp(list);
          break;
        default:
          // only interested in up/down actions
          return;
      }

      // move up or down if the current selection is a marker
      DevicePickerEntry entry = (DevicePickerEntry)list.getSelectedValue();
      while (entry.isMarker() && list.getSelectedIndex() != startIndex) {
        if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_PAGE_UP) {
          ListScrollingUtil.moveUp(list, e.getModifiersEx());
        }
        else {
          ListScrollingUtil.moveDown(list, e.getModifiersEx());
        }
        entry = (DevicePickerEntry)list.getSelectedValue();
      }

      e.consume();
    }
  }
}
