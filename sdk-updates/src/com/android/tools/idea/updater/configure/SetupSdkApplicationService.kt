/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.updater.configure

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.sdk.wizard.LicenseAgreementModel
import com.android.tools.idea.sdk.wizard.LicenseAgreementStep
import com.android.tools.idea.welcome.config.FirstRunWizardMode
import com.android.tools.idea.welcome.install.FirstRunWizardDefaults.getInitialSdkLocation
import com.android.tools.idea.welcome.isWritable
import com.android.tools.idea.welcome.wizard.ComponentInstallerProvider
import com.android.tools.idea.welcome.wizard.FirstRunWizardModel
import com.android.tools.idea.welcome.wizard.InstallComponentsProgressStep
import com.android.tools.idea.welcome.wizard.InstallSummaryStep
import com.android.tools.idea.welcome.wizard.SdkComponentsStep
import com.android.tools.idea.welcome.wizard.deprecated.ConsolidatedProgressStep
import com.android.tools.idea.welcome.wizard.deprecated.InstallComponentsPath
import com.android.tools.idea.wizard.WizardConstants
import com.android.tools.idea.wizard.dynamic.DialogWrapperHost
import com.android.tools.idea.wizard.dynamic.DynamicWizard
import com.android.tools.idea.wizard.dynamic.DynamicWizardHost
import com.android.tools.idea.wizard.dynamic.SingleStepPath
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizard.WizardListener
import com.android.tools.idea.wizard.model.ModelWizardDialog
import com.android.tools.idea.wizard.ui.StudioWizardDialogBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.text.StringUtil
import java.io.File

/** Callback invoked upon SDK update completion. Provides the selected SDK path. */
typealias SdkUpdatedCallback = (sdkPath: File) -> Unit

/**
 * A service responsible for showing the SDK setup wizard. This wizard guides the user through the
 * process of downloading and installing the Android SDK.
 */
@Service(Service.Level.APP)
class SetupSdkApplicationService : Disposable {

  /**
   * Displays the SDK setup wizard.
   *
   * @param sdkPathString The initial SDK path to use. If empty, a default location is used.
   * @param sdkUpdatedCallback A callback invoked when the SDK setup is complete, providing the
   *   final SDK path.
   */
  @UiThread
  fun showSdkSetupWizard(sdkPathString: String, sdkUpdatedCallback: SdkUpdatedCallback?) {
    val sdkPath =
      if (StringUtil.isEmpty(sdkPathString)) {
        getInitialSdkLocation(FirstRunWizardMode.MISSING_SDK)
      } else {
        File(sdkPathString)
      }

    if (StudioFlags.NPW_FIRST_RUN_WIZARD.get()) {
      showNewWizard(sdkPath, sdkUpdatedCallback)
    } else {
      showOldWizard(sdkPath, sdkUpdatedCallback)
    }
  }

  override fun dispose() {}

  private fun showOldWizard(sdkPath: File, sdkUpdatedCallback: SdkUpdatedCallback?) {
    val host: DynamicWizardHost = DialogWrapperHost(null)
    val wizard: DynamicWizard =
      object : DynamicWizard(null, null, "SDK Setup", host) {
        override fun init() {
          val progressStep = DownloadingComponentsStep(myHost.disposable, myHost)

          val path =
            InstallComponentsPath(
              FirstRunWizardMode.MISSING_SDK,
              sdkPath,
              progressStep,
              ComponentInstallerProvider(),
              false,
            )

          progressStep.setInstallComponentsPath(path)

          addPath(path)
          addPath(SingleStepPath(progressStep))
          super.init()
        }

        override fun performFinishingActions() {
          val stateSdkLocationPath = myState[WizardConstants.KEY_SDK_INSTALL_LOCATION]
          checkNotNull(stateSdkLocationPath)

          val stateSdkLocation = File(stateSdkLocationPath)
          sdkUpdatedCallback?.invoke(stateSdkLocation)
        }

        override fun getProgressTitle(): String {
          return "Setting up SDK..."
        }

        override fun getWizardActionDescription(): String {
          return "Setting up SDK..."
        }
      }
    wizard.init()
    wizard.show()
  }

  private fun showNewWizard(sdkPath: File, sdkUpdatedCallback: SdkUpdatedCallback?) {
    val model = FirstRunWizardModel(FirstRunWizardMode.MISSING_SDK, sdkPath.toPath(), installUpdates = false, ComponentInstallerProvider())

    val supplier = model.getPackagesToInstallSupplier()
    val licenseAgreementModel = LicenseAgreementModel(model.sdkInstallLocationProperty)
    val licenseAgreementStep = LicenseAgreementStep(licenseAgreementModel, supplier)

    val progressStep: InstallComponentsProgressStep =
      object : InstallComponentsProgressStep(model, licenseAgreementModel, this) {

        override fun shouldShow(): Boolean {
          val sdkInstallLocation = model.sdkInstallLocation
          return sdkInstallLocation != null && isWritable(sdkInstallLocation)
        }
      }

    val builder = ModelWizard.Builder()
    builder.addStep(
      SdkComponentsStep(model, null, FirstRunWizardMode.MISSING_SDK, licenseAgreementStep, this)
    )
    builder.addStep(InstallSummaryStep(model, supplier))
    builder.addStep(licenseAgreementStep)
    builder.addStep(progressStep)

    val wizard = builder.build()
    wizard.setCancelInterceptor {
      if (progressStep.isRunning()) {
        progressStep.getProgressIndicator().cancel()
        return@setCancelInterceptor true
      } else {
        return@setCancelInterceptor false
      }
    }
    wizard.addResultListener(
      object : WizardListener {
        override fun onWizardFinished(result: ModelWizard.WizardResult) {
          if (!result.isFinished) {
            return
          }

          val sdkLocation = model.sdkInstallLocation?.toFile()
          checkNotNull(sdkLocation)
          sdkUpdatedCallback?.invoke(sdkLocation)
        }
      }
    )

    val dialog =
      StudioWizardDialogBuilder(wizard, "SDK Setup")
        .setCancellationPolicy(ModelWizardDialog.CancellationPolicy.CAN_CANCEL_UNTIL_CAN_FINISH)
        .build()
    dialog.show()
  }

  companion object {
    @JvmStatic
    val instance: SetupSdkApplicationService
      get() = ApplicationManager.getApplication().getService(SetupSdkApplicationService::class.java)
  }

  private class DownloadingComponentsStep(disposable: Disposable, host: DynamicWizardHost) :
    ConsolidatedProgressStep(disposable, host) {
    private var myInstallComponentsPath: InstallComponentsPath? = null

    fun setInstallComponentsPath(installComponentsPath: InstallComponentsPath) {
      setPaths(listOf(installComponentsPath))
      myInstallComponentsPath = installComponentsPath
    }

    override fun isStepVisible(): Boolean {
      return myInstallComponentsPath!!.shouldDownloadingComponentsStepBeShown()
    }
  }
}
