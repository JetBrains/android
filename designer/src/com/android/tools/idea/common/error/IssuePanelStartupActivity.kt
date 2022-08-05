/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.common.error

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode

/**
 * The purpose of this startup activity is to make sure the [IssuePanelService] is created after project is opened.
 * The [IssuePanelService] creates and setups [DesignerCommonIssuePanel] and [DesignerCommonIssueModel] when it is constructed. IJ create
 * the project service lazily, so we explicit get the instance to make it be created.
 * TODO? (b/235832774): Consider to move the setup of [DesignerCommonIssuePanel] and [DesignerCommonIssueModel] from [IssuePanelService] to
 * here?
 */
class IssuePanelStartupActivity : StartupActivity {
  override fun runActivity(project: Project) {
    // Needs to call setupIssuePanel() proactively to test it.
    if (!isUnitTestMode()) {
      setupIssuePanel(project)
    }
  }

  @VisibleForTesting
  fun setupIssuePanel(project: Project) {
    // IJ's framework does not construct the instance of service before it is used.  Getting the instance to make sure the shared issue
    // panel is created.
    IssuePanelService.getInstance(project)
  }
}
