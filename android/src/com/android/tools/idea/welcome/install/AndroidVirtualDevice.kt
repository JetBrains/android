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
package com.android.tools.idea.welcome.install

import com.android.SdkConstants
import com.android.repository.api.RemotePackage
import com.android.resources.ScreenOrientation
import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.Abi
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.devices.Storage
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.ConfigKey
import com.android.sdklib.internal.avd.EmulatedProperties
import com.android.sdklib.internal.avd.GpuMode
import com.android.sdklib.internal.avd.HardwareProperties
import com.android.sdklib.internal.avd.InternalSdCard
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.IdDisplay
import com.android.sdklib.repository.meta.DetailsTypes
import com.android.tools.analytics.CommonMetricsData.osArchitecture
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.avdmanager.DeviceManagerConnection
import com.android.tools.idea.avdmanager.DeviceSkinUpdaterService
import com.android.tools.idea.avdmanager.SystemImageDescription
import com.android.tools.idea.avdmanager.ui.AvdOptionsModel
import com.android.tools.idea.avdmanager.ui.AvdWizardUtils
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.welcome.wizard.deprecated.InstallComponentsPath.findLatestPlatform
import com.android.tools.idea.welcome.wizard.deprecated.ProgressStep
import com.google.wireless.android.sdk.stats.ProductDetails
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.system.CpuArch
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path

/**
 * Logic for setting up Android virtual device
 */
