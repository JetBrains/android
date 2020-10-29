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

import com.android.repository.Revision
import com.android.repository.io.FileOpUtils
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenSize
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.devices.Storage
import com.android.sdklib.internal.avd.AvdCamera
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdManager
import com.android.sdklib.internal.avd.AvdNetworkLatency
import com.android.sdklib.internal.avd.AvdNetworkSpeed
import com.android.sdklib.internal.avd.EmulatedProperties
import com.android.sdklib.internal.avd.GpuMode
import com.android.sdklib.internal.avd.HardwareProperties
import com.android.tools.idea.deviceManager.avdmanager.AvdScreenData.Companion.getScreenDensity
import com.android.tools.idea.deviceManager.avdmanager.DeviceManagerConnection.Companion.defaultDeviceManagerConnection
import com.android.tools.idea.deviceManager.avdmanager.emulator.emulatorSupportsVirtualScene
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.observable.core.BoolProperty
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObjectProperty
import com.android.tools.idea.observable.core.ObjectValueProperty
import com.android.tools.idea.observable.core.ObservableObject
import com.android.tools.idea.observable.core.ObservableString
import com.android.tools.idea.observable.core.OptionalProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.wizard.model.WizardModel
import com.google.common.base.Objects
import com.google.common.base.Strings
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * [WizardModel] containing useful configuration settings for defining an AVD image.
 *
 * See also [AvdDeviceData], which these options supplement.
 *
 * @param avdCreatedCallback a function to run after AVD was created (e.g. update some related UI)
 */
