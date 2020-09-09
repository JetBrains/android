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
package com.android.tools.idea.deviceManager.avdmanager

import com.android.SdkConstants
import com.android.ide.common.rendering.HardwareConfigHelper
import com.android.repository.io.FileOpUtils
import com.android.resources.Density
import com.android.resources.Keyboard
import com.android.resources.Navigation
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRatio
import com.android.sdklib.devices.ButtonType
import com.android.sdklib.devices.CameraLocation
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.Sensor
import com.android.sdklib.devices.Software
import com.android.sdklib.devices.State
import com.android.sdklib.devices.Storage
import com.android.sdklib.repository.IdDisplay
import com.android.sdklib.repository.targets.SystemImage
import com.android.tools.idea.deviceManager.avdmanager.DeviceManagerConnection.Companion.defaultDeviceManagerConnection
import com.android.tools.idea.deviceManager.avdmanager.SkinLayoutDefinition.Companion.parseFile
import com.android.tools.idea.observable.core.BoolProperty
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.DoubleProperty
import com.android.tools.idea.observable.core.DoubleValueProperty
import com.android.tools.idea.observable.core.IntProperty
import com.android.tools.idea.observable.core.IntValueProperty
import com.android.tools.idea.observable.core.ObjectProperty
import com.android.tools.idea.observable.core.ObjectValueProperty
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.observable.core.ObservableDouble
import com.android.tools.idea.observable.core.OptionalProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.observable.expressions.bool.BooleanExpression
import com.android.tools.idea.observable.expressions.double_.DoubleExpression
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import java.awt.Dimension
import java.io.File
import kotlin.math.max
import kotlin.math.min

// TODO(qumeric): refactor to make idiomatic Kotlin
/**
 * Data class containing all properties needed to build a device.
 */
class AvdDeviceData() {
  private val myName: StringProperty = StringValueProperty()
  private val myDeviceType: OptionalProperty<IdDisplay> = OptionalValueProperty()
  private val myManufacturer: StringProperty = StringValueProperty()
  private val myTagId: StringProperty = StringValueProperty()
  private val myDeviceId: StringProperty = StringValueProperty()
  private val myDiagonalScreenSize: DoubleProperty = DoubleValueProperty()
  private val myScreenResolutionWidth: IntProperty = IntValueProperty()
  private val myScreenResolutionHeight: IntProperty = IntValueProperty()
  private val myScreenFoldedXOffset: IntProperty = IntValueProperty()
  private val myScreenFoldedYOffset: IntProperty = IntValueProperty()
  private val myScreenFoldedWidth: IntProperty = IntValueProperty()
  private val myScreenFoldedHeight: IntProperty = IntValueProperty()
  private val myRamStorage: ObjectProperty<Storage> = ObjectValueProperty(Storage(0, Storage.Unit.MiB))
  private val myHasHardwareButtons: BoolProperty = BoolValueProperty()
  private val myHasHardwareKeyboard: BoolProperty = BoolValueProperty()
  private val myNavigation: OptionalProperty<Navigation> = OptionalValueProperty()
  private val mySupportsLandscape: BoolProperty = BoolValueProperty()
  private val mySupportsPortrait: BoolProperty = BoolValueProperty()
  private val myNotLong: BoolProperty = BoolValueProperty()
  private val myHasBackCamera: BoolProperty = BoolValueProperty()
  private val myHasFrontCamera: BoolProperty = BoolValueProperty()
  private val myHasAccelerometer: BoolProperty = BoolValueProperty()
  private val myHasGyroscope: BoolProperty = BoolValueProperty()
  private val myHasGps: BoolProperty = BoolValueProperty()
  private val myHasProximitySensor: BoolProperty = BoolValueProperty()
  val hasSdCard: BoolProperty = BoolValueProperty()
  private val myCustomSkinFile: OptionalProperty<File> = OptionalValueProperty()
  private val mySelectedSnapshotFile: OptionalProperty<File> = OptionalValueProperty(File(""))
  private val myIsTv = BoolValueProperty()
  private val myIsWear = BoolValueProperty()
  private val myIsScreenRound = BoolValueProperty()
  private val myScreenChinSize = IntValueProperty()
  private var myDefaultState: State? = null
  private var myLastSkinFolder: File? = null
  private var myLastSkinDimension: Dimension? = null
  private val myDensity: ObjectProperty<Density> = ObjectValueProperty(Density.MEDIUM)
  private val mySoftware: OptionalProperty<Software> = OptionalValueProperty()
  private val myScreenDpi: DoubleExpression =  // Every time the screen size is changed we calculate its dpi to validate it on the step
    object : DoubleExpression(myScreenResolutionWidth, myScreenResolutionHeight, myDiagonalScreenSize) {
      override fun get(): Double {
        // The diagonal DPI will be somewhere in between the X and Y dpi if they differ
        return AvdScreenData.calculateDpi(
          myScreenResolutionWidth.get().toDouble(), myScreenResolutionHeight.get().toDouble(), myDiagonalScreenSize.get(),
          myIsScreenRound.get())
      }
    }
  private val mySkinSizeIsCompatible: ObservableBool = object : BooleanExpression(
    myScreenResolutionWidth, myScreenResolutionHeight, myCustomSkinFile) {
    override fun get(): Boolean {
      if (!myCustomSkinFile.get().isPresent) {
        return true
      }
      val dimension = getSkinDimension(myCustomSkinFile.valueOrNull)
      return dimension == null ||
             dimension.getWidth() >= myScreenResolutionWidth.get() && dimension.getHeight() >= myScreenResolutionHeight.get() ||
             dimension.getHeight() >= myScreenResolutionWidth.get() && dimension.getWidth() >= myScreenResolutionHeight.get()
    }
  }