class AndroidVirtualDevice(remotePackages: Map<String?, RemotePackage>, installUpdates: Boolean) : InstallableComponent(
  "Android Virtual Device",
  "A preconfigured and optimized Android Virtual Device for app testing on the emulator. (Recommended)",
  installUpdates
) {
  private val IS_ARM64_HOST_OS = CpuArch.isArm64() || osArchitecture == ProductDetails.CpuArchitecture.X86_ON_ARM
  private lateinit var myProgressStep: ProgressStep
  private var myLatestVersion: AndroidVersion? = null
  // After this we use x86-64 system images
  private val MAX_X86_API_LEVEL = 30

  @Throws(WizardException::class)
  private fun getSystemImageDescription(sdkHandler: AndroidSdkHandler): SystemImageDescription {
    val progress = StudioLoggerProgressIndicator(javaClass)
    if (myLatestVersion == null) {
      throw WizardException("Missing system image required for an AVD setup")
    }
    val systemImages = sdkHandler.getSystemImageManager(progress).lookup(ID_ADDON_GOOGLE_API_IMG, myLatestVersion!!, ID_VENDOR_GOOGLE)
    if (systemImages.isEmpty()) {
      throw WizardException("Missing system image required for an AVD setup")
    }
    return SystemImageDescription(systemImages.iterator().next())
  }

  fun isAvdCreationNeeded(sdkHandler: AndroidSdkHandler): Boolean {
    val avdManager = AvdManagerConnection.getAvdManagerConnection(sdkHandler)
    return avdManager.getAvds(true).isEmpty()
  }

  @Throws(WizardException::class)
  fun createAvd(sdkHandler: AndroidSdkHandler): AvdInfo? {
    val avdManager = AvdManagerConnection.getAvdManagerConnection(sdkHandler)
    val d = getDevice(sdkHandler.location!!)
    val systemImageDescription = getSystemImageDescription(sdkHandler)
    val sdCard = InternalSdCard(EmulatedProperties.DEFAULT_INTERNAL_STORAGE.size)
    val hardwareSkinPath = d.defaultHardware.skinFile?.let { sdkHandler.toCompatiblePath(it) }
      ?.let { defaultHardwareSkin ->
        DeviceSkinUpdaterService.getInstance().updateSkins(defaultHardwareSkin, systemImageDescription).get()
      }

    val displayName = avdManager.getDefaultDeviceDisplayName(d, systemImageDescription.version)
    val internalName = AvdWizardUtils.cleanAvdName(avdManager, displayName, true)
    val abi = Abi.getEnum(systemImageDescription.primaryAbiType)
    val useRanchu = AvdManagerConnection.doesSystemImageSupportQemu2(systemImageDescription)
    val supportsSmp = abi != null && abi.supportsMultipleCpuCores() && AvdWizardUtils.getMaxCpuCores() > 1
    val settings = getAvdSettings(displayName, internalName, d)
    if (useRanchu) {
      settings[ConfigKey.CPU_CORES] = "1".takeUnless { supportsSmp } ?: AvdWizardUtils.getMaxCpuCores().toString()
    }
    return avdManager.createOrUpdateAvd(
      null, internalName, d, systemImageDescription, ScreenOrientation.PORTRAIT, false, sdCard,
      hardwareSkinPath, settings, null, true
    )
  }

  @VisibleForTesting
  fun getRequiredSysimgPath(isArm64HostOs: Boolean): String {
    return DetailsTypes.getSysImgPath(ID_VENDOR_GOOGLE, myLatestVersion, ID_ADDON_GOOGLE_API_IMG,
                                      when {
                                        isArm64HostOs -> SdkConstants.ABI_ARM64_V8A
                                        (myLatestVersion?.compareTo(MAX_X86_API_LEVEL, null) ?: -1) > 0 -> SdkConstants.ABI_INTEL_ATOM64
                                        else ->  SdkConstants.ABI_INTEL_ATOM
                                      } )
  }

  override val requiredSdkPackages: Collection<String>
    get() {
      val result = mutableListOf<String>()
      if (myLatestVersion != null) {
        result.add(getRequiredSysimgPath(IS_ARM64_HOST_OS))
      }
      return result
    }

  override val optionalSdkPackages: Collection<String>
    get() {
      val result = mutableListOf<String>()
      if (myLatestVersion != null) {
        result.add(DetailsTypes.getAddonPath(ID_VENDOR_GOOGLE, myLatestVersion, ID_ADDON_GOOGLE_API_IMG))
      }
      return result
    }

  override fun init(progressStep: ProgressStep) {
    myProgressStep = progressStep
  }

  override fun configure(installContext: InstallContext, sdkHandler: AndroidSdkHandler) {
    try {
      myProgressStep.progressIndicator.isIndeterminate = true
      myProgressStep.progressIndicator.text = "Creating Android virtual device"
      installContext.print("Creating Android virtual device\n", ConsoleViewContentType.SYSTEM_OUTPUT)
      val avdManager = AvdManagerConnection.getAvdManagerConnection(sdkHandler)
      val avd = createAvd(sdkHandler) ?: throw WizardException("Unable to create Android virtual device")
      val successMessage = "Android virtual device ${avd.name} was successfully created\n"
      installContext.print(successMessage, ConsoleViewContentType.SYSTEM_OUTPUT)
    }
    catch (e: WizardException) {
      LOG.error(e)
      val failureMessage = "Unable to create a virtual device: ${e.message}\n"
      installContext.print(failureMessage, ConsoleViewContentType.ERROR_OUTPUT)
    }
  }

  public override fun isSelectedByDefault(): Boolean {
    if (sdkHandler == null) {
      return false
    }
    val desired: SystemImageDescription = try {
      getSystemImageDescription(sdkHandler!!)
    }
    catch (e: WizardException) {
      // No System Image yet. Default is to install.
      return true
    }
    val connection = AvdManagerConnection.getAvdManagerConnection(sdkHandler!!)
    val avds = connection.getAvds(false)
    for (avd in avds) {
      if (avd.abiType == desired.primaryAbiType && avd.androidVersion == desired.version) {
        // We have a similar avd already installed. Deselect by default.
        return false
      }
    }
    return true
  }

  companion object {
    val LOG = Logger.getInstance(AndroidVirtualDevice::class.java)
    private const val DEFAULT_DEVICE_ID = "medium_phone"
    private val ID_ADDON_GOOGLE_API_IMG = IdDisplay.create("google_apis_playstore", "Google Play")
    private val ID_VENDOR_GOOGLE = IdDisplay.create("google", "Google LLC")
    private val DEFAULT_RAM_SIZE = Storage(2, Storage.Unit.GiB)
    private val DEFAULT_HEAP_SIZE = Storage(336, Storage.Unit.MiB)
    private val ENABLED_HARDWARE = setOf(
        HardwareProperties.HW_ACCELEROMETER, HardwareProperties.HW_AUDIO_INPUT, HardwareProperties.HW_BATTERY,
        HardwareProperties.HW_GPS, HardwareProperties.HW_KEYBOARD, HardwareProperties.HW_ORIENTATION_SENSOR,
        HardwareProperties.HW_PROXIMITY_SENSOR, HardwareProperties.HW_SDCARD,
        ConfigKey.GPU_EMULATION)
    private val DISABLED_HARDWARE = setOf(
      HardwareProperties.HW_DPAD, HardwareProperties.HW_MAINKEYS, HardwareProperties.HW_TRACKBALL,
      ConfigKey.SNAPSHOT_PRESENT
    )

    @Throws(WizardException::class)
    private fun getDevice(sdkPath: Path): Device {
      return DeviceManagerConnection.getDeviceManagerConnection(sdkPath).devices.find { it.id == DEFAULT_DEVICE_ID } ?:
          throw WizardException("No device definition with \"$DEFAULT_DEVICE_ID\" ID found")
    }

    private fun getAvdSettings(displayName: String, internalName: String, device: Device): MutableMap<String, String> {
      val result: MutableMap<String, String> = hashMapOf()
      // First, initialize AVD settings based on the device definition
      result.putAll(DeviceManager.getHardwareProperties(device))
      // Then, override device definition defaults and fill in remaining fields
      result[ConfigKey.GPU_MODE] = GpuMode.AUTO.gpuSetting
      for (key in ENABLED_HARDWARE) {
        result[key] = HardwareProperties.BOOLEAN_YES
      }
      for (key in DISABLED_HARDWARE) {
        result[key] = HardwareProperties.BOOLEAN_NO
      }
      result[ConfigKey.CAMERA_BACK] = "virtualscene"
      result[ConfigKey.CAMERA_FRONT] = "emulated"
      result[ConfigKey.NETWORK_LATENCY] = EmulatedProperties.DEFAULT_NETWORK_LATENCY.asParameter
      result[ConfigKey.NETWORK_SPEED] = EmulatedProperties.DEFAULT_NETWORK_SPEED.asParameter
      result[ConfigKey.AVD_ID] = internalName
      result[ConfigKey.DISPLAY_NAME] = displayName
      setStorageSizeKey(result, ConfigKey.RAM_SIZE, DEFAULT_RAM_SIZE, false)
      setStorageSizeKey(result, ConfigKey.DATA_PARTITION_SIZE, DEFAULT_RAM_SIZE, false)
      setStorageSizeKey(result, ConfigKey.VM_HEAP_SIZE, DEFAULT_HEAP_SIZE, true)
      return result
    }

    private fun setStorageSizeKey(result: MutableMap<String, String>, key: String, size: Storage, convertToMb: Boolean) {
      result[key] = AvdOptionsModel.toIniString(size, convertToMb)
    }
  }

  init {
    val latestInfo = findLatestPlatform(remotePackages, true)
    myLatestVersion = if (latestInfo != null) (latestInfo.typeDetails as DetailsTypes.PlatformDetailsType).androidVersion else null
  }
}