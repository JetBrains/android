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
package com.android.tools.idea.appinspection.inspectors.network.ide

import com.android.tools.adtui.model.FpsTimer
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.api.awaitForDisposal
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTab
import com.android.tools.idea.appinspection.inspector.ide.FrameworkInspectorLaunchParams
import com.android.tools.idea.appinspection.inspector.ide.SingleAppInspectorTab
import com.android.tools.idea.appinspection.inspector.ide.SingleAppInspectorTabProvider
import com.android.tools.idea.appinspection.inspectors.network.ide.analytics.IdeNetworkInspectorTracker
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorClientImpl
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorDataSourceImpl
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorServicesImpl
import com.android.tools.idea.appinspection.inspectors.network.view.NetworkInspectorTab
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.flags.StudioFlags.ENABLE_NETWORK_MANAGER_INSPECTOR_TAB
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import icons.StudioIcons
import kotlinx.coroutines.launch
import javax.swing.Icon

/**
 * The number of updates per second our simulated object models receive.
 */
private const val UPDATES_PER_SECOND = 60

class NetworkInspectorTabProvider : SingleAppInspectorTabProvider() {
  override val inspectorId = "studio.network.inspection"
  override val displayName = "Network Inspector"
  override val icon: Icon = StudioIcons.Shell.Menu.NETWORK_INSPECTOR
  override val inspectorLaunchParams = FrameworkInspectorLaunchParams(
    AppInspectorJar("network-inspector.jar",
                    developmentDirectory = "bazel-bin/tools/base/app-inspection/inspectors/network",
                    releaseDirectory = "plugins/android/resources/app-inspection/")
  )

  override fun isApplicable(): Boolean {
    return ENABLE_NETWORK_MANAGER_INSPECTOR_TAB.get()
  }

  override fun supportsOffline() = true

  override fun createTab(
    project: Project,
    ideServices: AppInspectionIdeServices,
    processDescriptor: ProcessDescriptor,
    messenger: AppInspectorMessenger,
    parentDisposable: Disposable
  ): AppInspectorTab {
    val componentsProvider = DefaultUiComponentsProvider(project)
    val codeNavigationProvider = DefaultCodeNavigationProvider(project)
    val scope = AndroidCoroutineScope(parentDisposable)
    val dataSource = NetworkInspectorDataSourceImpl(messenger, scope)

    return object : SingleAppInspectorTab(messenger) {
      private val client = NetworkInspectorClientImpl(messenger)
      private val services = NetworkInspectorServicesImpl(
        codeNavigationProvider,
        client,
        FpsTimer(UPDATES_PER_SECOND),
        AndroidDispatchers.workerThread,
        AndroidDispatchers.uiThread,
        IdeNetworkInspectorTracker(project)
      )
      private val networkInspectorTab = NetworkInspectorTab(project, componentsProvider, dataSource, services, scope, parentDisposable)
      override val component = networkInspectorTab.component

      init {
        scope.launch {
          messenger.awaitForDisposal()
          networkInspectorTab.stopInspection()
        }
      }
    }
  }
}