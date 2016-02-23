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

import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.internal.avd.GpuMode;
import com.android.sdklib.internal.avd.HardwareProperties;
import com.android.tools.idea.ddms.screenshot.DeviceArtDescriptor;
import com.android.tools.idea.wizard.dynamic.*;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.android.tools.idea.avdmanager.AvdWizardConstants.*;

/**
 * Wizard for creating/editing AVDs
 */
public class AvdEditWizard extends DynamicWizard {
  @Nullable private final AvdInfo myAvdInfo;
  private final boolean myForceCreate;
  private final JComponent myParent;
  private AvdInfo myCreatedAvd;

  public AvdEditWizard(@Nullable JComponent parent,
                       @Nullable Project project,
                       @Nullable Module module,
                       @Nullable AvdInfo avdInfo,
                       boolean forceCreate) {
    super(project, module, "AvdEditWizard", new DialogWrapperHost(project, DialogWrapper.IdeModalityType.PROJECT));
    myAvdInfo = avdInfo;
    myForceCreate = forceCreate;
    myParent = parent;
    setTitle("Virtual Device Configuration");
  }

  @Override
  public void init() {
    if (myAvdInfo != null) {
      fillExistingInfo(myAvdInfo);
      if (myForceCreate) {
        String displayName = myAvdInfo.getProperties().get(DISPLAY_NAME_KEY.name);
        getState().put(DISPLAY_NAME_KEY, String.format("Copy of %1$s", displayName));
      }
    }
    else {
      initDefaultInfo();
    }
    addPath(new AvdConfigurationPath(getDisposable()));
    DynamicWizardStep configStep = new ConfigureAvdOptionsStep(getDisposable());
    addPath(new SingleStepPath(configStep));
    super.init();
    if (myForceCreate && myAvdInfo != null) {
      getState().put(IS_IN_EDIT_MODE_KEY, false);
    }
  }

  /**
   * Init the wizard with a set of reasonable defaults
   */
  private void initDefaultInfo() {
    ScopedStateStore state = getState();
    state.put(SCALE_SELECTION_KEY, DEFAULT_SCALE);
    state.put(NETWORK_SPEED_KEY, DEFAULT_NETWORK_SPEED);
    state.put(NETWORK_LATENCY_KEY, DEFAULT_NETWORK_LATENCY);
    state.put(FRONT_CAMERA_KEY, DEFAULT_CAMERA);
    state.put(BACK_CAMERA_KEY, DEFAULT_CAMERA);
    state.put(INTERNAL_STORAGE_KEY, DEFAULT_INTERNAL_STORAGE);
    state.put(IS_IN_EDIT_MODE_KEY, false);
    state.put(DISPLAY_SD_SIZE_KEY, new Storage(100, Storage.Unit.MiB));
    state.put(DISPLAY_USE_EXTERNAL_SD_KEY, false);
  }

