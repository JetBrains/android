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
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.assistant.OpenAssistSidePanelAction;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.concurrent.EdtExecutor;
import com.android.tools.idea.adb.AdbService;
import com.android.tools.idea.connection.assistant.ConnectionAssistantBundleCreator;
import com.android.tools.idea.fd.InstantRunSettings;
import com.android.tools.idea.run.*;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.wireless.android.sdk.stats.AdbAssistantStats;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.wm.ToolWindowManager;
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
import java.util.*;
import java.util.List;

public class DeployTargetPickerDialog extends DialogWrapper implements HelpHandler {
  private static final int DEVICE_TAB_INDEX = 0;
  private static final int FIRST_CUSTOM_DEPLOY_TARGET_INDEX = 1;

  private final int myContextId;
  @NotNull private final AndroidFacet myFacet;

  private final List<DeployTargetInfo> myDeployTargetInfos;

  private final DevicePicker myDevicePicker;
  private final ListenableFuture<AndroidDebugBridge> myAdbFuture;

  private JPanel myContentPane;
  private JBTabbedPane myTabbedPane;
  private JPanel myDevicesPanel;
  private DeployTarget myDeployTarget;
  private List<AndroidDevice> mySelectedDevices;

  public DeployTargetPickerDialog(int runContextId,
                                  @NotNull final AndroidFacet facet,
                                  @NotNull DeviceCount deviceCount,
                                  @NotNull List<DeployTargetProvider<DeployTargetState>> deployTargetProviders,
                                  @NotNull Map<String, DeployTargetState> deployTargetStates,
                                  @NotNull LaunchCompatibilityChecker compatibilityChecker) {
    super(facet.getModule().getProject(), true);

    myFacet = facet;
    myContextId = runContextId;

    // Tab 1
    myDevicePicker = new DevicePicker(getDisposable(), runContextId, facet, deviceCount, compatibilityChecker, this);
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

    // Extra tabs
    Module module = facet.getModule();
    myDeployTargetInfos = new ArrayList<>(deployTargetProviders.size());
    for (DeployTargetProvider<DeployTargetState> provider : deployTargetProviders) {
      DeployTargetState state = deployTargetStates.get(provider.getId());
      if (state == null) {
        continue;
      }
      DeployTargetConfigurable<DeployTargetState> configurable =
        provider.createConfigurable(module.getProject(), getDisposable(), new Context(module));
      myDeployTargetInfos.add(new DeployTargetInfo(provider, state, configurable));
      JComponent component = configurable.createComponent();
      if (component != null) {
        myTabbedPane.add(component);
      }
      configurable.resetFrom(state, myContextId);
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
    final JBLoadingPanel loadingPanel = new JBLoadingPanel(new BorderLayout(), getDisposable());
    loadingPanel.add(myDeployTargetInfos.isEmpty() ? myDevicesPanel : myContentPane);

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
        public void onFailure(@Nullable Throwable t) {
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
    int selectedIndex = myTabbedPane.getSelectedIndex();
    if (isDeviceTab(selectedIndex)) {
      return myDevicePicker.validate();
    }
    else if (isCustomDeployTargetTab(selectedIndex)) {
      DeployTargetInfo info = myDeployTargetInfos.get(selectedIndex - FIRST_CUSTOM_DEPLOY_TARGET_INDEX);
      info.myConfigurable.applyTo(info.myState, myContextId);
      List<ValidationError> errors = info.myState.validate(myFacet);
      if (!errors.isEmpty()) {
        return new ValidationInfo(errors.get(0).getMessage(), null);
      }
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
    if (isDeviceTab(selectedIndex)) {
      mySelectedDevices = myDevicePicker.getSelectedDevices();
      if (canLaunchDevices(mySelectedDevices)) {
        myDeployTarget = new RealizedDeployTarget(null, null, launchDevices(mySelectedDevices));
      }
      else {
        myDeployTarget = null;
        return;
      }
    }
    else if (isCustomDeployTargetTab(selectedIndex)) {
      DeployTargetInfo info = myDeployTargetInfos.get(selectedIndex - FIRST_CUSTOM_DEPLOY_TARGET_INDEX);
      mySelectedDevices = Collections.emptyList();
      myDeployTarget = new RealizedDeployTarget(info.myProvider, info.myState, null);
    }

    super.doOKAction();
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return "android.deploy.target.picker";
  }

  @Override
  protected void doHelpAction() {
    launchDiagnostics(AdbAssistantStats.Trigger.DONT_SEE_DEVICE);
  }

  @Override
  public void launchDiagnostics(AdbAssistantStats.Trigger trigger) {
    UsageTracker.getInstance().log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.ADB_ASSISTANT_STATS)
        .setAdbAssistantStats(AdbAssistantStats.newBuilder().setTrigger(trigger)));
    if (ConnectionAssistantBundleCreator.isAssistantEnabled()) {
      OpenAssistSidePanelAction action = new OpenAssistSidePanelAction();
      action.openWindow(ConnectionAssistantBundleCreator.BUNDLE_ID, myFacet.getModule().getProject());
      doCancelAction(); // need to close the dialog for tool window to show
    }
    else {
      BrowserUtil.browse("https://developer.android.com/r/studio-ui/devicechooser.html", myFacet.getModule().getProject());
    }
  }

  /**
   * Check the AVDs for missing system images and offer to download them.
   *
   * @return true if the devices are able to launch, false if the user cancelled.
   */
  private boolean canLaunchDevices(@NotNull List<AndroidDevice> devices) {
    Set<String> requiredPackages = Sets.newHashSet();
    for (AndroidDevice device : devices) {
      if (device instanceof LaunchableAndroidDevice) {
        LaunchableAndroidDevice avd = (LaunchableAndroidDevice)device;
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

  private boolean isDeviceTab(int index) {
    return myDeployTargetInfos.isEmpty() || index == DEVICE_TAB_INDEX;
  }

  private boolean isCustomDeployTargetTab(int index) {
    return index >= FIRST_CUSTOM_DEPLOY_TARGET_INDEX && index - FIRST_CUSTOM_DEPLOY_TARGET_INDEX < myDeployTargetInfos.size();
  }

  private static class DeployTargetInfo {
    @NotNull public final DeployTargetProvider<DeployTargetState> myProvider;
    @NotNull public final DeployTargetState myState;
    @NotNull public final DeployTargetConfigurable<DeployTargetState> myConfigurable;

    public DeployTargetInfo(@NotNull DeployTargetProvider<DeployTargetState> provider,
                            @NotNull DeployTargetState state,
                            @NotNull DeployTargetConfigurable<DeployTargetState> configurable) {
      myProvider = provider;
      myState = state;
      myConfigurable = configurable;
    }
  }

  private static final class Context implements DeployTargetConfigurableContext {
    private final Module myModule;

    public Context(@NotNull Module module) {
      myModule = module;
    }

    @NotNull
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
