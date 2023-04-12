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
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.avdmanager.ElevatedCommandLine
import com.android.tools.idea.welcome.wizard.deprecated.ProgressStep
import com.android.tools.idea.wizard.dynamic.ScopedStateStore
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
private val LOG: Logger
  get() = logger<Vm>()

abstract class Vm(
  private val installerInfo: VmInstallerInfo,
  @JvmField val installationIntention: InstallationIntention,
  @JvmField val isCustomInstall: ScopedStateStore.Key<Boolean>
) : InstallableComponent("Performance (${installerInfo.fullName})",
                         "Enables a hardware-assisted virtualization engine (hypervisor) to speed up " +
                         "Android app emulation on your development computer. (Recommended)",
                         installationIntention === InstallationIntention.INSTALL_WITH_UPDATES) {

  var isInstallerSuccessfullyCompleted: Boolean = false
    private set

  /** url with information on this component */
  protected abstract val installUrl: String
  /** prefix used for files and directories during install */
  protected abstract val filePrefix: String

  private lateinit var progressStep: ProgressStep

  public override val requiredSdkPackages
    get() = listOf(installerInfo.repoPackagePath)

  override fun init(progressStep: ProgressStep) {
    this.progressStep = progressStep
  }

  /**
   * Create a platform-dependant command line for running the silent installer.
   *
   * @return command line object
   * @throws WizardException If there's any wizard-specific problem with the installation
   * @throws IOException if there's a problem creating temporary files
   */
  @Throws(IOException::class, WizardException::class)
  protected open fun getInstallCommandLine(sdk: File): GeneralCommandLine {
    check(installerInfo.canRun()) { "Unsupported OS" }
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
    val logFile = FileUtil.createTempFile("${filePrefix}_log", ".txt")
    return ElevatedCommandLine(batFile.absolutePath)
      .withTempFilePrefix(filePrefix)
      .withWorkDirectory(source)
      .withParameters("-log", logFile.path)
  }

  override fun configure(installContext: InstallContext, sdkHandler: AndroidSdkHandler) {
    val sdkRoot = sdkHandler.location?.toFile()
    if (sdkRoot == null) {
      installContext.print(
        "${installerInfo.fullName} installer could not be run because SDK root isn't specified", ConsoleViewContentType.ERROR_OUTPUT)
      return
    }
    if (installationIntention === InstallationIntention.UNINSTALL) {
      configureForUninstall(installContext, sdkRoot)
      return
    }
    val accelerationErrorCode = installerInfo.checkInstallation()
    var solution = accelerationErrorCode.solution
    if (accelerationErrorCode == AccelerationErrorCode.ALREADY_INSTALLED) {
      if (installationIntention === InstallationIntention.INSTALL_WITHOUT_UPDATES) {
        isInstallerSuccessfullyCompleted = true
        return
      }
      // Force reinstall
      solution = installerInfo.reinstallSolution
    }
    when (solution) {
      installerInfo.installSolution, installerInfo.reinstallSolution -> try {
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
        val message = "Unable to install ${installerInfo.fullName}\n${accelerationErrorCode.problem}\n${accelerationErrorCode.solutionMessage}"
        installContext.print(message, ConsoleViewContentType.ERROR_OUTPUT)
      }
      else -> {}
    }
  }

  private fun printExceptionMessage(e: Exception,
                                    installContext: InstallContext) {
    LOG.warn("Tried to install ${installerInfo.fullName} on ${Platform.current().name} OS with " +
             "${AvdManagerConnection.getMemorySize()} memory size", e)
    installContext.print("Unable to install ${installerInfo.fullName}\n", ConsoleViewContentType.ERROR_OUTPUT)
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
      installContext.print("${installerInfo.fullName} is not installed, so not proceeding with its uninstallation.",
                           ConsoleViewContentType.NORMAL_OUTPUT)
      isInstallerSuccessfullyCompleted = true
    }
  }

  private fun isInstalled(context: InstallContext, sdkRoot: File): Boolean {
    val printError = { exception: Exception ->
      context.print("Failed to determine whether ${installerInfo.fullName} is installed: ${exception.message}",
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
        text = installerInfo.runningInstallerMessage
      }
      installContext.print(installerInfo.runningInstallerMessage + "\n", ConsoleViewContentType.SYSTEM_OUTPUT)
      val process = CapturingAnsiEscapesAwareProcessHandler(commandLine)
      val output = StringBuffer()
      process.addProcessListener(object : ProcessAdapter() {
        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
          output.append(event.text)
          super.onTextAvailable(event, outputType)
        }
      })
      progressStep.attachToProcess(process)
      val exitCode = process.runProcess().exitCode
      // More testing of bash scripts invocation with intellij process wrappers might be useful.
      if (exitCode != INSTALLER_EXIT_CODE_SUCCESS) {
        // According to the installer docs for Windows, installer may signify that a reboot is required
        if (SystemInfo.isWindows && exitCode == INSTALLER_EXIT_CODE_REBOOT_REQUIRED) {
          val rebootMessage = "Reboot required: ${installerInfo.fullName} installation succeeded, however the installer reported that a " +
                              "reboot is required in order for the changes to take effect"
          installContext.print(rebootMessage, ConsoleViewContentType.NORMAL_OUTPUT)
          AccelerationErrorSolution.promptAndRebootAsync(rebootMessage, ModalityState.NON_MODAL)
          isInstallerSuccessfullyCompleted = true
          progressIndicator?.apply { fraction = 1.0 }
          return
        }
        // The vm is not required so we do not stop setup process if this install failed.
        if (installationIntention === InstallationIntention.UNINSTALL) {
          installContext.print("${installerInfo.fullName} uninstallation failed", ConsoleViewContentType.ERROR_OUTPUT)
        }
        else {
          installContext.print(
            "${installerInfo.fullName} installation failed. To install ${installerInfo.fullName} " +
            "follow the instructions found at: $installUrl",
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
      installContext.print("Unable to run ${installerInfo.fullName} installer: ${e.message}\n", ConsoleViewContentType.ERROR_OUTPUT)
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
    File(sdk, FileUtil.join(SdkConstants.FD_EXTRAS, installerInfo.vendor, installerInfo.componentPath))
}

abstract class VmInstallerInfo(internal val fullName: String) {
  /** vendor, used as the second component of the sdk path */
  abstract val vendor: String
  /** last component of the sdk path */
  abstract val componentPath: String
  abstract val reinstallSolution: SolutionCode
  abstract val installSolution: SolutionCode

  val repoPackagePath
    get() = "extras;$vendor;$componentPath"
  val runningInstallerMessage
    get() = "Running $fullName installer"

  private var ourInitialCheck: AccelerationErrorCode? = null
  /** If set, an error code indicating that this component is incompatible with this system (os/cpu architecture) */
  protected abstract val incompatibleSystemError: AccelerationErrorCode?

  /**
   * Check the status of the vm installation.
   *
   * If the vm is installed we return the error code:
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
    incompatibleSystemError ?: AvdManagerConnection.getDefaultAvdManagerConnection().checkAcceleration()

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
