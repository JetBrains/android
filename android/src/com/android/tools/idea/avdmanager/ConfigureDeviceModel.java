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
package com.android.tools.idea.avdmanager;

import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.resources.*;
import com.android.sdklib.SystemImage;
import com.android.sdklib.devices.*;
import com.android.sdklib.repository.descriptors.IdDisplay;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.core.*;
import com.android.tools.idea.ui.properties.expressions.double_.DoubleExpression;
import com.android.tools.idea.wizard.model.WizardModel;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * {@link WizardModel} that holds all properties in {@link Device} to be used in
 * {@link ConfigureDeviceOptionsStep} for the user to edit.
 */
public final class ConfigureDeviceModel extends WizardModel {

  public static final List<IdDisplay> ALL_TAGS =
    Collections.unmodifiableList(Lists.newArrayList(SystemImage.DEFAULT_TAG, AvdWizardConstants.WEAR_TAG, AvdWizardConstants.TV_TAG));

  private StringProperty myName = new StringValueProperty();
  private OptionalProperty<IdDisplay> myDeviceType = new OptionalValueProperty<IdDisplay>();
  private StringProperty myWizardPanelDescription = new StringValueProperty();
  private StringProperty myManufacturer = new StringValueProperty();

  private DoubleProperty myDiagonalScreenSize = new DoubleValueProperty();
  private IntProperty myScreenResolutionWidth = new IntValueProperty();
  private IntProperty myScreenResolutionHeight = new IntValueProperty();
  private DoubleProperty myScreenDpi = new DoubleValueProperty();

  private ObjectProperty<Storage> myRamStorage = new ObjectValueProperty<Storage>(new Storage(0, Storage.Unit.MiB));

  private BoolProperty myHasHardwareButtons = new BoolValueProperty();
  private BoolProperty myHasHardwareKeyboard = new BoolValueProperty();
  private OptionalProperty<Navigation> myNavigation = new OptionalValueProperty<Navigation>();

  private BoolProperty mySupportsLandscape = new BoolValueProperty();
  private BoolProperty mySupportsPortrait = new BoolValueProperty();

  private BoolProperty myHasBackCamera = new BoolValueProperty();
  private BoolProperty myHasFrontCamera = new BoolValueProperty();

  private BoolProperty myHasAccelerometer = new BoolValueProperty();
  private BoolProperty myHasGyroscope = new BoolValueProperty();
  private BoolProperty myHasGps = new BoolValueProperty();
  private BoolProperty myHasProximitySensor = new BoolValueProperty();
  private OptionalProperty<File> myCustomSkinFile = new OptionalValueProperty<File>();

  private BoolValueProperty myIsTv = new BoolValueProperty();
  private BoolValueProperty myIsWear = new BoolValueProperty();
  private BoolValueProperty myIsScreenRound = new BoolValueProperty();
  private IntValueProperty myScreenChinSize = new IntValueProperty();

  private final DeviceUiAction.DeviceProvider myProvider;

  private BindingsManager myBindings = new BindingsManager();

  private OptionalProperty<Software> mySoftware = new OptionalValueProperty<Software>();

  private Device.Builder myBuilder = new Device.Builder();

  public StringProperty name() {
    return myName;
  }

  public OptionalProperty<IdDisplay> deviceType() {
    return myDeviceType;
  }

  public StringProperty wizardPanelDescription() {
    return myWizardPanelDescription;
  }

  public StringProperty manufacturer() {
    return myManufacturer;
  }

  public DoubleProperty diagonalScreenSize() {
    return myDiagonalScreenSize;
  }

  public IntProperty screenResolutionWidth() {
    return myScreenResolutionWidth;
  }

  public IntProperty screenResolutionHeight() {
    return myScreenResolutionHeight;
  }

  public ObservableDouble screenDpi() {
    return myScreenDpi;
  }

  public ObjectProperty<Storage> ramStorage() {
    return myRamStorage;
  }

  public BoolProperty hasHardwareButtons() {
    return myHasHardwareButtons;
  }

