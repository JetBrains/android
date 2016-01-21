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
import com.android.repository.io.FileOpUtils;
import com.android.resources.Keyboard;
import com.android.resources.Navigation;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.devices.*;
import com.android.sdklib.repositoryv2.IdDisplay;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.core.*;
import com.android.tools.idea.ui.properties.expressions.double_.DoubleExpression;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * Data class containing all properties needed to build a device.
 */
public final class AvdDeviceData implements Disposable{
  private StringProperty myName = new StringValueProperty();
  private OptionalProperty<IdDisplay> myDeviceType = new OptionalValueProperty<IdDisplay>();
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

  private OptionalProperty<Software> mySoftware = new OptionalValueProperty<Software>();

  private BindingsManager myBindings = new BindingsManager();

  public AvdDeviceData(@Nullable Device device, boolean forcedCreation) {
    Software software = new Software();
    software.setLiveWallpaperSupport(true);
    software.setGlVersion("2.0");

    mySoftware.setValue(software);
    myManufacturer.set("User");

    if (device == null) {
      initDefaultValues();
    }
    else {
      getValuesFromDevice(device, forcedCreation);
    }

    // Every time the screen size is changed we calculate its dpi to validate it on the step
    DoubleExpression dpiExpression =
      new DoubleExpression(myScreenResolutionWidth, myScreenResolutionHeight, myDiagonalScreenSize) {
        @NotNull
        @Override
        public Double get() {
          // The diagonal DPI will be somewhere in between the X and Y dpi if they differ
          return AvdScreenData.calculateDpi(myScreenResolutionWidth.get(), myScreenResolutionHeight.get(),
                                            myDiagonalScreenSize.get());
        }
      };
    myBindings.bind(myScreenDpi, dpiExpression);
  }

  private static String getUniqueId(@Nullable String id) {
    return DeviceManagerConnection.getDefaultDeviceManagerConnection().getUniqueId(id);
  }

  public StringProperty name() {
    return myName;
  }

  public OptionalProperty<IdDisplay> deviceType() {
    return myDeviceType;
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

  /**
   * Match our property values to the device we've been given
   */
  private void getValuesFromDevice(Device device, boolean forceCreation) {
    myName.set((forceCreation) ? getUniqueId(device.getDisplayName() + " (Edited)") : device.getDisplayName());
    String tagId = device.getTagId();
    if (tagId != null) {
      for (IdDisplay tag : AvdWizardConstants.ALL_TAGS) {
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
     * This is maxed out at {@link AvdWizardConstants.MAX_RAM_MB}, for more information read
     * {@link AvdWizardConstants#getDefaultRam(Hardware)}
     */
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

    File skinFile = AvdEditWizard.resolveSkinPath(defaultHardware.getSkinFile(), null, FileOpUtils.create());
    myCustomSkinFile.setValue((skinFile == null) ? AvdWizardConstants.NO_SKIN : skinFile);

    myIsTv.set(HardwareConfigHelper.isTv(device));
    myIsWear.set(HardwareConfigHelper.isWear(device));
    myIsScreenRound.set(device.isScreenRound());
    myScreenChinSize.set(device.getChinSize());
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
  }
}
