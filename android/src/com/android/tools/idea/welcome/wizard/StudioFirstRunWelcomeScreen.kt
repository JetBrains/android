/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.avdmanager.HardwareAccelerationCheck.isChromeOSAndIsNotHWAccelerated
import com.android.tools.idea.sdk.wizard.LicenseAgreementModel
import com.android.tools.idea.sdk.wizard.LicenseAgreementStep
import com.android.tools.idea.welcome.config.AndroidFirstRunPersistentData
import com.android.tools.idea.welcome.config.FirstRunWizardMode
import com.android.tools.idea.welcome.install.FirstRunWizardDefaults
import com.android.tools.idea.welcome.wizard.deprecated.CancelableWelcomeWizard
import com.android.tools.idea.welcome.wizard.deprecated.WelcomeScreenWindowListener
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizard.WizardResult
import com.android.tools.idea.wizard.model.ModelWizardDialog
import com.android.tools.idea.wizard.ui.StudioWizardDialogBuilder
import com.google.wireless.android.sdk.stats.SetupWizardEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo.isLinux
import com.intellij.openapi.wm.WelcomeScreen
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import java.util.function.BooleanSupplier
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel
import org.jetbrains.android.util.AndroidBundle.message
import org.jetbrains.kotlin.idea.util.application.isHeadlessEnvironment

/**
 * Android Studio's implementation of a [WelcomeScreen]. Starts up a wizard meant to run the first
 * time someone starts up Android Studio to ask them to pick from some initial, useful options. Once
 * the wizard is complete, it will bring the user to the initial "Welcome Screen" UI (with a list of
 * projects and options to start a new project, etc.)
 */