  public BoolProperty hasHardwareKeyboard() {
    return myHasHardwareKeyboard;
  }

  public OptionalProperty<Navigation> navigation() {
    return myNavigation;
  }

  public BoolProperty supportsLandscape() {
    return mySupportsLandscape;
  }

  public BoolProperty supportsPortrait() {
    return mySupportsPortrait;
  }

  public BoolProperty hasFrontCamera() {
    return myHasFrontCamera;
  }

  public BoolProperty hasBackCamera() {
    return myHasBackCamera;
  }

  public BoolProperty hasAccelerometer() {
    return myHasAccelerometer;
  }

  public BoolProperty hasGyroscope() {
    return myHasGyroscope;
  }

  public BoolProperty hasGps() {
    return myHasGps;
  }

  public BoolProperty hasProximitySensor() {
    return myHasProximitySensor;
  }

  public OptionalProperty<Software> software() {
    return mySoftware;
  }

  public OptionalProperty<File> customSkinFile() {
    return myCustomSkinFile;
  }

  public BoolProperty isTv() {
    return myIsTv;
  }

  public BoolProperty isWear() {
    return myIsWear;
  }

  public BoolProperty isScreenRound() {
    return myIsScreenRound;
  }

  public IntProperty screenChinSize() {
    return myScreenChinSize;
  }

  public ConfigureDeviceModel(@NotNull DeviceUiAction.DeviceProvider provider) {
    this(provider, null, true);
  }

  public ConfigureDeviceModel(@NotNull DeviceUiAction.DeviceProvider provider, @Nullable Device device, boolean forcedCreation) {
    if (device == null) {
      initDefaultValues();
    }
    else {
      initFromDevice(device, forcedCreation);
    }

    // Every time the screen size is changed we calculate its dpi to validate it on the step
    myBindings.bind(myScreenDpi, new DoubleExpression(myScreenResolutionWidth, myScreenResolutionHeight, myDiagonalScreenSize) {
      @NotNull
      @Override
      public Double get() {
        // The diagonal DPI will be somewhere in between the X and Y dpi if they differ
        return calculateDpi(myScreenResolutionWidth.get(),myScreenResolutionHeight.get(), myDiagonalScreenSize.get());
      }
    });

    Software software = new Software();
    software.setLiveWallpaperSupport(true);
    software.setGlVersion("2.0");

    mySoftware.setValue(software);
    myManufacturer.set("User");
    myProvider = provider;
  }


  /**
   * Initialize a reasonable set of default values (based on the Nexus 5)
   */
  private void initDefaultValues() {
    myName.set(getUniqueId(null));
    myWizardPanelDescription.set("Create a new hardware profile by selecting hardware features below.");
    myDiagonalScreenSize.set(5.0);
    myScreenResolutionWidth.set(1080);
    myScreenResolutionHeight.set(1920);
    myRamStorage.set(new Storage(2, Storage.Unit.GiB));

    myHasHardwareButtons.set(false);
    myHasHardwareKeyboard.set(false);
    myNavigation.setValue(Navigation.NONAV);

    mySupportsPortrait.set(true);
    mySupportsLandscape.set(true);

    myHasFrontCamera.set(true);
    myHasBackCamera.set(true);

    myHasAccelerometer.set(true);
    myHasGyroscope.set(true);
    myHasGps.set(true);
    myHasProximitySensor.set(true);
  }

