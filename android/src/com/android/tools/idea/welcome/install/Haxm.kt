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
import com.android.tools.idea.avdmanager.AccelerationErrorCode
import com.android.tools.idea.avdmanager.AccelerationErrorSolution.SolutionCode
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.avdmanager.CpuVendor
import com.android.tools.idea.observable.core.IntProperty
import com.android.tools.idea.observable.core.IntValueProperty
import com.android.tools.idea.sdk.install.VmType
import com.android.tools.idea.welcome.wizard.deprecated.HaxmInstallSettingsStep
import com.android.tools.idea.welcome.wizard.deprecated.VmUninstallInfoStep
import com.android.tools.idea.wizard.dynamic.DynamicWizardStep
import com.android.tools.idea.wizard.dynamic.ScopedStateStore
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.io.IOException

// In UI we cannot use longs, so we need to pick a unit other then byte
@JvmField
val UI_UNITS = Storage.Unit.MiB

/**
 * Intel® HAXM installable component
 */
class Haxm(
  installationIntention: InstallationIntention,
  isCustomInstall: ScopedStateStore.Key<Boolean>
) : Vm(InstallerInfo, installationIntention, isCustomInstall) {
  override val installUrl =
    if (SystemInfo.isWindows) HAXM_WINDOWS_INSTALL_URL
    else throw WizardException(AccelerationErrorCode.HAXM_REQUIRES_WINDOWS.problem)
  override val filePrefix = "haxm"

  private val emulatorMemoryMb: IntProperty = IntValueProperty(getRecommendedHaxmMemory(AvdManagerConnection.getMemorySize()))

  override fun createSteps(): Collection<DynamicWizardStep> =
    setOf(if (installationIntention === InstallationIntention.UNINSTALL) VmUninstallInfoStep(VmType.HAXM)
          else HaxmInstallSettingsStep(isCustomInstall, willBeInstalled, emulatorMemoryMb))

  override val steps: Collection<ModelWizardStep<*>>
    get() = setOf(if (installationIntention == InstallationIntention.UNINSTALL) com.android.tools.idea.welcome.wizard.VmUninstallInfoStep(
      VmType.HAXM)
                  else com.android.tools.idea.welcome.wizard.HaxmInstallSettingsStep(emulatorMemoryMb))

  /**
   * Create a platform-dependant command line for running the silent HAXM installer.
   *
   * @return command line object
   * @throws WizardException If there's any wizard-specific problem with the installation
   * @throws IOException if there's a problem creating temporary files
   */
  @Throws(WizardException::class, IOException::class)
  override fun getInstallCommandLine(sdk: File): GeneralCommandLine =
    addInstallParameters(super.getInstallCommandLine(sdk), emulatorMemoryMb.get())

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
    override val incompatibleSystemError = when {
      !SystemInfo.isWindows -> AccelerationErrorCode.HAXM_REQUIRES_WINDOWS
      !CpuVendor.isIntel -> AccelerationErrorCode.HAXM_REQUIRES_INTEL_CPU
      else -> null
    }
    override val componentPath = "Hardware_Accelerated_Execution_Manager"
  }
}