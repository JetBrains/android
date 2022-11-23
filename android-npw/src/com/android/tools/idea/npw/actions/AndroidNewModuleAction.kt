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

package com.android.tools.idea.npw.actions

import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.module.showDefaultWizard
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import org.jetbrains.android.sdk.AndroidSdkUtils
import org.jetbrains.android.util.AndroidBundle.message
import javax.swing.Icon

open class AndroidNewModuleAction : AnAction, DumbAware {
  constructor() : super(message("android.wizard.module.new.module.menu"), message("android.wizard.module.new.module.menu.description"), null)

  constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon) {}

  override fun update(e: AnActionEvent) {
    e.project?.let {
      e.presentation.isVisible = it.getProjectSystem().allowsFileCreation()
      e.presentation.isEnabled = !GradleSyncState.getInstance(it).isSyncInProgress
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    if (project.getProjectSystem().allowsFileCreation()) {
      if (!AndroidSdkUtils.isAndroidSdkAvailable()) {
        SdkQuickfixUtils.showSdkMissingDialog()
        return
      }

      showDefaultWizard(project, getModulePath(e), ProjectSyncInvoker.DefaultProjectSyncInvoker())
    }
  }

  // Overwritten by subclasses to return the module path
  protected open fun getModulePath(e: AnActionEvent): String = ":"
}