  /**
   * @param device Optional source device from which to derive values from, if present
   */
  constructor(device: Device?, systemImage: SystemImageDescription?) : this() {
    device?.let { updateValuesFromDevice(it, systemImage) }
  }

  fun setUniqueName(name: String) {
    myName.set(getUniqueId(name))
  }

  /**
   * Consider using [setUniqueName] instead of modifying this value directly, if you
   * need to ensure that an initial name is unique across devices.
   */
  fun name(): StringProperty {
    return myName
  }

  fun deviceType(): OptionalProperty<IdDisplay> {
    return myDeviceType
  }

  fun manufacturer(): StringProperty {
    return myManufacturer
  }

  fun tagId(): StringProperty {
    return myTagId
  }

  fun deviceId(): StringProperty {
    return myDeviceId
  }

  fun diagonalScreenSize(): DoubleProperty {
    return myDiagonalScreenSize
  }

  fun screenResolutionWidth(): IntProperty {
    return myScreenResolutionWidth
  }

  fun screenResolutionHeight(): IntProperty {
    return myScreenResolutionHeight
  }

  fun screenDpi(): ObservableDouble {
    return myScreenDpi
  }

  fun screenFoldedXOffset(): IntProperty {
    return myScreenFoldedXOffset
  }

  fun screenFoldedYOffset(): IntProperty {
    return myScreenFoldedYOffset
  }

  fun screenFoldedWidth(): IntProperty {
    return myScreenFoldedWidth
  }

  fun screenFoldedHeight(): IntProperty {
    return myScreenFoldedHeight
  }

  val isFoldable: BoolProperty
    get() = BoolValueProperty(myScreenFoldedHeight.get() > 0 && myScreenFoldedWidth.get() > 0)

  fun ramStorage(): ObjectProperty<Storage> {
    return myRamStorage
  }

  fun hasHardwareButtons(): BoolProperty {
    return myHasHardwareButtons
  }

  fun hasHardwareKeyboard(): BoolProperty {
    return myHasHardwareKeyboard
  }

  fun navigation(): OptionalProperty<Navigation> {
    return myNavigation
  }

  fun supportsLandscape(): BoolProperty {
    return mySupportsLandscape
  }

  fun notLong(): BoolProperty {
    return myNotLong
  }

  fun supportsPortrait(): BoolProperty {
    return mySupportsPortrait
  }

  fun hasFrontCamera(): BoolProperty {
    return myHasFrontCamera
  }

  fun hasBackCamera(): BoolProperty {
    return myHasBackCamera
  }

  fun hasAccelerometer(): BoolProperty {
    return myHasAccelerometer
  }

  fun hasGyroscope(): BoolProperty {
    return myHasGyroscope
  }

  fun hasGps(): BoolProperty {
    return myHasGps
  }

  fun hasProximitySensor(): BoolProperty {
    return myHasProximitySensor
  }

  fun software(): OptionalProperty<Software> {
    return mySoftware
  }

