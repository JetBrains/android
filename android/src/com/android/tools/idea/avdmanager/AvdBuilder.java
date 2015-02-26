/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.SystemImage;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Hardware;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.repository.MajorRevision;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.tools.idea.sdk.wizard.SdkQuickfixWizard;
import com.android.tools.idea.wizard.ScopedStateStore;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Locale;

import static com.android.tools.idea.avdmanager.AvdWizardConstants.*;
import static com.android.tools.idea.avdmanager.AvdWizardConstants.SD_CARD_STORAGE_KEY;

/**
 * UI-less builder for creating AVDs
 */
public class AvdBuilder {
  /** Maximum amount of RAM to *default* an AVD to, if the physical RAM on the device is higher */
  private static final int MAX_RAM_MB = 1536;
  private static final Logger LOG = Logger.getInstance(AvdBuilder.class);

  private final ScopedStateStore myState = new ScopedStateStore(ScopedStateStore.Scope.WIZARD, null, null);
  private Abi myAbiType;
  private Integer myApiLevel;
  private String myApiString;

  public static AvdBuilder create() {
    return new AvdBuilder();
  }

  private AvdBuilder() {
    myState.put(SCALE_SELECTION_KEY, DEFAULT_SCALE);
    myState.put(NETWORK_SPEED_KEY, DEFAULT_NETWORK_SPEED);
    myState.put(NETWORK_LATENCY_KEY, DEFAULT_NETWORK_LATENCY);
    myState.put(FRONT_CAMERA_KEY, DEFAULT_CAMERA);
    myState.put(BACK_CAMERA_KEY, DEFAULT_CAMERA);
    myState.put(INTERNAL_STORAGE_KEY, DEFAULT_INTERNAL_STORAGE);
    myState.put(USE_HOST_GPU_KEY, true);
    myState.put(SD_CARD_STORAGE_KEY, new Storage(100, Storage.Unit.MiB));
  }

  @NotNull
  public AvdBuilder setDevice(@NotNull String manufacturer, @NotNull String deviceId) throws AvdBuilderException {
    Device selectedDevice = null;
    List<Device> devices = DeviceManagerConnection.getDefaultDeviceManagerConnection().getDevices();
    for (Device device : devices) {
      if (manufacturer.equals(device.getManufacturer()) && deviceId.equals(device.getId())) {
        selectedDevice = device;
        break;
      }
    }
    if (selectedDevice == null) {
      throw new AvdBuilderException(String.format("No Device found with Manufacturer %1$s, ID %2$s", manufacturer, deviceId));
    }
    setDevice(selectedDevice);
    return this;
  }

  @NotNull
  public AvdBuilder setDevice(@NotNull Device device) {
    myState.put(DEVICE_DEFINITION_KEY, device);
    myState.put(RAM_STORAGE_KEY, getDefaultRam(device.getDefaultHardware()));
    myState.put(VM_HEAP_STORAGE_KEY, ConfigureAvdOptionsStep.calculateVmHeap(device));
    myState.put(DEFAULT_ORIENTATION_KEY, device.getDefaultState().getOrientation());
    return this;
  }

  /**
   * Get the default amount of ram to use for the given hardware in an AVD. This is typically
   * the same RAM as is used in the hardware, but it is maxed out at {@link #MAX_RAM_MB} since more than that
   * is usually detrimental to development system performance and most likely not needed by the
   * emulated app (e.g. it's intended to let the hardware run smoothly with lots of services and
   * apps running simultaneously)
   *
   * @param hardware the hardware to look up the default amount of RAM on
   * @return the amount of RAM to default an AVD to for the given hardware
   */
  @NotNull
  public static Storage getDefaultRam(@NotNull Hardware hardware) {
    Storage ram = hardware.getRam();
    if (ram.getSizeAsUnit(Storage.Unit.MiB) >= MAX_RAM_MB) {
      return new Storage(MAX_RAM_MB, Storage.Unit.MiB);
    }

    return ram;
  }