class StudioFirstRunWelcomeScreen(
  private val mode: FirstRunWizardMode,
  private val sdkComponentInstallerProvider: SdkComponentInstallerProvider,
  private val tracker: FirstRunWizardTracker,
) : WelcomeScreen {
  private lateinit var modelWizard: ModelWizard
  private var mainPanel: JComponent? = null
  private var frame: JFrame? = null

  companion object {
    fun buildWizard(
      model: FirstRunWizardModel,
      mode: FirstRunWizardMode,
      cancelInterceptor: BooleanSupplier,
      tracker: FirstRunWizardTracker,
    ): ModelWizard {
      val licenseAgreementModel = LicenseAgreementModel(model.sdkInstallLocationProperty)
      val progressStep = InstallComponentsProgressStep(model, licenseAgreementModel, tracker)

      val modelWizard =
        ModelWizard.Builder()
          .apply {
            if (mode == FirstRunWizardMode.NEW_INSTALL) {
              addStep(FirstRunWelcomeStep(model, tracker))

              if (model.isStandardInstallSupported) {
                addStep(InstallationTypeWizardStep(model, tracker))
              }
            }

            if (mode == FirstRunWizardMode.MISSING_SDK) {
              addStep(MissingSdkAlertStep(tracker))
            }

            val supplier = model.getPackagesToInstallSupplier()
            val licenseAgreementStep =
              object : LicenseAgreementStep(licenseAgreementModel, supplier) {
                override fun onShowing() {
                  super.onShowing()
                  tracker.trackStepShowing(
                    SetupWizardEvent.WizardStep.WizardStepKind.LICENSE_AGREEMENT
                  )
                }
              }

            addStep(SdkComponentsStep(model, null, mode, licenseAgreementStep, tracker))

            if (mode != FirstRunWizardMode.INSTALL_HANDOFF) {
              addStep(InstallSummaryStep(model, supplier, tracker))
              addStep(licenseAgreementStep)
            }

            if (
              isLinux &&
                !isChromeOSAndIsNotHWAccelerated() &&
                mode == FirstRunWizardMode.NEW_INSTALL
            ) {
              addStep(LinuxKvmInfoStep(tracker))
            }

            addStep(progressStep)
          }
          .build()

      modelWizard.setCancelInterceptor {
        if (progressStep.isRunning()) {
          progressStep.getProgressIndicator().cancel()
          true
        } else {
          cancelInterceptor.asBoolean
        }
      }

      return modelWizard
    }
  }

  private fun setupWizard() {
    val initialSdkLocation = FirstRunWizardDefaults.getInitialSdkLocation(mode)
    val model =
      FirstRunWizardModel(
        mode,
        initialSdkLocation.toPath(),
        installUpdates = true,
        sdkComponentInstallerProvider,
        tracker,
      )
    modelWizard = buildWizard(model, mode, this::shouldPreventWizardCancel, tracker)

    // Note: We create a ModelWizardDialog, but we are only interested in its Content Panel
    // This is a bit of a hack, but it's the simplest way to reuse logic from ModelWizardDialog
    // (which inherits from IntelliJ's DialogWrapper class, which we can't refactor here).
    val modelWizardDialog =
      StudioWizardDialogBuilder(modelWizard, "")
        .setCancellationPolicy(ModelWizardDialog.CancellationPolicy.CAN_CANCEL_UNTIL_CAN_FINISH)
        .build()
    mainPanel = modelWizardDialog.contentPanel

    // Replace Content Panel with dummy version, as we are going to return its original value to the
    // welcome frame
    modelWizardDialog.peer.setContentPane(JPanel())

    Disposer.register(this, modelWizardDialog.disposable)
    Disposer.register(this, modelWizard)

    modelWizard.addResultListener(
      object : ModelWizard.WizardListener {
        override fun onWizardFinished(wizardResult: WizardResult) {
          closeDialog()

          tracker.trackWizardFinished(
            if (wizardResult == WizardResult.FINISHED) SetupWizardEvent.CompletionStatus.FINISHED
            else SetupWizardEvent.CompletionStatus.CANCELED
          )
        }
      }
    )
  }

  override fun getWelcomePanel(): JComponent {
    tracker.trackWizardStarted()

    // TODO(qumeric): I am not sure at which point getWelcomePanel runs.
    //  Maybe it is worth to run setupWizard earlier and wait here for finish.
    if (mainPanel == null) {
      ApplicationManager.getApplication().invokeAndWait { setupWizard() }
    }

    return mainPanel!!
  }

  override fun setupFrame(frame: JFrame) {
    this.frame = frame

    frame.run {
      title =
        if (IdeInfo.getInstance().isAndroidStudio) message("android.as.wizard.welcome.dialog.title")
        else message("android.ij.wizard.welcome.dialog.title")
      pack()
      setLocationRelativeTo(null)

      // Intercept windowClosing event, to show the closing confirmation dialog
      WelcomeScreenWindowListener.install(
        this,
        object : CancelableWelcomeWizard {
          @UiThread
          override fun cancel() {
            modelWizard.cancel()
          }

          @get:UiThread
          override val isActive: Boolean
            get() = !modelWizard.isFinished
        },
      )
    }
  }

  override fun dispose() {}

  private fun closeDialog() {
    frame?.isVisible = false
    frame?.dispose()

    if (isHeadlessEnvironment()) {
      // No UI should be shown when IDE is running in this mode.
    } else {
      WelcomeFrame.showNow()
    }
  }

  private fun shouldPreventWizardCancel(): Boolean {
    return when (ConfirmFirstRunWizardCloseDialog.show()) {
      ConfirmFirstRunWizardCloseDialog.Result.Skip -> {
        AndroidFirstRunPersistentData.getInstance().markSdkUpToDate(mode.installerTimestamp)
        false
      }
      ConfirmFirstRunWizardCloseDialog.Result.Rerun -> {
        false
      }
      ConfirmFirstRunWizardCloseDialog.Result.DoNotClose -> {
        true
      }
      else -> throw RuntimeException("Invalid Close result") // Unknown option
    }
  }
}