  /**
   * Init the wizard by filling in the information from the given AVD
   */
  private void fillExistingInfo(@NotNull AvdInfo avdInfo) {
    ScopedStateStore state = getState();
    List<Device> devices = DeviceManagerConnection.getDefaultDeviceManagerConnection().getDevices();
    Device selectedDevice = null;
    String manufacturer = avdInfo.getDeviceManufacturer();
    String deviceId = avdInfo.getProperties().get(AvdManager.AVD_INI_DEVICE_NAME);
    for (Device device : devices) {
      if (manufacturer.equals(device.getManufacturer()) && deviceId.equals(device.getId())) {
        selectedDevice = device;
        break;
      }
    }
    state.put(DEVICE_DEFINITION_KEY, selectedDevice);
    ISystemImage selectedImage = avdInfo.getSystemImage();
    if (selectedImage != null) {
      SystemImageDescription systemImageDescription = new SystemImageDescription(selectedImage);
      state.put(SYSTEM_IMAGE_KEY, systemImageDescription);
    }

    Map<String, String> properties = avdInfo.getProperties();

    state.put(CPU_CORES_KEY, StringUtil.parseInt(properties.get(CPU_CORES_KEY.name), 1));
    state.put(RANCHU_KEY, properties.containsKey(CPU_CORES_KEY.name));
    state.put(RAM_STORAGE_KEY, getStorageFromIni(properties.get(RAM_STORAGE_KEY.name)));
    state.put(VM_HEAP_STORAGE_KEY, getStorageFromIni(properties.get(VM_HEAP_STORAGE_KEY.name)));
    state.put(INTERNAL_STORAGE_KEY, getStorageFromIni(properties.get(INTERNAL_STORAGE_KEY.name)));

    String sdCardLocation = null;
    if (properties.get(EXISTING_SD_LOCATION.name) != null) {
      sdCardLocation = properties.get(EXISTING_SD_LOCATION.name);
    }
    else if (properties.get(SD_CARD_STORAGE_KEY.name) != null) {
      sdCardLocation = FileUtil.join(avdInfo.getDataFolderPath(), "sdcard.img");
    }
    state.put(EXISTING_SD_LOCATION, sdCardLocation);
    String dataFolderPath = avdInfo.getDataFolderPath();
    File sdLocationFile = null;
    if (sdCardLocation != null) {
      sdLocationFile = new File(sdCardLocation);
    }
    if (sdLocationFile != null && Objects.equal(sdLocationFile.getParent(), dataFolderPath)) {
      // the image is in the AVD folder, consider it to be internal
      File sdFile = new File(sdCardLocation);
      Storage sdCardSize = new Storage(sdFile.length());
      myState.put(DISPLAY_USE_EXTERNAL_SD_KEY, false);
      myState.put(SD_CARD_STORAGE_KEY, sdCardSize);
      myState.put(DISPLAY_SD_SIZE_KEY, sdCardSize);
    }
    else {
      // the image is external
      myState.put(DISPLAY_USE_EXTERNAL_SD_KEY, true);
      myState.put(DISPLAY_SD_LOCATION_KEY, sdCardLocation);
    }

    String scale = properties.get(SCALE_SELECTION_KEY.name);
    if (scale != null) {
      state.put(SCALE_SELECTION_KEY, AvdScaleFactor.findByValue(scale));
    }
    state.put(USE_HOST_GPU_KEY, fromIniString(properties.get(USE_HOST_GPU_KEY.name)));
    String mode = properties.get(HOST_GPU_MODE_KEY.name);
    if (mode != null) {
      state.put(HOST_GPU_MODE_KEY, GpuMode.fromGpuSetting(mode));
    }
    state.put(FRONT_CAMERA_KEY, properties.get(FRONT_CAMERA_KEY.name));
    state.put(BACK_CAMERA_KEY, properties.get(BACK_CAMERA_KEY.name));
    state.put(NETWORK_LATENCY_KEY, properties.get(NETWORK_LATENCY_KEY.name));
    state.put(NETWORK_SPEED_KEY, properties.get(NETWORK_SPEED_KEY.name));
    state.put(HAS_HARDWARE_KEYBOARD_KEY, fromIniString(properties.get(HAS_HARDWARE_KEYBOARD_KEY.name)));
    state.put(DISPLAY_NAME_KEY, AvdManagerConnection.getAvdDisplayName(avdInfo));

    String orientation = properties.get(HardwareProperties.HW_INITIAL_ORIENTATION);
    if (orientation != null) {
      state.put(DEFAULT_ORIENTATION_KEY, ScreenOrientation.getByShortDisplayName(orientation));
    }

    String skinPath = properties.get(CUSTOM_SKIN_FILE_KEY.name);
    if (skinPath != null) {
      File skinFile;
      if (skinPath.equals(NO_SKIN.getPath())) {
        skinFile = NO_SKIN;
      }
      else {
        skinFile = new File(skinPath);
      }
      if (skinFile.isDirectory()) {
        state.put(CUSTOM_SKIN_FILE_KEY, skinFile);
      }
    }
    String backupSkinPath = properties.get(BACKUP_SKIN_FILE_KEY.name);
    if (backupSkinPath != null) {
      File skinFile = new File(backupSkinPath);
      if (skinFile.isDirectory() || FileUtil.filesEqual(skinFile, NO_SKIN)) {
        state.put(BACKUP_SKIN_FILE_KEY, skinFile);
      }
    }
    state.put(IS_IN_EDIT_MODE_KEY, true);
  }