  /**
   * Initialize our state to match the device we've been given as a starting point
   */
  private void initFromDevice(Device device, boolean forceCreation) {
    myWizardPanelDescription.set(device.getDisplayName() + " (Edited)");
    myName.set((forceCreation) ? getUniqueId(device.getDisplayName() + " (Edited)") : device.getDisplayName());
    String tagId = device.getTagId();
    if (tagId != null) {
      for (IdDisplay tag : ALL_TAGS) {
        if (tag.getId().equals(tagId)) {
          myDeviceType.setValue(tag);
          break;
        }
      }
    }

    for (Map.Entry<String, String> entry : device.getBootProps().entrySet()) {
      myBuilder.addBootProp(entry.getKey(), entry.getValue());
    }

    Hardware defaultHardware = device.getDefaultHardware();
    Screen screen = defaultHardware.getScreen();

    myDiagonalScreenSize.set(screen.getDiagonalLength());
    myScreenResolutionWidth.set(screen.getXDimension());
    myScreenResolutionHeight.set(screen.getYDimension());
    myRamStorage.set(AvdWizardConstants.getDefaultRam(defaultHardware));
    myHasHardwareButtons.set(defaultHardware.getButtonType() == ButtonType.HARD);
    myHasHardwareKeyboard.set(defaultHardware.getKeyboard() != Keyboard.NOKEY);
    myNavigation.setValue(defaultHardware.getNav());

    List<State> states = device.getAllStates();

    for (State state : states) {
      if (state.getOrientation().equals(ScreenOrientation.PORTRAIT)) {
        mySupportsPortrait.set(true);
      }
      if (state.getOrientation().equals(ScreenOrientation.LANDSCAPE)) {
        mySupportsLandscape.set(true);
      }
    }

    myHasFrontCamera.set(defaultHardware.getCamera(CameraLocation.FRONT) != null);
    myHasBackCamera.set(defaultHardware.getCamera(CameraLocation.BACK) != null);

    myHasAccelerometer.set(defaultHardware.getSensors().contains(Sensor.ACCELEROMETER));
    myHasGyroscope.set(defaultHardware.getSensors().contains(Sensor.GYROSCOPE));
    myHasGps.set(defaultHardware.getSensors().contains(Sensor.GPS));
    myHasProximitySensor.set(defaultHardware.getSensors().contains(Sensor.PROXIMITY_SENSOR));

    File skinFile = AvdEditWizard.resolveSkinPath(defaultHardware.getSkinFile(), null);
    myCustomSkinFile.setValue((skinFile == null) ? AvdWizardConstants.NO_SKIN : skinFile);

    myIsTv.set(HardwareConfigHelper.isTv(device));
    myIsTv.set(HardwareConfigHelper.isWear(device));
    myIsTv.set(device.isScreenRound());
    myScreenChinSize.set(device.getChinSize());
  }


  @Override
  protected void handleFinished() {
    Device device = buildDevice();
      DeviceManagerConnection.getDefaultDeviceManagerConnection().createOrEditDevice(device);
      myProvider.refreshDevices();
      myProvider.setDevice(device);
  }

  /**
   * Once we finish editing the device, we set it to its final configuration
   */
  @NotNull
  private Device buildDevice() {
    String deviceName = myName.get();
    myBuilder.setName(deviceName);
    myBuilder.setId(deviceName);
    myBuilder.addSoftware(mySoftware.getValue());
    myBuilder.setManufacturer(myManufacturer.get());
    IdDisplay tag = myDeviceType.getValueOrNull();
    myBuilder.setTagId((SystemImage.DEFAULT_TAG.equals(tag) || tag == null) ? null : tag.getId());
    List<State> states = generateStates(buildHardware());
    myBuilder.addAllState(states);
    return myBuilder.build();
  }