  fun customSkinFile(): OptionalProperty<File> {
    return myCustomSkinFile
  }

  fun selectedSnapshotFile(): OptionalProperty<File> {
    return mySelectedSnapshotFile
  }

  val isTv: BoolProperty
    get() = myIsTv

  val isWear: BoolProperty
    get() = myIsWear

  val isScreenRound: BoolProperty
    get() = myIsScreenRound

  fun screenChinSize(): IntProperty {
    return myScreenChinSize
  }

  fun compatibleSkinSize(): ObservableBool {
    return mySkinSizeIsCompatible
  }

  fun density(): ObjectProperty<Density> {
    return myDensity
  }

  /**
   * Initialize a reasonable set of default values (based on the Nexus 5)
   */
  private fun initDefaultValues() {
    myName.set(getUniqueId(null))
    myDiagonalScreenSize.set(5.0)
    myScreenResolutionWidth.set(1080)
    myScreenResolutionHeight.set(1920)
    myScreenFoldedWidth.set(0)
    myScreenFoldedHeight.set(0)
    myRamStorage.set(Storage(2, Storage.Unit.GiB))
    myHasHardwareButtons.set(false)
    myHasHardwareKeyboard.set(false)
    myNavigation.value = Navigation.NONAV
    mySupportsPortrait.set(true)
    mySupportsLandscape.set(true)
    myNotLong.set(false)
    myDensity.set(Density.MEDIUM)
    myHasFrontCamera.set(true)
    myHasBackCamera.set(true)
    myHasAccelerometer.set(true)
    myHasGyroscope.set(true)
    myHasGps.set(true)
    myHasProximitySensor.set(true)
  }

  private fun getSkinDimension(skinFolder: File?): Dimension? {
    if (!FileUtil.filesEqual(skinFolder, myLastSkinFolder)) {
      myLastSkinDimension = computeSkinDimension(skinFolder)
      myLastSkinFolder = skinFolder
    }
    return myLastSkinDimension
  }

  fun updateValuesFromDevice(device: Device,
                             systemImage: SystemImageDescription?) {
    myName.set(device.displayName)
    val tagId = device.tagId
    if (myTagId.get().isEmpty()) {
      myTagId.set(SystemImage.DEFAULT_TAG.id)
      myDeviceType.setValue(SystemImage.DEFAULT_TAG)
    }
    else {
      for (tag in AvdWizardUtils.ALL_DEVICE_TAGS) {
        if (tag.id == tagId) {
          myDeviceType.value = tag
          break
        }
      }
    }
    myDeviceId.set(device.id)
    val defaultHardware = device.defaultHardware
    val screen = defaultHardware.screen
    myDiagonalScreenSize.set(screen.diagonalLength)
    myScreenResolutionWidth.set(screen.xDimension)
    myScreenResolutionHeight.set(screen.yDimension)
    myScreenFoldedXOffset.set(screen.foldedXOffset)
    myScreenFoldedYOffset.set(screen.foldedYOffset)
    myScreenFoldedWidth.set(screen.foldedWidth)
    myScreenFoldedHeight.set(screen.foldedHeight)
    /*
     * This is maxed out at {@link AvdWizardUtils.MAX_RAM_MB}, for more information read
     * {@link AvdWizardUtils#getDefaultRam(Hardware)}
     */myRamStorage.set(AvdWizardUtils.getDefaultRam(defaultHardware))
    myHasHardwareButtons.set(defaultHardware.buttonType == ButtonType.HARD)
    myHasHardwareKeyboard.set(defaultHardware.keyboard != Keyboard.NOKEY)
    myNavigation.value = defaultHardware.nav
    myDensity.set(defaultHardware.screen.pixelDensity)
    hasSdCard.set(defaultHardware.hasSdCard())
    val states = device.allStates
    mySupportsPortrait.set(false)
    mySupportsLandscape.set(false)
    for (state in states) {
      if (state.isDefaultState) {
        myDefaultState = state
      }
      if (state.orientation == ScreenOrientation.PORTRAIT) {
        mySupportsPortrait.set(true)
      }
      if (state.orientation == ScreenOrientation.LANDSCAPE) {
        mySupportsLandscape.set(true)
      }
      if (state.hardware.screen.ratio == ScreenRatio.NOTLONG) {
        myNotLong.set(true)
      }
    }
    myHasFrontCamera.set(defaultHardware.getCamera(CameraLocation.FRONT) != null)
    myHasBackCamera.set(defaultHardware.getCamera(CameraLocation.BACK) != null)
    myHasAccelerometer.set(defaultHardware.sensors.contains(Sensor.ACCELEROMETER))
    myHasGyroscope.set(defaultHardware.sensors.contains(Sensor.GYROSCOPE))
    myHasGps.set(defaultHardware.sensors.contains(Sensor.GPS))
    myHasProximitySensor.set(defaultHardware.sensors.contains(Sensor.PROXIMITY_SENSOR))
    myIsTv.set(HardwareConfigHelper.isTv(device))
    myIsWear.set(HardwareConfigHelper.isWear(device))
    myIsScreenRound.set(device.isScreenRound)
    myScreenChinSize.set(device.chinSize)
    updateSkinFromDeviceAndSystemImage(device, systemImage)
  }