  /**
   * Decodes the given string from the INI file and returns a {@link Storage} of
   * corresponding size.
   */
  @Nullable
  private static Storage getStorageFromIni(String iniString) {
    if (iniString == null) {
      return null;
    }
    String numString = iniString.substring(0, iniString.length() - 1);
    char unitChar = iniString.charAt(iniString.length() - 1);
    Storage.Unit selectedUnit = null;
    for (Storage.Unit u : Storage.Unit.values()) {
      if (u.toString().charAt(0) == unitChar) {
        selectedUnit = u;
        break;
      }
    }
    if (selectedUnit == null) {
      selectedUnit = Storage.Unit.MiB; // Values expressed without a unit read as MB
      numString = iniString;
    }
    try {
      long numLong = Long.parseLong(numString);
      return new Storage(numLong, selectedUnit);
    }
    catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public void performFinishingActions() {
    Device device = myState.get(DEVICE_DEFINITION_KEY);
    assert device != null; // Validation should be done by individual steps
    SystemImageDescription systemImageDescription = myState.get(SYSTEM_IMAGE_KEY);
    assert systemImageDescription != null;
    ScreenOrientation orientation = myState.get(DEFAULT_ORIENTATION_KEY);
    if (orientation == null) {
      orientation = device.getDefaultState().getOrientation();
    }

    Map<String, String> hardwareProperties = DeviceManager.getHardwareProperties(device);
    Map<String, Object> userEditedProperties = myState.flatten();

    // Skin is handled separately below.
    userEditedProperties.remove(AvdManager.AVD_INI_SKIN_PATH);

    // Remove the SD card setting that we're not using
    String sdCard = null;

    Boolean useExternalSdCard = myState.get(DISPLAY_USE_EXTERNAL_SD_KEY);
    boolean useExisting = useExternalSdCard != null && useExternalSdCard;
    if (!useExisting) {
      if (Objects.equal(myState.get(SD_CARD_STORAGE_KEY), myState.get(DISPLAY_SD_SIZE_KEY))) {
        // unchanged, use existing card
        useExisting = true;
      }
    }
    boolean hasSdCard;
    if (!useExisting) {
      userEditedProperties.remove(EXISTING_SD_LOCATION.name);
      Storage storage = myState.get(DISPLAY_SD_SIZE_KEY);
      myState.put(SD_CARD_STORAGE_KEY, storage);
      if (storage != null) {
        sdCard = toIniString(storage, false);
      }
      hasSdCard = storage != null && storage.getSize() > 0;
    }
    else {
      sdCard = myState.get(DISPLAY_SD_LOCATION_KEY);
      myState.put(EXISTING_SD_LOCATION, sdCard);
      userEditedProperties.remove(SD_CARD_STORAGE_KEY.name);
      assert sdCard != null;
      hasSdCard = true;
      hardwareProperties.put(HardwareProperties.HW_SDCARD, toIniString(true));
    }
    hardwareProperties.put(HardwareProperties.HW_SDCARD, toIniString(hasSdCard));
    // Remove any internal keys from the map
    userEditedProperties = Maps.filterEntries(userEditedProperties, new Predicate<Map.Entry<String, Object>>() {
      @Override
      public boolean apply(Map.Entry<String, Object> input) {
        return !input.getKey().startsWith(WIZARD_ONLY) && input.getValue() != null;
      }
    });
    // Call toString() on all remaining values
    hardwareProperties.putAll(Maps.transformEntries(userEditedProperties, new Maps.EntryTransformer<String, Object, String>() {
      @Override
      public String transformEntry(String key, Object value) {
        if (value instanceof Storage) {
          if (key.equals(RAM_STORAGE_KEY.name) || key.equals(VM_HEAP_STORAGE_KEY.name)) {
            return toIniString((Storage)value, true);
          }
          else {
            return toIniString((Storage)value, false);
          }
        }
        else if (value instanceof Boolean) {
          return toIniString((Boolean)value);
        }
        else if (value instanceof AvdScaleFactor) {
          return toIniString((AvdScaleFactor)value);
        }
        else if (value instanceof File) {
          return toIniString((File)value);
        }
        else if (value instanceof Double) {
          return toIniString((Double)value);
        }
        else if (value instanceof GpuMode) {
          return ((GpuMode)value).getGpuSetting();
        }
        else {
          return value.toString();
        }
      }
    }));

    File skinFile = myState.get(CUSTOM_SKIN_FILE_KEY);
    if (skinFile == null) {
      skinFile = resolveSkinPath(device.getDefaultHardware().getSkinFile(), systemImageDescription, FileOpUtils.create());
    }
    File backupSkinFile = myState.get(BACKUP_SKIN_FILE_KEY);
    if (backupSkinFile != null) {
      hardwareProperties.put(AvdManager.AVD_INI_BACKUP_SKIN_PATH, backupSkinFile.getPath());
    }

    // Add defaults if they aren't already set differently
    if (!hardwareProperties.containsKey(HardwareProperties.HW_KEYBOARD)) {
      hardwareProperties.put(HardwareProperties.HW_KEYBOARD, toIniString(false));
    }

    boolean isCircular = device.isScreenRound();

    String tempAvdName = myState.get(AVD_ID_KEY);
    if (tempAvdName == null || tempAvdName.isEmpty()) {
      tempAvdName = calculateAvdName(myAvdInfo, hardwareProperties, device, myForceCreate);
    }
    final String avdName = tempAvdName;

    // If we're editing an AVD and we downgrade a system image, wipe the user data with confirmation
    if (myAvdInfo != null && !myForceCreate) {
      ISystemImage image = myAvdInfo.getSystemImage();
      if (image != null) {

        int oldApiLevel = image.getAndroidVersion().getFeatureLevel();
        int newApiLevel = systemImageDescription.getVersion().getFeatureLevel();
        final String oldApiName = image.getAndroidVersion().getApiString();
        final String newApiName = systemImageDescription.getVersion().getApiString();
        if (oldApiLevel > newApiLevel ||
            (oldApiLevel == newApiLevel && image.getAndroidVersion().isPreview() && !systemImageDescription.getVersion().isPreview())) {
          final AtomicReference<Boolean> shouldContinue = new AtomicReference<Boolean>();
          ApplicationManager.getApplication().invokeAndWait(new Runnable() {
            @Override
            public void run() {
              String message =
                String.format(Locale.getDefault(), "You are about to downgrade %1$s from API level %2$s to API level %3$s.\n" +
                                                   "This requires a wipe of the userdata partition of the AVD.\nDo you wish to " +
                                                   "continue with the data wipe?", avdName, oldApiName, newApiName);
              int result = Messages.showYesNoDialog((Project)null, message, "Confirm Data Wipe", AllIcons.General.QuestionDialog);
              shouldContinue.set(result == Messages.YES);
            }
          }, ModalityState.any());
          if (shouldContinue.get()) {
            AvdManagerConnection.getDefaultAvdManagerConnection().wipeUserData(myAvdInfo);
          }
          else {
            return;
          }
        }
      }
    }

    AvdManagerConnection connection = AvdManagerConnection.getDefaultAvdManagerConnection();
    myCreatedAvd = connection.createOrUpdateAvd(myForceCreate ? null : myAvdInfo, avdName, device, systemImageDescription, orientation, isCircular, sdCard,
                                        skinFile, hardwareProperties, false);
  }

  @Nullable
  public AvdInfo getCreatedAvd() {
    return myCreatedAvd;
  }

  @NotNull
  @Override
  protected String getProgressTitle() {
    return "Saving AVD...";
  }

  @Nullable
  @Override
  public JComponent getProgressParentComponent() {
    return myParent;
  }

  /**
   * Resolve a possibly relative path into a skin directory. If {@code image} is provided, try to match the given path
   * against a skin path from {@code image.getSkins()}. If no match is found or no image is provided, look in the path given by
   * {@link DeviceArtDescriptor#getBundledDescriptorsFolder()}. If no match is found, return {@code path}.
   *
   * @param path  The path to resolve.
   * @param image A SystemImageDescription to use as an additional source of skin directories.
   * @return The resolved path.
   */
  @Nullable
  public static File resolveSkinPath(@Nullable File path, @Nullable SystemImageDescription image, @NotNull FileOp fop) {
    if (path == null || path.getPath().isEmpty()) {
      return path;
    }
    if (FileUtil.filesEqual(path, NO_SKIN)) {
      return NO_SKIN;
    }
    if (!path.isAbsolute()) {
      if (image != null) {
        File[] skins = image.getSkins();
        for (File skin : skins) {
          if (skin.getPath().endsWith(File.separator + path.getPath())) {
            return skin;
          }
        }
      }
      AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
      File dest = null;
      if (sdkData != null) {
        File sdkDir = sdkData.getLocation();
        File sdkSkinDir = new File(sdkDir, "skins");
        dest = new File(sdkSkinDir, path.getPath());
        if (fop.exists(dest)) {
          return dest;
        }
      }

      File resourceDir = DeviceArtDescriptor.getBundledDescriptorsFolder();
      if (resourceDir != null) {
        File resourcePath = new File(resourceDir, path.getPath());
        if (fop.exists(resourcePath)) {
          if (dest != null) {
            try {
              FileOpUtils.recursiveCopy(resourcePath, dest.getParentFile(), fop);
              return new File(dest, path.getPath());
            }
            catch (IOException e) {
              LOG.warn(String.format("Failed to copy skin directory to %1$s, using studio-relative path %2$s",
                                     dest, resourcePath));
            }
          }
          return resourcePath;
        }
      }
    }
    return path;
  }

  @NotNull
  private static String toIniString(@NotNull Double value) {
    return String.format(Locale.US, "%f", value);
  }

  @NotNull
  private static String toIniString(@NotNull File value) {
    return value.getPath();
  }

  /**
   * Encode the given value as a string that can be placed in the AVD's INI file.
   */
  @NotNull
  private static String toIniString(@NotNull AvdScaleFactor value) {
    return value.getValue();
  }

  @NotNull
  private static String calculateAvdName(@Nullable AvdInfo avdInfo,
                                         @NotNull Map<String, String> hardwareProperties,
                                         @NotNull Device device,
                                         boolean forceCreate) {
    if (avdInfo != null && !forceCreate) {
      return avdInfo.getName();
    }
    String candidateBase = hardwareProperties.get(AvdManagerConnection.AVD_INI_DISPLAY_NAME);
    if (candidateBase == null || candidateBase.isEmpty()) {
      String deviceName = device.getDisplayName().replace(' ', '_');
      String manufacturer = device.getManufacturer().replace(' ', '_');
      candidateBase = String.format("AVD_for_%1$s_by_%2$s", deviceName, manufacturer);
    }
    return cleanAvdName(AvdManagerConnection.getDefaultAvdManagerConnection(), candidateBase, true);
  }

  /**
   * Get a version of {@code candidateBase} modified such that it is a valid filename. Invalid characters will be
   * removed, and if requested the name will be made unique.
   *
   * @param candidateBase the name on which to base the avd name.
   * @param uniquify      if true, _n will be appended to the name if necessary to make the name unique, where n is the first
   *                      number that makes the filename unique.
   * @return The modified filename.
   */
  public static String cleanAvdName(@NotNull AvdManagerConnection connection, @NotNull String candidateBase, boolean uniquify) {
    candidateBase = candidateBase.replaceAll("[^0-9a-zA-Z_-]+", " ").trim().replaceAll("[ _]+", "_");
    if (candidateBase.isEmpty()) {
      candidateBase = "myavd";
    }
    String candidate = candidateBase;
    if (uniquify) {
      int i = 1;
      while (connection.avdExists(candidate)) {
        candidate = String.format("%1$s_%2$d", candidateBase, i++);
      }
    }
    return candidate;
  }

  /**
   * Encode the given value as a string that can be placed in the AVD's INI file.
   * Example: 10M or 1G
   */
  @NotNull
  public static String toIniString(@NotNull Storage storage, boolean convertToMb) {
    Storage.Unit unit = convertToMb ? Storage.Unit.MiB : storage.getAppropriateUnits();
    String unitString = convertToMb ? "" : unit.toString().substring(0, 1);
    return String.format("%1$d%2$s", storage.getSizeAsUnit(unit), unitString);
  }


  /**
   * Encode the given value as a string that can be placed in the AVD's INI file.
   */
  @NotNull
  private static String toIniString(@NotNull Boolean b) {
    return b ? "yes" : "no";
  }

  /**
   * Decode the given value from an AVD's INI file.
   */
  private static boolean fromIniString(@Nullable String s) {
    return "yes".equals(s);
  }

  @Override
  protected String getWizardActionDescription() {
    return "Create/Edit an Android Virtual Device";
  }
}
