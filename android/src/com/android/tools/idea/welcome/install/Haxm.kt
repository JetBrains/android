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

import com.android.sdklib.devices.Storage
import com.android.tools.idea.avdmanager.AccelerationErrorSolution.SolutionCode
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.welcome.wizard.deprecated.HaxmInstallSettingsStep
import com.android.tools.idea.welcome.wizard.deprecated.ProgressStep
import com.android.tools.idea.welcome.wizard.deprecated.VmUninstallInfoStep
import com.android.tools.idea.wizard.dynamic.DynamicWizardStep
import com.android.tools.idea.wizard.dynamic.ScopedStateStore
import com.android.tools.idea.wizard.dynamic.ScopedStateStore.Companion.createKey
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.io.IOException

private val KEY_EMULATOR_MEMORY_MB = createKey("emulator.memory", ScopedStateStore.Scope.PATH, Int::class.java)

// In UI we cannot use longs, so we need to pick a unit other then byte
@JvmField
val UI_UNITS = Storage.Unit.MiB

/**
 * Intel® HAXM installable component
 */
class Haxm(
  installationIntention: InstallationIntention,
  store: ScopedStateStore,
  isCustomInstall: ScopedStateStore.Key<Boolean>
) : Vm(InstallerInfo, store, installationIntention, isCustomInstall) {
  override val installUrl = if (SystemInfo.isWindows) HAXM_WINDOWS_INSTALL_URL else HAXM_MAC_INSTALL_URL
  override val filePrefix = "haxm"

  private val recommendedMemoryAllocation: Int
    get() = getRecommendedHaxmMemory(AvdManagerConnection.getMemorySize())

  @Throws(WizardException::class)
  override fun getMacBaseCommandLine(source: File): GeneralCommandLine {
    // The new executable now requests admin access and executes the shell script. We need to make sure both exist and are executable.
    ensureExistsAndIsExecutable(source, "silent_install.sh")
    val executable = ensureExistsAndIsExecutable(source, "HAXM installation")
    return GeneralCommandLine(executable.absolutePath).withWorkDirectory(source)
  }

  override fun init(progressStep: ProgressStep) {
    super.init(progressStep)
    if (installationIntention === InstallationIntention.INSTALL_WITH_UPDATES
        || installationIntention === InstallationIntention.INSTALL_WITHOUT_UPDATES) {
      stateStore.put(KEY_EMULATOR_MEMORY_MB, recommendedMemoryAllocation)
    }
  }

  override fun createSteps(): Collection<DynamicWizardStep> =
    setOf(if (installationIntention === InstallationIntention.UNINSTALL) VmUninstallInfoStep(VmType.HAXM)
          else HaxmInstallSettingsStep(isCustomInstall, key, KEY_EMULATOR_MEMORY_MB))

  /**
   * Create a platform-dependant command line for running the silent HAXM installer.
   *
   * @return command line object
   * @throws WizardException If there's any wizard-specific problem with the installation
   * @throws IOException if there's a problem creating temporary files
   */
  @Throws(WizardException::class, IOException::class)
  override fun getInstallCommandLine(sdk: File): GeneralCommandLine {
    val memorySize = stateStore.getNotNull(KEY_EMULATOR_MEMORY_MB, recommendedMemoryAllocation)
    return addInstallParameters(super.getInstallCommandLine(sdk), memorySize)
  }

  /**
   * Modifies cl with parameters used during installation and returns it.
   * @param cl The command line for the base command. Modified in-place by this method.
   * @param memorySize The memory that haxm should use
   * @return cl
   */
  private fun addInstallParameters(cl: GeneralCommandLine, memorySize: Int): GeneralCommandLine {
    cl.addParameters("-m", memorySize.toString())
    return cl
  }

  companion object InstallerInfo : VmInstallerInfo("Intel® HAXM") {
    override val vendor = "intel"
    override val installSolution = SolutionCode.INSTALL_HAXM
    override val reinstallSolution = SolutionCode.REINSTALL_HAXM
    override val compatibleSystem = SystemInfo.isMac || (SystemInfo.isWindows && CpuVendor.isIntel())
    override val componentPath = "Hardware_Accelerated_Execution_Manager"
  }
}