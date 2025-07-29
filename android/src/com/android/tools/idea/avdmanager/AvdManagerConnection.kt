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
import com.android.prefs.AndroidLocationsException
import com.android.prefs.AndroidLocationsSingleton
import com.android.repository.api.ProgressIndicator
import com.android.repository.api.RepoPackage
import com.android.sdklib.deviceprovisioner.DeviceActionCanceledException
import com.android.sdklib.deviceprovisioner.DeviceActionException
import com.android.sdklib.deviceprovisioner.ProcessHandleProvider
import com.android.sdklib.devices.Abi
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdManager
import com.android.sdklib.internal.avd.ConfigKey
import com.android.sdklib.internal.avd.ConfigKey.SKIN_PATH
import com.android.sdklib.internal.avd.EmulatorAdvancedFeatures
import com.android.sdklib.internal.avd.EmulatorPackage
import com.android.sdklib.internal.avd.getEmulatorPackage
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.avdmanager.AccelerationErrorSolution.SolutionCode
import com.android.tools.idea.avdmanager.AvdManagerConnection.Companion.NULL_CONNECTION
import com.android.tools.idea.avdmanager.DeviceSkinUpdater.updateSkin
import com.android.tools.idea.avdmanager.emulatorcommand.BootWithSnapshotEmulatorCommandBuilder
import com.android.tools.idea.avdmanager.emulatorcommand.ColdBootEmulatorCommandBuilder
import com.android.tools.idea.avdmanager.emulatorcommand.DefaultEmulatorCommandBuilderFactory
import com.android.tools.idea.avdmanager.emulatorcommand.EmulatorCommandBuilder
import com.android.tools.idea.avdmanager.emulatorcommand.EmulatorCommandBuilderFactory
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.IdeAvdManagers
import com.android.tools.idea.streaming.EmulatorSettings
import com.android.utils.PathUtils
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import com.intellij.credentialStore.isFulfilled
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportProgress
import com.intellij.platform.util.progress.withProgressText
import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxyConfiguration.StaticProxyConfiguration
import com.intellij.util.net.ProxyCredentialStore
import com.intellij.util.net.ProxySettings
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.WeakHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.listDirectoryEntries
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly

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
  private val uiContext: CoroutineContext = Dispatchers.EDT + ModalityState.any().asContextElement(),
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

  /** Stops the emulator if it is running and waits for it to terminate. */
  @Slow
  @JvmOverloads
  fun stopAvd(avd: AvdInfo, forcibly: Boolean = false) {
    // TODO: Move the implementation to AvdManager when it starts targeting JDK 9+.
    avdManager ?: return
    val pid = avdManager.getPid(avd)
    if (pid != 0L) {
      ProcessHandleProvider.getProcessHandle(pid)?.let {
        // Kill the emulator process if it is running.
        val termination = it.onExit()
        if (!termination.isDone) {
          val success = if (forcibly) it.destroyForcibly() else it.destroy()
          if (success) {
            service<RunningAvdTracker>().shuttingDown(avd.id)
            try {
              // Wait for the emulator process to terminate.
              termination.get()
            } catch (_: Exception) {}
          }
        }
      }
    }
  }

  /**
   * Starts the emulator without mounting a file to store or load state snapshots, forcing a full
   * boot and disabling state snapshot functionality.
   */
  suspend fun coldBoot(
    project: Project?,
    avd: AvdInfo,
    forceLaunchInToolWindow: Boolean = false,
  ): IDevice {
    return startAvd(project, avd, forceLaunchInToolWindow, ::ColdBootEmulatorCommandBuilder)
  }

  /** Starts the emulator, booting from the "default_boot" snapshot. */
  suspend fun quickBoot(
    project: Project?,
    avd: AvdInfo,
    forceLaunchInToolWindow: Boolean = false,
  ): IDevice {
    return startAvd(project, avd, forceLaunchInToolWindow, ::EmulatorCommandBuilder)
  }

  /**
   * Starts the emulator, booting from the given snapshot (specified as the directory name beneath
   * "snapshots", not a full path).
   */
  suspend fun bootWithSnapshot(
    project: Project?,
    avd: AvdInfo,
    snapshot: String,
    forceLaunchInToolWindow: Boolean = false,
  ): IDevice {
    return startAvd(project, avd, forceLaunchInToolWindow) { emulator, avd ->
      BootWithSnapshotEmulatorCommandBuilder(emulator, avd, snapshot)
    }
  }

  /** Boots the AVD, using its .ini file to determine the booting method. */
  open suspend fun startAvd(
    project: Project?,
    avd: AvdInfo,
    forceLaunchInToolWindow: Boolean = false,
  ): IDevice {
    return startAvd(project, avd, forceLaunchInToolWindow, DefaultEmulatorCommandBuilderFactory())
  }

  suspend fun startAvd(
    project: Project?,
    avd: AvdInfo,
    forceLaunchInToolWindow: Boolean = false,
    factory: EmulatorCommandBuilderFactory,
  ): IDevice {
    avdManager ?: throw DeviceActionException("No Android SDK Found")
    checkNotNull(sdkHandler)

    val skinPath = avd.properties[SKIN_PATH]
    if (skinPath != null) {
      var skin = sdkHandler.toCompatiblePath(skinPath)
      // For historical reasons skin.path in config.ini may be a path relative to SDK
      // rather than its "skins" directory. Remove the "skins" prefix in that case.
      if (!skin.isAbsolute && skin.nameCount > 1 && skin.getName(0).toString() == "skins") {
        skin = skin.subpath(1, skin.nameCount)
      }

      updateSkin(skin)
    }

    return withContext(AndroidDispatchers.workerThread) {
      val code = checkAcceleration(sdkHandler)
      continueToStartAvdIfAccelerationErrorIsNotBlocking(code, project, avd, forceLaunchInToolWindow, factory)
    }
  }

  private suspend fun continueToStartAvdIfAccelerationErrorIsNotBlocking(
    code: AccelerationErrorCode,
    project: Project?,
    avd: AvdInfo,
    forceLaunchInToolWindow: Boolean,
    factory: EmulatorCommandBuilderFactory,
  ): IDevice {
    if (!code.problem.isEmpty()) {
      IJ_LOG.warn(String.format("Launching %s: %s: %s", avd.name, code, code.problem))
    }
    when (code) {
      AccelerationErrorCode.ALREADY_INSTALLED ->
        return continueToStartAvd(project, avd, forceLaunchInToolWindow, factory)
      AccelerationErrorCode.PLATFORM_TOOLS_UPDATE_ADVISED,
      AccelerationErrorCode.SYSTEM_IMAGE_UPDATE_ADVISED ->
        // Launch the virtual device with possibly degraded performance even if there are updates
        // noinspection DuplicateBranchesInSwitch
        return continueToStartAvd(project, avd, forceLaunchInToolWindow, factory)
      AccelerationErrorCode.EMULATOR_UPDATE_REQUIRED,
      AccelerationErrorCode.NO_EMULATOR_INSTALLED ->
        return handleAccelerationError(project, avd, forceLaunchInToolWindow, code)
      else -> {
        val abi =
          Abi.getEnum(avd.abiType)
            ?: return continueToStartAvd(project, avd, forceLaunchInToolWindow, factory)

        if (abi == Abi.X86 || abi == Abi.X86_64) {
          return handleAccelerationError(project, avd, forceLaunchInToolWindow, code)
        }

        // Let ARM and MIPS virtual devices launch without hardware acceleration
        return continueToStartAvd(project, avd, forceLaunchInToolWindow, factory)
      }
    }
  }

  private suspend fun continueToStartAvd(
    project: Project?,
    avd: AvdInfo,
    forceLaunchInToolWindow: Boolean,
    factory: EmulatorCommandBuilderFactory,
  ): IDevice = coroutineScope {
    var avd = avd
    val emulator = emulator
    if (emulator == null) {
      IJ_LOG.error("No emulator binary found!")
      throw DeviceActionException("No emulator installed")
    }

    val emulatorBinary = emulator.emulatorBinary
    if (emulatorBinary == null) {
      IJ_LOG.error("No emulator binary found!")
      throw DeviceActionException("No emulator binary found")
    }

    val avdManager = checkNotNull(avdManager)
    avd = avdManager.reloadAvd(avd) // Reload the AVD in case it was modified externally.
    val avdName = avd.displayName

    val pid = avdManager.getPid(avd)
    if (pid != 0L) {
      if (ProcessHandleProvider.getProcessHandle(pid)?.isAlive == true) {
        // TODO: Bring the running emulator's window to the front.
        throw AvdIsAlreadyRunningException(avd.displayName, pid)
      } else {
        avdManager.deleteLockFiles(avd)
      }
    }

    val commandLine =
      newEmulatorCommand(
        project,
        emulatorBinary,
        avd,
        forceLaunchInToolWindow,
        factory,
      )
    val runner = EmulatorRunner(commandLine, avd)

    val processHandler =
      try {
        runner.start()
      } catch (e: ExecutionException) {
        IJ_LOG.error("Error launching emulator", e)
        throw DeviceActionException(String.format("Error launching emulator %1\$s", avdName), e)
      }

    // It takes >= 8 seconds to start the Emulator. Display a small progress indicator; otherwise,
    // it seems like the action wasn't invoked.
    val progressJob = launch {
      if (project != null) {
        withBackgroundProgress(project, "Launching emulator") {
          reportProgress(80) { reporter ->
            withProgressText("Starting AVD...") {
              repeat(80) {
                reporter.itemStep { delay(100) }
                if (processHandler.isProcessTerminated) {
                  return@withProgressText
                }
              }
            }
          }
        }
      }
    }
    try {
      return@coroutineScope EmulatorConnectionListener.getDeviceForEmulator(
          project,
          avd.name,
          processHandler,
          5,
          TimeUnit.MINUTES,
        )
        .await()
    } finally {
      progressJob.cancel()
    }
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
          (forceLaunchInToolWindow || EmulatorSettings.getInstance().launchInToolWindow)
      )
      .addAllStudioEmuParams(params)
      .build()
  }

  /** Write HTTP Proxy information to a temporary file. */
  private fun writeParameterFile(): Path? {
    // These are defined in the HTTP Proxy section of the Settings dialog.
    // We can only use static HTTP proxies; ignore the other types.
    val config =
      ProxySettings.getInstance().getProxyConfiguration() as? StaticProxyConfiguration
        ?: return null
    val params = config.toStudioParams(ProxyCredentialStore.getInstance())
    if (params.isEmpty()) {
      return null
    }
    return writeTempFile(params)?.absoluteFile?.toPath()
  }

  private suspend fun handleAccelerationError(
    project: Project?,
    info: AvdInfo,
    launchInToolWindow: Boolean,
    code: AccelerationErrorCode,
  ): IDevice {
    if (code.solution == SolutionCode.NONE) {
      throw DeviceActionException("${code.problem}\n\n${code.solutionMessage}\n")
    }

    val result = showAccelerationErrorDialog(code, project)
    if (result == Messages.CANCEL) {
      throw DeviceActionCanceledException("Could not start AVD")
    }

    return tryFixingAccelerationError(project, info, launchInToolWindow, code)
  }

  private suspend fun showAccelerationErrorDialog(
    code: AccelerationErrorCode,
    project: Project?,
  ): Int =
    withContext(uiContext) {
      val message = "${code.problem}\n\n${code.solutionMessage}"
      Messages.showOkCancelDialog(
        project,
        message,
        code.solution.description,
        Messages.getOkButton(),
        Messages.getCancelButton(),
        AllIcons.General.WarningDialog,
      )
    }

  private suspend fun tryFixingAccelerationError(
    project: Project?,
    avd: AvdInfo,
    launchInToolWindow: Boolean,
    code: AccelerationErrorCode,
  ): IDevice {
    val changeWasMade = CompletableDeferred<Boolean>()

    ApplicationManager.getApplication()
      .invokeLater(
        AccelerationErrorSolution.getActionForFix(
          code,
          project,
          { changeWasMade.complete(true) },
          { changeWasMade.complete(false) },
        )
      )

    if (changeWasMade.await()) {
      return startAvd(project, avd, launchInToolWindow)
    } else {
      throw DeviceActionCanceledException("Retry after fixing problem by hand")
    }
  }

  fun reloadAvd(avdFolder: Path): AvdInfo? {
    val avd = findAvdWithFolder(avdFolder)
    if (avd != null) {
      return avdManager?.reloadAvd(avd)
    }
    return null
  }

  /**
   * Kills the emulator if it is running and deletes all AVD files and subdirectories except the
   * ones that were created when the AVD itself was created.
   */
  @Slow
  fun wipeUserData(avdInfo: AvdInfo): Boolean {
    avdManager ?: return false
    checkNotNull(sdkHandler)
    stopAvd(avdInfo, forcibly = true)

    val avdFolder = avdInfo.dataFolderPath
    avdFolder.listDirectoryEntries().forEach { path ->
      if (!avdManager.isFoundationalAvdFile(path, avdInfo)) {
        try {
          PathUtils.deleteRecursivelyIfExists(path)
        } catch (_: IOException) {
          return false
        }
      }
    }
    return true
  }

  open fun findAvdWithFolder(avdFolder: Path): AvdInfo? {
    return avdManager?.findAvdWithFolder(avdFolder)
  }

  companion object {
    private val IJ_LOG = Logger.getInstance(AvdManagerConnection::class.java)
    private val REPO_LOG: ProgressIndicator =
      StudioLoggerProgressIndicator(AvdManagerConnection::class.java)
    // The dispatcher on NULL_CONNECTION is unused. Pass Unconfined rather than the default
    // Dispatchers.EDT + ModalityState.any().asContextElement(), because ModalityState.any()
    // requires Application, which may not exist at class-init time in a test.
    private val NULL_CONNECTION = AvdManagerConnection(null, null, Dispatchers.Unconfined)

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
        ToolWindowManager.getInstance(project).getToolWindow("Running Devices") != null &&
             (StudioFlags.EMBEDDED_EMULATOR_ALLOW_XR_AVD.get() || !avd.isXrHeadsetDevice) &&
             !avd.isXrGlassesDevice // Glasses devices are not supported by the tool window yet.
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
      } catch (_: IOException) {
        // Try to remove the temporary file
        if (tempFile != null) {
          tempFile.delete()
          tempFile = null
        }
      }
      return tempFile
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

internal fun StaticProxyConfiguration.toStudioParams(
  credentialStore: ProxyCredentialStore
): List<String> {
  // The emulator consumes this in settings-page-proxy.cpp:getStudioProxyString().
  val proxyParameters = mutableListOf<String>()
  if (protocol == ProxyConfiguration.ProxyProtocol.HTTP && host.isNotBlank() && port > 0) {
    proxyParameters.add("http.proxyHost=${host}")
    proxyParameters.add("http.proxyPort=${port}")

    val credentials = credentialStore.getCredentials(host, port)
    if (credentials != null && credentials.isFulfilled()) {
      proxyParameters.add("proxy.authentication.username=${credentials.userName}")
      proxyParameters.add("proxy.authentication.password=${credentials.getPasswordAsString()}")
    }
  }
  return proxyParameters
}
