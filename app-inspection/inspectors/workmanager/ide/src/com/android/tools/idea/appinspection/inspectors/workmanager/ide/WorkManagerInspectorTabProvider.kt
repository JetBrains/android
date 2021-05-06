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

import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTab
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTabProvider
import com.android.tools.idea.appinspection.inspector.ide.LibraryInspectorLaunchParams
import com.android.tools.idea.appinspection.inspector.ide.SingleAppInspectorTab
import com.android.tools.idea.appinspection.inspector.ide.SingleAppInspectorTabProvider
import com.android.tools.idea.appinspection.inspectors.workmanager.analytics.IdeWorkManagerInspectorTracker
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorkManagerInspectorClient
import com.android.tools.idea.appinspection.inspectors.workmanager.view.WorkManagerInspectorTab
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.flags.StudioFlags.ENABLE_WORK_MANAGER_INSPECTOR_TAB
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import icons.StudioIcons
import javax.swing.Icon
import javax.swing.JComponent

const val MINIMUM_WORKMANAGER_VERSION = "2.5.0"

class WorkManagerInspectorTabProvider : SingleAppInspectorTabProvider() {
  override val inspectorId = "androidx.work.inspection"
  override val displayName = "Background Task Inspector"
  override val icon: Icon = StudioIcons.LayoutEditor.Palette.LIST_VIEW
  override val inspectorLaunchParams = LibraryInspectorLaunchParams(
    AppInspectorJar("workmanager-inspection.jar",
                    developmentDirectory = "prebuilts/tools/common/app-inspection/androidx/work/"),
    ArtifactCoordinate("androidx.work", "work-runtime", MINIMUM_WORKMANAGER_VERSION, ArtifactCoordinate.Type.AAR)
  )
  override val learnMoreUrl = "https://d.android.com/r/studio-ui/background-task-inspector-help"

  override fun isApplicable(): Boolean {
    return ENABLE_WORK_MANAGER_INSPECTOR_TAB.get()
  }

  override fun createTab(
    project: Project,
    ideServices: AppInspectionIdeServices,
    processDescriptor: ProcessDescriptor,
    messenger: AppInspectorMessenger,
    parentDisposable: Disposable
  ): AppInspectorTab {
    val scope = AndroidCoroutineScope(parentDisposable)
    return object : SingleAppInspectorTab(messenger) {
      private val client = WorkManagerInspectorClient(messenger, scope, IdeWorkManagerInspectorTracker(project))
      override val component: JComponent = WorkManagerInspectorTab(client, ideServices, scope).component
    }
  }

  override fun compareTo(other: AppInspectorTabProvider): Int {
    // TODO(b/183624170): This is a temporary patch to make sure WMI doesn't show up before DBI for now, while
    //  the UI is still mostly barren (this should not be true in a later version of Studio)
    return 1
  }
}
