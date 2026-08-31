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

import com.android.annotations.concurrency.UiThread
import com.android.repository.api.RemotePackage
import com.android.sdklib.AndroidApiLevel
import com.android.sdklib.SystemImageTags
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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.IdeAvdManagers
import com.google.wireless.android.sdk.stats.ProductDetails
import com.google.wireless.android.sdk.stats.SetupWizardEvent
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.system.CpuArch
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Logic for setting up Android virtual device */
class AndroidVirtualDeviceSdkComponentTreeNode
@JvmOverloads
constructor(
  installUpdates: Boolean,
  val api: AndroidApiLevel = StudioFlags.NPW_COMPILE_SDK_VERSION.get(),
) :
  InstallableSdkComponentTreeNode(
    "Android Virtual Device",
    "A preconfigured and optimized Android Virtual Device for app testing on the emulator. (Recommended)",
    installUpdates,
  ) {
  private val isArm64Host = CpuArch.isArm64() || osArchitecture == ProductDetails.CpuArchitecture.X86_ON_ARM

  var systemImageDescription: SystemImageDescription? = null
    private set

  override fun onSdkHandlerUpdated() {
    val images = repositoryPackages.remotePackages.values.filter { it.isUsable() }.map { SystemImageDescription(it) }
    // Favor newest API level (up to [api]), fewer tags, favor base extension level.
    images
      .sortedWith(
        compareByDescending<SystemImageDescription> { it.version.withBaseExtensionLevel() }
          .thenBy { it.tags.size }
          .thenBy { it.version.extensionLevel }
      )
      .firstOrNull()
      ?.let { systemImageDescription = it }
  }

  private fun RemotePackage.isUsable(): Boolean {
    val details = typeDetails as? DetailsTypes.SysImgDetailsType ?: return false
    val wantedAbi = if (isArm64Host) Abi.ARM64_V8A else Abi.X86_64
    return details.abis.firstOrNull() == wantedAbi.toString() &&
      details.vendor == ID_VENDOR_GOOGLE &&
      details.tags.contains(SystemImageTags.PLAY_STORE_TAG) &&
      details.androidVersion.androidApiLevel <= api &&
      !details.androidVersion.isPreview
  }

  @Throws(WizardException::class)
  private fun resolveSystemImage(sdkHandler: AndroidSdkHandler): SystemImageDescription {
    val systemImageDescription = systemImageDescription ?: throw WizardException("Missing system image required for an AVD setup")
    val systemImage =
      sdkHandler.getSystemImageManager(StudioLoggerProgressIndicator(javaClass)).images.firstOrNull {
        it.`package`.path == systemImageDescription.remotePackage?.path
      } ?: throw WizardException("Missing system image required for an AVD setup")
    return SystemImageDescription(systemImage)
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
    val systemImageDescription = resolveSystemImage(sdkHandler)
    val avdBuilder = avdManager.createAvdBuilder(device)
    with(avdBuilder) {
      displayName = avdManager.uniquifyDisplayName(AvdNames.getDefaultDeviceDisplayName(device, systemImageDescription.version))
      avdName = avdManager.uniquifyAvdName(AvdNames.cleanAvdName(displayName))
      systemImage = systemImageDescription.systemImage
      sdCard = InternalSdCard(EmulatedProperties.DEFAULT_SDCARD_SIZE.size)
      skin =
        device.defaultHardware.skinFile
          ?.let { sdkHandler.toCompatiblePath(it) }
          ?.let { defaultHardwareSkin ->
            OnDiskSkin(DeviceSkinUpdaterService.getInstance().updateSkins(defaultHardwareSkin, systemImageDescription).get())
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

  override val requiredSdkPackages: Collection<String>
    get() = systemImageDescription?.let { listOfNotNull(it.remotePackage?.path) } ?: emptyList()

  override fun configure(installContext: InstallContext, sdkHandler: AndroidSdkHandler) {
    try {
      installContext.progressIndicator.isIndeterminate = true
      installContext.progressIndicator.text = "Creating Android virtual device"
      installContext.print("Creating Android virtual device\n", ConsoleViewContentType.SYSTEM_OUTPUT)
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
    val desired: SystemImageDescription = systemImageDescription ?: return true // No System Image yet. Default is to install.
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

  override fun sdkComponentsMetricKind() = SetupWizardEvent.SdkInstallationMetrics.SdkComponentKind.ANDROID_VIRTUAL_DEVICE

  companion object {
    val LOG = Logger.getInstance(AndroidVirtualDeviceSdkComponentTreeNode::class.java)
    private const val DEFAULT_DEVICE_ID = "medium_phone"
    private val ID_VENDOR_GOOGLE = IdDisplay.create("google", "Google LLC")
    private val DEFAULT_RAM_SIZE = Storage(2, Storage.Unit.GiB)
    private val DEFAULT_HEAP_SIZE = Storage(336, Storage.Unit.MiB)

    @Throws(WizardException::class)
    private fun getDevice(sdkPath: Path): Device {
      return DeviceManagerConnection.getDeviceManagerConnection(sdkPath).devices.find { it.id == DEFAULT_DEVICE_ID }
        ?: throw WizardException("No device definition with \"$DEFAULT_DEVICE_ID\" ID found")
    }
  }
}
