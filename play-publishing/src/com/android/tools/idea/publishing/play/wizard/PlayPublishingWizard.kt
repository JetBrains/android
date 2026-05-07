/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.publishing.play.wizard

import com.android.tools.adtui.compose.ComposeWizard
import com.android.tools.idea.publishing.AppPublishingContext
import com.android.tools.idea.publishing.play.wizard.page.LoggedOutPage
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project

fun showPublishingWizard(project: Project, context: AppPublishingContext) {
  val wizard =
    ComposeWizard(project, "Upload to Play Wizard") {
      getOrCreateState { context.toPublishingWizardState() }
      LoggedOutPage()
    }
  invokeLater { wizard.show() }
}

private fun AppPublishingContext.toPublishingWizardState() = PlayPublishingWizardState(artifactPath, isRegistered)
