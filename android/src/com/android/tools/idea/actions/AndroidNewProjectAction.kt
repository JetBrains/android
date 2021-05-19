/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.actions

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.model.NewProjectModel
import com.android.tools.idea.npw.project.ChooseAndroidProjectStep
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.android.tools.idea.ui.wizard.SimpleStudioWizardLayout
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder
import com.android.tools.idea.ui.wizard.StudioWizardLayout
import com.android.tools.idea.wizard.model.ModelWizard.Builder
import com.intellij.idea.ActionsBundle.actionText
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen.updateNewProjectIconIfWelcomeScreen
import org.jetbrains.android.sdk.AndroidSdkUtils

class AndroidNewProjectAction @JvmOverloads constructor(text: String = actionText("NewDirectoryProject")) : AnAction(text), DumbAware {
  override fun update(e: AnActionEvent) {
    updateNewProjectIconIfWelcomeScreen(e)
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (!AndroidSdkUtils.isAndroidSdkAvailable()) {
      SdkQuickfixUtils.showSdkMissingDialog()
      return
    }
    val wizard = Builder().addStep(ChooseAndroidProjectStep(NewProjectModel())).build()!!
    val wizardLayout = if (StudioFlags.NPW_NEW_MODULE_WITH_SIDE_BAR.get()) SimpleStudioWizardLayout() else StudioWizardLayout()
    StudioWizardDialogBuilder(wizard, actionText("WelcomeScreen.CreateNewProject")).build(wizardLayout).show()
  }
}