  /**
   * Constructs an instance of {@link Hardware} based on a reasonable set of defaults and user input.
   */
  private Hardware buildHardware() {
    Hardware hardware = new Hardware();

    hardware.addNetwork(Network.BLUETOOTH);
    hardware.addNetwork(Network.WIFI);
    hardware.addNetwork(Network.NFC);

    hardware.addSensor(Sensor.BAROMETER);
    hardware.addSensor(Sensor.COMPASS);
    hardware.addSensor(Sensor.LIGHT_SENSOR);

    hardware.setHasMic(true);
    hardware.addInternalStorage(new Storage(4, Storage.Unit.GiB));
    hardware.setCpu("Generic CPU");
    hardware.setGpu("Generic GPU");

    hardware.addAllSupportedAbis(EnumSet.allOf(Abi.class));

    hardware.setChargeType(PowerType.BATTERY);

    if (myHasAccelerometer.get()) {
      hardware.addSensor(Sensor.ACCELEROMETER);
    }

    if (myHasGyroscope.get()) {
      hardware.addSensor(Sensor.GYROSCOPE);
    }

    if (myHasGps.get()) {
      hardware.addSensor(Sensor.GPS);
    }

    if (myHasProximitySensor.get()) {
      hardware.addSensor(Sensor.PROXIMITY_SENSOR);
    }

    if (myHasBackCamera.get()) {
      hardware.addCamera(new Camera(CameraLocation.BACK, true, true));
    }

    if (myHasFrontCamera.get()) {
      hardware.addCamera(new Camera(CameraLocation.FRONT, true, true));
    }

    if (myHasHardwareKeyboard.get()) {
      hardware.setKeyboard(Keyboard.QWERTY);
    }
    else {
      hardware.setKeyboard(Keyboard.NOKEY);
    }

    if (myHasHardwareButtons.get()) {
      hardware.setButtonType(ButtonType.HARD);
    }
    else {
      hardware.setButtonType(ButtonType.SOFT);
    }

    if (myNavigation.getValueOrNull() != null) {
      hardware.setNav(myNavigation.getValue());
    }

    if (myCustomSkinFile.getValueOrNull() != null) {
      hardware.setSkinFile(myCustomSkinFile.getValue());
    }

    hardware.setRam(myRamStorage.get());

    hardware.setScreen(createScreen());

    return hardware;
  }


  /**
   * Create a screen based on a reasonable set of defaults and user input.
   */
  private Screen createScreen() {
    Screen screen = new Screen();
    screen.setMultitouch(Multitouch.JAZZ_HANDS);
    screen.setMechanism(TouchScreen.FINGER);
    screen.setScreenType(ScreenType.CAPACITIVE);

    screen.setDiagonalLength(myDiagonalScreenSize.get());
    screen.setSize(getScreenSize(myDiagonalScreenSize.get()));

    screen.setXDimension(myScreenResolutionWidth.get());
    screen.setYDimension(myScreenResolutionHeight.get());

    screen.setRatio(getScreenRatio(myScreenResolutionWidth.get(), myScreenResolutionHeight.get()));

    Double dpi = myScreenDpi.get();
    if (dpi <= 0) {
      dpi = calculateDpi(myScreenResolutionWidth.get(), myScreenResolutionHeight.get(), myDiagonalScreenSize.get());
    }

    dpi = Math.round(dpi * 100) / 100.0;
    screen.setYdpi(dpi);
    screen.setXdpi(dpi);

    if (myIsTv.get()) {
      // TVs can have varied densities, including much lower than the normal range.
      // Set the density explicitly in that case.
      screen.setPixelDensity(Density.TV);
    }
    else {
      screen.setPixelDensity(getScreenDensity(dpi));
    }

    return screen;
  }

  /**
   * Get the resource bucket value that corresponds to the given size in inches.
   * @param diagonalSize Diagonal Screen size in inches.
   *                     If null, a default diagonal size is used
   */
  @NotNull
  public static ScreenSize getScreenSize(@Nullable Double diagonalSize) {
    if (diagonalSize == null) {
      return ScreenSize.NORMAL;
    }


    /**
     * Density-independent pixel (dp) : The density-independent pixel is
     * equivalent to one physical pixel on a 160 dpi screen,
     * which is the baseline density assumed by the system for a
     * "medium" density screen.
     *
     * Taken from http://developer.android.com/guide/practices/screens_support.html
     */
    double diagonalDp = 160.0 * diagonalSize;

    // Set the Screen Size
    if (diagonalDp >= 1200) {
      return ScreenSize.XLARGE;
    }
    else if (diagonalDp >= 800) {
      return ScreenSize.LARGE;
    }
    else if (diagonalDp >= 568) {
      return ScreenSize.NORMAL;
    }
    else {
      return ScreenSize.SMALL;
    }
  }

