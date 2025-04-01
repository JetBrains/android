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
import com.android.annotations.concurrency.UiThread
import com.android.repository.api.RemotePackage
import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.Abi
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.Storage
import com.android.sdklib.internal.avd.AvdCamera
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdManagerException
import com.android.sdklib.internal.avd.AvdNames
import com.android.sdklib.internal.avd.EmulatedProperties
import com.android.sdklib.internal.avd.GpuMode
import com.android.sdklib.internal.avd.InternalSdCard
import com.android.sdklib.internal.avd.OnDiskSkin
import com.android.sdklib.internal.avd.defaultGenericSkin
import com.android.sdklib.internal.avd.uniquifyAvdName
import com.android.sdklib.internal.avd.uniquifyDisplayName
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.IdDisplay
import com.android.sdklib.repository.meta.DetailsTypes
import com.android.tools.analytics.CommonMetricsData.osArchitecture
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.avdmanager.DeviceManagerConnection
import com.android.tools.idea.avdmanager.DeviceSkinUpdaterService
import com.android.tools.idea.avdmanager.SystemImageDescription
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.IdeAvdManagers
import com.android.tools.idea.welcome.wizard.deprecated.InstallComponentsPath.findLatestPlatform
import com.google.wireless.android.sdk.stats.ProductDetails
import com.google.wireless.android.sdk.stats.SetupWizardEvent
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.system.CpuArch
import com.jetbrains.rd.framework.util.withContext
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting

