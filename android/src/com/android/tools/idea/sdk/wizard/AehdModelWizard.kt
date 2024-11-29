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
package com.android.tools.idea.sdk.wizard

import com.android.repository.api.RemotePackage
import com.android.repository.api.RepoManager
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.observable.core.BoolProperty
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.sdk.StudioSettingsController
import com.android.tools.idea.welcome.install.AehdSdkComponent
import com.android.tools.idea.welcome.install.SdkComponentInstaller
import com.android.tools.idea.welcome.wizard.AehdInstallInfoStep
import com.android.tools.idea.welcome.wizard.AehdUninstallInfoStep
import com.android.tools.idea.welcome.wizard.ProgressStep
import com.android.tools.idea.welcome.wizard.StudioFirstRunWelcomeScreen
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardDialog
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.tools.idea.wizard.model.WizardModel
import com.android.tools.idea.wizard.ui.StudioWizardDialogBuilder
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.Disposer
import java.util.concurrent.atomic.AtomicBoolean

class AehdModelWizard(private val installationIntention: AehdSdkComponent.InstallationIntention) {
  companion object {
    var LOG: Logger = Logger.getInstance(AehdModelWizard::class.java)
  }

  val aehdSdkComponent = AehdSdkComponent(installationIntention)

  fun showAndGet(): Boolean {
    val parent = Disposer.newDisposable()
    try {
      val modelWizard = buildModelWizard(parent)
      val wizardDialog: ModelWizardDialog = StudioWizardDialogBuilder(modelWizard, "AEHD")
        .setCancellationPolicy(ModelWizardDialog.CancellationPolicy.CAN_CANCEL_UNTIL_CAN_FINISH)
        .build()
      return wizardDialog.showAndGet()
    } finally {
      Disposer.dispose(parent)
    }
  }

  private fun buildModelWizard(parent: Disposable): ModelWizard {
    val modelWizardBuilder = ModelWizard.Builder()
      .addStep(getInstallationStep(installationIntention))

    if (installationIntention != AehdSdkComponent.InstallationIntention.UNINSTALL) {
      val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()

      // Ensure the SDK handler is loaded
      val progressIndicator = StudioLoggerProgressIndicator(javaClass)
      sdkHandler
        .getSdkManager(progressIndicator)
        .loadSynchronously(
          RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, progressIndicator, StudioDownloader(), StudioSettingsController.getInstance())
      aehdSdkComponent.updateState(sdkHandler)

      modelWizardBuilder.addStep(LicenseAgreementStep(LicenseAgreementModel(sdkHandler.location)) {
        resolvePackagesToInstall(sdkHandler, aehdSdkComponent)
      })
    }

    val progressStep = SetupProgressStep(BlankModel(), parent, "Invoking installer")
    modelWizardBuilder.addStep(progressStep)

    val modelWizard = modelWizardBuilder.build()
    modelWizard.setCancelInterceptor {
      if (progressStep.isRunning()) {
        progressStep.getProgressIndicator().cancel()
        true
      } else {
        false
      }
    }

    modelWizard.addResultListener(object : ModelWizard.WizardListener {
      override fun onWizardFinished(result: ModelWizard.WizardResult) {
        if (!progressStep.isSuccessfullyCompleted.get()) {
          AehdWizardUtils.handleCancel(installationIntention, aehdSdkComponent, javaClass, LOG)
        }
      }
    })

    return modelWizard
  }

  private fun getInstallationStep(installationIntention: AehdSdkComponent.InstallationIntention): ModelWizardStep.WithoutModel {
    return when (installationIntention) {
      AehdSdkComponent.InstallationIntention.UNINSTALL -> AehdUninstallInfoStep()
      AehdSdkComponent.InstallationIntention.INSTALL_WITH_UPDATES,
      AehdSdkComponent.InstallationIntention.INSTALL_WITHOUT_UPDATES,
      AehdSdkComponent.InstallationIntention.CONFIGURE_ONLY -> AehdInstallInfoStep()
    }
  }

  private fun resolvePackagesToInstall(sdkHandler: AndroidSdkHandler, aehdSdkComponent: AehdSdkComponent): Collection<RemotePackage> {
    try {
      val componentInstaller = SdkComponentInstaller(sdkHandler)
      return componentInstaller.getPackagesToInstall(listOf(aehdSdkComponent))
    }
    catch (e: SdkQuickfixUtils.PackageResolutionException) {
      logger<StudioFirstRunWelcomeScreen>().warn(e)
      return emptyList()
    }
  }

  class AtomicBooleanProperty(initialValue: Boolean) : BoolProperty() {
    private val value = AtomicBoolean(initialValue)

    override fun setDirectly(newValue: Boolean) {
      value.set(newValue)
    }

    override fun get(): Boolean {
      return value.get()
    }
  }

  inner class SetupProgressStep(model: AehdModelWizard.BlankModel, parent: Disposable, name: String): ProgressStep<BlankModel>(model, parent, name) {
    val isSuccessfullyCompleted = AtomicBooleanProperty(false)
    val progressIndicator = StudioLoggerProgressIndicator(javaClass)

    override fun canGoForward(): ObservableBool = isSuccessfullyCompleted.and(super.canGoForward())

    override fun canGoBack(): Boolean = false

    override fun execute() {
      isSuccessfullyCompleted.set(false)

      val application = ApplicationManager.getApplication()
      application.assertIsDispatchThread()

      val task: Task.Backgroundable = object : Task.Backgroundable(null, "AEHD Installation", true) {
        override fun run(indicator: ProgressIndicator) {
          try {
            AehdWizardUtils.setupAehd(aehdSdkComponent, this@SetupProgressStep, progressIndicator)
            isSuccessfullyCompleted.set(aehdSdkComponent.isInstallerSuccessfullyCompleted)
          }
          catch (e: Exception) {
            LOG.warn("Exception caught while trying to configure AEHD", e)
            showConsole()
            print(e.message + "\n", ConsoleViewContentType.ERROR_OUTPUT)
          }
        }
      }
      ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, getProgressIndicator())
    }
  }

  class BlankModel: WizardModel() {
    override fun handleFinished() {
    }
  }
}