/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.model

import com.android.tools.adtui.model.StopwatchTimer
import com.android.tools.adtui.model.updater.Updater
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspectors.network.model.analytics.NetworkInspectorTracker
import com.android.tools.idea.appinspection.inspectors.network.model.analytics.StubNetworkInspectorTracker
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.coroutines.asCoroutineDispatcher
import studio.network.inspection.NetworkInspectorProtocol

/** Test implementation of [NetworkInspectorServices]. */
class TestNetworkInspectorServices(
  override val navigationProvider: CodeNavigationProvider,
  timer: StopwatchTimer,
  override val client: NetworkInspectorClient =
    object : NetworkInspectorClient {
      override suspend fun startInspection(): NetworkInspectorProtocol.StartInspectionResponse =
        NetworkInspectorProtocol.StartInspectionResponse.getDefaultInstance()

      override suspend fun interceptResponse(command: NetworkInspectorProtocol.InterceptCommand) =
        Unit
    },
  override val usageTracker: NetworkInspectorTracker = StubNetworkInspectorTracker(),
) : NetworkInspectorServices {
  override val updater = Updater(timer)
  override val workerDispatcher = MoreExecutors.directExecutor().asCoroutineDispatcher()
  override val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()
  override val ideServices =
    object : AppInspectionIdeServices {
      override fun showToolWindow() {
        TODO("Not yet implemented")
      }

      override fun showNotification(
        content: String,
        title: String,
        severity: AppInspectionIdeServices.Severity,
        hyperlinkClicked: () -> Unit,
      ) {
        TODO("Not yet implemented")
      }

      override suspend fun navigateTo(codeLocation: AppInspectionIdeServices.CodeLocation) {
        TODO("Not yet implemented")
      }

      override fun isTabSelected(inspectorId: String): Boolean {
        TODO("Not yet implemented")
      }
    }
}
