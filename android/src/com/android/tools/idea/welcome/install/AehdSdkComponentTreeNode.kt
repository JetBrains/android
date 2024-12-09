/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.avdmanager.AccelerationErrorCode
import com.android.tools.idea.avdmanager.AccelerationErrorSolution
import com.android.tools.idea.avdmanager.AccelerationErrorSolution.SolutionCode
import com.android.tools.idea.avdmanager.ElevatedCommandLine
import com.android.tools.idea.avdmanager.checkAcceleration
import com.android.tools.idea.memorysettings.MemorySettingsUtil
import com.android.tools.idea.sdk.AndroidSdks
import com.intellij.execution.ExecutionException
import com.intellij.execution.Platform
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingAnsiEscapesAwareProcessHandler
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import java.io.File
import java.io.IOException

private const val INSTALLER_EXIT_CODE_SUCCESS = 0
private const val INSTALLER_EXIT_CODE_REBOOT_REQUIRED = 2
private const val INSTALLER_EXIT_CODE_USER_CANCELLED = 1223
private val LOG: Logger
  get() = logger<AehdSdkComponentTreeNode>()

/**
 * Google AEHD installable component
 */
class AehdSdkComponentTreeNode(
  @JvmField val installationIntention: InstallationIntention
) : InstallableSdkComponentTreeNode("Performance (Android Emulator hypervisor visor})",
                                    "Enables a hardware-assisted virtualization engine (hypervisor) to speed up " +
                         "Android app emulation on your development computer. (Recommended)",
                                    installationIntention == InstallationIntention.INSTALL_WITH_UPDATES) {
  /**
   * Specifies what to do with the AEHD package.
   *
   * For most packages managed by the SDK manager, "installation" means simply unpacking the packages
   * into the appropriate directory beneath the SDK root. For the AEHD packages, however,
   * the contents of the package are only installation (and uninstallation) scripts that install the
   * AEHD in the operating system.
   *
   * Thus, installation has two phases: unpacking the packages (which we call "installation", and
   * running the setup script (which we call "configuration").
   *
   * The AEHD needs to be installed and configured. (Note that "install" here means unpacking the
   * package files into their installation directory below the SDK root, and "configure" means running
   * the installation script that installs the components in the OS.)
   */
  enum class InstallationIntention {
    /**
     * Install and configure. If it is already installed, it will be updated if there is a newer
     * version.
     */
    INSTALL_WITH_UPDATES,
    /** Install and configure. If it is already installed, do nothing. */
    INSTALL_WITHOUT_UPDATES,
    /** Only configure; the package should already be installed. */
    CONFIGURE_ONLY,
    /**
     * Run the uninstallation script to remove it from the operating system, and remove the package.
     */
    UNINSTALL;

    fun isInstall(): Boolean = this == INSTALL_WITHOUT_UPDATES || this == INSTALL_WITH_UPDATES
  }

  var isInstallerSuccessfullyCompleted: Boolean = false
    private set

  public override val requiredSdkPackages
    get() = listOf("extras;google;Android_Emulator_Hypervisor_Driver")

  /**
   * Create a platform-dependant command line for running the silent installer.
   *
   * @return command line object
   * @throws WizardException If there's any wizard-specific problem with the installation
   * @throws IOException if there's a problem creating temporary files
   */
  @Throws(IOException::class, WizardException::class)
  protected open fun getInstallCommandLine(sdk: File): GeneralCommandLine {
    check(canRun()) { "Unsupported OS" }
    return getInstallerBaseCommandLine(sdk)
  }

  @Throws(WizardException::class, IOException::class)
  protected fun getInstallerBaseCommandLine(sdk: File): GeneralCommandLine {
    return when {
      SystemInfo.isWindows -> getWindowsBaseCommandLine(getSourceLocation(sdk))
      else -> throw IllegalStateException("Unsupported OS")
    }
  }

  @Throws(WizardException::class, IOException::class)
  protected open fun getUninstallCommandLine(sdk: File): GeneralCommandLine =
    getInstallerBaseCommandLine(sdk).apply { addParameters("-u") }

  @Throws(IOException::class)
  protected fun getWindowsBaseCommandLine(source: File): GeneralCommandLine {
    val batFile = File(source, "silent_install.bat")
    val logFile = FileUtil.createTempFile("aehd_log", ".txt")
    return ElevatedCommandLine(batFile.absolutePath)
      .withTempFilePrefix("aehd")
      .withWorkDirectory(source)
      .withParameters("-log", logFile.path)
  }

  override fun configure(installContext: InstallContext, sdkHandler: AndroidSdkHandler) {
    val sdkRoot = sdkHandler.location?.toFile()
    if (sdkRoot == null) {
      installContext.print(
        "Android Emulator hypervisor driver installer could not be run because SDK root isn't specified", ConsoleViewContentType.ERROR_OUTPUT)
      return
    }
    if (installationIntention == InstallationIntention.UNINSTALL) {
      configureForUninstall(installContext, sdkRoot)
      return
    }
    val accelerationErrorCode = checkInstallation()
    var solution = accelerationErrorCode.solution
    if (accelerationErrorCode == AccelerationErrorCode.ALREADY_INSTALLED) {
      if (installationIntention == InstallationIntention.INSTALL_WITHOUT_UPDATES) {
        isInstallerSuccessfullyCompleted = true
        return
      }
      // Force reinstall
      solution = reinstallSolution
    }
    when (solution) {
      installSolution, reinstallSolution -> try {
        val commandLine: GeneralCommandLine = getInstallCommandLine(sdkRoot)
        runInstaller(installContext, commandLine)
      }
      catch (e: WizardException) {
        printExceptionMessage(e, installContext)
      }
      catch (e: IOException) {
        printExceptionMessage(e, installContext)
      }
      SolutionCode.NONE -> {
        val message = "Unable to install Android Emulator hypervisor driver\n${accelerationErrorCode.problem}\n${accelerationErrorCode.solutionMessage}"
        installContext.print(message, ConsoleViewContentType.ERROR_OUTPUT)
      }
      else -> {}
    }
  }

  private fun printExceptionMessage(e: Exception,
                                    installContext: InstallContext) {
    LOG.warn("Tried to install Android Emulator hypervisor driver on ${Platform.current().name} OS with " +
             "${MemorySettingsUtil.getMachineMemoryBytes()} memory size", e)
    installContext.print("Unable to install Android Emulator hypervisor driver\n", ConsoleViewContentType.ERROR_OUTPUT)
    var message = e.message ?: "(unknown)"
    if (!StringUtil.endsWithLineBreak(message)) {
      message += "\n"
    }
    installContext.print(message, ConsoleViewContentType.ERROR_OUTPUT)
  }

  private fun configureForUninstall(installContext: InstallContext, sdkRoot: File) {
    if (isInstalled(installContext, sdkRoot)) {
      try {
        val commandLine: GeneralCommandLine = getUninstallCommandLine(sdkRoot)
        runInstaller(installContext, commandLine)
      }
      catch (e: WizardException) {
        printExceptionMessage(e, installContext)
      }
      catch (e: IOException) {
        printExceptionMessage(e, installContext)
      }
    }
    else {
      // The vm is not installed and the intention is to uninstall, so nothing to do here
      // This should not normally be the case unless some of the previous installation/uninstallation
      // operations failed or were executed outside of Studio
      installContext.print("Android Emulator hypervisor driver is not installed, so not proceeding with its uninstallation.",
                           ConsoleViewContentType.NORMAL_OUTPUT)
      isInstallerSuccessfullyCompleted = true
    }
  }

  private fun isInstalled(context: InstallContext, sdkRoot: File): Boolean {
    val printError = { exception: Exception ->
      context.print("Failed to determine whether Android Emulator hypervisor driver is installed: ${exception.message}",
                    ConsoleViewContentType.ERROR_OUTPUT)
    }
    try {
      val command: GeneralCommandLine = getInstallerBaseCommandLine(sdkRoot)
      command.addParameter("-v")
      return OSProcessHandler(command).apply {
        startNotify()
        waitFor()
      }.exitCode == 0
    }
    catch (exception: ExecutionException) {
      printError(exception)
    }
    catch (exception: WizardException) {
      printError(exception)
    }
    catch (exception: IOException) {
      printError(exception)
    }
    return false
  }

  private fun runInstaller(installContext: InstallContext, commandLine: GeneralCommandLine) {
    try {
      val progressIndicator = ProgressManager.getInstance().progressIndicator
      progressIndicator?.apply {
        isIndeterminate = true
        text = runningInstallerMessage
      }
      installContext.print(runningInstallerMessage + "\n", ConsoleViewContentType.SYSTEM_OUTPUT)
      val process = CapturingAnsiEscapesAwareProcessHandler(commandLine)
      val output = StringBuffer()
      process.addProcessListener(object : ProcessAdapter() {
        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
          output.append(event.text)
          super.onTextAvailable(event, outputType)
        }
      })
      installContext.attachToProcess(process)
      val exitCode = process.runProcess().exitCode
      // More testing of bash scripts invocation with intellij process wrappers might be useful.
      if (exitCode != INSTALLER_EXIT_CODE_SUCCESS) {
        // According to the installer docs for Windows, installer may signify that a reboot is required
        if (SystemInfo.isWindows && exitCode == INSTALLER_EXIT_CODE_REBOOT_REQUIRED) {
          val rebootMessage = "Reboot required: Android Emulator hypervisor driver installation succeeded, however the installer reported that a " +
                              "reboot is required in order for the changes to take effect"
          installContext.print(rebootMessage, ConsoleViewContentType.NORMAL_OUTPUT)
          AccelerationErrorSolution.promptAndRebootAsync(rebootMessage, ModalityState.nonModal())
          isInstallerSuccessfullyCompleted = true
          progressIndicator?.apply { fraction = 1.0 }
          return
        }
        // The installer failed to run with administrator privilege
        if (SystemInfo.isWindows && exitCode == INSTALLER_EXIT_CODE_USER_CANCELLED) {
          installContext.print(
            "Android Emulator hypervisor driver installation failed because the operation was cancelled. " +
            "To install Android Emulator hypervisor driver, try the installer again. Make sure to click \"yes\" when " +
            "the installer requests administrator privilege. This has to be done before the request times out.",
            ConsoleViewContentType.ERROR_OUTPUT)
          isInstallerSuccessfullyCompleted = false
          progressIndicator?.apply { fraction = 1.0 }
          return
        }
        // The vm is not required so we do not stop setup process if this install failed.
        if (installationIntention == InstallationIntention.UNINSTALL) {
          installContext.print("Android Emulator hypervisor driver uninstallation failed", ConsoleViewContentType.ERROR_OUTPUT)
        }
        else {
          installContext.print(
            "Android Emulator hypervisor driver installation failed. To install Android Emulator hypervisor driver " +
            "follow the instructions found at: https://github.com/google/android-emulator-hypervisor-driver",
            ConsoleViewContentType.ERROR_OUTPUT)
        }
        val file = Regex("installation log:\\s*\"(.*)\"").find(output.toString())?.groupValues?.get(1)
        if (file != null) {
          installContext.print("Installer log is located at ${file}", ConsoleViewContentType.ERROR_OUTPUT)
          try {
            installContext.print("Installer log contents:\n", ConsoleViewContentType.ERROR_OUTPUT)
            installContext.print(FileUtil.loadFile(File(file), "UTF-16"),
                                 ConsoleViewContentType.NORMAL_OUTPUT)
          }
          catch (e: IOException) {
            installContext.print("Failed to read installer output log.\n", ConsoleViewContentType.ERROR_OUTPUT)
          }
        }
        isInstallerSuccessfullyCompleted = false
      }
      else {
        isInstallerSuccessfullyCompleted = true
      }
      progressIndicator?.apply { fraction = 1.0 }
    }
    catch (e: ExecutionException) {
      installContext.print("Unable to run Android Emulator hypervisor driver installer: ${e.message}\n", ConsoleViewContentType.ERROR_OUTPUT)
      LOG.warn(e)
    }
  }

  @Throws(WizardException::class)
  protected fun ensureExistsAndIsExecutable(path: File, exeName: String): File {
    val executable = File(path, exeName)
    return when {
      !executable.isFile -> throw WizardException("Installer executable is missing: ${executable.absolutePath}")
      executable.canExecute() || executable.setExecutable(true) -> executable
      else -> throw WizardException("Unable to set execute permission bit on installer executable: ${executable.absolutePath}")
    }
  }

  private fun getSourceLocation(sdk: File) =
    File(sdk, FileUtil.join(SdkConstants.FD_EXTRAS, "google", "Android_Emulator_Hypervisor_Driver"))

  companion object InstallerInfo {
    val installSolution = SolutionCode.INSTALL_AEHD
    val reinstallSolution = SolutionCode.REINSTALL_AEHD
    val incompatibleSystemError = when {
      !SystemInfo.isWindows -> AccelerationErrorCode.AEHD_REQUIRES_WINDOWS
      else -> null
    }

    val repoPackagePath
      get() = "extras;google;Android_Emulator_Hypervisor_Driver"
    val runningInstallerMessage
      get() = "Running Android Emulator hypervisor driver installer"

    private var ourInitialCheck: AccelerationErrorCode? = null

    /**
     * Check the status of the AEHD installation.
     *
     * If the AEHD is installed we return the error code:
     *  * [AccelerationErrorCode.ALREADY_INSTALLED]
     * Other possible error conditions:
     *
     *  * On an OS other than Windows
     *  * On Windows (until we fix the headless installer to use an admin account)
     *  * If the CPU is not a supported processor
     *  * If there is not enough memory available
     *  * BIOS is not setup correctly
     *
     * For some of these error conditions the user may rectify the problem and install Aehd later.
     */
    fun checkInstallation(): AccelerationErrorCode =
      incompatibleSystemError ?: checkAcceleration(AndroidSdks.getInstance().tryToChooseSdkHandler())

    /**
     * Return true if it is possible to install on the current machine without any other configuration changes.
     */
    fun canRun(): Boolean {
      val check = ourInitialCheck ?: checkInstallation().also { ourInitialCheck = it }
      return when (check) {
        AccelerationErrorCode.NO_EMULATOR_INSTALLED, AccelerationErrorCode.UNKNOWN_ERROR -> {
          // We don't know if we can install. Assume we can if this is a compatible system:
          incompatibleSystemError == null
        }
        AccelerationErrorCode.NOT_ENOUGH_MEMORY -> false
        AccelerationErrorCode.ALREADY_INSTALLED -> true // just continue anyway
        else -> when (check.solution) {
          installSolution, reinstallSolution -> true
          else -> false
        }
      }
    }
  }
}
