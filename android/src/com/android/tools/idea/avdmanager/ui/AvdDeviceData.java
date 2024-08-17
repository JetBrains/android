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
package com.android.tools.idea.avdmanager.ui;

import static com.google.common.collect.Comparators.min;
import static java.util.Comparator.comparing;

import com.android.SdkConstants;
import com.android.resources.Density;
import com.android.resources.Keyboard;
import com.android.resources.Navigation;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.SystemImageTags;
import com.android.sdklib.devices.ButtonType;
import com.android.sdklib.devices.CameraLocation;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Hardware;
import com.android.sdklib.devices.Screen;
import com.android.sdklib.devices.Sensor;
import com.android.sdklib.devices.Software;
import com.android.sdklib.devices.State;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.repository.IdDisplay;
import com.android.tools.idea.avdmanager.DeviceManagerConnection;
import com.android.tools.idea.avdmanager.SkinLayoutDefinition;
import com.android.tools.idea.avdmanager.SkinUtils;
import com.android.tools.idea.avdmanager.SystemImageDescription;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.DoubleProperty;
import com.android.tools.idea.observable.core.DoubleValueProperty;
import com.android.tools.idea.observable.core.IntProperty;
import com.android.tools.idea.observable.core.IntValueProperty;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObjectValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.ObservableDouble;
import com.android.tools.idea.observable.core.OptionalProperty;
import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.observable.expressions.bool.BooleanExpression;
import com.android.tools.idea.observable.expressions.double_.DoubleExpression;
import com.android.tools.preview.config.Densities;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import java.awt.Dimension;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Data class containing all properties needed to build a device.
 */
public final class AvdDeviceData {
  private final StringProperty myName = new StringValueProperty();
  private final OptionalProperty<IdDisplay> myDeviceType = new OptionalValueProperty<>();
  private final StringProperty myManufacturer = new StringValueProperty();
  private final StringProperty myTagId = new StringValueProperty();
  private final StringProperty myDeviceId = new StringValueProperty();

  private final DoubleProperty myDiagonalScreenSize = new DoubleValueProperty();
  private final IntProperty myScreenResolutionWidth = new IntValueProperty();
  private final IntProperty myScreenResolutionHeight = new IntValueProperty();
  private final IntProperty myScreenFoldedXOffset = new IntValueProperty();
  private final IntProperty myScreenFoldedYOffset = new IntValueProperty();
  private final IntProperty myScreenFoldedWidth = new IntValueProperty();
  private final IntProperty myScreenFoldedHeight = new IntValueProperty();
  private final IntProperty myScreenFoldedXOffset2 = new IntValueProperty();
  private final IntProperty myScreenFoldedYOffset2 = new IntValueProperty();
  private final IntProperty myScreenFoldedWidth2 = new IntValueProperty();
  private final IntProperty myScreenFoldedHeight2 = new IntValueProperty();
  private final IntProperty myScreenFoldedXOffset3 = new IntValueProperty();
  private final IntProperty myScreenFoldedYOffset3 = new IntValueProperty();
  private final IntProperty myScreenFoldedWidth3 = new IntValueProperty();
  private final IntProperty myScreenFoldedHeight3 = new IntValueProperty();

  private final ObjectProperty<Storage> myRamStorage = new ObjectValueProperty<>(new Storage(0, Storage.Unit.MiB));

  private final BoolProperty myHasHardwareButtons = new BoolValueProperty();
  private final BoolProperty myHasHardwareKeyboard = new BoolValueProperty();
  private final OptionalProperty<Navigation> myNavigation = new OptionalValueProperty<>();

  private final BoolProperty mySupportsLandscape = new BoolValueProperty();
  private final BoolProperty mySupportsPortrait = new BoolValueProperty();

  private final BoolProperty myHasBackCamera = new BoolValueProperty();
  private final BoolProperty myHasFrontCamera = new BoolValueProperty();

