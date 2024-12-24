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
import com.android.tools.idea.welcome.install.AehdSdkComponentTreeNode
import com.android.tools.idea.welcome.install.SdkComponentInstaller
import com.android.tools.idea.welcome.wizard.AbstractProgressStep
import com.android.tools.idea.welcome.wizard.AehdInstallInfoStep
import com.android.tools.idea.welcome.wizard.AehdUninstallInfoStep
import com.android.tools.idea.welcome.wizard.FirstRunWizardTracker
import com.android.tools.idea.welcome.wizard.StudioFirstRunWelcomeScreen
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizard.WizardResult
import com.android.tools.idea.wizard.model.ModelWizardDialog
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.tools.idea.wizard.model.WizardModel
import com.android.tools.idea.wizard.ui.StudioWizardDialogBuilder
import com.google.wireless.android.sdk.stats.SetupWizardEvent
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import java.util.concurrent.atomic.AtomicBoolean

class AehdModelWizard(
  private val installationIntention: AehdSdkComponentTreeNode.InstallationIntention,
  private val aehdWizardController: AehdWizardController,
  private val tracker: FirstRunWizardTracker
) {
  companion object {
    var LOG: Logger = Logger.getInstance(AehdModelWizard::class.java)
  }

  val myAehdSdkComponentTreeNode = AehdSdkComponentTreeNode(installationIntention)

  fun showAndGet(): Boolean {
    tracker.trackWizardStarted()
    val modelWizard = buildModelWizard()
    val wizardDialog: ModelWizardDialog = StudioWizardDialogBuilder(modelWizard, "AEHD")
      .setCancellationPolicy(ModelWizardDialog.CancellationPolicy.CAN_CANCEL_UNTIL_CAN_FINISH)
      .build()
    modelWizard.addResultListener(object : ModelWizard.WizardListener {
      override fun onWizardFinished(wizardResult: WizardResult) {
        tracker.trackWizardFinished(
          if (wizardResult == WizardResult.FINISHED) SetupWizardEvent.CompletionStatus.FINISHED
          else SetupWizardEvent.CompletionStatus.CANCELED
        )
      }
    })
    return wizardDialog.showAndGet()
  }

  private fun buildModelWizard(): ModelWizard {
    val modelWizardBuilder = ModelWizard.Builder()
      .addStep(getInstallationStep(installationIntention))

    if (installationIntention != AehdSdkComponentTreeNode.InstallationIntention.UNINSTALL) {
      val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()

      // Ensure the SDK handler is loaded
      val progressIndicator = StudioLoggerProgressIndicator(javaClass)
      sdkHandler
        .getSdkManager(progressIndicator)
        .loadSynchronously(
          RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, progressIndicator, StudioDownloader(), StudioSettingsController.getInstance())
      myAehdSdkComponentTreeNode.updateState(sdkHandler)

      modelWizardBuilder.addStep(LicenseAgreementStep(LicenseAgreementModel(sdkHandler.location)) {
        resolvePackagesToInstall(sdkHandler, myAehdSdkComponentTreeNode)
      })
    }

    val progressStep = SetupProgressStep(BlankModel(), "Invoking installer", tracker)
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
      override fun onWizardFinished(result: WizardResult) {
        if (!progressStep.isSuccessfullyCompleted.get()) {
          aehdWizardController.handleCancel(installationIntention, myAehdSdkComponentTreeNode, javaClass, LOG)
        }
      }
    })

    return modelWizard
  }

  private fun getInstallationStep(installationIntention: AehdSdkComponentTreeNode.InstallationIntention): ModelWizardStep.WithoutModel {
    return when (installationIntention) {
      AehdSdkComponentTreeNode.InstallationIntention.UNINSTALL -> AehdUninstallInfoStep()
      AehdSdkComponentTreeNode.InstallationIntention.INSTALL_WITH_UPDATES,
      AehdSdkComponentTreeNode.InstallationIntention.INSTALL_WITHOUT_UPDATES,
      AehdSdkComponentTreeNode.InstallationIntention.CONFIGURE_ONLY -> AehdInstallInfoStep()
    }
  }

  private fun resolvePackagesToInstall(sdkHandler: AndroidSdkHandler, aehdSdkComponentTreeNode: AehdSdkComponentTreeNode): Collection<RemotePackage> {
    try {
      val componentInstaller = SdkComponentInstaller(sdkHandler)
      return componentInstaller.getPackagesToInstall(listOf(aehdSdkComponentTreeNode))
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

  inner class SetupProgressStep(model: AehdModelWizard.BlankModel, name: String, private val tracker: FirstRunWizardTracker): AbstractProgressStep<BlankModel>(
    model, name) {
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
          tracker.trackInstallingComponentsStarted()
          try {
            tracker.trackSdkComponentsToInstall(listOf(myAehdSdkComponentTreeNode.sdkComponentsMetricKind()))

            val success = aehdWizardController.setupAehd(myAehdSdkComponentTreeNode, this@SetupProgressStep, progressIndicator)
            isSuccessfullyCompleted.set(success)
          }
          catch (e: Exception) {
            LOG.warn("Exception caught while trying to configure AEHD", e)
            showConsole()
            print(e.message + "\n", ConsoleViewContentType.ERROR_OUTPUT)
          }
          finally {
            if (this@SetupProgressStep.isCanceled()) {
              tracker.trackInstallingComponentsFinished(SetupWizardEvent.SdkInstallationMetrics.SdkInstallationResult.CANCELED)
            }
            else if (isSuccessfullyCompleted.get()) {
              tracker.trackInstallingComponentsFinished(SetupWizardEvent.SdkInstallationMetrics.SdkInstallationResult.SUCCESS)
            }
            else {
              tracker.trackInstallingComponentsFinished(SetupWizardEvent.SdkInstallationMetrics.SdkInstallationResult.ERROR)
            }
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