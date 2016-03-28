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

import com.android.SdkConstants;
import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.resources.Keyboard;
import com.android.resources.Navigation;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.devices.*;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.tools.idea.ui.properties.core.*;
import com.android.tools.idea.ui.properties.expressions.bool.BooleanExpression;
import com.android.tools.idea.ui.properties.expressions.double_.DoubleExpression;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * Data class containing all properties needed to build a device.
 */
public final class AvdDeviceData {
  private StringProperty myName = new StringValueProperty();
  private OptionalProperty<IdDisplay> myDeviceType = new OptionalValueProperty<IdDisplay>();
  private StringProperty myManufacturer = new StringValueProperty();
  private StringProperty myTagId = new StringValueProperty();

  private DoubleProperty myDiagonalScreenSize = new DoubleValueProperty();
  private IntProperty myScreenResolutionWidth = new IntValueProperty();
  private IntProperty myScreenResolutionHeight = new IntValueProperty();

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
  private State myDefaultState;
  private File myLastSkinFolder;
  private Dimension myLastSkinDimension;

  private OptionalProperty<Software> mySoftware = new OptionalValueProperty<Software>();

  private DoubleExpression myScreenDpi =
    // Every time the screen size is changed we calculate its dpi to validate it on the step
    new DoubleExpression(myScreenResolutionWidth, myScreenResolutionHeight, myDiagonalScreenSize) {
      @NotNull
      @Override
      public Double get() {
        // The diagonal DPI will be somewhere in between the X and Y dpi if they differ
        return AvdScreenData.calculateDpi(myScreenResolutionWidth.get(), myScreenResolutionHeight.get(), myDiagonalScreenSize.get());
      }
    };

  private ObservableBool mySkinSizeIsCompatible = new BooleanExpression(myScreenResolutionWidth, myScreenResolutionHeight, myCustomSkinFile) {
    @NotNull
    @Override
    public Boolean get() {
      if (!myCustomSkinFile.get().isPresent()) {
        return true;
      }

      Dimension dimension = getSkinDimension(myCustomSkinFile.getValueOrNull());
      return dimension == null ||
             (dimension.getWidth() >= myScreenResolutionWidth.get() &&
              dimension.getHeight() >= myScreenResolutionHeight.get());
    }
  };

  public AvdDeviceData() {
    Software software = new Software();
    software.setLiveWallpaperSupport(true);
    software.setGlVersion("2.0");

    mySoftware.setValue(software);
    myManufacturer.set("User");

    initDefaultValues();
  }

  /**
   * @param device Optional source device from which to derive values from, if present
   */
  public AvdDeviceData(@Nullable Device device) {
    this();
    if (device != null) {
      updateValuesFromDevice(device);
    }
  }

  private static String getUniqueId(@Nullable String id) {
    return DeviceManagerConnection.getDefaultDeviceManagerConnection().getUniqueId(id);
  }

  public void setUniqueName(@NotNull String name) {
    myName.set(getUniqueId(name));
  }

  /**
   * Consider using {@link #setUniqueName(String)} instead of modifying this value directly, if you
   * need to ensure that an initial name is unique across devices.
   */
  @NotNull
  public StringProperty name() {
    return myName;
  }

  @NotNull
  public OptionalProperty<IdDisplay> deviceType() {
    return myDeviceType;
  }

  @NotNull
  public StringProperty manufacturer() {
    return myManufacturer;
  }

  @NotNull
  public StringProperty tagId() {
    return myTagId;
  }

  @NotNull
  public DoubleProperty diagonalScreenSize() {
    return myDiagonalScreenSize;
  }

  @NotNull
  public IntProperty screenResolutionWidth() {
    return myScreenResolutionWidth;
  }

  @NotNull
  public IntProperty screenResolutionHeight() {
    return myScreenResolutionHeight;
  }

  @NotNull
  public ObservableDouble screenDpi() {
    return myScreenDpi;
  }

  @NotNull
  public ObjectProperty<Storage> ramStorage() {
    return myRamStorage;
  }

  @NotNull
  public BoolProperty hasHardwareButtons() {
    return myHasHardwareButtons;
  }

  @NotNull
  public BoolProperty hasHardwareKeyboard() {
    return myHasHardwareKeyboard;
  }

  @NotNull
  public OptionalProperty<Navigation> navigation() {
    return myNavigation;
  }

  @NotNull
  public BoolProperty supportsLandscape() {
    return mySupportsLandscape;
  }

  @NotNull
  public BoolProperty supportsPortrait() {
    return mySupportsPortrait;
  }

  @NotNull
  public BoolProperty hasFrontCamera() {
    return myHasFrontCamera;
  }

  @NotNull
  public BoolProperty hasBackCamera() {
    return myHasBackCamera;
  }

  @NotNull
  public BoolProperty hasAccelerometer() {
    return myHasAccelerometer;
  }

  @NotNull
  public BoolProperty hasGyroscope() {
    return myHasGyroscope;
  }

  @NotNull
  public BoolProperty hasGps() {
    return myHasGps;
  }

  @NotNull
  public BoolProperty hasProximitySensor() {
    return myHasProximitySensor;
  }

  @NotNull
  public OptionalProperty<Software> software() {
    return mySoftware;
  }

  @NotNull
  public OptionalProperty<File> customSkinFile() {
    return myCustomSkinFile;
  }

  @NotNull
  public BoolProperty isTv() {
    return myIsTv;
  }