  private final BoolProperty myHasAccelerometer = new BoolValueProperty();
  private final BoolProperty myHasGyroscope = new BoolValueProperty();
  private final BoolProperty myHasGps = new BoolValueProperty();
  private final BoolProperty myHasProximitySensor = new BoolValueProperty();
  private final BoolProperty myHasSdCard = new BoolValueProperty();
  private final OptionalProperty<@NotNull Path> myCustomSkinFile = new OptionalValueProperty<>();
  private final OptionalProperty<File> mySelectedSnapshotFile = new OptionalValueProperty<>(new File(""));

  private final BoolValueProperty myIsAutomotive = new BoolValueProperty();
  private final BoolValueProperty myIsTv = new BoolValueProperty();
  private final BoolValueProperty myIsWear = new BoolValueProperty();
  private final BoolValueProperty myIsDesktop = new BoolValueProperty();
  private final BoolValueProperty myIsScreenRound = new BoolValueProperty();
  private final IntValueProperty myScreenChinSize = new IntValueProperty();
  private State myDefaultState;
  private File myLastSkinFolder;
  private Dimension myLastSkinDimension;
  private final ObjectProperty<Density> myDensity = new ObjectProperty<>() {
    private Density myDensity = Density.MEDIUM;

    @Override
    protected void setDirectly(@NotNull Density value) {
      myDensity = value;
    }

    @Override
    public @NotNull Density get() {
      if (myOriginalDpi == myScreenDpi.get()) {
        return myDensity;
      }
      else {
        return Densities.getScreenDensity(isTv().get(), myScreenDpi.get(), myScreenResolutionHeight.get());
      }
    }
  };

  private final OptionalProperty<Software> mySoftware = new OptionalValueProperty<>();

  private double myOriginalDpi;

  private final DoubleExpression myScreenDpi =
    // Every time the screen size is changed we calculate its dpi to validate it on the step
    new DoubleExpression(myScreenResolutionWidth, myScreenResolutionHeight, myDiagonalScreenSize) {
      @NotNull
      @Override
      public Double get() {
        // The diagonal DPI will be somewhere in between the X and Y dpi if they differ
        return AvdScreenData.calculateDpi(
          myScreenResolutionWidth.get(), myScreenResolutionHeight.get(), myDiagonalScreenSize.get(), myIsScreenRound.get());
      }
    };

  private final ObservableBool mySkinSizeIsCompatible =
    new BooleanExpression(myScreenResolutionWidth, myScreenResolutionHeight, myCustomSkinFile) {
      @NotNull
      @Override
      public Boolean get() {
        if (myCustomSkinFile.get().isEmpty()) {
          return true;
        }

        var dimension = getSkinDimension(myCustomSkinFile.get().map(Path::toFile).orElse(null));
        return dimension == null ||
               (dimension.getWidth() >= myScreenResolutionWidth.get() && dimension.getHeight() >= myScreenResolutionHeight.get()) ||
               (dimension.getHeight() >= myScreenResolutionWidth.get() && dimension.getWidth() >= myScreenResolutionHeight.get());
      }
    };

  public AvdDeviceData() {
    Software software = new Software();
    software.setLiveWallpaperSupport(true);
    software.setGlVersion("2.0");

    mySoftware.setValue(software);
    myManufacturer.set("User");

    initDefaultValues();

    myDiagonalScreenSize.addConstraint(value -> Math.max(0.1d, value));
    myScreenResolutionWidth.addConstraint(value -> Math.max(1, value));
    myScreenResolutionHeight.addConstraint(value -> Math.max(1, value));
  }

