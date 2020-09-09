/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.avdmanager.emulator

import com.android.SdkConstants
import com.android.repository.Revision
import com.android.tools.idea.deviceManager.avdmanager.AvdManagerConnection
import com.android.tools.idea.deviceManager.avdmanager.ElevatedCommandLine
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.android.tools.idea.sdk.wizard.VmWizard
import com.android.tools.idea.welcome.install.VmType
import com.google.common.collect.ImmutableList
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingAnsiEscapesAwareProcessHandler
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtilRt
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Solution strings used in [AccelerationErrorCode], and associated Runnables to fix them.
 */
class AccelerationErrorSolution private constructor(
  private val error: AccelerationErrorCode,
  private val project: Project?,
  private val refresh: Runnable?,
  private val cancel: Runnable?
) {
  /**
   * Solution to problems that we can fix from Android Studio.
   */
  enum class SolutionCode(val description: String) {
    NONE("Troubleshoot"),
    DOWNLOAD_EMULATOR("Install Emulator"),
    UPDATE_EMULATOR("Update Emulator"),
    UPDATE_PLATFORM_TOOLS("Update Platform Tools"),
    UPDATE_SYSTEM_IMAGES("Update System Images"),
    INSTALL_KVM("Install KVM"),
    INSTALL_HAXM("Install Haxm"),
    REINSTALL_HAXM("Reinstall Haxm"),
    TURNOFF_HYPER_V("Turn off Hyper-V"),
    INSTALL_GVM("Install Android Emulator Hypervisor Driver for AMD Processors"),
    REINSTALL_GVM("Reinstall Android Emulator Hypervisor Driver for AMD Processors");
  }

  private var changesMade = false
  private val action: Runnable
    get() = when (error.solution) {
      SolutionCode.DOWNLOAD_EMULATOR, SolutionCode.UPDATE_EMULATOR -> Runnable {
        try {
          showQuickFix(ImmutableList.of(SdkConstants.FD_EMULATOR))
        }
        finally {
          reportBack()
        }
      }
      SolutionCode.UPDATE_PLATFORM_TOOLS -> Runnable {
        try {
          showQuickFix(ImmutableList.of(SdkConstants.FD_PLATFORM_TOOLS))
        }
        finally {
          reportBack()
        }
      }
      SolutionCode.UPDATE_SYSTEM_IMAGES -> Runnable {
        try {
          val avdManager = AvdManagerConnection.getDefaultAvdManagerConnection()
          showQuickFix(avdManager.systemImageUpdates)
        }
        finally {
          reportBack()
        }
      }
      SolutionCode.INSTALL_KVM -> Runnable {
        try {
          val install = createKvmInstallCommand()
          if (install == null) {
            BrowserUtil.browse(KVM_INSTRUCTIONS, project)
          }
          else {
            val text = """Linux systems vary a great deal; the installation steps we will attempt may not work in your particular scenario.

The steps are:

  ${install.commandLineString}

If you prefer, you can skip this step and perform the KVM installation steps on your own.

There might be more details at: $KVM_INSTRUCTIONS
"""
            val response = Messages
              .showDialog(text, error.solution.description, arrayOf("Skip", "Proceed"), 1, Messages.getQuestionIcon())
            if (response == 1) {
              try {
                execute(install)
                changesMade = true
              }
              catch (ex: ExecutionException) {
                LOG.error(ex)
                BrowserUtil.browse( KVM_INSTRUCTIONS, project)
                Messages.showWarningDialog(
                  project,
                  """
                  Automatic KVM installation failed, please retry manually.

                  For more details on the automatic installation failure, please consult the IDE log (Help | Show Log)
                  """.trimIndent(),
                  "Installation Failed")
              }
            }
            else {
              BrowserUtil.browse( KVM_INSTRUCTIONS, project)
            }
          }
        }
        finally {
          reportBack()
        }
      }
      SolutionCode.INSTALL_HAXM, SolutionCode.REINSTALL_HAXM, SolutionCode.INSTALL_GVM, SolutionCode.REINSTALL_GVM -> {
        val type = if (error.solution == SolutionCode.INSTALL_GVM || error.solution == SolutionCode.REINSTALL_GVM) VmType.GVM else VmType.HAXM
        Runnable {
          changesMade = try {
            val wizard = VmWizard(false, type)
            wizard.init()
            wizard.showAndGet()
          }
          finally {
            reportBack()
          }
        }
      }
      SolutionCode.TURNOFF_HYPER_V -> Runnable {
        try {
          val turnHyperVOff: GeneralCommandLine = ElevatedCommandLine().withTempFilePrefix("turn_hypervisor_off")
          turnHyperVOff.exePath = "bcdedit"
          turnHyperVOff.addParameters( "/set", "hypervisorlaunchtype", "off")
          turnHyperVOff.setWorkDirectory( FileUtilRt.getTempDirectory())
          try {
            execute( turnHyperVOff)
            promptAndReboot( SOLUTION_REBOOT_AFTER_TURNING_HYPER_V_OFF)
          }
          catch (ex: ExecutionException) {
            LOG.error( ex)
            Messages.showWarningDialog( project, SOLUTION_TURN_OFF_HYPER_V, "Operation Failed")
          }
        }
        finally {
          reportBack()
        }
      }
      else -> Runnable {
        try {
          Messages.showWarningDialog(project, error.solutionMessage, error.solution.description)
        }
        finally {
          reportBack()
        }
      }
    }

  private fun showQuickFix(requested: List<String>) {
    SdkQuickfixUtils.createDialogForPaths(project, requested)?.also {
      it.show()
      if (it.exitCode == DialogWrapper.OK_EXIT_CODE) {
        changesMade = true
      }
    }
  }

  private fun reportBack() {
    val reporter = if (changesMade) refresh else cancel
    if (reporter != null) {
      ApplicationManager.getApplication().invokeLater(reporter)
    }
  }

  companion object {
    private val LOG: Logger get() = logger<AccelerationErrorNotificationPanel>()
    private val KARMIC_KERNEL = Revision.parseRevision("2.6.31")
    private const val KVM_INSTRUCTIONS = "https://help.ubuntu.com/community/KVM/Installation"
    const val SOLUTION_NESTED_VIRTUAL_MACHINE = """Unfortunately, the Android Emulator can't support virtual machine acceleration from within a virtual machine.
Here are some of your options:
 1) Use a physical device for testing
 2) Start the emulator on a non-virtualized operating system
 3) Use an Android Virtual Device based on an ARM system image (This is 10x slower than hardware accelerated virtualization)
"""
    const val SOLUTION_ACCELERATION_NOT_SUPPORTED = """Unfortunately, your computer does not support hardware accelerated virtualization.
Here are some of your options:
 1) Use a physical device for testing
 2) Develop on a Windows/OSX computer with an Intel processor that supports VT-x and NX
 3) Develop on a Linux computer that supports VT-x or SVM
 4) Use an Android Virtual Device based on an ARM system image
   (This is 10x slower than hardware accelerated virtualization)
"""
    const val SOLUTION_TURN_OFF_HYPER_V = """Unfortunately, you cannot have Hyper-V running and use the emulator.
Here is what you can do:
  1) Start a command prompt as Administrator
  2) Run the following command: C:\Windows\system32> bcdedit /set hypervisorlaunchtype off
  3) Reboot your machine.
"""
    const val SOLUTION_REBOOT_AFTER_TURNING_HYPER_V_OFF = """Hyper-V was successfully turned off. However a system restart is required for this to take effect.

Do you want to reboot now?
"""
    private val ourRebootRequestedAsync = AtomicBoolean(false)

    /**
     * Returns a [Runnable] with code for applying the solution for a given problem [AccelerationErrorCode].
     * In some cases all we can do is present some text to the user in which case the returned [Runnable] will simply
     * display a dialog box with text that the user can use as a guide for solving the problem on their own.<br></br>
     * In other cases we can install the component that is required.<br></br>
     * It is guaranteed that one and only one of the callbacks `refresh` and `cancel` is called if they are both supplied.
     * @param error the problem we are creating an action for
     * @param project the project (may be null but this circumvents certain updates)
     * @param refresh a [Runnable] to execute after a change has been applied
     * @param cancel a [Runnable] to execute if no change was applied
     * @return a [Runnable] to "fix" or display a "fix" for/to the user
     */
    @JvmStatic
    fun getActionForFix(
      error: AccelerationErrorCode,
      project: Project?,
      refresh: Runnable?,
      cancel: Runnable?
    ): Runnable = AccelerationErrorSolution(error, project, refresh, cancel).action

    /**
     * Prompts the user to reboot now, and performs the reboot if accepted.
     * HAXM Installer may need a reboot only on Windows, so this method is intended to work only on Windows
     * and only for HAXM installer use case
     *
     * @param prompt the message to display to the user
     * @exception ExecutionException if the shutdown command fails to execute
     */
    @Throws(ExecutionException::class)
    fun promptAndReboot(prompt: String) {
      val response = Messages
        .showOkCancelDialog(null, prompt, "Reboot Now", "Reboot", "Cancel", Messages.getQuestionIcon())
      if (response == Messages.OK) {
        val reboot: GeneralCommandLine = ElevatedCommandLine().apply {
          exePath = "shutdown"
          addParameters("/g", "/t", "10") // shutdown & restart after a 10 sec delay
          setWorkDirectory(FileUtilRt.getTempDirectory())
        }
        execute(reboot)
      }
    }

    /**
     * Async version of [.promptAndReboot], which uses [ModalityState] to determine when to prompt for the reboot.
     * This is required when a reboot may be requested from multiple places during one wizard execution, and:
     * - we don't want the prompt to appear before the wizard finishes
     * - we don't want the prompt to appear several times
     *
     * If one reboot request is already queued, subsequent calls will be a no-op even when called with a different
     * [ModalityState] or reboot message. Once a prompt is released to the user and the choice has been made,
     * another call to this method will queue another reboot request.
     *
     * @param prompt the message to display to the user
     * @param modality ModalityState which determines when the reboot prompt will actually appear
     */
    @JvmStatic
    fun promptAndRebootAsync(prompt: String, modality: ModalityState) {
      if (ourRebootRequestedAsync.compareAndSet(false, true)) {
        ApplicationManager.getApplication().invokeLater(
          {
            try {
              promptAndReboot(prompt)
            }
            catch (e: ExecutionException) {
              LOG.warn("Automatic reboot attempt failed due to an exception", e)
              Messages.showErrorDialog("Reboot attempt failed. Please reboot manually", "Automatic Reboot")
            }
            ourRebootRequestedAsync.set(false)
          }, modality)
      }
    }

    @Throws(ExecutionException::class)
    private fun execute(command: String, vararg parameters: String): String {
      return execute(generateCommand(command, *parameters))
    }

    private fun generateCommand(command: String, vararg parameters: String): GeneralCommandLine = GeneralCommandLine().apply {
      exePath = command
      addParameters(*parameters)
    }

    @Throws(ExecutionException::class)
    private fun execute(commandLine: GeneralCommandLine): String {
      val exitValue: Int
      val process = CapturingAnsiEscapesAwareProcessHandler(commandLine)
      val output = process.runProcess()
      exitValue = output.exitCode
      return if (exitValue == 0) {
        output.stdout
      }
      else {
        throw ExecutionException(String.format("Error running: %1\$s", process.commandLine))
      }
    }

    private fun createKvmInstallCommand(): GeneralCommandLine? {
      try {
        val version = execute("uname", "-r")
        val revision = toRevision(version)
        return if (revision <= KARMIC_KERNEL) {
          generateCommand("gksudo", "aptitude -y", "install", "kvm", "libvirt-bin", "ubuntu-vm-builder", "bridge-utils")
        }
        else {
          generateCommand("gksudo", "apt-get --assume-yes", "install", "qemu-kvm", "libvirt-bin", "ubuntu-vm-builder", "bridge-utils")
        }
      }
      catch (ex: ExecutionException) {
        LOG.error(ex)
      }
      catch (ex: NumberFormatException) {
        LOG.error(ex)
      }
      return null
    }

    private fun toRevision(version: String): Revision = Revision.parseRevision(version.substringBefore('-'))
  }

  init {
    assert(error !== AccelerationErrorCode.ALREADY_INSTALLED)
  }
}