class AvdOptionsModel @JvmOverloads constructor(
  private val avdInfo: AvdInfo?,
  private val avdCreatedCallback: () -> Unit = {}
) : WizardModel() {
  /**
   * It is used to name the family of virtual hardware boards supported by the QEMU2 engines
   * (which is different from the one for the classic engines, called 'goldfish').
   */
  val useQemu2: BoolProperty = BoolValueProperty(true)
  val avdId: StringProperty = StringValueProperty()
  val avdDisplayName: StringProperty = StringValueProperty()
  val internalStorage: ObjectProperty<Storage> = ObjectValueProperty(EmulatedProperties.DEFAULT_INTERNAL_STORAGE)
  val selectedAvdOrientation: ObjectProperty<ScreenOrientation> = ObjectValueProperty(ScreenOrientation.PORTRAIT)
  val selectedAvdFrontCamera: ObjectProperty<AvdCamera> = ObjectValueProperty(AvdCamera.EMULATED)
  val selectedAvdBackCamera: ObjectProperty<AvdCamera>
  val hasDeviceFrame: BoolProperty = BoolValueProperty(true)
  val useExternalSdCard: BoolProperty = BoolValueProperty(false)
  val useBuiltInSdCard: BoolProperty = BoolValueProperty(true)
  val selectedNetworkSpeed: ObjectProperty<AvdNetworkSpeed> = ObjectValueProperty(EmulatedProperties.DEFAULT_NETWORK_SPEED)
  val selectedNetworkLatency: ObjectProperty<AvdNetworkLatency> = ObjectValueProperty(EmulatedProperties.DEFAULT_NETWORK_LATENCY)
  val systemImageName: StringProperty = StringValueProperty()
  val systemImageDetails: StringProperty = StringValueProperty()
  val cpuCoreCount: OptionalProperty<Int> = OptionalValueProperty(EmulatedProperties.RECOMMENDED_NUMBER_OF_CORES)
  val vmHeapStorage: ObjectProperty<Storage> = ObjectValueProperty(EmulatedProperties.DEFAULT_HEAP)
  val externalSdCardLocation: StringProperty = StringValueProperty()
  val sdCardStorage: OptionalProperty<Storage> = OptionalValueProperty(defaultSdSize)
  val useHostGpu: BoolProperty = BoolValueProperty(true)
  val coldBoot: BoolProperty = BoolValueProperty(false)
  val fastBoot: BoolProperty = BoolValueProperty(true)
  val chosenSnapshotBoot: BoolProperty = BoolValueProperty(false)
  val chosenSnapshotFile: StringProperty = StringValueProperty()
  val hostGpuMode: OptionalProperty<GpuMode> = OptionalValueProperty(GpuMode.AUTO)
  val enableHardwareKeyboard: BoolProperty = BoolValueProperty(true)
  val isInEditMode: BoolProperty = BoolValueProperty()
  val backupSkinFile: OptionalProperty<File> = OptionalValueProperty()
  val systemImage: OptionalProperty<SystemImageDescription> = OptionalValueProperty()
  val device: OptionalProperty<Device> = OptionalValueProperty()

  private val removePreviousAvd: BoolProperty = BoolValueProperty(true) // Assume 'rename', not 'duplicate'
  private var existingSdLocation: ObservableString = StringValueProperty()
  private var originalSdCard: ObservableObject<Storage>? = null
  var avdDeviceData: AvdDeviceData = AvdDeviceData()
    private set
  var createdAvd: AvdInfo? = null
    private set

  val avdLocation: String?
    get() = avdInfo?.dataFolderPath

  fun setAsCopy() {
    // Copying this AVD. Adjust its name.
    val originalName = avdDisplayName.get()
    var newName = "Copy_of_$originalName"
    var copyNum = 2
    while (AvdManagerConnection.getDefaultAvdManagerConnection().findAvdWithName(newName) && copyNum < 10000) {
      newName = "Copy_${copyNum}_of_$originalName"
      copyNum++
    }
    avdDisplayName.set(newName)
    // Don't remove the original AVD
    removePreviousAvd.set(false)
  }

  val isPlayStoreCompatible: Boolean
    get() = device.isPresent.get() && device.value.hasPlayStore() &&
            systemImage.isPresent.get() && systemImage.value.systemImage.hasPlayStore()

  fun minSdCardSize(): Storage = when {
    !useBuiltInSdCard.get() && !useExternalSdCard.get() -> zeroSdSize
    isPlayStoreCompatible -> minPlayStoreSdSize
    else -> minGeneralSdSize
  }

  fun minInternalMemSize(): Storage = if (isPlayStoreCompatible) minPlayStoreInternalMemSize else minGeneralInternalMemSize

  /**
   * Ensure that the SD card size and internal memory
   * size are large enough. (If a device is Play Store
   * enabled, a larger size is required.)
   */
  fun ensureMinimumMemory() {
    if (sdCardStorage.value.lessThan(minSdCardSize())) {
      sdCardStorage.value = minSdCardSize()
    }
    if (internalStorage.get().lessThan(minInternalMemSize())) {
      internalStorage.set(minInternalMemSize())
    }
  }

  fun useQemu2(): BoolProperty = useQemu2

  fun avdId(): StringProperty = avdId

  fun avdDisplayName(): StringProperty = avdDisplayName

  fun selectedAvdOrientation(): ObjectProperty<ScreenOrientation> = selectedAvdOrientation

  fun selectedFrontCamera(): ObjectProperty<AvdCamera> = selectedAvdFrontCamera

  fun selectedBackCamera(): ObjectProperty<AvdCamera> = selectedAvdBackCamera

  fun hasDeviceFrame(): BoolProperty = hasDeviceFrame

  fun selectedNetworkSpeed(): ObjectProperty<AvdNetworkSpeed> = selectedNetworkSpeed

  fun selectedNetworkLatency(): ObjectProperty<AvdNetworkLatency> = selectedNetworkLatency

  fun internalStorage(): ObjectProperty<Storage> = internalStorage

  fun useExternalSdCard(): BoolProperty = useExternalSdCard

  fun useBuiltInSdCard(): BoolProperty = useBuiltInSdCard

  fun systemImageName(): StringProperty = systemImageName

  fun systemImageDetails(): StringProperty = systemImageDetails

  fun vmHeapStorage(): ObjectProperty<Storage> = vmHeapStorage

  fun cpuCoreCount(): OptionalProperty<Int> = cpuCoreCount

  fun sdCardStorage(): OptionalProperty<Storage> = sdCardStorage

  fun externalSdCardLocation(): StringProperty = externalSdCardLocation

  fun useHostGpu(): BoolProperty = useHostGpu

  fun useColdBoot(): BoolProperty = coldBoot

  fun useFastBoot(): BoolProperty = fastBoot

  fun useChosenSnapshotBoot(): BoolProperty = chosenSnapshotBoot

  fun chosenSnapshotFile(): StringProperty = chosenSnapshotFile

  fun hostGpuMode(): OptionalProperty<GpuMode> = hostGpuMode

  fun enableHardwareKeyboard(): BoolProperty = enableHardwareKeyboard

  fun backupSkinFile(): OptionalProperty<File> = backupSkinFile

  fun device(): OptionalProperty<Device> = device

  fun systemImage(): OptionalProperty<SystemImageDescription> = systemImage

  private fun updateValuesWithAvdInfo(avdInfo: AvdInfo) {
    val devices = defaultDeviceManagerConnection.devices
    val manufacturer = avdInfo.deviceManufacturer
    val deviceId = avdInfo.deviceName
    val selectedDevice = devices.firstOrNull { manufacturer == it.manufacturer && deviceId == it.id }
    device.setNullableValue(selectedDevice)
    var systemImageDescription: SystemImageDescription? = null
    val selectedImage = avdInfo.systemImage
    if (selectedImage != null) {
      systemImageDescription = SystemImageDescription(selectedImage)
      systemImage.value = systemImageDescription
    }
    avdDeviceData = AvdDeviceData(selectedDevice, systemImageDescription)
    val properties = avdInfo.properties
    useQemu2.set(properties.containsKey(AvdWizardUtils.CPU_CORES_KEY))
    cpuCoreCount.value = properties[AvdWizardUtils.CPU_CORES_KEY]?.toInt() ?: 1
    var storage = getStorageFromIni(properties[AvdWizardUtils.RAM_STORAGE_KEY], false)
    if (storage != null) {
      avdDeviceData.ramStorage().set(storage)
    }
    storage = getStorageFromIni(properties[AvdWizardUtils.VM_HEAP_STORAGE_KEY], false)
    if (storage != null) {
      vmHeapStorage.set(storage)
    }
    storage = getStorageFromIni(properties[AvdWizardUtils.INTERNAL_STORAGE_KEY], true)
    if (storage != null) {
      internalStorage.set(storage)
    }

    // check if AVD has sdcard first, then decided the kind of sdcard
    if (properties.getOrDefault(HardwareProperties.HW_SDCARD, toIniString(true)) == toIniString(true)) {
      var sdCardLocation: String? = null
      if (properties[AvdWizardUtils.EXISTING_SD_LOCATION] != null) {
        sdCardLocation = properties[AvdWizardUtils.EXISTING_SD_LOCATION]
      }
      else if (properties[AvdWizardUtils.SD_CARD_STORAGE_KEY] != null) {
        sdCardLocation = FileUtil.join(avdInfo.dataFolderPath, "sdcard.img")
      }
      existingSdLocation = StringValueProperty(Strings.nullToEmpty(sdCardLocation))
      val dataFolderPath = avdInfo.dataFolderPath
      var sdLocationFile: File? = null
      if (sdCardLocation != null) {
        sdLocationFile = File(sdCardLocation)
      }
      if (sdLocationFile != null) {
        if (sdLocationFile.parent == dataFolderPath) {
          // the image is in the AVD folder, consider it to be internal
          val sdFile = File(sdCardLocation)
          val sdCardSize = Storage(sdFile.length())
          useExternalSdCard.set(false)
          useBuiltInSdCard.set(true)
          originalSdCard = ObjectValueProperty(sdCardSize)
          sdCardStorage.setValue(sdCardSize)
        }
        else {
          // the image is external
          useExternalSdCard.set(true)
          useBuiltInSdCard.set(false)
          externalSdCardLocation().set(sdCardLocation!!)
        }
      }
    }
    else { //if avd doesn't have sdcard set it to no sdcard
      useExternalSdCard.set(false)
      useBuiltInSdCard.set(false)
    }
    useHostGpu.set(fromIniString(properties[AvdWizardUtils.USE_HOST_GPU_KEY]))
    selectedAvdFrontCamera.set(AvdCamera.fromName(properties[AvdWizardUtils.FRONT_CAMERA_KEY]))
    selectedAvdBackCamera.set(AvdCamera.fromName(properties[AvdWizardUtils.BACK_CAMERA_KEY]))
    selectedNetworkLatency.set(AvdNetworkLatency.fromName(properties[AvdWizardUtils.NETWORK_LATENCY_KEY]))
    selectedNetworkSpeed.set(AvdNetworkSpeed.fromName(properties[AvdWizardUtils.NETWORK_SPEED_KEY]))
    enableHardwareKeyboard.set(fromIniString(properties[AvdWizardUtils.HAS_HARDWARE_KEYBOARD_KEY]))
    avdDisplayName.set(AvdManagerConnection.getAvdDisplayName(avdInfo))
    hasDeviceFrame.set(fromIniString(properties[AvdWizardUtils.DEVICE_FRAME_KEY]))
    coldBoot.set(fromIniString(properties[AvdWizardUtils.USE_COLD_BOOT]))
    fastBoot.set(fromIniString(properties[AvdWizardUtils.USE_FAST_BOOT]))
    chosenSnapshotBoot.set(fromIniString(properties[AvdWizardUtils.USE_CHOSEN_SNAPSHOT_BOOT]))
    val chosenFile = properties[AvdWizardUtils.CHOSEN_SNAPSHOT_FILE]
    chosenSnapshotFile.set(StringUtil.notNullize(chosenFile))
    var screenOrientation: ScreenOrientation? = null
    val orientation = properties[HardwareProperties.HW_INITIAL_ORIENTATION]
    if (!Strings.isNullOrEmpty(orientation)) {
      screenOrientation = ScreenOrientation.getByShortDisplayName(orientation)
    }
    selectedAvdOrientation.set(screenOrientation ?: ScreenOrientation.PORTRAIT)
    val skinPath = properties[AvdWizardUtils.CUSTOM_SKIN_FILE_KEY]
    if (skinPath != null) {
      val skinFile = if (skinPath == AvdWizardUtils.NO_SKIN.path) AvdWizardUtils.NO_SKIN else File(skinPath)
      if (skinFile.isDirectory) {
        avdDeviceData.customSkinFile().value = skinFile
      }
    }
    val backupSkinPath = properties[AvdWizardUtils.BACKUP_SKIN_FILE_KEY]
    if (backupSkinPath != null) {
      val skinFile = File(backupSkinPath)
      if (skinFile.isDirectory || FileUtil.filesEqual(skinFile, AvdWizardUtils.NO_SKIN)) {
        backupSkinFile().value = skinFile
      }
    }
    val modeString = properties[AvdWizardUtils.HOST_GPU_MODE_KEY]
    hostGpuMode.value = GpuMode.fromGpuSetting(modeString)
    isInEditMode.set(true)
  }

  /**
   * Set the initial internal storage size and sd card storage size, using values from hardware-properties.ini
   */
  private fun updateValuesFromHardwareProperties() {
    val conn = AvdManagerConnection.getDefaultAvdManagerConnection()
    var storage = getStorageFromIni(conn.sdCardSizeFromHardwareProperties, false)
    if (storage != null) {
      sdCardStorage.value = storage
    }
    storage = getStorageFromIni(conn.internalStorageSizeFromHardwareProperties, true)
    // TODO(b/65811265) Currently, internal storage size in hardware-properties.ini is defaulted
    // to 0. In this case, We will skip this default value. When the hardware-properties.ini is
    // updated, we will delete the redundant value check.
    if (storage != null && storage.size != 0L) {
      internalStorage.set(storage)
    }
  }

  /**
   * Returns a map containing all of the properties editable on this wizard to be passed on to the AVD prior to serialization
   */
  private fun generateUserEditedPropertiesMap(): MutableMap<String, Any?> {
    val map = hashMapOf(
      AvdWizardUtils.DEVICE_DEFINITION_KEY to device,
      AvdWizardUtils.SYSTEM_IMAGE_KEY to systemImage,
      AvdWizardUtils.AVD_ID_KEY to avdId.get(),
      AvdWizardUtils.VM_HEAP_STORAGE_KEY to vmHeapStorage.get(),
      AvdWizardUtils.DISPLAY_NAME_KEY to avdDisplayName.get(),
      AvdWizardUtils.DEFAULT_ORIENTATION_KEY to selectedAvdOrientation.get(),
      AvdWizardUtils.RAM_STORAGE_KEY to avdDeviceData.ramStorage().get(),
      AvdWizardUtils.IS_IN_EDIT_MODE_KEY to isInEditMode.get(),
      AvdWizardUtils.HAS_HARDWARE_KEYBOARD_KEY to enableHardwareKeyboard.get(),
      HardwareProperties.HW_INITIAL_ORIENTATION to selectedAvdOrientation.get().shortDisplayValue,
      AvdWizardUtils.USE_HOST_GPU_KEY to useHostGpu.get(),
      AvdWizardUtils.DEVICE_FRAME_KEY to hasDeviceFrame.get(),
      AvdWizardUtils.HOST_GPU_MODE_KEY to hostGpuMode.value,
      AvdWizardUtils.USE_COLD_BOOT to coldBoot.get(),
      AvdWizardUtils.USE_FAST_BOOT to fastBoot.get(),
      AvdWizardUtils.USE_CHOSEN_SNAPSHOT_BOOT to chosenSnapshotBoot.get(),
      AvdWizardUtils.CHOSEN_SNAPSHOT_FILE to chosenSnapshotFile.get(),
      AvdWizardUtils.DISPLAY_USE_EXTERNAL_SD_KEY to useExternalSdCard.get(),
      AvdWizardUtils.INTERNAL_STORAGE_KEY to internalStorage.get(),
      AvdWizardUtils.NETWORK_SPEED_KEY to selectedNetworkSpeed.get().asParameter,
      AvdWizardUtils.NETWORK_LATENCY_KEY to selectedNetworkLatency.get().asParameter,
      AvdWizardUtils.FRONT_CAMERA_KEY to if (avdDeviceData.hasFrontCamera().get()) selectedAvdFrontCamera.get().asParameter else AvdCamera.NONE,
      AvdWizardUtils.BACK_CAMERA_KEY to if (avdDeviceData.hasBackCamera().get()) selectedAvdBackCamera.get().asParameter else AvdCamera.NONE
    )

    if (useQemu2.get()) {
      if (cpuCoreCount.get().isPresent) {
        map[AvdWizardUtils.CPU_CORES_KEY] = cpuCoreCount.value
      }
      else {
        // Force the use the new emulator (qemu2)
        map[AvdWizardUtils.CPU_CORES_KEY] = 1
      }
    }
    else {
      // Do NOT use the new emulator (qemu2)
      map.remove(AvdWizardUtils.CPU_CORES_KEY)
    }
    if (originalSdCard != null) {
      map[AvdWizardUtils.SD_CARD_STORAGE_KEY] = originalSdCard
    }
    if (existingSdLocation.get() != "") {
      map[AvdWizardUtils.EXISTING_SD_LOCATION] = existingSdLocation.get()
    }
    if (externalSdCardLocation.get() != "") {
      map[AvdWizardUtils.EXISTING_SD_LOCATION] = externalSdCardLocation.get()
      map[AvdWizardUtils.DISPLAY_SD_LOCATION_KEY] = externalSdCardLocation.get()
    }
    if (avdDeviceData.customSkinFile().get().isPresent) {
      map[AvdWizardUtils.CUSTOM_SKIN_FILE_KEY] = avdDeviceData.customSkinFile().value
    }
    if (backupSkinFile.get().isPresent) {
      map[AvdWizardUtils.BACKUP_SKIN_FILE_KEY] = backupSkinFile.value
    }
    if (sdCardStorage.get().isPresent) {
      map[AvdWizardUtils.DISPLAY_SD_SIZE_KEY] = sdCardStorage.value
    }
    return map
  }

  // TODO(qumeric): remove
  fun hf() = handleFinished()

  override fun handleFinished() {
    // By this point we should have both a Device and a SystemImage
    val device = device.value
    val systemImage = systemImage.value
    val hardwareProperties = DeviceManager.getHardwareProperties(device)
    val userEditedProperties = generateUserEditedPropertiesMap()
    var sdCard: String? = null
    var hasSdCard = false
    if (useExternalSdCard.get()) {
      sdCard = externalSdCardLocation.get()
      // Remove SD card storage size because it will use external file
      userEditedProperties.remove(AvdWizardUtils.SD_CARD_STORAGE_KEY)
      hasSdCard = true
    }
    else if (useBuiltInSdCard.get()) {
      if (sdCardStorage().get().isPresent && originalSdCard != null && sdCardStorage().value == originalSdCard!!.get()) {
        // unchanged, use existing card
        sdCard = existingSdLocation.get()
        hasSdCard = true
      }
      else {
        // Remove existing sd card because we will create a new one
        userEditedProperties.remove(AvdWizardUtils.EXISTING_SD_LOCATION)
        var storage: Storage? = null
        originalSdCard = ObjectValueProperty(sdCardStorage.value)
        if (sdCardStorage.get().isPresent) {
          storage = sdCardStorage.value
          sdCard = toIniString(storage, false)
        }
        hasSdCard = storage != null && storage.size > 0
      }
    }
    else {
      // Remove existing sd card, since device doesn't have sdcard
      userEditedProperties.remove(AvdWizardUtils.EXISTING_SD_LOCATION)
    }
    hardwareProperties[HardwareProperties.HW_SDCARD] = toIniString(hasSdCard)

    val transformedProperties = userEditedProperties
      .filter { !it.key.startsWith(AvdWizardUtils.WIZARD_ONLY) && it.value != null } // Remove any internal keys from the map
      .mapValues { (key: String?, value: Any?) ->
        when (value) {
          is Storage -> {
            if (key == AvdWizardUtils.RAM_STORAGE_KEY || key == AvdWizardUtils.VM_HEAP_STORAGE_KEY) {
              toIniString(value, true)
            }
            else {
              toIniString(value, false)
            }
          }
          is Boolean -> toIniString(value)
          is File -> toIniString(value)
          is Double -> toIniString(value)
          is GpuMode -> {
            if (value == GpuMode.SWIFT &&
                !AvdManagerConnection.getDefaultAvdManagerConnection().emulatorVersionIsAtLeast(Revision(27, 1, 6))) {
              // Older Emulator versions expect "guest" when SWIFT is selected on the UI
              "guest"
            }
            else {
              value.gpuSetting
            }
          }
          else -> value.toString()
        }
      }

    // Call toIniString() on all remaining values
    hardwareProperties.putAll(transformedProperties)
    val skinFile =
      if (avdDeviceData.customSkinFile().get().isPresent) avdDeviceData.customSkinFile().value
      else AvdWizardUtils.pathToUpdatedSkins(device.defaultHardware.skinFile, systemImage, FileOpUtils.create())!!
    if (backupSkinFile.get().isPresent) {
      hardwareProperties[AvdManager.AVD_INI_BACKUP_SKIN_PATH] = backupSkinFile.value.path
    }

    // Add defaults if they aren't already set differently
    if (!hardwareProperties.containsKey(AvdManager.AVD_INI_SKIN_DYNAMIC)) {
      hardwareProperties[AvdManager.AVD_INI_SKIN_DYNAMIC] = toIniString(true)
    }
    if (!hardwareProperties.containsKey(HardwareProperties.HW_KEYBOARD)) {
      hardwareProperties[HardwareProperties.HW_KEYBOARD] = toIniString(false)
    }
    val isCircular = avdDeviceData.isScreenRound.get()
    val avdName = avdId.get().takeUnless { it.isEmpty() } ?: calculateAvdName(avdInfo, hardwareProperties, device)

    fun invokeAndWait(f: () -> Unit) = ApplicationManager.getApplication().invokeAndWait(f, ModalityState.any())

    // If we're editing an AVD and we downgrade a system image, wipe the user data with confirmation
    if (avdInfo != null) {
      val image = avdInfo.systemImage
      if (image != null) {
        val oldApiLevel = image.androidVersion.featureLevel
        val newApiLevel = systemImage.version.featureLevel
        val oldApiName = image.androidVersion.apiString
        val newApiName = systemImage.version.apiString
        if (oldApiLevel > newApiLevel || oldApiLevel == newApiLevel && image.androidVersion.isPreview && !systemImage.version.isPreview) {
          val shouldContinue = AtomicReference<Boolean>()
          invokeAndWait {
            val message = """You are about to downgrade $avdName from API level $oldApiName to API level $newApiName.
This requires a wipe of the userdata partition of the AVD.
Do you wish to continue with the data wipe?"""
            val result = Messages.showYesNoDialog(null as Project?, message, "Confirm Data Wipe", AllIcons.General.QuestionDialog)
            shouldContinue.set(result == Messages.YES)
          }
          if (shouldContinue.get()) {
            AvdManagerConnection.getDefaultAvdManagerConnection().wipeUserData(avdInfo)
          }
          else {
            return
          }
        }
      }
    }
    val connection = AvdManagerConnection.getDefaultAvdManagerConnection()
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      {
        createdAvd = connection.createOrUpdateAvd(
          avdInfo, avdName, device, systemImage, selectedAvdOrientation.get(),
          isCircular, sdCard, skinFile, hardwareProperties, removePreviousAvd.get()
        )
        avdCreatedCallback()
      },
      "Creating Android Virtual Device", false, null
    )
    if (createdAvd == null) {
      invokeAndWait {
        Messages.showErrorDialog(
          null as Project?, "An error occurred while creating the AVD. See idea.log for details.", "Error Creating AVD"
        )
      }
    }
  }

  companion object {
    private val minGeneralInternalMemSize = Storage(200, Storage.Unit.MiB)
    private val minPlayStoreInternalMemSize = Storage(2, Storage.Unit.GiB)
    private val minGeneralSdSize = Storage(10, Storage.Unit.MiB)
    private val minPlayStoreSdSize = Storage(100, Storage.Unit.MiB)
    private val defaultSdSize = Storage(512, Storage.Unit.MiB)
    private val zeroSdSize = Storage(0, Storage.Unit.MiB)

    /**
     * Decodes the given string from the INI file and returns a [Storage] of corresponding size.
     */
    private fun getStorageFromIni(iniString: String?, isInternalStorage: Boolean): Storage? {
      if (iniString == null) {
        return null
      }
      var numString = iniString.dropLast(1)
      val unitChar = iniString.last()
      var selectedUnit: Storage.Unit? = Storage.Unit.values().firstOrNull {
        it.toString().first() == unitChar
      }
      if (selectedUnit == null) {
        selectedUnit = if (isInternalStorage) Storage.Unit.B else Storage.Unit.MiB // Values expressed without a unit read as B for internal storage
        numString = iniString
      }
      return try {
        Storage(numString.toLong(), selectedUnit)
      }
      catch (e: NumberFormatException) {
        null
      }
    }

    /**
     * Encode the given value as a string that can be placed in the AVD's INI file.
     */
    private fun toIniString(value: Double): String = String.format(Locale.US, "%f", value)

    /**
     * Encode the given value as a string that can be placed in the AVD's INI file.
     */
    private fun toIniString(value: File): String = value.path

    /**
     * Encode the given value as a string that can be placed in the AVD's INI file.
     * Example: 10M or 1G
     */
    fun toIniString(storage: Storage, convertToMb: Boolean): String {
      val unit = if (convertToMb) Storage.Unit.MiB else storage.appropriateUnits
      val unitString = if (convertToMb) "" else unit.toString().first().toString()
      return "${storage.getSizeAsUnit(unit)}$unitString"
    }

    /**
     * Encode the given value as a string that can be placed in the AVD's INI file.
     */
    private fun toIniString(b: Boolean): String = if (b) "yes" else "no"

    /**
     * Decode the given value from an AVD's INI file.
     */
    private fun fromIniString(s: String?): Boolean = "yes" == s

    private fun calculateAvdName(avdInfo: AvdInfo?, hardwareProperties: Map<String, String?>, device: Device): String {
      if (avdInfo != null) {
        return avdInfo.name
      }
      var candidateBase = hardwareProperties[AvdManager.AVD_INI_DISPLAY_NAME]
      if (candidateBase.isNullOrEmpty()) {
        val deviceName = device.displayName.replace(' ', '_')
        val manufacturer = device.manufacturer.replace(' ', '_')
        candidateBase = "AVD_for_${deviceName}_by_$manufacturer"
      }
      return AvdWizardUtils.cleanAvdName(AvdManagerConnection.getDefaultAvdManagerConnection(), candidateBase, true)
    }
  }

  init {
    val supportsVirtualCamera = emulatorSupportsVirtualScene(
      AndroidSdks.getInstance().tryToChooseSdkHandler(),
      StudioLoggerProgressIndicator(AvdOptionsModel::class.java),
      LogWrapper(Logger.getInstance(AvdOptionsModel::class.java))
    )
    selectedAvdBackCamera = ObjectValueProperty(if (supportsVirtualCamera) AvdCamera.VIRTUAL_SCENE else AvdCamera.EMULATED)
    if (avdInfo != null) {
      updateValuesWithAvdInfo(avdInfo)
    }
    else {
      updateValuesFromHardwareProperties()
    }
    device.addListener {
      if (device.get().isPresent) {
        avdDeviceData.updateValuesFromDevice(device.value, systemImage.valueOrNull)
        val size = ScreenSize.getScreenSize(avdDeviceData.diagonalScreenSize().get())
        val density = getScreenDensity(
          avdDeviceData.deviceId().get(),
          avdDeviceData.isTv.get(),
          avdDeviceData.screenDpi().get(),
          avdDeviceData.screenResolutionHeight().get()
        )
        val vmHeapSize = EmulatedProperties.calculateDefaultVmHeapSize(size, density, avdDeviceData.isTv.get())
        vmHeapStorage.set(vmHeapSize)
        if (avdDeviceData.hasSdCard.get()) {
          // has sdcard in device, go with default setting
          useBuiltInSdCard.set(true)
        }
        else {
          useBuiltInSdCard.set(false)
        }
        useExternalSdCard.set(false)
      }
    }
    systemImage.addListener {
      if (device.get().isPresent) {
        avdDeviceData.updateSkinFromDeviceAndSystemImage(device.value, systemImage.valueOrNull)
      }
    }
  }
}