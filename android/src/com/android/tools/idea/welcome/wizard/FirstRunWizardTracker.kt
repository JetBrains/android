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
package com.android.tools.idea.welcome.wizard

import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.SetupWizardEvent
import com.google.wireless.android.sdk.stats.SetupWizardEvent.InstallationMode
import com.google.wireless.android.sdk.stats.SetupWizardEvent.SdkInstallationMetrics
import com.google.wireless.android.sdk.stats.SetupWizardEvent.SdkInstallationMetrics.SdkInstallationResult
import java.time.Duration
import java.time.Instant

/**
 * Tracks user interactions and progress within the Android Studio First Run Wizard. This interface
 * allows monitoring key events, such as wizard start/finish, SDK selection and installation
 * progress.
 *
 * The event containing the metrics is logged when the `trackWizardFinished()` method is called, so
 * it's important to ensure this method is called whenever the wizard is finished or cancelled.
 */
class FirstRunWizardTracker(private val mode: SetupWizardEvent.SetupWizardMode) {
  private val eventBuilder =
    AndroidStudioEvent.newBuilder().setKind(AndroidStudioEvent.EventKind.SETUP_WIZARD_EVENT)

  private var wizardStartedTime: Instant? = null
  private var installationMode: InstallationMode = InstallationMode.UNKNOWN_INSTALLATION_MODE
  private val sdkInstallationMetricsBuilder: SdkInstallationMetrics.Builder =
    eventBuilder.setupWizardEventBuilder.sdkInstallationMetricsBuilder
  private var installingComponentsStartTime: Instant? = null

  private var lastStepShown: SetupWizardEvent.WizardStep.WizardStepKind? = null
  private var lastStepShownAtTimeMs = -1L

  /**
   * Tracks the start of the First Run Wizard. Call this method as soon as the wizard is launched.
   */
  fun trackWizardStarted() {
    if (wizardStartedTime != null) {
      return // We've already tracked the start of the wizard
    }
    wizardStartedTime = Instant.now()
  }

  /**
   * Tracks when a specific step is shown in the First Run Wizard. This should be called each time a
   * step's UI is displayed to the user, including when navigating back to a previously shown step.
   */
  fun trackStepShowing(wizardStepKind: SetupWizardEvent.WizardStep.WizardStepKind) {
    if (wizardStepKind == lastStepShown) {
      return
    }

    addCurrentStep()

    lastStepShown = wizardStepKind
    lastStepShownAtTimeMs = System.currentTimeMillis()
  }

  /** Tracks the [installationMode] chosen by the user (e.g., Standard, Custom). */
  fun trackInstallationMode(installationMode: InstallationMode) {
    this.installationMode = installationMode
  }

  /** Tracks when the user changes the SDK installation location from the default. */
  fun trackSdkInstallLocationChanged() {
    sdkInstallationMetricsBuilder.sdkInstallLocationChanged = true
  }

  /** Records the set of SDK components selected for installation by the user. */
  fun trackSdkComponentsToInstall(
    sdkComponentsToInstall: List<SdkInstallationMetrics.SdkComponentKind>
  ) {
    sdkInstallationMetricsBuilder.clearSdkComponentsToInstall()
    sdkInstallationMetricsBuilder.addAllSdkComponentsToInstall(sdkComponentsToInstall)
  }

  /** Tracks the start of the SDK installation process. */
  fun trackInstallingComponentsStarted() {
    installingComponentsStartTime = Instant.now()
  }

  /**
   * Tracks the result of the SDK installation process (Success, Failure, Cancelled). If
   * `trackInstallingComponentsStarted` was called, then it also tracks the time spent installing
   * the components.
   */
  fun trackInstallingComponentsFinished(sdkInstallationResult: SdkInstallationResult) {
    this.sdkInstallationMetricsBuilder.sdkInstallationResult = sdkInstallationResult

    if (installingComponentsStartTime != null) {
      val duration = Duration.between(installingComponentsStartTime, Instant.now())
      this.sdkInstallationMetricsBuilder.timeSpentInstallingSdkComponentsMs = duration.toMillis()
    }
  }

  /**
   * Tracks the completion of the First Run Wizard, including whether the user finished or
   * cancelled. Must be called once when the wizard is finished or cancelled. The underlying event
   * is logged when this method is called.
   */
  fun trackWizardFinished(completionStatus: SetupWizardEvent.CompletionStatus) {
    if (eventBuilder.setupWizardEventBuilder.hasTimeSpentInWizardMs()) {
      return
    }

    addCurrentStep()

    eventBuilder.setupWizardEventBuilder.mode = mode
    eventBuilder.setupWizardEventBuilder.installationMode = installationMode
    eventBuilder.setupWizardEventBuilder.completionStatus = completionStatus

    val timeSpentInWizardMs = Duration.between(wizardStartedTime, Instant.now())
    eventBuilder.setupWizardEventBuilder.timeSpentInWizardMs = timeSpentInWizardMs.toMillis()

    UsageTracker.log(eventBuilder)
  }

  private fun addCurrentStep() {
    if (lastStepShown != null && lastStepShownAtTimeMs != -1L) {
      val timeInPreviousStepMs = System.currentTimeMillis() - lastStepShownAtTimeMs
      eventBuilder.setupWizardEventBuilder.addWizardSteps(
        SetupWizardEvent.WizardStep.newBuilder()
          .setWizardStepKind(lastStepShown)
          .setTimeSpentInStepMs(timeInPreviousStepMs)
      )
    }
  }
}