/** Logic for setting up Android virtual device */
class AndroidVirtualDeviceSdkComponentTreeNode(
  private val androidVersion: AndroidVersion?,
  installUpdates: Boolean,
) :
  InstallableSdkComponentTreeNode(
    "Android Virtual Device",
    "A preconfigured and optimized Android Virtual Device for app testing on the emulator. (Recommended)",
    installUpdates,
  ) {
  // This is a bit weird that we take a collection of RemotePackages just to find the latest
  // version, but then later require an AndroidSdkHandler in a bunch of methods, which is the
  // source of RemotePackages in the first place.
  //
  // Plus, there's an sdkHandler in the superclass; why aren't we using it?
  constructor(
    remotePackages: Collection<RemotePackage>,
    installUpdates: Boolean,
  ) : this(
    findLatestPlatform(remotePackages, true)?.let {
      (it.typeDetails as DetailsTypes.PlatformDetailsType).androidVersion
    },
    installUpdates,
  )

  private val IS_ARM64_HOST_OS =
    CpuArch.isArm64() || osArchitecture == ProductDetails.CpuArchitecture.X86_ON_ARM

  // After this we use x86-64 system images
  private val MAX_X86_API_LEVEL = 30

  @Throws(WizardException::class)
  private fun getSystemImageDescription(sdkHandler: AndroidSdkHandler): SystemImageDescription {
    val progress = StudioLoggerProgressIndicator(javaClass)
    if (androidVersion == null) {
      throw WizardException("Missing system image required for an AVD setup")
    }
    val systemImages =
      sdkHandler
        .getSystemImageManager(progress)
        .lookup(ID_ADDON_GOOGLE_API_IMG, androidVersion, ID_VENDOR_GOOGLE)
    if (systemImages.isEmpty()) {
      throw WizardException("Missing system image required for an AVD setup")
    }
    return SystemImageDescription(systemImages.iterator().next())
  }

  @UiThread
  fun isAvdCreationNeeded(sdkHandler: AndroidSdkHandler): Boolean {
    val avdManager = AvdManagerConnection.getAvdManagerConnection(sdkHandler)

    var shouldCreateAvd = true
    try {
      // Fetching the current AVDs is slow due to FileIO - so let's run it
      // in a background thread and show a modal progress indicator.
      runWithModalProgressBlocking(
        owner = ModalTaskOwner.guess(),
        title = "Checking for existing Android Virtual Devices",
        cancellation = TaskCancellation.cancellable(),
      ) {
        withContext(Dispatchers.IO) { shouldCreateAvd = avdManager.getAvds(true).isEmpty() }
      }
    } catch (e: ProcessCanceledException) {
      // Default to showing option to install AVDs when the user
      // cancels the check
      shouldCreateAvd = true
    }

    return shouldCreateAvd
  }

  @Throws(WizardException::class)
  fun createAvd(sdkHandler: AndroidSdkHandler): AvdInfo {
    val avdManager = IdeAvdManagers.getAvdManager(sdkHandler)
    val device = getDevice(sdkHandler.location!!)
    val systemImageDescription = getSystemImageDescription(sdkHandler)

    val avdBuilder = avdManager.createAvdBuilder(device)
    with(avdBuilder) {
      displayName =
        avdManager.uniquifyDisplayName(
          AvdNames.getDefaultDeviceDisplayName(device, systemImageDescription.version)
        )
      avdName = avdManager.uniquifyAvdName(AvdNames.cleanAvdName(displayName))
      systemImage = systemImageDescription.systemImage
      sdCard = InternalSdCard(EmulatedProperties.DEFAULT_SDCARD_SIZE.size)
      skin =
        device.defaultHardware.skinFile
          ?.let { sdkHandler.toCompatiblePath(it) }
          ?.let { defaultHardwareSkin ->
            OnDiskSkin(
              DeviceSkinUpdaterService.getInstance()
                .updateSkins(defaultHardwareSkin, systemImageDescription)
                .get()
            )
          } ?: device.defaultGenericSkin()

      gpuMode = GpuMode.AUTO
      backCamera = AvdCamera.VIRTUAL_SCENE
      frontCamera = AvdCamera.EMULATED
      enableKeyboard = true
      networkLatency = EmulatedProperties.DEFAULT_NETWORK_LATENCY
      networkSpeed = EmulatedProperties.DEFAULT_NETWORK_SPEED
      ram = DEFAULT_RAM_SIZE
      internalStorage = EmulatedProperties.defaultInternalStorage(device)
      vmHeap = DEFAULT_HEAP_SIZE
    }

    val abi = Abi.getEnum(systemImageDescription.primaryAbiType)
    val supportsSmp = abi != null && abi.supportsMultipleCpuCores() && maxCpuCores() > 1
    avdBuilder.cpuCoreCount = if (supportsSmp) maxCpuCores() else 1

    try {
      return avdManager.createAvd(avdBuilder)
    } catch (e: AvdManagerException) {
      throw WizardException(e.message ?: "Unable to create AVD", e)
    }
  }

  /** Return the max number of cores that an AVD can use on this development system. */
  private fun maxCpuCores(): Int {
    return Runtime.getRuntime().availableProcessors() / 2
  }

  @VisibleForTesting
  fun getRequiredSysimgPath(isArm64HostOs: Boolean): String {
    return DetailsTypes.getSysImgPath(
      ID_VENDOR_GOOGLE,
      androidVersion,
      ID_ADDON_GOOGLE_API_IMG,
      when {
        isArm64HostOs -> SdkConstants.ABI_ARM64_V8A
        androidVersion == null -> SdkConstants.ABI_INTEL_ATOM
        // Note that this covers previews for MAX_X86_API_LEVEL + 1 as well.
        androidVersion > AndroidVersion(MAX_X86_API_LEVEL) -> SdkConstants.ABI_INTEL_ATOM64
        else -> SdkConstants.ABI_INTEL_ATOM
      },
    )
  }

  override val requiredSdkPackages: Collection<String>
    get() =
      if (androidVersion == null) emptyList() else listOf(getRequiredSysimgPath(IS_ARM64_HOST_OS))

  override fun configure(installContext: InstallContext, sdkHandler: AndroidSdkHandler) {
    try {
      installContext.progressIndicator.isIndeterminate = true
      installContext.progressIndicator.text = "Creating Android virtual device"
      installContext.print(
        "Creating Android virtual device\n",
        ConsoleViewContentType.SYSTEM_OUTPUT,
      )
      val avd = createAvd(sdkHandler)
      val successMessage = "Android virtual device ${avd.name} was successfully created\n"
      installContext.print(successMessage, ConsoleViewContentType.SYSTEM_OUTPUT)
    } catch (e: WizardException) {
      LOG.error(e)
      val failureMessage = "Unable to create a virtual device: ${e.message}\n"
      installContext.print(failureMessage, ConsoleViewContentType.ERROR_OUTPUT)
    }
  }

  public override fun isSelectedByDefault(): Boolean {
    val sdkHandler = sdkHandler ?: return false
    val desired: SystemImageDescription =
      try {
        getSystemImageDescription(sdkHandler)
      } catch (e: WizardException) {
        // No System Image yet. Default is to install.
        return true
      }
    val connection = AvdManagerConnection.getAvdManagerConnection(sdkHandler)
    val avds = connection.getAvds(false)
    for (avd in avds) {
      if (avd.abiType == desired.primaryAbiType && avd.androidVersion == desired.version) {
        // We have a similar avd already installed. Deselect by default.
        return false
      }
    }
    return true
  }

  override fun sdkComponentsMetricKind() =
    SetupWizardEvent.SdkInstallationMetrics.SdkComponentKind.ANDROID_VIRTUAL_DEVICE

  companion object {
    val LOG = Logger.getInstance(AndroidVirtualDeviceSdkComponentTreeNode::class.java)
    private const val DEFAULT_DEVICE_ID = "medium_phone"
    private val ID_ADDON_GOOGLE_API_IMG = IdDisplay.create("google_apis_playstore", "Google Play")
    private val ID_VENDOR_GOOGLE = IdDisplay.create("google", "Google LLC")
    private val DEFAULT_RAM_SIZE = Storage(2, Storage.Unit.GiB)
    private val DEFAULT_HEAP_SIZE = Storage(336, Storage.Unit.MiB)

    @Throws(WizardException::class)
    private fun getDevice(sdkPath: Path): Device {
      return DeviceManagerConnection.getDeviceManagerConnection(sdkPath).devices.find {
        it.id == DEFAULT_DEVICE_ID
      } ?: throw WizardException("No device definition with \"$DEFAULT_DEVICE_ID\" ID found")
    }
  }
}
