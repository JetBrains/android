/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.workmanager.ide

import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTab
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTabProvider
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorkManagerInspectorClient
import com.android.tools.idea.appinspection.inspectors.workmanager.view.WorkManagerInspectorTab
import com.android.tools.idea.flags.StudioFlags.ENABLE_WORK_MANAGER_INSPECTOR_TAB
import com.intellij.openapi.project.Project
import javax.swing.JComponent

class WorkManagerInspectorTabProvider : AppInspectorTabProvider {
  override val inspectorId = "androidx.work.inspector"
  override val displayName = "WorkManager Inspector"
  override val inspectorAgentJar = AppInspectorJar("workmanager-inspector.jar",
                                                   developmentDirectory = "../../prebuilts/tools/common/app-inspection/androidx/work/",
                                                   releaseDirectory = "plugins/android/resources/app-inspection/")

  override fun isApplicable(): Boolean {
    return ENABLE_WORK_MANAGER_INSPECTOR_TAB.get()
  }

  override fun createTab(project: Project,
                         messenger: AppInspectorClient.CommandMessenger,
                         ideServices: AppInspectionIdeServices): AppInspectorTab {
    return object : AppInspectorTab {
      override val client = WorkManagerInspectorClient(messenger)

      override val component: JComponent = WorkManagerInspectorTab(client).component
    }
  }
}