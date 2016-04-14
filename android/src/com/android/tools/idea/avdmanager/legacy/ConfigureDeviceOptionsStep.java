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
package com.android.tools.idea.avdmanager.legacy;

import com.android.SdkConstants;
import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.repository.io.FileOpUtils;
import com.android.resources.*;
import com.android.sdklib.devices.*;
import com.android.sdklib.repositoryv2.IdDisplay;
import com.android.sdklib.repositoryv2.targets.SystemImage;
import com.android.tools.idea.avdmanager.AvdEditWizard;
import com.android.tools.idea.avdmanager.AvdWizardConstants;
import com.android.tools.idea.avdmanager.DeviceManagerConnection;
import com.android.tools.idea.ddms.screenshot.DeviceArtDescriptor;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStepWithDescription;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.android.tools.swing.util.FormScalingUtil;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.Document;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.tools.idea.avdmanager.AvdWizardConstants.*;

/**
 * UI for configuring a Device Hardware Profile.
 */
public class ConfigureDeviceOptionsStep extends DynamicWizardStepWithDescription {
  private static final String DEFAULT_DEVICE_TYPE_LABEL = "Phone/Tablet";

  @Nullable private final Device myTemplateDevice;
  private final boolean myForceCreation;
  private DeviceDefinitionPreview myDeviceDefinitionPreview;
  private JTextField myDiagonalScreenSize;
  private JTextField myScreenResolutionWidth;
  private StorageField myRamField;
  private JCheckBox mySupportsLandscape;
  private JTextField myScreenResolutionHeight;
  private JCheckBox myHasBackFacingCamera;
  private JCheckBox myHasFrontFacingCamera;
  private JCheckBox myHasAccelerometer;
  private JCheckBox myHasGyroscope;
  private JCheckBox myHasGps;
  private JCheckBox myHasProximitySensor;
  private JCheckBox mySupportsPortrait;
  private JComboBox myNavigationControlsCombo;
  private JPanel myRootPanel;
  private JCheckBox myHasHardwareButtons;
  private JCheckBox myHasHardwareKeyboard;
  private JTextField myDeviceName;
  private JBLabel myHelpAndErrorLabel;
  private HyperlinkLabel myHardwareSkinHelpLabel;
  private SkinChooser myCustomSkinPath;
  private ComboBox myDeviceTypeComboBox;

  /**
   * This contains the Software for the device. Since it has no effect on the
   * emulator whatsoever, we just use a single instance with reasonable
   * defaults. */
  private final Software mySoftware;

  private final Camera myFrontCamera = new Camera(CameraLocation.FRONT, true, true);
  private final Camera myBackCamera = new Camera(CameraLocation.BACK, true, true);

  private Device.Builder myBuilder = new Device.Builder();

  public ConfigureDeviceOptionsStep(@Nullable Device templateDevice, boolean forceCreation, @Nullable Disposable parentDisposable) {
    super(parentDisposable);
    setBodyComponent(myRootPanel);
    FormScalingUtil.scaleComponentTree(this.getClass(), createStepBody());

    myTemplateDevice = templateDevice;
    myForceCreation = forceCreation;

    mySoftware = new Software();
    mySoftware.setLiveWallpaperSupport(true);
    mySoftware.setGlVersion("2.0");

    myDeviceTypeComboBox.setModel(new CollectionComboBoxModel(ALL_TAGS));
    myDeviceTypeComboBox.setRenderer(new ListCellRendererWrapper<IdDisplay>() {
      @Override
      public void customize(JList list, IdDisplay value, int index, boolean selected, boolean hasFocus) {
        if (SystemImage.DEFAULT_TAG.equals(value) || value == null) {
          setText(DEFAULT_DEVICE_TYPE_LABEL);
        } else {
          setText(value.getDisplay());
        }
      }
    });

  }

  private static String getUniqueId(@Nullable String id) {
    return DeviceManagerConnection.getDefaultDeviceManagerConnection().getUniqueId(id);
  }

  private void createUIComponents() {
    myNavigationControlsCombo = new ComboBox(new EnumComboBoxModel<Navigation>(Navigation.class)) {
      @Override
      public ListCellRenderer getRenderer() {
        return new ColoredListCellRenderer() {
          @Override
          protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
            append(((Navigation)value).getShortDisplayValue());
          }
        };
      }
    };

