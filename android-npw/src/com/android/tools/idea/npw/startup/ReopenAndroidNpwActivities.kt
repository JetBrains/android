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
package com.android.tools.idea.npw.startup

import com.android.tools.idea.npw.ideahost.AndroidModuleBuilder
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.concurrency.EdtExecutorService

// Matches the private key in com.intellij.ide.projectWizard.projectTypeStep.ProjectTypeList
private const val PROJECT_WIZARD_GROUP_KEY = "project.wizard.group"
private const val NEW_PROJECT_ACTION_ID = "WelcomeScreen.CreateNewProject"
private val androidGroupId = AndroidModuleBuilder().builderId

// Reopens the Android npw when the IDE launches straight into a project, skipping the welcome screen.
internal class ReopenAndroidNpwStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    reopenAndroidNpwIfRequested()
  }
}

internal class OpenAndroidProjectWizardStartupLifecycleListener : AppLifecycleListener {
  override fun appFrameCreated(commandLineArgs: List<String?>) {
    PromotionTemplateStateService.getInstance().markRestartedIfNpwReopenPending()
  }

  override fun welcomeScreenDisplayed() {
    reopenAndroidNpwIfRequested()
  }
}

private fun reopenAndroidNpwIfRequested() {
  if (!PromotionTemplateStateService.getInstance().consumeNpwReopenRequestAfterRestart()) return

  val properties = PropertiesComponent.getInstance()
  properties.setValue(PROJECT_WIZARD_GROUP_KEY, androidGroupId)

  val action = ActionManager.getInstance().getAction(NEW_PROJECT_ACTION_ID) ?: return
  val event = AnActionEvent.createEvent(
    DataContext.EMPTY_CONTEXT,
    action.templatePresentation.clone(),
    ActionPlaces.WELCOME_SCREEN,
    ActionUiKind.NONE,
    null,
  )
  EdtExecutorService.getInstance().execute {
    ActionUtil.performAction(action, event)
  }
}
