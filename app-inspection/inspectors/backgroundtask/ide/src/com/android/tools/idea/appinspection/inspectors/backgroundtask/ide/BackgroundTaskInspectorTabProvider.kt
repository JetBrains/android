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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.ide

import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorLaunchConfig
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorMessengerTarget
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTab
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTabProvider
import com.android.tools.idea.appinspection.inspector.ide.FrameworkInspectorLaunchParams
import com.android.tools.idea.appinspection.inspector.ide.LibraryInspectorLaunchParams
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorClient
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.WmiMessengerTarget
import com.android.tools.idea.appinspection.inspectors.backgroundtask.view.BackgroundTaskInspectorTab
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import icons.StudioIcons
import javax.swing.Icon

const val MINIMUM_WORKMANAGER_VERSION = "2.5.0"

class BackgroundTaskInspectorTabProvider : AppInspectorTabProvider {
  override val launchConfigs =
    listOf(
      AppInspectorLaunchConfig(
        "backgroundtask.inspection",
        FrameworkInspectorLaunchParams(
          AppInspectorJar(
            "backgroundtask-inspection.jar",
            developmentDirectory = "bazel-bin/tools/base/app-inspection/inspectors/backgroundtask",
            releaseDirectory = "plugins/android/resources/app-inspection/"
          ),
        ),
      ),
      AppInspectorLaunchConfig(
        "androidx.work.inspection",
        LibraryInspectorLaunchParams(
          AppInspectorJar(
            "workmanager-inspection.jar",
            developmentDirectory = "prebuilts/tools/common/app-inspection/androidx/work/"
          ),
          ArtifactCoordinate(
            "androidx.work",
            "work-runtime",
            MINIMUM_WORKMANAGER_VERSION,
            ArtifactCoordinate.Type.AAR
          )
        )
      )
    )

  override val displayName = "Background Task Inspector"
  override val icon: Icon = StudioIcons.LayoutEditor.Palette.LIST_VIEW
  override val learnMoreUrl = "https://d.android.com/r/studio-ui/background-task-inspector-help"

  override fun isApplicable(): Boolean {
    return StudioFlags.ENABLE_BACKGROUND_TASK_INSPECTOR_TAB.get()
  }

  override fun createTab(
    project: Project,
    ideServices: AppInspectionIdeServices,
    processDescriptor: ProcessDescriptor,
    messengerTargets: List<AppInspectorMessengerTarget>,
    parentDisposable: Disposable
  ): AppInspectorTab {

    val btiMessenger = (messengerTargets[0] as AppInspectorMessengerTarget.Resolved).messenger
    val wmiMessengerTarget =
      when (val target = messengerTargets[1]) {
        is AppInspectorMessengerTarget.Resolved -> WmiMessengerTarget.Resolved(target.messenger)
        is AppInspectorMessengerTarget.Unresolved -> WmiMessengerTarget.Unresolved(target.error)
      }
    val scope = AndroidCoroutineScope(parentDisposable)
    val client =
      BackgroundTaskInspectorClient(
        btiMessenger,
        wmiMessengerTarget,
        scope,
        IdeBackgroundTaskInspectorTracker(project)
      )

    return object : AppInspectorTab {
      override val messengers =
        messengerTargets.mapNotNull { target ->
          (target as? AppInspectorMessengerTarget.Resolved)?.messenger
        }
      override val component =
        BackgroundTaskInspectorTab(
            client,
            ideServices,
            IntellijUiComponentsProvider(project),
            scope,
            AndroidDispatchers.uiThread
          )
          .component
    }
  }

  override fun compareTo(other: AppInspectorTabProvider): Int {
    // TODO(b/183624170): This is a temporary patch to make sure WMI doesn't show up before DBI for
    // now, while
    //  the UI is still mostly barren (this should not be true in a later version of Studio)
    return 1
  }
}
