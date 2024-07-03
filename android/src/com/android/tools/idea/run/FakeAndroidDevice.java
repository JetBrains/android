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
package com.android.tools.idea.run;

import static com.android.ddmlib.IDevice.PROP_DEVICE_BOOT_QEMU_DISPLAY_NAME;

import com.android.ddmlib.AvdData;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.ddms.DeviceNameRendererEx;
import com.android.tools.idea.ddms.DevicePropertyUtil;
import com.android.tools.idea.run.util.LaunchUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@TestOnly
public final class FakeAndroidDevice implements AndroidDevice {
  @NotNull private final IDevice myDevice;
  @Nullable private final DeviceNameRendererEx myDeviceNameRenderer;
  private volatile String myDeviceManufacturer;
  private volatile String myDeviceModel;

  @NotNull
  public static DeviceFutures forDevices(@NotNull Iterable<IDevice> devices) {
    ImmutableList.Builder<AndroidDevice> futures = ImmutableList.builder();
    for (IDevice device : devices) {
      futures.add(new FakeAndroidDevice(device));
    }
    return new DeviceFutures(futures.build());
  }

  public FakeAndroidDevice(@NotNull IDevice device) {
    myDevice = device;
    myDeviceNameRenderer = getRendererExtension(device);
  }

  @Override
  public boolean isRunning() {
    return true;
  }

  @Override
  public boolean isVirtual() {
    return myDevice.isEmulator();
  }

  @NotNull
  @Override
  public AndroidVersion getVersion() {
    return myDevice.getVersion();
  }

  @Override
  public int getDensity() {
    return myDevice.getDensity();
  }

  @Override
  public boolean supportsMultipleScreenFormats() {
    return myDevice.getVersion().isGreaterOrEqualThan(AndroidVersion.MIN_RESIZABLE_DEVICE_API)
           && "resizable".equals(myDevice.getProperty(PROP_DEVICE_BOOT_QEMU_DISPLAY_NAME));
  }

  @NotNull
  @Override
  public List<Abi> getAbis() {
    List<String> abis = myDevice.getAbis();
    ImmutableList.Builder<Abi> builder = ImmutableList.builder();

    for (String abi : abis) {
      Abi a = Abi.getEnum(abi);
      if (a != null) {
        builder.add(a);
      }
    }

    return builder.build();
  }

  @Nullable
  @Override
  public String getAppPreferredAbi() {
    if (!isVirtual()) {
      return null;
    }

    try {
      AvdData avdData = myDevice.getAvdData().get();
      if (avdData == null) {
        return null;
      }
      String avdName = avdData.getName();
      if (avdName == null) {
        return null;
      }
      AvdInfo info = AvdManagerConnection.getDefaultAvdManagerConnection().findAvd(avdName);
      if (info == null) {
        return null;
      }
      return info.getUserSettings().get(AvdManager.USER_SETTINGS_INI_PREFERRED_ABI);
    }
    catch (ExecutionException | InterruptedException e) {
      return null;
    }
  }

  @NotNull
  @Override
  public String getSerial() {
    if (myDevice.isEmulator()) {
      String avdName = myDevice.getAvdName();
      if (avdName != null) {
        return "AVD: " + avdName;
      }
    }

    return "Connected Device: " +  myDevice.getSerialNumber();
  }

  @Override
  public boolean supportsFeature(@NotNull IDevice.HardwareFeature feature) {
    return myDevice.supportsFeature(feature);
  }

  @Override
  public boolean getSupportsSdkRuntime() {
    return myDevice.services().containsKey("sdk_sandbox") && myDevice.getVersion().isGreaterOrEqualThan(34);
  }

  @NotNull
  @Override
  public String getName() {
    if (myDeviceNameRenderer != null) {
      return myDeviceNameRenderer.getName(myDevice);
    }

    if (isVirtual()) {
      String virtualIdentifier = null;
      if (myDevice.isEmulator()) {
        virtualIdentifier = myDevice.getAvdName();
      }
      if (virtualIdentifier == null) {
        virtualIdentifier = myDevice.getSerialNumber();
      }
      return getDeviceName() + " [" + virtualIdentifier + "]";
    }
    return getDeviceName();
  }

  @NotNull
  private String getDeviceName() {
    StringBuilder name = new StringBuilder(20);
    name.append(getDeviceManufacturer());
    if (name.length() > 0) {
      name.append(' ');
    }
    name.append(getDeviceModel());
    return name.toString();
  }

  @NotNull
  private String getDeviceManufacturer() {
    if (myDeviceManufacturer == null) {
      assert isNotDispatchThread();
      myDeviceManufacturer = DevicePropertyUtil.getManufacturer(myDevice, "");
    }
    return myDeviceManufacturer;
  }

  @NotNull
  private String getDeviceModel() {
    if (myDeviceModel == null) {
      assert isNotDispatchThread();
      myDeviceModel = DevicePropertyUtil.getModel(myDevice, "");
    }
    return myDeviceModel;
  }

  @NotNull
  @Override
  public ListenableFuture<IDevice> launch(@NotNull Project project) {
    return getLaunchedDevice();
  }

  @NotNull
  @Override
  public ListenableFuture<IDevice> getLaunchedDevice() {
    return Futures.immediateFuture(myDevice);
  }

  @Nullable
  private static DeviceNameRendererEx getRendererExtension(@NotNull IDevice device) {
    Application application = ApplicationManager.getApplication();
    if (application == null || application.isUnitTestMode()) {
      return null;
    }
    for (DeviceNameRendererEx extensionRenderer : DeviceNameRendererEx.EP_NAME.getExtensions()) {
      if (extensionRenderer.isApplicable(device)) {
        return extensionRenderer;
      }
    }
    return null;
  }

  @Override
  @NotNull
  public LaunchCompatibility canRun(@NotNull AndroidVersion minSdkVersion,
                                    @NotNull IAndroidTarget projectTarget,
                                    @NotNull Supplier<EnumSet<IDevice.HardwareFeature>> getRequiredHardwareFeatures,
                                    @NotNull Set<Abi> supportedAbis) {
    return LaunchCompatibility.canRunOnDevice(minSdkVersion, projectTarget, getRequiredHardwareFeatures, supportedAbis, this);
  }

  private boolean isNotDispatchThread() {
    Application application = ApplicationManager.getApplication();
    return application == null || !application.isDispatchThread();
  }

  @Override
  public boolean isDebuggable() {
    return LaunchUtils.isDebuggableDevice(myDevice);
  }
}
