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
package com.android.tools.idea.avdmanager

import com.android.annotations.concurrency.Slow
import com.android.ddmlib.IDevice
import com.android.io.CancellableFileIo
import com.android.prefs.AndroidLocationsException
import com.android.prefs.AndroidLocationsSingleton
import com.android.repository.api.ProgressIndicator
import com.android.repository.api.RepoPackage
import com.android.repository.io.FileOpUtils
import com.android.resources.ScreenOrientation
import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.DeviceActionCanceledException
import com.android.sdklib.deviceprovisioner.DeviceActionException
import com.android.sdklib.devices.Abi
import com.android.sdklib.devices.Device
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdManager
import com.android.sdklib.internal.avd.AvdNames
import com.android.sdklib.internal.avd.AvdNames.cleanAvdName
import com.android.sdklib.internal.avd.ConfigKey
import com.android.sdklib.internal.avd.ConfigKey.SKIN_PATH
import com.android.sdklib.internal.avd.EmulatorAdvancedFeatures
import com.android.sdklib.internal.avd.EmulatorPackage
import com.android.sdklib.internal.avd.GenericSkin
import com.android.sdklib.internal.avd.HardwareProperties
import com.android.sdklib.internal.avd.OnDiskSkin
import com.android.sdklib.internal.avd.SdCard
import com.android.sdklib.internal.avd.getEmulatorPackage
import com.android.sdklib.internal.avd.uniquifyAvdName
import com.android.sdklib.internal.avd.uniquifyDisplayName
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.avdmanager.AccelerationErrorSolution.SolutionCode
import com.android.tools.idea.avdmanager.DeviceSkinUpdater.updateSkin
import com.android.tools.idea.avdmanager.emulatorcommand.BootWithSnapshotEmulatorCommandBuilder
import com.android.tools.idea.avdmanager.emulatorcommand.ColdBootEmulatorCommandBuilder
import com.android.tools.idea.avdmanager.emulatorcommand.ColdBootNowEmulatorCommandBuilder
import com.android.tools.idea.avdmanager.emulatorcommand.DefaultEmulatorCommandBuilderFactory
import com.android.tools.idea.avdmanager.emulatorcommand.EmulatorCommandBuilder
import com.android.tools.idea.avdmanager.emulatorcommand.EmulatorCommandBuilderFactory
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.IdeAvdManagers
import com.android.tools.idea.streaming.EmulatorSettings.Companion.getInstance
import com.android.utils.ILogger
import com.android.utils.PathUtils
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.net.HttpConfigurable
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.OptionalLong
import java.util.WeakHashMap
import java.util.concurrent.TimeUnit
import org.jetbrains.annotations.TestOnly
import org.jetbrains.ide.PooledThreadExecutor

/**
 * A wrapper class for communicating with [AvdManager] and exposing helper functions for dealing
 * with [AvdInfo] objects inside Android Studio.
 *
 * Much of what this class does is actually handle the case where the SDK location is not defined.
 * In that case, the [NULL_CONNECTION] object, with null values for [sdkHandler] and [avdManager] is
 * returned from the factory method. Many of the methods on this class are simple delegates to
 * [AvdManager] that handle the null case.
 *
 * Both [sdkHandler] and [avdManager] will be set for all instances except [NULL_CONNECTION].
 */