  /**
   * Calculate the screen ratio. Beyond a 5:3 ratio is considered "long"
   */
  @NotNull
  public static ScreenRatio getScreenRatio(int width, int height) {
    int longSide = Math.max(width, height);
    int shortSide = Math.min(width, height);

    // Above a 5:3 ratio is "long"
    if (((double)longSide) / shortSide >= 5.0 / 3) {
      return ScreenRatio.LONG;
    }
    else {
      return ScreenRatio.NOTLONG;
    }
  }

  /**
   * Calculate the density resource bucket for the given dots-per-inch
   */
  @NotNull
  public static Density getScreenDensity(double dpi) {
    double minDifference = Double.MAX_VALUE;
    Density bucket = Density.MEDIUM;
    for (Density d : Density.values()) {
      if (!d.isValidValueForDevice()) {
        continue;
      }
      // Search for the density enum whose value is closest to the density of our device.
      double difference = Math.abs(d.getDpiValue() - dpi);
      if (difference < minDifference) {
        minDifference = Math.abs(d.getDpiValue() - dpi);
        bucket = d;
      }
    }
    return bucket;
  }
  private static String getUniqueId(@Nullable String id) {

    return DeviceManagerConnection.getDefaultDeviceManagerConnection().getUniqueId(id);
  }

  @Nullable
  private static State createState(ScreenOrientation orientation, Hardware hardware, boolean hasHardwareKeyboard) {
    State state = null;
    String name = "";
    String description = "";

    if (orientation == ScreenOrientation.LANDSCAPE) {
      name = "Landscape";
      description = "The device in landscape orientation";
      state = new State();
    }
    else if (orientation == ScreenOrientation.PORTRAIT) {
      name = "Portrait";
      description = "The device in portrait orientation";
      state = new State();
    }

    if (state != null) {
      if (hasHardwareKeyboard) {
        name += " with keyboard";
        description += " with a keyboard open";
        state.setKeyState(KeyboardState.EXPOSED);
      }
      else {
        if (hardware.getKeyboard() != null && hardware.getKeyboard().equals(Keyboard.NOKEY)) {
          state.setKeyState(KeyboardState.SOFT);
        }
        else {
          state.setKeyState(KeyboardState.HIDDEN);
        }
      }
      state.setName(name);
      state.setHardware(hardware);
      state.setOrientation(orientation);
      state.setDescription(description);
      state.setNavState(hardware.getNav().equals(Navigation.NONAV) ? NavigationState.HIDDEN : NavigationState.EXPOSED);
    }

    return state;
  }

  private List<State> generateStates(Hardware hardware) {
    List<State> states = Lists.newArrayListWithExpectedSize(4);

    if (mySupportsPortrait.get()) {
      states.add(createState(ScreenOrientation.PORTRAIT, hardware, false));
    }

    if (mySupportsLandscape.get()) {
      states.add(createState(ScreenOrientation.LANDSCAPE, hardware, false));
    }

    if (myHasHardwareKeyboard.get()) {
      if (mySupportsPortrait.get()) {
        states.add(createState(ScreenOrientation.PORTRAIT, hardware, true));
      }

      if (mySupportsLandscape.get()) {
        states.add(createState(ScreenOrientation.LANDSCAPE, hardware, true));
      }
    }

    // We've added states in the order of most common to least common, so let's mark the first one as default
    states.get(0).setDefaultState(true);
    return states;
  }

  private static double calculateDpi(double screenResolutionWidth, double screenResolutionHeight, double diagonalScreenSize){
    // Calculate diagonal resolution in pixels using the Pythagorean theorem: Dp = (pixelWidth^2 + pixelHeight^2)^1/2
    double diagonalPixelResolution = Math.sqrt(Math.pow(screenResolutionWidth, 2) + Math.pow(screenResolutionHeight, 2));
    // Calculate dos per inch: DPI = Dp / diagonalInchSize
    return diagonalPixelResolution / diagonalScreenSize;
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
  }
}
