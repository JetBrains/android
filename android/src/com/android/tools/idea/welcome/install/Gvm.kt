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

import com.android.tools.idea.avdmanager.AccelerationErrorCode
import com.android.tools.idea.avdmanager.AccelerationErrorSolution.SolutionCode
import com.android.tools.idea.observable.core.IntValueProperty
import com.android.tools.idea.sdk.install.VmType
import com.android.tools.idea.welcome.install.InstallationIntention.UNINSTALL
import com.android.tools.idea.welcome.wizard.HaxmInstallSettingsStep
import com.android.tools.idea.welcome.wizard.deprecated.GvmInstallInfoStep
import com.android.tools.idea.welcome.wizard.deprecated.VmUninstallInfoStep
import com.android.tools.idea.wizard.dynamic.ScopedStateStore
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.intellij.openapi.util.SystemInfo

/**
 * Google GVM installable component
 */
class Gvm(
  installationIntention: InstallationIntention,
  isCustomInstall: ScopedStateStore.Key<Boolean>
) : Vm(Gvm, installationIntention, isCustomInstall) {
  override val filePrefix = "gvm"
  override val installUrl = GVM_WINDOWS_INSTALL_URL
  override val steps: Collection<ModelWizardStep<*>>
    get() = setOf(if (installationIntention == UNINSTALL) com.android.tools.idea.welcome.wizard.VmUninstallInfoStep(VmType.GVM)
      else HaxmInstallSettingsStep(IntValueProperty(1024))) // FIXME

  override fun createSteps() =
    setOf(if (installationIntention === UNINSTALL) VmUninstallInfoStep(VmType.GVM)
          else GvmInstallInfoStep(isCustomInstall))

  companion object InstallerInfo : VmInstallerInfo("Android Emulator hypervisor driver") {
    override val vendor = "google"
    override val installSolution = SolutionCode.INSTALL_GVM
    override val reinstallSolution = SolutionCode.REINSTALL_GVM
    override val incompatibleSystemError = when {
      !SystemInfo.isWindows -> AccelerationErrorCode.GVM_REQUIRES_WINDOWS
      else -> null
    }
    override val componentPath = "Android_Emulator_Hypervisor_Driver"
  }
}