  /**
   * @param device Optional source device from which to derive values from, if present
   */
  public AvdDeviceData(@Nullable Device device, @Nullable SystemImageDescription systemImage) {
    this();
    if (device != null) {
      updateValuesFromDevice(device, systemImage);
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
  public StringProperty deviceId() {
    return myDeviceId;
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
  public IntProperty screenFoldedXOffset() {
    return myScreenFoldedXOffset;
  }

  @NotNull
  public IntProperty screenFoldedYOffset() {
    return myScreenFoldedYOffset;
  }

  @NotNull
  public IntProperty screenFoldedWidth() {
    return myScreenFoldedWidth;
  }

  @NotNull
  public IntProperty screenFoldedHeight() {
    return myScreenFoldedHeight;
  }

  @NotNull
  public IntProperty screenFoldedXOffset2() {
    return myScreenFoldedXOffset2;
  }

  @NotNull
  public IntProperty screenFoldedYOffset2() {
    return myScreenFoldedYOffset2;
  }

  @NotNull
  public IntProperty screenFoldedWidth2() {
    return myScreenFoldedWidth2;
  }

  @NotNull
  public IntProperty screenFoldedHeight2() {
    return myScreenFoldedHeight2;
  }

  @NotNull
  public IntProperty screenFoldedXOffset3() {
    return myScreenFoldedXOffset3;
  }

  @NotNull
  public IntProperty screenFoldedYOffset3() {
    return myScreenFoldedYOffset3;
  }

  @NotNull
  public IntProperty screenFoldedWidth3() {
    return myScreenFoldedWidth3;
  }

  @NotNull
  public IntProperty screenFoldedHeight3() {
    return myScreenFoldedHeight3;
  }

  @NotNull
  public BoolProperty isFoldable() {
    return new BoolValueProperty(myScreenFoldedHeight.get() > 0 && myScreenFoldedWidth.get() > 0);
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
  public BoolProperty getHasSdCard() {
    return myHasSdCard;
  }

  @NotNull
  public OptionalProperty<Software> software() {
    return mySoftware;
  }

  @NotNull OptionalProperty<@NotNull Path> customSkinFile() {
    return myCustomSkinFile;
  }

  @NotNull
  public OptionalProperty<File> selectedSnapshotFile() {
    return mySelectedSnapshotFile;
  }

  @NotNull
  public BoolProperty isAutomotive() {
    return myIsAutomotive;
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
  public BoolProperty isDesktop() { return myIsDesktop; }

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

  @NotNull
  public ObjectProperty<Density> density() {
    return myDensity;
  }

  /**
   * Initialize a reasonable set of default values (based on the Nexus 5)
   */
  private void initDefaultValues() {
    myName.set(getUniqueId(null));
    myDiagonalScreenSize.set(5.0);
    myScreenResolutionWidth.set(1080);
    myScreenResolutionHeight.set(1920);
    myOriginalDpi = AvdScreenData.calculateDpi(
      myScreenResolutionWidth.get(), myScreenResolutionHeight.get(), myDiagonalScreenSize.get(), myIsScreenRound.get());
    myScreenFoldedWidth.set(0);
    myScreenFoldedHeight.set(0);
    myScreenFoldedWidth2.set(0);
    myScreenFoldedWidth2.set(0);
    myScreenFoldedHeight2.set(0);
    myScreenFoldedWidth3.set(0);
    myScreenFoldedHeight3.set(0);
    myRamStorage.set(new Storage(2, Storage.Unit.GiB));

    myHasHardwareButtons.set(false);
    myHasHardwareKeyboard.set(false);
    myNavigation.setValue(Navigation.NONAV);

    mySupportsPortrait.set(true);
    mySupportsLandscape.set(true);
    myDensity.set(Density.MEDIUM);

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
    SkinLayoutDefinition skin = SkinLayoutDefinition.parseFile(skinLayoutFile);
    if (skin == null) {
      return null;
    }
    int height = StringUtil.parseInt(skin.getValue("parts.device.display.height"), -1);
    int width = StringUtil.parseInt(skin.getValue("parts.device.display.width"), -1);
    if (height <= 0 || width <= 0) {
      return null;
    }
    return new Dimension(width, height);
  }

  public void updateValuesFromDevice(@NotNull Device device, @Nullable SystemImageDescription systemImage) {
    myName.set(device.getDisplayName());
    String tagId = device.getTagId();
    if (myTagId.get().isEmpty()) {
      myTagId.set(SystemImageTags.DEFAULT_TAG.getId());
      myDeviceType.setValue(SystemImageTags.DEFAULT_TAG);
    }
    else {
      for (IdDisplay tag : AvdWizardUtils.ALL_DEVICE_TAGS) {
        if (tag.getId().equals(tagId)) {
          myDeviceType.setValue(tag);
          break;
        }
      }
    }
    myDeviceId.set(device.getId());
    Hardware defaultHardware = device.getDefaultHardware();
    Screen screen = defaultHardware.getScreen();

    myDiagonalScreenSize.set(screen.getDiagonalLength());
    myScreenResolutionWidth.set(screen.getXDimension());
    myScreenResolutionHeight.set(screen.getYDimension());
    myOriginalDpi = AvdScreenData.calculateDpi(
      myScreenResolutionWidth.get(), myScreenResolutionHeight.get(), myDiagonalScreenSize.get(), myIsScreenRound.get());
    myScreenFoldedXOffset.set(screen.getFoldedXOffset());
    myScreenFoldedYOffset.set(screen.getFoldedYOffset());
    myScreenFoldedWidth.set(screen.getFoldedWidth());
    myScreenFoldedHeight.set(screen.getFoldedHeight());
    myScreenFoldedXOffset2.set(screen.getFoldedXOffset2());
    myScreenFoldedYOffset2.set(screen.getFoldedYOffset2());
    myScreenFoldedWidth2.set(screen.getFoldedWidth2());
    myScreenFoldedHeight2.set(screen.getFoldedHeight2());
    myScreenFoldedXOffset3.set(screen.getFoldedXOffset3());
    myScreenFoldedYOffset3.set(screen.getFoldedYOffset3());
    myScreenFoldedWidth3.set(screen.getFoldedWidth3());
    myScreenFoldedHeight3.set(screen.getFoldedHeight3());
    myRamStorage.set(min(defaultHardware.getRam(), AvdWizardUtils.getMaxDefaultRam(device), comparing(Storage::getSize)));
    myHasHardwareButtons.set(defaultHardware.getButtonType() == ButtonType.HARD);
    myHasHardwareKeyboard.set(defaultHardware.getKeyboard() != Keyboard.NOKEY);
    myNavigation.setValue(defaultHardware.getNav());
    myDensity.set(defaultHardware.getScreen().getPixelDensity());
    myHasSdCard.set(defaultHardware.hasSdCard());

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

    myIsAutomotive.set(Device.isAutomotive(device));
    myIsTv.set(Device.isTv(device));
    myIsWear.set(Device.isWear(device));
    myIsDesktop.set(Device.isDesktop(device));
    myIsScreenRound.set(device.isScreenRound());
    myScreenChinSize.set(device.getChinSize());

    updateSkinFromDeviceAndSystemImage(device, systemImage);
  }

  public void updateSkinFromDeviceAndSystemImage(@NotNull Device device, @Nullable SystemImageDescription systemImage) {
    Hardware defaultHardware = device.getDefaultHardware();
    File defaultHardwareSkinFile = defaultHardware.getSkinFile();
    File skinFile = AvdWizardUtils.pathToUpdatedSkins(defaultHardwareSkinFile == null ? null : defaultHardwareSkinFile.toPath(),
                                                      systemImage);
    myCustomSkinFile.setValue(skinFile == null ? SkinUtils.noSkin() : skinFile.toPath());
  }

  @NotNull
  public Dimension getDeviceScreenDimension() {
    int width = myScreenResolutionWidth.get();
    int height = myScreenResolutionHeight.get();
    ScreenOrientation orientation = getDefaultDeviceOrientation();

    assert width > 0 && height > 0;

    // compute width and height to take orientation into account.
    int finalWidth, finalHeight;

    // Update dimensions according to the orientation setting.
    // Landscape should always be more wide than tall;
    // portrait should be more tall than wide.
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
    if (mySupportsLandscape.get()) {
      if ((myDefaultState != null && myDefaultState.getOrientation() == ScreenOrientation.LANDSCAPE) ||
          myIsTv.get() || myIsDesktop.get() || !mySupportsPortrait.get()) {
        return ScreenOrientation.LANDSCAPE;
      }
    }
    if (mySupportsPortrait.get()) {
      return ScreenOrientation.PORTRAIT;
    }
    return ScreenOrientation.SQUARE;
  }
}