  @NotNull
  public BoolProperty isWear() {
    return myIsWear;
  }

  @NotNull
  public BoolProperty isScreenRound() {
    return myIsScreenRound;
  }

  @NotNull
  public IntProperty screenChinSize() {
    return myScreenChinSize;
  }

  @NotNull
  public ObservableBool compatibleSkinSize() {
    return mySkinSizeIsCompatible;
  }

  /**
   * Initialize a reasonable set of default values (based on the Nexus 5)
   */
  private void initDefaultValues() {
    myName.set(getUniqueId(null));
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

  @Nullable
  private Dimension getSkinDimension(@Nullable File skinFolder) {
    if (!FileUtil.filesEqual(skinFolder, myLastSkinFolder)) {
      myLastSkinDimension = computeSkinDimension(skinFolder);
      myLastSkinFolder = skinFolder;
    }
    return myLastSkinDimension;
  }

  @Nullable
  private static Dimension computeSkinDimension(@Nullable File skinFolder) {
    if (skinFolder == null || FileUtil.filesEqual(skinFolder, AvdWizardUtils.NO_SKIN)) {
      return null;
    }
    File skinLayoutFile = new File(skinFolder, SdkConstants.FN_SKIN_LAYOUT);
    if (!skinLayoutFile.isFile()) {
      return null;
    }
    FileOp fop = FileOpUtils.create();
    SkinLayoutDefinition skin = SkinLayoutDefinition.parseFile(skinLayoutFile, fop);
    if (skin == null) {
      return null;
    }
    int height = StringUtil.parseInt(skin.get("parts.device.display.height"), -1);
    int width = StringUtil.parseInt(skin.get("parts.device.display.width"), -1);
    if (height <= 0 || width <= 0) {
      return null;
    }
    return new Dimension(width, height);
  }

  public void updateValuesFromDevice(@NotNull Device device) {
    myName.set(device.getDisplayName());
    String tagId = device.getTagId();
    if (myTagId.get().isEmpty()) {
      myTagId.set(SystemImage.DEFAULT_TAG.getId());
      myDeviceType.setValue(SystemImage.DEFAULT_TAG);
    }
    else {
      for (IdDisplay tag : AvdWizardUtils.ALL_DEVICE_TAGS) {
        if (tag.getId().equals(tagId)) {
          myDeviceType.setValue(tag);
          break;
        }
      }
    }
    Hardware defaultHardware = device.getDefaultHardware();
    Screen screen = defaultHardware.getScreen();

    myDiagonalScreenSize.set(screen.getDiagonalLength());
    myScreenResolutionWidth.set(screen.getXDimension());
    myScreenResolutionHeight.set(screen.getYDimension());
    /**
     * This is maxed out at {@link AvdWizardUtils.MAX_RAM_MB}, for more information read
     * {@link AvdWizardUtils#getDefaultRam(Hardware)}
     */
    myRamStorage.set(AvdWizardUtils.getDefaultRam(defaultHardware));
    myHasHardwareButtons.set(defaultHardware.getButtonType() == ButtonType.HARD);
    myHasHardwareKeyboard.set(defaultHardware.getKeyboard() != Keyboard.NOKEY);
    myNavigation.setValue(defaultHardware.getNav());

    List<State> states = device.getAllStates();

    mySupportsPortrait.set(false);
    mySupportsLandscape.set(false);
    for (State state : states) {
      if (state.isDefaultState()) {
        myDefaultState = state;
      }
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

    File skinFile = AvdWizardUtils.resolveSkinPath(defaultHardware.getSkinFile(), null, FileOpUtils.create());
    myCustomSkinFile.setValue((skinFile == null) ? AvdWizardUtils.NO_SKIN : skinFile);

    myIsTv.set(HardwareConfigHelper.isTv(device));
    myIsWear.set(HardwareConfigHelper.isWear(device));
    myIsScreenRound.set(device.isScreenRound());
    myScreenChinSize.set(device.getChinSize());
  }

  @SuppressWarnings("SuspiciousNameCombination") // We sometimes deliberately swap x/width y/height relationships depending on orientation
  @NotNull
  public Dimension getDeviceScreenDimension() {
    int width = myScreenResolutionWidth.get();
    int height = myScreenResolutionHeight.get();
    ScreenOrientation orientation = getDefaultDeviceOrientation();

    assert width > 0 && height > 0;

    // compute width and height to take orientation into account.
    int finalWidth, finalHeight;

    // Landscape should always be longer than taller; portrait taller than longer
    if (orientation == ScreenOrientation.LANDSCAPE) {
      finalWidth = Math.max(width, height);
      finalHeight = Math.min(width, height);
    }
    else {
      finalWidth = Math.min(width, height);
      finalHeight = Math.max(width, height);
    }
    return new Dimension(finalWidth, finalHeight);
  }

  /**
   * Going from the most common to the default case, return the {@link AvdDeviceData} instance
   * default orientation
   */
  @NotNull
  public ScreenOrientation getDefaultDeviceOrientation() {
    if (myDefaultState != null && myDefaultState.getOrientation() == ScreenOrientation.LANDSCAPE && mySupportsLandscape.get()) {
      return ScreenOrientation.LANDSCAPE;
    }

    return (mySupportsPortrait.get())
           ? ScreenOrientation.PORTRAIT
           : (mySupportsLandscape.get()) ? ScreenOrientation.LANDSCAPE : ScreenOrientation.SQUARE;
  }
}
