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

import com.android.ddmlib.AndroidDebugBridge;
import com.android.prefs.AndroidLocation;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.ddms.adb.AdbService;
import com.android.tools.idea.run.*;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.Alarm;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DeployTargetPickerDialog extends DialogWrapper {
  private static final int DEVICE_TAB_INDEX = 0;
  private static final int CUSTOM_RUNPROFILE_PROVIDER_TARGET_INDEX = 1;

  private final int myContextId;
  @NotNull private final AndroidFacet myFacet;

  @Nullable private final DeployTargetProvider myDeployTargetProvider;
  @Nullable private final DeployTargetState myDeployTargetState;
  @Nullable private final DeployTargetConfigurable myDeployTargetConfigurable;

  private final DevicePicker myDevicePicker;
  private final ListenableFuture<AndroidDebugBridge> myAdbFuture;

  private JPanel myContentPane;
  private JBTabbedPane myTabbedPane;
  private JPanel myCloudTargetsPanel;
  private JPanel myDevicesPanel;
  private DeployTarget myDeployTarget;
  private List<AndroidDevice> mySelectedDevices;

  public DeployTargetPickerDialog(int runContextId,
                                  @NotNull final AndroidFacet facet,
                                  @NotNull DeviceCount deviceCount,
                                  @NotNull List<DeployTargetProvider> deployTargetProviders,
                                  @NotNull Map<String, DeployTargetState> deployTargetStates) {
    super(facet.getModule().getProject(), true);

    // TODO: Eventually we may support more than 1 custom run provider. In such a case, there should be
    // a combo to pick one of the cloud providers.
    if (deployTargetProviders.size() > 1) {
      throw new IllegalArgumentException("Only 1 custom run profile state provider can be displayed right now..");
    }

    myFacet = facet;
    myContextId = runContextId;
    if (!deployTargetProviders.isEmpty()) {
      myDeployTargetProvider = deployTargetProviders.get(0);
      myDeployTargetState = deployTargetStates.get(myDeployTargetProvider.getId());
    } else {
      myDeployTargetProvider = null;
      myDeployTargetState = null;
    }

    // Tab 1
    myDevicePicker = new DevicePicker(getDisposable(), runContextId, facet, deviceCount);
    myDevicesPanel.add(myDevicePicker.getComponent(), BorderLayout.CENTER);
    myDevicesPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
    myDevicePicker.installDoubleClickListener(new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent event) {
        Action okAction = getOKAction();
        if (okAction.isEnabled()) {
          okAction.actionPerformed(null);
          return true;
        }
        return false;
      }
    });

    // Tab 2
    if (!deployTargetProviders.isEmpty()) {
      Module module = facet.getModule();
      myDeployTargetConfigurable = myDeployTargetProvider.createConfigurable(module.getProject(), getDisposable(), new Context(module));
      JComponent component = myDeployTargetConfigurable.createComponent();
      if (component != null) {
        myCloudTargetsPanel.add(component, BorderLayout.CENTER);
      }
      myDeployTargetConfigurable.resetFrom(myDeployTargetState, myContextId);
    } else {
      myDeployTargetConfigurable = null;
    }

    File adb = AndroidSdkUtils.getAdb(myFacet.getModule().getProject());
    if (adb == null) {
      throw new IllegalArgumentException("Unable to locate adb");
    }
    myAdbFuture = AdbService.getInstance().getDebugBridge(adb);

    DeployTargetState state = deployTargetStates.get(ShowChooserTargetProvider.ID);
    setDoNotAskOption(new UseSameDevicesOption((ShowChooserTargetProvider.State)state));
    setTitle("Select Deployment Target");
    setModal(true);
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    final JBLoadingPanel loadingPanel = new JBLoadingPanel(new BorderLayout(), myFacet.getModule().getProject());
    loadingPanel.add(myDeployTargetProvider == null ? myDevicesPanel : myContentPane);

    loadingPanel.setLoadingText("Initializing ADB");

    if (!myAdbFuture.isDone()) {
      loadingPanel.startLoading();
      Futures.addCallback(myAdbFuture, new FutureCallback<AndroidDebugBridge>() {
        @Override
        public void onSuccess(AndroidDebugBridge result) {
          loadingPanel.stopLoading();
          Logger.getInstance(DeployTargetPickerDialog.class).info("Successfully obtained debug bridge");
        }

        @Override
        public void onFailure(Throwable t) {
          loadingPanel.stopLoading();
          Logger.getInstance(DeployTargetPickerDialog.class).info("Unable to obtain debug bridge", t);
          // TODO: show an inline banner to restart adb?
        }
      }, EdtExecutor.INSTANCE);
    }

    return loadingPanel;
  }

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return "deploy.picker.dialog";
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myDevicePicker.getPreferredFocusedComponent();
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    if (myTabbedPane.getSelectedIndex() == CUSTOM_RUNPROFILE_PROVIDER_TARGET_INDEX) {
      myDeployTargetConfigurable.applyTo(myDeployTargetState, myContextId);
      List<ValidationError> errors = myDeployTargetState.validate(myFacet);
      if (!errors.isEmpty()) {
        return new ValidationInfo(errors.get(0).getMessage(), null);
      }
    } else {
      return myDevicePicker.validate();
    }

    return super.doValidate();
  }

  @NotNull
  @Override
  protected Alarm.ThreadToUse getValidationThreadToUse() {
    return Alarm.ThreadToUse.SWING_THREAD;
  }

  @Override
  protected void doOKAction() {
    int selectedIndex = myTabbedPane.getSelectedIndex();
    if (selectedIndex == DEVICE_TAB_INDEX) {
      mySelectedDevices = myDevicePicker.getSelectedDevices();
      if (canLaunchDevices(mySelectedDevices)) {
        myDeployTarget = new RealizedDeployTarget(null, null, launchDevices(mySelectedDevices));
      }
      else {
        myDeployTarget = null;
        return;
      }
    } else if (selectedIndex == CUSTOM_RUNPROFILE_PROVIDER_TARGET_INDEX) {
      mySelectedDevices = Collections.emptyList();
      myDeployTarget = new RealizedDeployTarget(myDeployTargetProvider, myDeployTargetState, null);
    }

    super.doOKAction();
  }

  /**
   * Check the AVDs for missing system images and offer to download them.
   * @return true if the devices are able to launch, false if the user cancelled.
   */
  private boolean canLaunchDevices(@NotNull List<AndroidDevice> devices) {
    Set<String> requiredPackages = Sets.newHashSet();
    for (AndroidDevice device : devices) {
      if (device instanceof LaunchableAndroidDevice) {
        LaunchableAndroidDevice avd = (LaunchableAndroidDevice) device;
        AvdInfo info = avd.getAvdInfo();
        if (AvdManagerConnection.isSystemImageDownloadProblem(info.getStatus())) {
          requiredPackages.add(AvdManagerConnection.getRequiredSystemImagePath(info));
        }
      }
    }
    if (requiredPackages.isEmpty()) {
      return true;
    }

    String title;
    StringBuilder message = new StringBuilder();
    if (requiredPackages.size() == 1) {
      title = "Download System Image";
      message.append("The system image: ").append(Iterables.getOnlyElement(requiredPackages)).append(" is missing.\n\n");
      message.append("Download it now?");
    }
    else {
      title = "Download System Images";
      message.append("The following system images are missing:\n");
      for (String packageName : requiredPackages) {
        message.append(packageName).append("\n");
      }
      message.append("\nDownload them now?");
    }
    int response = Messages.showOkCancelDialog(message.toString(), title, Messages.getQuestionIcon());
    if (response != Messages.OK) {
      return false;
    }
    ModelWizardDialog sdkQuickfixWizard = SdkQuickfixUtils.createDialogForPaths(myFacet.getModule().getProject(), requiredPackages);
    if (sdkQuickfixWizard == null) {
      return false;
    }
    sdkQuickfixWizard.show();
    myDevicePicker.refreshAvds(null);
    if (!sdkQuickfixWizard.isOK()) {
      return false;
    }
    AvdManagerConnection manager = AvdManagerConnection.getDefaultAvdManagerConnection();
    for (AndroidDevice device : devices) {
      if (device instanceof LaunchableAndroidDevice) {
        LaunchableAndroidDevice avd = (LaunchableAndroidDevice)device;
        AvdInfo info = avd.getAvdInfo();
        String problem;
        try {
          AvdInfo reloadedAvdInfo = manager.reloadAvd(info);
          problem = reloadedAvdInfo.getErrorMessage();
        }
        catch (AndroidLocation.AndroidLocationException e) {
          problem = "AVD cannot be loaded";
        }
        if (problem != null) {
          Messages.showErrorDialog(myFacet.getModule().getProject(), problem, "Emulator Launch Failed");
          return false;
        }
      }
    }
    return true;
  }

  @NotNull
  private DeviceFutures launchDevices(@NotNull List<AndroidDevice> devices) {
    if (devices.isEmpty()) {
      throw new IllegalStateException("Incorrect validation? No device was selected in device picker.");
    }

    // NOTE: WE ARE LAUNCHING EMULATORS HERE
    for (AndroidDevice device : devices) {
      if (!device.isRunning()) {
        device.launch(myFacet.getModule().getProject());
      }
    }

    return new DeviceFutures(devices);
  }

  @Nullable
  public DeployTarget getSelectedDeployTarget() {
    return myDeployTarget;
  }

  @NotNull
  public List<AndroidDevice> getSelectedDevices() {
    return mySelectedDevices;
  }

  private static final class Context implements DeployTargetConfigurableContext {
    private final Module myModule;

    public Context(@NotNull Module module) {
      myModule = module;
    }

    @Nullable
    @Override
    public Module getModule() {
      return myModule;
    }

    @Override
    public void addModuleChangeListener(@NotNull ActionListener listener) {
    }

    @Override
    public void removeModuleChangeListener(@NotNull ActionListener listener) {
    }
  }

  private static class UseSameDevicesOption implements DoNotAskOption {
    @NotNull private final ShowChooserTargetProvider.State myState;

    public UseSameDevicesOption(@NotNull ShowChooserTargetProvider.State state) {
      myState = state;
    }

    @Override
    public boolean isToBeShown() {
      return !myState.USE_LAST_SELECTED_DEVICE;
    }

    @Override
    public void setToBeShown(boolean toBeShown, int exitCode) {
      myState.USE_LAST_SELECTED_DEVICE = !toBeShown;
    }

    @Override
    public boolean canBeHidden() {
      return true;
    }

    @Override
    public boolean shouldSaveOptionsOnCancel() {
      return true;
    }

    @NotNull
    @Override
    public String getDoNotShowMessage() {
      return "Use same selection for future launches";
    }
  }
}