  @NotNull
  public AvdBuilder setAbiType(@NotNull Abi abiType) {
    myAbiType = abiType;
    return this;
  }

  @NotNull
  public AvdBuilder setAndroidVersion(@NotNull AndroidVersion version) {
    setApiLevel(version.getApiLevel());
    setApiString(version.getApiString());
    return this;
  }

  @NotNull
  public AvdBuilder setApiLevel(int apiLevel) {
    myApiLevel = apiLevel;
    return this;
  }

  @NotNull
  public AvdBuilder setApiString(@NotNull String apiString) {
    myApiString = apiString;
    return this;
  }

  @NotNull
  public AvdBuilder setSystemImageDescription(@NotNull SystemImageDescription description) {
    myState.put(SYSTEM_IMAGE_KEY, description);
    return this;
  }

  @NotNull
  public AvdBuilder setRam(@NotNull Storage ram) {
    myState.put(RAM_STORAGE_KEY, ram);
    return this;
  }

  @NotNull
  public AvdBuilder setVmHeapStorage(@NotNull Storage vmHeapStorage) {
    myState.put(VM_HEAP_STORAGE_KEY, vmHeapStorage);
    return this;
  }

  @NotNull
  public AvdBuilder setSdCard(@NotNull File sdCardFile) {
    myState.remove(SD_CARD_STORAGE_KEY);
    myState.put(EXISTING_SD_LOCATION, sdCardFile.getPath());
    return this;
  }

  @NotNull
  public AvdBuilder setSdCard(@NotNull Storage sdCard) {
    myState.remove(EXISTING_SD_LOCATION);
    myState.put(SD_CARD_STORAGE_KEY, sdCard);
    return this;
  }

  @NotNull
  public AvdBuilder setInternalStorage(@NotNull Storage internalStorage) {
    myState.put(INTERNAL_STORAGE_KEY, internalStorage);
    return this;
  }


  @NotNull
  public AvdBuilder setScale(@NotNull AvdScaleFactor scale) {
    myState.put(SCALE_SELECTION_KEY, scale);
    return this;
  }

  @NotNull
  public AvdBuilder setUseHostGpu(boolean useHostGpu) {
    myState.put(USE_HOST_GPU_KEY, useHostGpu);

    if (useHostGpu) {
      myState.put(USE_SNAPSHOT_KEY, false);
    }
    return this;
  }

  @NotNull
  public AvdBuilder setUseSnapshot(boolean useSnapshot) {
    myState.put(USE_SNAPSHOT_KEY, useSnapshot);

    if (useSnapshot) {
      myState.put(USE_HOST_GPU_KEY, false);
    }

    return this;
  }

  /**
   * Set the front facing camera. Options are:
   * None
   * Emulated
   * Webcam0
   * @return
   */
  @NotNull
  public AvdBuilder setFrontCamera(@NotNull String cameraType) {
    myState.put(FRONT_CAMERA_KEY, cameraType.toLowerCase());
    return this;
  }

  /**
   * Set the rear facing camera. Options are:
   * None
   * Emulated
   * Webcam0
   */
  @NotNull
  public AvdBuilder setBackCamera(@NotNull String cameraType) {
    myState.put(BACK_CAMERA_KEY, cameraType.toLowerCase());
    return this;
  }

  /**
   * Set the network latency. Options are:
   * None
   * UMTS
   * EDGE
   * GPRS
   */
  @NotNull
  public AvdBuilder setNetworkLatency(@NotNull String latency) {
    myState.put(NETWORK_LATENCY_KEY, latency.toLowerCase());
    return this;
  }

  /**
   * Set the network speed. Options are:
   * Full
   * HSDPA
   * UMTS
   * EDGE
   * GPRS
   * HSCSD
   * GSM
   */
  @NotNull
  public AvdBuilder setNetworkSpeed(@NotNull String speed) {
    myState.put(NETWORK_SPEED_KEY, speed.toLowerCase());
    return this;
  }

