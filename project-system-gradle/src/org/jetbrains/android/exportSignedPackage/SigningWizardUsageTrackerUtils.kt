/*
 * Copyright (C) 2021 The Android Open Source Project
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
package org.jetbrains.android.exportSignedPackage

import com.android.tools.analytics.UsageTracker.log
import com.android.tools.analytics.withProjectId
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.SIGNING_WIZARD_CANCEL_ACTION
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.SIGNING_WIZARD_GRADLE_SIGNING_FAILED
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.SIGNING_WIZARD_GRADLE_SIGNING_SUCCEEDED
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.SIGNING_WIZARD_INTELLIJ_SIGNING_FAILED
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.SIGNING_WIZARD_INTELLIJ_SIGNING_SUCCEEDED
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.SIGNING_WIZARD_OK_ACTION
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.SIGNING_WIZARD_OPEN
import com.google.wireless.android.sdk.stats.SigningWizardEvent
import com.google.wireless.android.sdk.stats.SigningWizardEvent.SigningWizardFailureCause
import com.intellij.openapi.project.Project

fun trackWizardOpen(project: Project) {
  createAndLog(project, SIGNING_WIZARD_OPEN)
}

fun trackWizardClosed(project: Project) {
  createAndLog(project, SIGNING_WIZARD_CANCEL_ACTION)
}

fun trackWizardOkAction(project: Project) {
  createAndLog(project, SIGNING_WIZARD_OK_ACTION)
}

fun trackWizardGradleSigningFailed(project: Project, cause: SigningWizardFailureCause) {
  val event = createEvent(project, SIGNING_WIZARD_GRADLE_SIGNING_FAILED)
    .setSigningWizardEvent(SigningWizardEvent.newBuilder().setFailureCause(cause))
  log(event)
}

fun trackWizardIntellijSigningFailed(project: Project, cause: SigningWizardFailureCause) {
  val event = createEvent(project, SIGNING_WIZARD_INTELLIJ_SIGNING_FAILED)
    .setSigningWizardEvent(SigningWizardEvent.newBuilder().setFailureCause(cause))
  log(event)
}

fun trackWizardGradleSigning(
  project: Project,
  targetType: SigningWizardEvent.SigningTargetType,
  numberOfModules: Int,
  numberOfVariants: Int
) {
  val signEvent = SigningWizardEvent.newBuilder()
    .setTargetType(targetType)
    .setNumberOfModules(numberOfModules)
    .setNumberOfVariants(numberOfVariants)
  val event = createEvent(project, SIGNING_WIZARD_GRADLE_SIGNING_SUCCEEDED)
    .setSigningWizardEvent(signEvent)
  log(event)
}

fun trackWizardIntellijSigning(project: Project) {
  createAndLog(project, SIGNING_WIZARD_INTELLIJ_SIGNING_SUCCEEDED)
}

private fun createEvent(project: Project, kind: AndroidStudioEvent.EventKind): AndroidStudioEvent.Builder {
  return AndroidStudioEvent.newBuilder()
    .setCategory(AndroidStudioEvent.EventCategory.PROJECT_SYSTEM)
    .setKind(kind)
    .withProjectId(project)
}

private fun createAndLog(project: Project, kind: AndroidStudioEvent.EventKind) {
  val event = createEvent(project, kind)
  log(event)
}