  fun updateSkinFromDeviceAndSystemImage(device: Device, systemImage: SystemImageDescription?) {
    val defaultHardware = device.defaultHardware
    val skinFile = AvdWizardUtils.pathToUpdatedSkins(defaultHardware.skinFile, systemImage, FileOpUtils.create())
    myCustomSkinFile.value = skinFile ?: AvdWizardUtils.NO_SKIN
  } // The height and width are significantly different.
  // Landscape should always be more wide than tall;
  // portrait should be more tall than wide.
  // The device is 'not long': its width and height are
  // pretty similar. Accept the user's values directly.

  // compute width and height to take orientation into account.
  val deviceScreenDimension: Dimension
    get() {
      val width = myScreenResolutionWidth.get()
      val height = myScreenResolutionHeight.get()
      val orientation = defaultDeviceOrientation
      assert(width > 0 && height > 0)

      // compute width and height to take orientation into account.
      val finalWidth: Int
      val finalHeight: Int
      if (myNotLong.get()) {
        // The device is 'not long': its width and height are
        // pretty similar. Accept the user's values directly.
        finalWidth = width
        finalHeight = height
      }
      else {
        // The height and width are significantly different.
        // Landscape should always be more wide than tall;
        // portrait should be more tall than wide.
        if (orientation == ScreenOrientation.LANDSCAPE) {
          finalWidth = max(width, height)
          finalHeight = min(width, height)
        }
        else {
          finalWidth = min(width, height)
          finalHeight = max(width, height)
        }
      }
      return Dimension(finalWidth, finalHeight)
    }

  /**
   * Going from the most common to the default case, return the [AvdDeviceData] instance default orientation
   */
  val defaultDeviceOrientation: ScreenOrientation
    get() {
      if (myDefaultState != null && myDefaultState!!.orientation == ScreenOrientation.LANDSCAPE && mySupportsLandscape.get()) {
        return ScreenOrientation.LANDSCAPE
      }
      return if (mySupportsPortrait.get()) ScreenOrientation.PORTRAIT else if (mySupportsLandscape.get()) ScreenOrientation.LANDSCAPE else ScreenOrientation.SQUARE
    }

  companion object {
    private fun getUniqueId(id: String?): String = defaultDeviceManagerConnection.getUniqueId(id)

    private fun computeSkinDimension(skinFolder: File?): Dimension? {
      if (skinFolder == null || FileUtil.filesEqual(skinFolder, AvdWizardUtils.NO_SKIN)) {
        return null
      }
      val skinLayoutFile = File(skinFolder, SdkConstants.FN_SKIN_LAYOUT)
      if (!skinLayoutFile.isFile) {
        return null
      }
      val fop = FileOpUtils.create()
      val skin = parseFile( skinLayoutFile, fop) ?: return null
      val height = StringUtil.parseInt(skin.getValue("parts.device.display.height"), -1)
      val width = StringUtil.parseInt(skin.getValue("parts.device.display.width"), -1)
      return if (height <= 0 || width <= 0) null else Dimension(width, height)
    }
  }

  init {
    val software = Software().apply {
      setLiveWallpaperSupport(true)
      glVersion = "2.0"
    }
    mySoftware.value = software
    myManufacturer.set("User")
    initDefaultValues()
    myDiagonalScreenSize.addConstraint { value: Double? -> max(0.1, value!!) }
    myScreenResolutionWidth.addConstraint { value: Int? -> max(1, value!!) }
    myScreenResolutionHeight.addConstraint { value: Int? -> max(1, value!!) }
  }
}