    myHelpAndErrorLabel = new JBLabel();
    myHelpAndErrorLabel.setBackground(JBColor.background());
    myHelpAndErrorLabel.setForeground(JBColor.foreground());
    myHelpAndErrorLabel.setOpaque(true);
    myHardwareSkinHelpLabel = new HyperlinkLabel("How do I create a custom hardware skin?");
    myHardwareSkinHelpLabel.setHyperlinkTarget(AvdWizardConstants.CREATE_SKIN_HELP_LINK);
    myCustomSkinPath = new SkinChooser(getProject());
  }

  @Override
  public void init() {
    super.init();
    registerComponents();
    String desc;
    if (myTemplateDevice == null) {
      initDefaultParams();
      desc = "Create a new hardware profile by selecting hardware features below.";
    } else {
      initFromDevice();
      desc = myTemplateDevice.getDisplayName() + " (Edited)";
    }
    myState.put(KEY_DESCRIPTION, desc);

    myBuilder.setManufacturer("User");
    myBuilder.addSoftware(mySoftware);

    invokeUpdate(null);
  }

  @Override
  protected JLabel getDescriptionLabel() {
    return myHelpAndErrorLabel;
  }

  /**
   * Initialize our state to match the device we've been given as a starting point
   */
  private void initFromDevice() {
    assert myTemplateDevice != null;
    if (myForceCreation) {
      String name = myTemplateDevice.getDisplayName() + " (Edited)";
      myState.put(DEVICE_NAME_KEY, getUniqueId(name));
    } else {
      myState.put(DEVICE_NAME_KEY, myTemplateDevice.getDisplayName());
    }

    String tagId = myTemplateDevice.getTagId();
    if (tagId != null) {
      for (IdDisplay tag : ALL_TAGS) {
        if (tag.getId().equals(tagId)) {
          myState.put(TAG_ID_KEY, tag);
          break;
        }
      }
    }

    for (Map.Entry<String, String> entry : myTemplateDevice.getBootProps().entrySet()) {
      myBuilder.addBootProp(entry.getKey(), entry.getValue());
    }

    Hardware defaultHardware = myTemplateDevice.getDefaultHardware();
    Screen screen = defaultHardware.getScreen();
    myState.put(DIAGONAL_SCREENSIZE_KEY, screen.getDiagonalLength());
    myState.put(RESOLUTION_WIDTH_KEY, screen.getXDimension());
    myState.put(RESOLUTION_HEIGHT_KEY, screen.getYDimension());
    myState.put(RAM_STORAGE_KEY, AvdWizardConstants.getDefaultRam(defaultHardware));

    myState.put(HAS_HARDWARE_BUTTONS_KEY, defaultHardware.getButtonType() == ButtonType.HARD);
    myState.put(HAS_HARDWARE_KEYBOARD_KEY, defaultHardware.getKeyboard() != Keyboard.NOKEY);
    myState.put(NAVIGATION_KEY, defaultHardware.getNav());

    final List<State> states = myTemplateDevice.getAllStates();
    myState.put(SUPPORTS_PORTRAIT_KEY, Iterables.any(states, new Predicate<State>() {
      @Override
      public boolean apply(State input) {
        return input.getOrientation().equals(ScreenOrientation.PORTRAIT);
      }
    }));
    myState.put(SUPPORTS_LANDSCAPE_KEY, Iterables.any(states, new Predicate<State>() {
      @Override
      public boolean apply(State input) {
        return input.getOrientation().equals(ScreenOrientation.LANDSCAPE);
      }
    }));

    myState.put(HAS_FRONT_CAMERA_KEY, defaultHardware.getCamera(CameraLocation.FRONT) != null);
    myState.put(HAS_BACK_CAMERA_KEY, defaultHardware.getCamera(CameraLocation.BACK) != null);

    myState.put(HAS_ACCELEROMETER_KEY, defaultHardware.getSensors().contains(Sensor.ACCELEROMETER));
    myState.put(HAS_GYROSCOPE_KEY, defaultHardware.getSensors().contains(Sensor.GYROSCOPE));
    myState.put(HAS_GPS_KEY, defaultHardware.getSensors().contains(Sensor.GPS));
    myState.put(HAS_PROXIMITY_SENSOR_KEY, defaultHardware.getSensors().contains(Sensor.PROXIMITY_SENSOR));
    File skinFile = AvdEditWizard.resolveSkinPath(defaultHardware.getSkinFile(), null, FileOpUtils.create());
    if (skinFile != null) {
      myState.put(CUSTOM_SKIN_FILE_KEY, skinFile);
    } else {
      myState.put(CUSTOM_SKIN_FILE_KEY, NO_SKIN);
    }
  }

  /**
   * Initialize a reasonable set of default values (based on the Nexus 5)
   */
  private void initDefaultParams() {
    myState.put(DEVICE_NAME_KEY, getUniqueId(null));
    myState.put(DIAGONAL_SCREENSIZE_KEY, 5.0);
    myState.put(RESOLUTION_WIDTH_KEY, 1080);
    myState.put(RESOLUTION_HEIGHT_KEY, 1920);
    myState.put(RAM_STORAGE_KEY, new Storage(2, Storage.Unit.GiB));

    myState.put(HAS_HARDWARE_BUTTONS_KEY, false);
    myState.put(HAS_HARDWARE_KEYBOARD_KEY, false);
    myState.put(NAVIGATION_KEY, Navigation.NONAV);

    myState.put(SUPPORTS_PORTRAIT_KEY, true);
    myState.put(SUPPORTS_LANDSCAPE_KEY, true);

    myState.put(HAS_FRONT_CAMERA_KEY, true);
    myState.put(HAS_BACK_CAMERA_KEY, true);

    myState.put(HAS_ACCELEROMETER_KEY, true);
    myState.put(HAS_GYROSCOPE_KEY, true);
    myState.put(HAS_GPS_KEY, true);
    myState.put(HAS_PROXIMITY_SENSOR_KEY, true);
    myState.put(CUSTOM_SKIN_FILE_KEY, NO_SKIN);
  }

  @Override
  public boolean validate() {
    setErrorHtml("");
    if (myState.get(DIAGONAL_SCREENSIZE_KEY) == null) {
      setErrorHtml("Please enter a positive floating point value for the screen size");
      return false;
    }
    if (myState.get(RESOLUTION_WIDTH_KEY) == null || myState.get(RESOLUTION_HEIGHT_KEY) == null) {
      setErrorHtml("Please enter positive integer values for the screen resolution (width x height)");
      return false;
    }
    if (myState.get(RAM_STORAGE_KEY) == null) {
      setErrorHtml("Please specify a non-zero amount of RAM");
      return false;
    }
    Double dpi = myState.get(WIP_SCREEN_DPI_KEY);
    if (dpi == null || dpi <= 0) {
      setErrorHtml("The given resolution and screensize specified have a DPI that is too low.");
      return false;
    }

    Boolean supportsLandscape = myState.get(SUPPORTS_LANDSCAPE_KEY);
    supportsLandscape = supportsLandscape == null ? false : supportsLandscape;
    Boolean supportsPortrait = myState.get(SUPPORTS_PORTRAIT_KEY);
    supportsPortrait = supportsPortrait == null ? false : supportsPortrait;

    if (!(supportsLandscape || supportsPortrait)) {
      setErrorHtml("A device must support at least one orientation (Portrait or Landscape)");
      return false;
    }

    File skinPath = myState.get(CUSTOM_SKIN_FILE_KEY);
    if (skinPath != null && !skinPath.equals(NO_SKIN) && !skinPath.isAbsolute()) {
      skinPath = new File(DeviceArtDescriptor.getBundledDescriptorsFolder(), skinPath.getPath());
    }
    if (skinPath != null && !skinPath.equals(NO_SKIN)) {
      File layoutFile = new File(skinPath, SdkConstants.FN_SKIN_LAYOUT);
      if (!layoutFile.isFile()) {
        setErrorHtml("The skin directory does not point to a valid skin.");
      }
    }
    return myState.get(DEVICE_DEFINITION_KEY) != null;
  }

  @Override
  public void deriveValues(Set<ScopedStateStore.Key> modified) {
    boolean refreshAll = modified == null;
    if (refreshAll) {
      modified = ImmutableSet.of();
    }

    if (refreshAll || modified.contains(DEVICE_NAME_KEY)) {
      String name = myState.get(DEVICE_NAME_KEY);
      myBuilder.setName(name == null ? "" : name);
      myBuilder.setId(getUniqueId(name));
    }

    if (refreshAll || modified.contains(TAG_ID_KEY)) {
      IdDisplay tag = myState.get(TAG_ID_KEY);
      if (SystemImage.DEFAULT_TAG.equals(tag) || tag == null) {
        myBuilder.setTagId(null);
      } else {
        myBuilder.setTagId(tag.getId());
      }
    }

    if (!myState.containsKey(WIP_SCREEN_KEY)) {
      Screen s = createDefaultScreen();
      myState.put(WIP_SCREEN_KEY, s);
    }
    Screen screen = myState.get(WIP_SCREEN_KEY);
    assert screen != null;

    if (refreshAll || !Collections.disjoint(modified, ImmutableSet.of(DIAGONAL_SCREENSIZE_KEY, RESOLUTION_WIDTH_KEY, RESOLUTION_HEIGHT_KEY))) {
      Double diagonalLength = myState.get(DIAGONAL_SCREENSIZE_KEY);
      if (diagonalLength != null) {
        screen.setDiagonalLength(diagonalLength);
        screen.setSize(getScreenSize(diagonalLength));
      } else {
        diagonalLength = -1.0;
      }

      Integer resolutionWidth = myState.get(RESOLUTION_WIDTH_KEY);
      if (resolutionWidth != null) {
        screen.setXDimension(resolutionWidth);
      } else {
        resolutionWidth = 0;
      }

      Integer resolutionHeight = myState.get(RESOLUTION_HEIGHT_KEY);
      if (resolutionHeight != null) {
        screen.setYDimension(resolutionHeight);
      } else {
        resolutionHeight = 0;
      }

      // The diagonal DPI will be somewhere in between the X and Y dpi if
      // they differ
      double dpi = Math.sqrt(resolutionWidth * resolutionWidth + resolutionHeight * resolutionHeight) / diagonalLength;
      // The diagonal DPI should keep only two digits precision.
      if (dpi >= 0) {
        dpi = Math.round(dpi * 100) / 100.0;
        myState.put(WIP_SCREEN_DPI_KEY, dpi);
        screen.setYdpi(dpi);
        screen.setXdpi(dpi);

        screen.setRatio(getScreenRatio(resolutionWidth, resolutionHeight));
        if (HardwareConfigHelper.isTv(myTemplateDevice)) {
          // TVs can have varied densities, including much lower than the normal range.
          // Set the density explicitly in that case.
          screen.setPixelDensity(Density.TV);
        } else {
          screen.setPixelDensity(getDensity(dpi));
        }
      }
    }

    if (!myState.containsKey(WIP_HARDWARE_KEY)) {
      Hardware h = createDefaultHardware();
      myState.put(WIP_HARDWARE_KEY, h);
    }
    Hardware hardware = myState.get(WIP_HARDWARE_KEY);
    assert hardware != null;

    if (refreshAll || modified.contains(HAS_ACCELEROMETER_KEY)) {
      Boolean hasAccelerometer = myState.get(HAS_ACCELEROMETER_KEY);
      if (hasAccelerometer != null && hasAccelerometer) {
        hardware.addSensor(Sensor.ACCELEROMETER);
      } else {
        hardware.getSensors().remove(Sensor.ACCELEROMETER);
      }
    }

    if (refreshAll || modified.contains(HAS_GYROSCOPE_KEY)) {
      Boolean hasGyroscope = myState.get(HAS_GYROSCOPE_KEY);
      if (hasGyroscope != null && hasGyroscope) {
        hardware.addSensor(Sensor.GYROSCOPE);
      } else {
        hardware.getSensors().remove(Sensor.GYROSCOPE);
      }
    }

    if (refreshAll || modified.contains(HAS_GPS_KEY)) {
      Boolean hasGps = myState.get(HAS_GPS_KEY);
      if (hasGps != null && hasGps) {
        hardware.addSensor(Sensor.GPS);
      } else {
        hardware.getSensors().remove(Sensor.GPS);
      }
    }

    if (refreshAll || modified.contains(HAS_PROXIMITY_SENSOR_KEY)) {
      Boolean hasProximitySensor = myState.get(HAS_PROXIMITY_SENSOR_KEY);
      if (hasProximitySensor != null && hasProximitySensor) {
        hardware.addSensor(Sensor.PROXIMITY_SENSOR);
      } else {
        hardware.getSensors().remove(Sensor.PROXIMITY_SENSOR);
      }
    }

    if (refreshAll || modified.contains(HAS_FRONT_CAMERA_KEY)) {
      Boolean hasFrontCamera = myState.get(HAS_FRONT_CAMERA_KEY);
      if (hasFrontCamera != null && hasFrontCamera) {
        hardware.addCamera(myFrontCamera);
      } else {
        hardware.getCameras().remove(myFrontCamera);
      }
    }

    if (refreshAll || modified.contains(HAS_BACK_CAMERA_KEY)) {
      Boolean hasBackCamera = myState.get(HAS_BACK_CAMERA_KEY);
      if (hasBackCamera != null && hasBackCamera) {
        hardware.addCamera(myBackCamera);
      } else {
        hardware.getCameras().remove(myBackCamera);
      }
    }

    Boolean hasHardwareKeyboard = myState.get(HAS_HARDWARE_KEYBOARD_KEY);
    if (refreshAll || modified.contains(HAS_HARDWARE_KEYBOARD_KEY)) {
      if (hasHardwareKeyboard != null && hasHardwareKeyboard) {
        hardware.setKeyboard(Keyboard.QWERTY);
      } else {
        hardware.setKeyboard(Keyboard.NOKEY);
      }
    }
    if (hasHardwareKeyboard == null) {
      hasHardwareKeyboard = false;
    }

    if (refreshAll || modified.contains(HAS_HARDWARE_BUTTONS_KEY)) {
      Boolean hasHardwareButtons = myState.get(HAS_HARDWARE_BUTTONS_KEY);
      if (hasHardwareButtons != null && hasHardwareButtons) {
        hardware.setButtonType(ButtonType.HARD);
      } else {
        hardware.setButtonType(ButtonType.SOFT);
      }
    }

    if (refreshAll || modified.contains(NAVIGATION_KEY)) {
      Navigation nav = myState.get(NAVIGATION_KEY);
      if (nav != null) {
        hardware.setNav(nav);
      }
    }

    if (refreshAll || modified.contains(RAM_STORAGE_KEY)) {
      Storage ram = myState.get(RAM_STORAGE_KEY);
      if (ram != null) {
        hardware.setRam(ram);
      }
    }

    if (refreshAll || modified.contains(CUSTOM_SKIN_FILE_KEY)) {
      File skinFile = myState.get(CUSTOM_SKIN_FILE_KEY);
      if (skinFile != null) {
        hardware.setSkinFile(skinFile);
      }
    }

    // Add the screen to the hardware definition
    hardware.setScreen(screen);

    // Set up our states
    setStates(hardware, hasHardwareKeyboard);

    myState.remove(DEVICE_DEFINITION_KEY);
    try {
      Device d = myBuilder.build();
      myDeviceDefinitionPreview.setDevice(d);
      myState.put(DEVICE_DEFINITION_KEY, d);
    } catch (Exception e) {
      // Pass
    }
  }

  /**
   * Add the proper device states based on keyboard availability and orientation support.
   */
  private void setStates(Hardware hardware, Boolean hasHardwareKeyboard) {
    Boolean supportsLandscape = myState.get(SUPPORTS_LANDSCAPE_KEY);
    supportsLandscape = supportsLandscape == null ? false : supportsLandscape;
    Boolean supportsPortrait = myState.get(SUPPORTS_PORTRAIT_KEY);
    supportsPortrait = supportsPortrait == null ? false : supportsPortrait;

    boolean defaultSelected = false;
    if (supportsPortrait) {
      State state = new State();
      state.setName("Portrait");
      state.setDescription("The device in portrait orientation");
      state.setOrientation(ScreenOrientation.PORTRAIT);
      if (hardware.getNav().equals(Navigation.NONAV)) {
        state.setNavState(NavigationState.HIDDEN);
      } else {
        state.setNavState(NavigationState.EXPOSED);
      }
      if (hardware.getKeyboard().equals(Keyboard.NOKEY)) {
        state.setKeyState(KeyboardState.SOFT);
      } else {
        state.setKeyState(KeyboardState.HIDDEN);
      }
      state.setHardware(hardware);
      state.setDefaultState(true);
      defaultSelected = true;
      myBuilder.removeState(state.getName());
      myBuilder.addState(state);
    }
    if (supportsLandscape) {
      State state = new State();
      state.setName("Landscape");
      state.setDescription("The device in landscape orientation");
      state.setOrientation(ScreenOrientation.LANDSCAPE);
      if (hardware.getNav().equals(Navigation.NONAV)) {
        state.setNavState(NavigationState.HIDDEN);
      } else {
        state.setNavState(NavigationState.EXPOSED);
      }
      if (hardware.getKeyboard().equals(Keyboard.NOKEY)) {
        state.setKeyState(KeyboardState.SOFT);
      } else {
        state.setKeyState(KeyboardState.HIDDEN);
      }
      state.setHardware(hardware);
      if (!defaultSelected) {
        state.setDefaultState(true);
        defaultSelected = true;
      }
      myBuilder.removeState(state.getName());
      myBuilder.addState(state);
    }
    if (hasHardwareKeyboard) {
      if (supportsPortrait) {
        State state = new State();
        state.setName("Portrait with keyboard");
        state.setDescription("The device in portrait orientation with a keyboard open");
        state.setOrientation(ScreenOrientation.LANDSCAPE);
        if (hardware.getNav().equals(Navigation.NONAV)) {
          state.setNavState(NavigationState.HIDDEN);
        } else {
          state.setNavState(NavigationState.EXPOSED);
        }
        state.setKeyState(KeyboardState.EXPOSED);
        state.setHardware(hardware);
        if (!defaultSelected) {
          state.setDefaultState(true);
          defaultSelected = true;
        }
        myBuilder.removeState(state.getName());
        myBuilder.addState(state);
      }
      if (supportsLandscape) {
        State state = new State();
        state.setName("Landscape with keyboard");
        state.setDescription("The device in landscape orientation with a keyboard open");
        state.setOrientation(ScreenOrientation.LANDSCAPE);
        if (hardware.getNav().equals(Navigation.NONAV)) {
          state.setNavState(NavigationState.HIDDEN);
        } else {
          state.setNavState(NavigationState.EXPOSED);
        }
        state.setKeyState(KeyboardState.EXPOSED);
        state.setHardware(hardware);
        if (!defaultSelected) {
          state.setDefaultState(true);
        }
        myBuilder.removeState(state.getName());
        myBuilder.addState(state);
      }
    }
  }

  /**
   * Create a screen with a reasonable set of defaults.
   */
  private static Screen createDefaultScreen() {
    Screen s = new Screen();
    s.setXdpi(316);
    s.setYdpi(316);
    s.setMultitouch(Multitouch.JAZZ_HANDS);
    s.setMechanism(TouchScreen.FINGER);
    s.setScreenType(ScreenType.CAPACITIVE);
    return s;
  }

  /**
   * Create a hardware profile with a reasonable set of defaults.
   */
  private static Hardware createDefaultHardware() {
    Hardware h = new Hardware();
    h.addNetwork(Network.BLUETOOTH);
    h.addNetwork(Network.WIFI);
    h.addNetwork(Network.NFC);

    h.addSensor(Sensor.BAROMETER);
    h.addSensor(Sensor.COMPASS);
    h.addSensor(Sensor.LIGHT_SENSOR);

    h.setHasMic(true);
    h.addInternalStorage(new Storage(4, Storage.Unit.GiB));
    h.setCpu("Generic CPU");
    h.setGpu("Generic GPU");

    h.addSupportedAbi(Abi.ARMEABI);
    h.addSupportedAbi(Abi.ARMEABI_V7A);
    h.addSupportedAbi(Abi.MIPS);
    h.addSupportedAbi(Abi.X86);

    h.setChargeType(PowerType.BATTERY);
    return h;
  }

  /**
   * Get the resource bucket value that corresponds to the given size in inches.
   */
  @NotNull
  private static ScreenSize getScreenSize(@Nullable Double diagonalSize) {
    if (diagonalSize == null) {
      return ScreenSize.NORMAL;
    }
    double diagonalDp = 160.0 * diagonalSize;

    // Set the Screen Size
    if (diagonalDp >= 1200) {
      return ScreenSize.XLARGE;
    } else if (diagonalDp >= 800) {
      return ScreenSize.LARGE;
    } else if (diagonalDp >= 568) {
      return ScreenSize.NORMAL;
    } else {
      return ScreenSize.SMALL;
    }
  }

  /**
   * Calculate the screen ratio. Beyond a 5:3 ratio is considered "long"
   */
  @NotNull
  private static ScreenRatio getScreenRatio(int width, int height) {
    int longSide = Math.max(width, height);
    int shortSide = Math.min(width, height);

    // Above a 5:3 ratio is "long"
    if (longSide * 1.0 / shortSide >= 5.0 / 3) {
      return ScreenRatio.LONG;
    } else {
      return ScreenRatio.NOTLONG;
    }
  }

  /**
   * Calculate the density resource bucket for the given dots-per-inch
   */
  @NotNull
  private static Density getDensity(double dpi) {
    double difference = Double.MAX_VALUE;
    Density bucket = Density.MEDIUM;
    for (Density d : Density.values()) {
      if (!d.isValidValueForDevice()) {
        continue;
      }
      if (Math.abs(d.getDpiValue() - dpi) < difference) {
        difference = Math.abs(d.getDpiValue() - dpi);
        bucket = d;
      }
    }
    return bucket;
  }

  /**
   * Bind our controls to their state store keys
   */
  private void registerComponents() {
    register(DEVICE_NAME_KEY, myDeviceName);
    setControlDescription(myDeviceName, "Name of the Device Profile");

    register(DIAGONAL_SCREENSIZE_KEY, myDiagonalScreenSize, DOUBLE_BINDING);
    setControlDescription(myDiagonalScreenSize, "Actual Android Virtual Device size of the screen, measured as the screen's diagonal");
    register(RESOLUTION_WIDTH_KEY, myScreenResolutionWidth, INT_BINDING);
    setControlDescription(myScreenResolutionWidth, "The total number of physical pixels on a screen. " +
                                                   "When adding support for multiple screens, applications do not work directly " +
                                                   "with resolution; applications should be concerned only with screen size and density," +
                                                   " as specified by the generalized size and density groups.\n" +
                                                   "\n" +
                                                   "Width in pixels\n");
    register(RESOLUTION_HEIGHT_KEY, myScreenResolutionHeight, INT_BINDING);
    setControlDescription(myScreenResolutionHeight, "The total number of physical pixels on a screen. " +
                            "When adding support for multiple screens, applications do not work directly " +
                            "with resolution; applications should be concerned only with screen size and density," +
                            " as specified by the generalized size and density groups.\n" +
                            "<br>" +
                            "Height in pixels\n");

    register(RAM_STORAGE_KEY, myRamField, myRamField.getBinding());
    setControlDescription(myRamField, "The amount of physical RAM on the device.");

    register(HAS_HARDWARE_BUTTONS_KEY, myHasHardwareButtons);
    setControlDescription(myHasHardwareButtons, "Enables hardware navigation button support in Android Virtual Device");
    register(HAS_HARDWARE_KEYBOARD_KEY, myHasHardwareKeyboard);
    setControlDescription(myHasHardwareKeyboard, "Enables hardware keyboard  support in Android Virtual Device");
    register(NAVIGATION_KEY, myNavigationControlsCombo, NAVIGATION_BINDING);
    setControlDescription(myNavigationControlsCombo, "No Navigation - No navigational controls \n" +
                            "<br>" +
                            "Directional Pad - Enables direction pad support in emulator\n" +
                            "<br>" +
                            "Trackball - Enables trackball support in emulator");

    register(SUPPORTS_LANDSCAPE_KEY, mySupportsLandscape);
    setControlDescription(mySupportsLandscape, "Enables the landscape device screen state in emulator ");
    register(SUPPORTS_PORTRAIT_KEY, mySupportsPortrait);
    setControlDescription(mySupportsPortrait, "Enables the portrait device screen state in emulator ");

    register(HAS_BACK_CAMERA_KEY, myHasBackFacingCamera);
    setControlDescription(myHasBackFacingCamera, "Enables back-facing camera support in emulator");
    register(HAS_FRONT_CAMERA_KEY, myHasFrontFacingCamera);
    setControlDescription(myHasFrontFacingCamera, "Enables front- facing camera support in emulator");

    register(HAS_ACCELEROMETER_KEY, myHasAccelerometer);
    setControlDescription(myHasAccelerometer, "Enables accelerometer support in emulator");
    register(HAS_GYROSCOPE_KEY, myHasGyroscope);
    setControlDescription(myHasGyroscope, "Enables gyroscope support in emulator");
    register(HAS_GPS_KEY, myHasGps);
    setControlDescription(myHasGps, "Enables GPS (global positioning support) support in emulator");
    register(HAS_PROXIMITY_SENSOR_KEY, myHasProximitySensor);
    setControlDescription(myHasProximitySensor, "Enables proximity sensor support in emulator");

    File skinFile = myState.get(CUSTOM_SKIN_FILE_KEY);
    register(CUSTOM_SKIN_FILE_KEY, myCustomSkinPath, myCustomSkinPath.getBinding());
    myState.put(CUSTOM_SKIN_FILE_KEY, skinFile);
    setControlDescription(myCustomSkinPath, "Path to a directory containing a custom skin");

    register(TAG_ID_KEY, myDeviceTypeComboBox);
  }

  private static final ComponentBinding<Double, JTextField> DOUBLE_BINDING = new ComponentBinding<Double, JTextField>() {
    @Override
    public void setValue(@Nullable Double newValue, @NotNull JTextField component) {
      if (newValue != null) {
        component.setText(Double.toString(newValue));
      }
    }

    @Nullable
    @Override
    public Double getValue(@NotNull JTextField component) {
      String text = component.getText();
      if (text != null) {
        try {
          return Double.parseDouble(text);
        } catch (NumberFormatException e) {
          // Pass, will return null below
        }
      }
      return null;
    }

    @Nullable
    @Override
    public Document getDocument(@NotNull JTextField component) {
      return component.getDocument();
    }
  };

  private static final ComponentBinding<Integer, JTextField> INT_BINDING = new ComponentBinding<Integer, JTextField>() {
    @Override
    public void setValue(@Nullable Integer newValue, @NotNull JTextField component) {
      if (newValue != null) {
        component.setText(Integer.toString(newValue));
      }
    }

    @Nullable
    @Override
    public Integer getValue(@NotNull JTextField component) {
      String text = component.getText();
      if (text != null) {
        try {
          return Integer.parseInt(text);
        } catch (NumberFormatException e) {
          // Pass, will return null below
        }
      }
      return null;
    }

    @Nullable
    @Override
    public Document getDocument(@NotNull JTextField component) {
      return component.getDocument();
    }
  };

  private static final ComponentBinding<Navigation, JComboBox> NAVIGATION_BINDING = new ComponentBinding<Navigation, JComboBox>() {
    @Override
    public void setValue(@Nullable Navigation newValue, @NotNull JComboBox component) {
      component.setSelectedItem(newValue);
    }

    @Nullable
    @Override
    public Navigation getValue(@NotNull JComboBox component) {
      return (Navigation)component.getSelectedItem();
    }

    @Override
    public void addItemListener(@NotNull ItemListener listener, @NotNull JComboBox component) {
      component.addItemListener(listener);
    }
  };

  @NotNull
  @Override
  public String getStepName() {
    return "Configure Device Profile";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @NotNull
  @Override
  protected String getStepTitle() {
    return "Configure Hardware Profile";
  }

  @Nullable
  @Override
  protected String getStepDescription() {
    return null;
  }
}