open class AvdManagerConnection
@VisibleForTesting
constructor(
  private val sdkHandler: AndroidSdkHandler?,
  private val avdManager: AvdManager?,
  private val edtListeningExecutorService: ListeningExecutorService =
    MoreExecutors.listeningDecorator(EdtExecutorService.getInstance()),
) {
  val emulator: EmulatorPackage?
    get() = sdkHandler?.getEmulatorPackage(REPO_LOG)

  /**
   * @param forceRefresh if true the manager will read the AVD list from disk. If false, the cached
   *   version in memory is returned if available
   * @return a list of AVDs currently present on the system.
   */
  @Slow
  open fun getAvds(forceRefresh: Boolean): List<AvdInfo> {
    avdManager ?: return ImmutableList.of()
    if (forceRefresh) {
      try {
        avdManager.reloadAvds()
      } catch (e: AndroidLocationsException) {
        IJ_LOG.error("Could not find Android SDK!", e)
      }
    }
    return Lists.newArrayList(*avdManager.allAvds)
  }

  /** Delete the given AVD if it exists. */
  @Slow
  fun deleteAvd(info: AvdInfo): Boolean {
    return avdManager?.deleteAvd(info) ?: false
  }

  @Slow
  fun isAvdRunning(avd: AvdInfo): Boolean {
    avdManager ?: return false

    return avdManager.getPid(avd).orNull()?.let { ProcessHandle.of(it).orElse(null) }?.isAlive
      ?: run {
        SDK_LOG.warning("Unable to determine if " + avd.name + " is online, assuming it's not")
        false
      }
  }

  @Slow
  fun stopAvd(info: AvdInfo) {
    avdManager?.stopAvd(info)
  }

  /**
   * Starts the emulator without mounting a file to store or load state snapshots, forcing a full
   * boot and disabling state snapshot functionality.
   */
  fun coldBoot(
    project: Project?,
    avd: AvdInfo,
    requestType: AvdLaunchListener.RequestType,
  ): ListenableFuture<IDevice> {
    return startAvd(project, avd, requestType, ::ColdBootEmulatorCommandBuilder)
  }

  /** Starts the emulator, booting from the "default_boot" snapshot. */
  fun quickBoot(
    project: Project?,
    avd: AvdInfo,
    requestType: AvdLaunchListener.RequestType,
  ): ListenableFuture<IDevice> {
    return startAvd(project, avd, requestType, ::EmulatorCommandBuilder)
  }

  /**
   * Starts the emulator, booting from the given snapshot (specified as the directory name beneath
   * "snapshots", not a full path).
   */
  fun bootWithSnapshot(
    project: Project?,
    avd: AvdInfo,
    snapshot: String,
    requestType: AvdLaunchListener.RequestType,
  ): ListenableFuture<IDevice> {
    return startAvd(project, avd, requestType) { emulator, avd ->
      BootWithSnapshotEmulatorCommandBuilder(emulator, avd, snapshot)
    }
  }

  /** Boots the AVD, using its .ini file to determine the booting method. */
  open fun startAvd(
    project: Project?,
    info: AvdInfo,
    requestType: AvdLaunchListener.RequestType,
  ): ListenableFuture<IDevice> {
    return startAvd(project, info, requestType, DefaultEmulatorCommandBuilderFactory())
  }

  /** Performs a cold boot and saves the emulator state on exit. */
  fun startAvdWithColdBoot(
    project: Project?,
    info: AvdInfo,
    requestType: AvdLaunchListener.RequestType,
  ): ListenableFuture<IDevice> {
    return startAvd(project, info, requestType, ::ColdBootNowEmulatorCommandBuilder)
  }

  fun startAvd(
    project: Project?,
    info: AvdInfo,
    requestType: AvdLaunchListener.RequestType,
    factory: EmulatorCommandBuilderFactory,
  ): ListenableFuture<IDevice> {
    avdManager
      ?: return Futures.immediateFailedFuture(DeviceActionException("No Android SDK Found"))
    checkNotNull(sdkHandler)

    val skinPath = info.properties[SKIN_PATH]
    if (skinPath != null) {
      var skin = sdkHandler.toCompatiblePath(skinPath)
      // For historical reasons skin.path in config.ini may be a path relative to SDK
      // rather than its "skins" directory. Remove the "skins" prefix in that case.
      if (!skin.isAbsolute && skin.nameCount > 1 && skin.getName(0).toString() == "skins") {
        skin = skin.subpath(1, skin.nameCount)
      }

      updateSkin(skin)
    }

    return Futures.transformAsync(
      MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE).submit<
        AccelerationErrorCode
      > {
        checkAcceleration(sdkHandler)
      },
      { code: AccelerationErrorCode ->
        continueToStartAvdIfAccelerationErrorIsNotBlocking(
          code,
          project,
          info,
          requestType,
          factory,
        )
      },
      MoreExecutors.directExecutor(),
    )
  }

  private fun continueToStartAvdIfAccelerationErrorIsNotBlocking(
    code: AccelerationErrorCode,
    project: Project?,
    info: AvdInfo,
    requestType: AvdLaunchListener.RequestType,
    factory: EmulatorCommandBuilderFactory,
  ): ListenableFuture<IDevice> {
    if (!code.problem.isEmpty()) {
      IJ_LOG.warn(String.format("Launching %s: %s: %s", info.name, code, code.problem))
    }
    when (code) {
      AccelerationErrorCode.ALREADY_INSTALLED ->
        return continueToStartAvd(project, info, requestType, factory)
      AccelerationErrorCode.TOOLS_UPDATE_REQUIRED,
      AccelerationErrorCode.PLATFORM_TOOLS_UPDATE_ADVISED,
      AccelerationErrorCode
        .SYSTEM_IMAGE_UPDATE_ADVISED -> // Launch the virtual device with possibly degraded
        // performance even if there are updates
        // noinspection DuplicateBranchesInSwitch
        return continueToStartAvd(project, info, requestType, factory)
      AccelerationErrorCode.NO_EMULATOR_INSTALLED ->
        return handleAccelerationError(project, info, requestType, code)
      else -> {
        val abi =
          Abi.getEnum(info.abiType)
            ?: return continueToStartAvd(project, info, requestType, factory)

        if (abi == Abi.X86 || abi == Abi.X86_64) {
          return handleAccelerationError(project, info, requestType, code)
        }

        // Let ARM and MIPS virtual devices launch without hardware acceleration
        return continueToStartAvd(project, info, requestType, factory)
      }
    }
  }

  private fun continueToStartAvd(
    project: Project?,
    avd: AvdInfo,
    requestType: AvdLaunchListener.RequestType,
    factory: EmulatorCommandBuilderFactory,
  ): ListenableFuture<IDevice> {
    var avd = avd
    val emulator = emulator
    if (emulator == null) {
      IJ_LOG.error("No emulator binary found!")
      return Futures.immediateFailedFuture(DeviceActionException("No emulator installed"))
    }

    val emulatorBinary = emulator.emulatorBinary
    if (emulatorBinary == null) {
      IJ_LOG.error("No emulator binary found!")
      return Futures.immediateFailedFuture(DeviceActionException("No emulator binary found"))
    }

    val avdManager = checkNotNull(avdManager)
    avd = avdManager.reloadAvd(avd) // Reload the AVD in case it was modified externally.
    val avdName = avd.displayName

    // TODO: The emulator stores pid of the running process inside the .lock file
    // (userdata-qemu.img.lock in Linux and userdata-qemu.img.lock/pid on Windows). We should detect
    // whether those lock files are stale and if so, delete them without showing this error. Either
    // the emulator provides a command to do that, or we learn about its internals
    // (qemu/android/utils/filelock.c) and perform the same action here. If it is not stale, then we
    // should show this error and if possible, bring that window to the front.
    if (avdManager.isAvdRunning(avd)) {
      avdManager.logRunningAvdInfo(avd)
      return Futures.immediateFailedFuture(AvdIsAlreadyRunningException(avd))
    }

    val commandLine =
      newEmulatorCommand(
        project,
        emulatorBinary,
        avd,
        requestType == AvdLaunchListener.RequestType.DIRECT_RUNNING_DEVICES,
        factory,
      )
    val runner = EmulatorRunner(commandLine, avd)

    val processHandler =
      try {
        runner.start()
      } catch (e: ExecutionException) {
        IJ_LOG.error("Error launching emulator", e)
        return Futures.immediateFailedFuture(
          DeviceActionException(String.format("Error launching emulator %1\$s", avdName), e)
        )
      }

    // If we're using qemu2, it has its own progress bar, so put ours in the background. Otherwise,
    // show it.
    val p =
      if (emulator.isQemu2)
        BackgroundableProcessIndicator(
          project,
          "Launching emulator",
          PerformInBackgroundOption.ALWAYS_BACKGROUND,
          "",
          "",
          false,
        )
      else ProgressWindow(false, true, project)
    p.isIndeterminate = false
    p.setDelayInMillis(0)

    // It takes >= 8 seconds to start the Emulator. Display a small progress indicator otherwise it
    // seems like the action wasn't invoked and users tend to click multiple times on it, ending up
    // with several instances of the emulator.
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        p.start()
        p.text = "Starting AVD..."
        var d = 0.0
        while (d < 1) {
          p.fraction = d
          Thread.sleep(100)
          if (processHandler.isProcessTerminated) {
            break
          }
          d += 1.0 / 80
        }
      } catch (ignore: InterruptedException) {} finally {
        p.stop()
        p.processFinish()
      }
    }

    // Send notification that the device has been launched.
    val messageBus = project?.messageBus ?: ApplicationManager.getApplication().messageBus
    messageBus
      .syncPublisher(AvdLaunchListener.TOPIC)
      .avdLaunched(avd, commandLine, requestType, project)

    return EmulatorConnectionListener.getDeviceForEmulator(
      project,
      avd.name,
      processHandler,
      5,
      TimeUnit.MINUTES,
    )
  }

  protected open fun newEmulatorCommand(
    project: Project?,
    emulator: Path,
    avd: AvdInfo,
    forceLaunchInToolWindow: Boolean,
    factory: EmulatorCommandBuilderFactory,
  ): GeneralCommandLine {
    val params = System.getenv("studio.emu.params")?.split(',') ?: emptyList()

    return factory
      .newEmulatorCommandBuilder(emulator, avd)
      .setAvdHome(avdManager!!.baseAvdFolder)
      .setEmulatorSupportsSnapshots(
        this.emulator.getEmulatorFeatures().contains(EmulatorAdvancedFeatures.FAST_BOOT)
      )
      .setStudioParams(writeParameterFile())
      .setLaunchInToolWindow(
        canLaunchInToolWindow(avd, project) &&
          (forceLaunchInToolWindow || getInstance().launchInToolWindow)
      )
      .addAllStudioEmuParams(params)
      .build()
  }

  /** Write HTTP Proxy information to a temporary file. */
  private fun writeParameterFile(): Path? {
    if (!emulator!!.hasStudioParamsSupport()) {
      return null
    }
    val httpInstance = HttpConfigurable.getInstance() ?: return null

    // Extract the proxy information
    val proxyParameters: MutableList<String> = ArrayList()

    val myPropList = httpInstance.getJvmProperties(false, null)
    for (kv in myPropList) {
      when (kv.getFirst()) {
        "http.proxyHost",
        "http.proxyPort",
        "https.proxyHost",
        "https.proxyPort",
        "proxy.authentication.username",
        "proxy.authentication.password" ->
          proxyParameters.add(kv.getFirst() + "=" + kv.getSecond() + "\n")
        else -> {}
      }
    }

    if (proxyParameters.isEmpty()) {
      return null
    }

    return writeTempFile(proxyParameters)?.absoluteFile?.toPath()
  }

  private fun handleAccelerationError(
    project: Project?,
    info: AvdInfo,
    requestType: AvdLaunchListener.RequestType,
    code: AccelerationErrorCode,
  ): ListenableFuture<IDevice> {
    if (code.solution == SolutionCode.NONE) {
      return Futures.immediateFailedFuture(
        DeviceActionException("${code.problem}\n\n${code.solutionMessage}\n")
      )
    }

    // noinspection ConstantConditions, UnstableApiUsage
    return Futures.transformAsync(
      showAccelerationErrorDialog(code, project),
      { result -> tryFixingAccelerationError(result, project, info, requestType, code) },
      MoreExecutors.directExecutor(),
    )
  }

  private fun showAccelerationErrorDialog(
    code: AccelerationErrorCode,
    project: Project?,
  ): ListenableFuture<Int> {
    return edtListeningExecutorService.submit<Int> {
      val hypervisor = if (SystemInfo.isLinux) "KVM" else "Intel HAXM"
      val message =
        "$hypervisor is required to run this AVD.\n\n${code.problem}\n\n${code.solutionMessage}"
      Messages.showOkCancelDialog(
        project,
        message,
        code.solution.description,
        Messages.getOkButton(),
        Messages.getCancelButton(),
        AllIcons.General.WarningDialog,
      )
    }
  }

  private fun tryFixingAccelerationError(
    result: Int,
    project: Project?,
    info: AvdInfo,
    requestType: AvdLaunchListener.RequestType,
    code: AccelerationErrorCode,
  ): ListenableFuture<IDevice> {
    if (result == Messages.CANCEL) {
      return Futures.immediateFailedFuture(DeviceActionCanceledException("Could not start AVD"))
    }

    val future = SettableFuture.create<IDevice>()

    ApplicationManager.getApplication()
      .invokeLater(
        AccelerationErrorSolution.getActionForFix(
          code,
          project,
          { future.setFuture(startAvd(project, info, requestType)) },
          {
            future.setException(DeviceActionCanceledException("Retry after fixing problem by hand"))
          },
        )
      )

    return future
  }

  /**
   * Update the given AVD with the new settings or create one if no AVD is specified. Returns the
   * created AVD.
   */
  fun createOrUpdateAvd(
    currentInfo: AvdInfo?,
    avdName: String,
    device: Device,
    systemImageDescription: SystemImageDescription,
    orientation: ScreenOrientation,
    isCircular: Boolean,
    sdCard: SdCard?,
    skinFolder: Path?,
    hardwareProperties: Map<String, String>,
    userSettings: Map<String, String>?,
    removePrevious: Boolean,
  ): AvdInfo? {
    var skinFolder = skinFolder
    val hardwareProperties = hardwareProperties.toMutableMap()
    avdManager ?: return null
    checkNotNull(sdkHandler)

    val avdFolder =
      try {
        currentInfo?.dataFolderPath ?: AvdInfo.getDefaultAvdFolder(avdManager, avdName, true)
      } catch (e: Throwable) {
        IJ_LOG.error("Could not create AVD $avdName", e)
        return null
      }

    val resolution = checkNotNull(device.getScreenSize(orientation))
    if (skinFolder == null && isCircular) {
      val skinFile = getRoundSkin(systemImageDescription)
      skinFolder = if (skinFile == null) null else sdkHandler.toCompatiblePath(skinFile)
    }
    if (skinFolder == SkinUtils.noSkin()) {
      skinFolder = null
    }
    val skin =
      if (skinFolder == null) {
        GenericSkin(
          Math.round(resolution.getWidth()).toInt(),
          Math.round(resolution.getHeight()).toInt(),
        )
      } else {
        OnDiskSkin(skinFolder)
      }
    if (orientation == ScreenOrientation.LANDSCAPE) {
      hardwareProperties[HardwareProperties.HW_INITIAL_ORIENTATION] =
        ScreenOrientation.LANDSCAPE.shortDisplayValue.lowercase()
    }
    if (currentInfo != null && avdName != currentInfo.name && removePrevious) {
      val success = avdManager.moveAvd(currentInfo, avdName, currentInfo.dataFolderPath)
      if (!success) {
        return null
      }
    }

    return avdManager.createAvd(
      avdFolder,
      avdName,
      systemImageDescription.systemImage,
      skin,
      sdCard,
      hardwareProperties,
      userSettings,
      device.bootProps,
      device.hasPlayStore(),
      false,
      removePrevious,
    )
  }

  private fun getRoundSkin(systemImageDescription: SystemImageDescription): File? {
    for (skin in systemImageDescription.skins) {
      if (skin.fileName.toString().contains("Round")) {
        return FileOpUtils.toFile(skin)
      }
    }
    return null
  }

  open fun findAvd(avdId: String): AvdInfo? {
    return avdManager?.getAvd(avdId, false)
  }

  fun avdExists(candidate: String): Boolean {
    return findAvd(candidate) != null
  }

  fun reloadAvd(avdId: String): AvdInfo? {
    val avd = findAvd(avdId)
    if (avd != null) {
      return avdManager?.reloadAvd(avd)
    }
    return null
  }

  fun wipeUserDataAsync(avd: AvdInfo): ListenableFuture<Boolean> {
    return Futures.submit<Boolean>({ wipeUserData(avd) }, AppExecutorUtil.getAppExecutorService())
  }

  @Slow
  fun wipeUserData(avdInfo: AvdInfo): Boolean {
    avdManager ?: return false
    checkNotNull(sdkHandler)
    // Delete the current user data file
    val path = avdInfo.dataFolderPath.resolve(AvdManager.USERDATA_QEMU_IMG)
    if (Files.exists(path)) {
      try {
        PathUtils.deleteRecursivelyIfExists(path)
      } catch (e: IOException) {
        return false
      }
    }
    // Delete the snapshots directory
    val snapshotDirectory = avdInfo.dataFolderPath.resolve(AvdManager.SNAPSHOTS_DIRECTORY)
    try {
      PathUtils.deleteRecursivelyIfExists(snapshotDirectory)
    } catch (ignore: IOException) {}

    return true
  }

  /**
   * Computes a reasonable display name for a newly-created AVD with the given device and version.
   */
  fun getDefaultDeviceDisplayName(device: Device, version: AndroidVersion): String {
    val name = AvdNames.getDefaultDeviceDisplayName(device, version)
    return avdManager?.uniquifyDisplayName(name) ?: name
  }

  fun findAvdWithDisplayName(name: String): Boolean {
    return avdManager?.findAvdWithDisplayName(name) != null
  }

  /**
   * Get a version of `candidateBase` modified such that it is a valid filename. Invalid characters
   * will be removed, and if requested the name will be made unique.
   *
   * @param candidateBase the name on which to base the avd name.
   * @param uniquify if true, _n will be appended to the name if necessary to make the name unique,
   *   where n is the first number that makes the filename unique.
   * @return The modified filename.
   */
  fun cleanAvdName(candidateBase: String, uniquify: Boolean): String {
    val cleaned = cleanAvdName(candidateBase)
    return if (uniquify && avdManager != null) avdManager.uniquifyAvdName(cleaned) else cleaned
  }

  companion object {
    private val IJ_LOG = Logger.getInstance(AvdManagerConnection::class.java)
    private val SDK_LOG: ILogger = LogWrapper(IJ_LOG)
    private val REPO_LOG: ProgressIndicator =
      StudioLoggerProgressIndicator(AvdManagerConnection::class.java)
    private val NULL_CONNECTION = AvdManagerConnection(null, null)

    private val ourAvdCache: MutableMap<Path?, AvdManagerConnection> = WeakHashMap()

    private var ourConnectionFactory: (AndroidSdkHandler, Path) -> AvdManagerConnection =
      ::defaultConnectionFactory

    private fun defaultConnectionFactory(sdkHandler: AndroidSdkHandler, avdHomeFolder: Path) =
      AvdManagerConnection(sdkHandler, IdeAvdManagers.getAvdManager(sdkHandler, avdHomeFolder))

    @JvmStatic
    fun getDefaultAvdManagerConnection(): AvdManagerConnection {
      return getAvdManagerConnection(AndroidSdks.getInstance().tryToChooseSdkHandler())
    }

    @Synchronized
    fun getAvdManagerConnection(handler: AndroidSdkHandler): AvdManagerConnection {
      return ourAvdCache.computeIfAbsent(handler.location ?: return NULL_CONNECTION) {
        ourConnectionFactory(handler, AndroidLocationsSingleton.avdLocation)
      }
    }

    /**
     * Sets the factory to be used for creating connections, so subclasses can be injected for
     * testing.
     *
     * Note that the passed path is always AndroidLocationsSingleton.avdLocation, but tests may
     * ignore it and use their own AVD directory.
     */
    @JvmStatic
    @TestOnly
    @Synchronized
    fun setConnectionFactory(factory: (AndroidSdkHandler, Path) -> AvdManagerConnection) {
      ourAvdCache.clear()
      ourConnectionFactory = factory
    }

    @JvmStatic
    @TestOnly
    fun resetConnectionFactory() {
      setConnectionFactory(::defaultConnectionFactory)
    }

    /** Checks whether the emulator can be launched in the Running Device tool window. */
    private fun canLaunchInToolWindow(avd: AvdInfo, project: Project?): Boolean {
      return project != null &&
        ToolWindowManager.getInstance(project).getToolWindow("Running Devices") != null
    }

    @JvmStatic
    fun isFoldable(avd: AvdInfo): Boolean {
      val displayRegionWidth = avd.getProperty("hw.displayRegion.0.1.width")
      return displayRegionWidth != null && "0" != displayRegionWidth
    }

    /**
     * Creates a temporary file and write some parameters into it. This is how we pass parameters to
     * the Emulator (other than on the command line). The file is marked to be deleted when Studio
     * exits. This is to increase security in case the file contains sensitive information.
     *
     * @param fileContents What should be written to the file.
     * @return The temporary file. This will be null if we could not create or write the file.
     */
    @JvmStatic
    fun writeTempFile(fileContents: List<String>): File? {
      var tempFile: File? = null
      try {
        tempFile = FileUtil.createTempFile("emu", ".tmp", true)
        tempFile.deleteOnExit() // File disappears when Studio exits
        if (
          !tempFile.setReadable(false, false) || // Non-owner cannot read
            !tempFile.setReadable(true, true)
        ) { // Owner can read
          IJ_LOG.warn("Error setting permissions for " + tempFile.absolutePath)
        }

        Files.write(tempFile.toPath(), fileContents, StandardOpenOption.WRITE)
      } catch (e: IOException) {
        // Try to remove the temporary file
        if (tempFile != null) {
          tempFile.delete()
          tempFile = null
        }
      }
      return tempFile
    }

    @JvmStatic
    fun doesSystemImageSupportQemu2(description: SystemImageDescription): Boolean {
      val location = description.systemImage.location
      try {
        CancellableFileIo.list(location).use { files ->
          return files.anyMatch { it.fileName.toString().startsWith("kernel-ranchu") }
        }
      } catch (e: IOException) {
        return false
      }
    }

    @JvmStatic
    fun getRequiredSystemImagePath(avdInfo: AvdInfo): String? {
      val imageSystemDir = avdInfo.properties[ConfigKey.IMAGES_1] ?: return null
      return StringUtil.trimEnd(
        imageSystemDir.replace(File.separatorChar, RepoPackage.PATH_SEPARATOR),
        RepoPackage.PATH_SEPARATOR,
      )
    }
  }
}

private fun OptionalLong.orNull(): Long? = if (isPresent) asLong else null