  @NotNull
  public AvdBuilder setDisplayName(@NotNull String displayName) {
    myState.put(DISPLAY_NAME_KEY, displayName);
    return this;
  }

  @NotNull
  public AvdBuilder setSkinFile(@NotNull File skinFile) {
    myState.put(CUSTOM_SKIN_FILE_KEY, skinFile);
    return this;
  }

  /**
   * Build an AVD with the selected info
   * @return the created AVD
   * @throws AvdBuilderException if the given info is incomplete. At minimum, the builder
   * requires a device and an ABI/API combination
   */
  @NotNull
  public AvdInfo build() throws AvdBuilderException {

    if (myState.get(DEVICE_DEFINITION_KEY) == null) {
      throw new AvdBuilderException("No device specified. Call setDevice(manufacturer, id).");
    }
    if (myState.get(SYSTEM_IMAGE_KEY) == null) {
      if (myAbiType == null) {
        throw new AvdBuilderException("No ABI type specified. Call setAbiType(ABI)");
      }
      if (myApiString == null && myApiLevel == null) {
        throw new AvdBuilderException("No API level specified. Call setApiLevel(API)");
      }
      SystemImageDescription description = getSystemImageDescription();
      if (description == null) {
        description = installRequestedSystemImage();
      }
      if (description == null) {
        throw new AvdBuilderException(String.format(Locale.getDefault(),
                                                    "Could not find a matching system image for ABI %1$s, API %2$d/%3$s",
                                                    myAbiType, myApiLevel, myApiString));
      } else {
        myState.put(SYSTEM_IMAGE_KEY, description);
      }
    }

    AvdInfo avd = AvdEditWizard.createAvd(null, myState, true);
    if (avd == null) {
      throw new AvdBuilderException("Could not create AVD");
    }
    return avd;
  }

  @Nullable
  private SystemImageDescription installRequestedSystemImage() throws AvdBuilderException {
    List<IPkgDesc> requestedPackages = Lists.newArrayListWithCapacity(3);
    AndroidVersion version = new AndroidVersion(myApiLevel, myApiString);
    requestedPackages.add(PkgDesc.Builder.newSysImg(version, SystemImage.DEFAULT_TAG,
                                                    Abi.X86.toString(), new MajorRevision(1)).create());
    requestedPackages.add(PkgDesc.Builder.newSysImg(version, WEAR_TAG,
                                                    Abi.X86.toString(), new MajorRevision(1)).create());
    requestedPackages.add(PkgDesc.Builder.newSysImg(version, TV_TAG,
                                                    Abi.X86.toString(), new MajorRevision(1)).create());
    SdkQuickfixWizard sdkQuickfixWizard = new SdkQuickfixWizard(null, null, requestedPackages);
    sdkQuickfixWizard.init();
    sdkQuickfixWizard.showAndGet();
    return getSystemImageDescription();
  }

  @Nullable
  private SystemImageDescription getSystemImageDescription() throws AvdBuilderException {
    AndroidSdkData sdk = AndroidSdkUtils.tryToChooseAndroidSdk();
    if (sdk == null) {
      throw new AvdBuilderException("No SDK found!");
    }
    sdk.getLocalSdk().clearLocalPkg(PkgType.PKG_ALL);
    SystemImageDescription description = null;
    List<IAndroidTarget> targets = Lists.newArrayList(sdk.getTargets());
    for (IAndroidTarget target : targets) {
      if (target.getVersion().getApiLevel() == myApiLevel || myApiString != null && myApiString.equals(target.getVersion().getCodename())) {
        ISystemImage[] systemImages = target.getSystemImages();
        if (systemImages != null) {
          for (ISystemImage image : systemImages) {
            if (image.getAbiType().equals(myAbiType.toString())) {
              description = new AvdWizardConstants.SystemImageDescription(target, image);
              break;
            }
          }
        }
      }
    }
    return description;
  }

  public static class AvdBuilderException extends Exception {
    public AvdBuilderException(String message) {
      super(message);
    }
  }
}
