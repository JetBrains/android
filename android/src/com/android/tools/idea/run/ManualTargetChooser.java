package com.android.tools.idea.run;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.cloud.CloudMatrixTarget;
import com.android.tools.idea.run.testing.AndroidTestRunConfiguration;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * A target chooser corresponding to the "Extended device chooser" dialog.
 * Either a device target or a cloud matrix target may be chosen.
 */
public class ManualTargetChooser implements TargetChooser {
  @NotNull private static final Logger LOG = Logger.getInstance(ManualTargetChooser.class);

  @NotNull private final AndroidRunConfigurationBase myConfiguration;
  @NotNull private final AndroidFacet myFacet;
  @NotNull private final EmulatorLaunchOptions myEmulatorLaunchOptions;

  public ManualTargetChooser(
    @NotNull AndroidRunConfigurationBase configuration,
    @NotNull AndroidFacet facet,
    @NotNull EmulatorLaunchOptions emulatorLaunchOptions
  ) {
    myConfiguration = configuration;
    myFacet = facet;
    myEmulatorLaunchOptions = emulatorLaunchOptions;
  }

  @Override
  public boolean matchesDevice(@NotNull IDevice device) {
    if (myConfiguration.USE_LAST_SELECTED_DEVICE) {
      DeviceStateAtLaunch lastLaunchState = myConfiguration.getDevicesUsedInLastLaunch();
      return lastLaunchState != null && lastLaunchState.usedDevice(device);
    }
    return false;
  }

  @Nullable
  @Override
  public DeployTarget getTarget(@NotNull ConsolePrinter printer, @NotNull DeviceCount deviceCount, boolean debug) {
    Collection<IDevice> devices = getReusableDevices(deviceCount);
    if (!devices.isEmpty()) {
      return DeviceTarget.forDevices(devices);
    }

    AndroidPlatform platform = myFacet.getConfiguration().getAndroidPlatform();
    if (platform == null) {
      LOG.error("Android platform not set for module: " + myFacet.getModule().getName());
      return null;
    }

    boolean showCloudTarget = myConfiguration instanceof AndroidTestRunConfiguration && !debug;
    final ExtendedDeviceChooserDialog chooser =
      new ExtendedDeviceChooserDialog(myFacet, platform.getTarget(), deviceCount.isMultiple(), true,
                                      myConfiguration.USE_LAST_SELECTED_DEVICE, showCloudTarget, myEmulatorLaunchOptions.getCommandLine());
    chooser.show();
    if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
      // The user canceled.
      return null;
    }

    if (chooser.isToLaunchEmulator()) {
      final String selectedAvd = chooser.getSelectedAvd();
      if (selectedAvd == null) {
        return null;
      }
      EmulatorTargetChooser emulatorChooser =
        new EmulatorTargetChooser(myFacet, myEmulatorLaunchOptions, selectedAvd);
      return emulatorChooser.getTarget(printer, deviceCount, debug);
    }
    else if (chooser.isCloudTestOptionSelected()) {
      return new CloudMatrixTarget(chooser.getSelectedMatrixConfigurationId(), chooser.getChosenCloudProjectId());
    }
    else {
      final IDevice[] selectedDevices = chooser.getSelectedDevices();
      if (selectedDevices.length == 0) {
        return null;
      }
      if (chooser.useSameDevicesAgain()) {
        myConfiguration.USE_LAST_SELECTED_DEVICE = true;
        myConfiguration.setDevicesUsedInLaunch(Sets.newHashSet(selectedDevices), getOnlineDevices());
      } else {
        myConfiguration.USE_LAST_SELECTED_DEVICE = false;
        myConfiguration.setDevicesUsedInLaunch(Collections.<IDevice>emptySet(), Collections.<IDevice>emptySet());
      }
      return DeviceTarget.forDevices(Arrays.asList(selectedDevices));
    }
  }

  /** Re-use the last used devices if we are configured to do so and the online devices have not changed. */
  @NotNull
  private Collection<IDevice> getReusableDevices(@NotNull DeviceCount deviceCount) {
    DeviceStateAtLaunch devicesToReuse = myConfiguration.getDevicesUsedInLastLaunch();
    if (!myConfiguration.USE_LAST_SELECTED_DEVICE || devicesToReuse == null) {
      return ImmutableList.of();
    }

    Set<IDevice> onlineDevices = getOnlineDevices();
    if (devicesToReuse.matchesCurrentAvailableDevices(onlineDevices)) {
      Collection<IDevice> usedDevices = devicesToReuse.filterByUsed(onlineDevices);
      if (usedDevices.size() == 1 || deviceCount.isMultiple()) {
        return usedDevices;
      }
    }
    return ImmutableList.of();
  }

  private Set<IDevice> getOnlineDevices() {
    AndroidDebugBridge debugBridge = AndroidSdkUtils.getDebugBridge(myFacet.getModule().getProject());
    if (debugBridge == null) {
      return Collections.emptySet();
    }
    return Sets.newHashSet(debugBridge.getDevices());
  }
}
