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
package com.android.tools.idea.appinspection.inspectors.network.model

import com.android.tools.adtui.model.StopwatchTimer
import com.android.tools.adtui.model.updater.Updater
import com.android.tools.idea.appinspection.inspectors.network.model.analytics.NetworkInspectorTracker
import com.android.tools.idea.appinspection.inspectors.network.model.analytics.StubNetworkInspectorTracker
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import studio.network.inspection.NetworkInspectorProtocol

interface NetworkInspectorServices {
  val navigationProvider: CodeNavigationProvider
  val client: NetworkInspectorClient
  val updater: Updater
  val workerDispatcher: CoroutineDispatcher
  val uiDispatcher: CoroutineDispatcher
  val usageTracker: NetworkInspectorTracker
}

/**
 * Contains the suite of services on which the network inspector relies. Ex: Timeline and updater.
 */
class NetworkInspectorServicesImpl(
  override val navigationProvider: CodeNavigationProvider,
  override val client: NetworkInspectorClient,
  timer: StopwatchTimer,
  override val workerDispatcher: CoroutineDispatcher,
  override val uiDispatcher: CoroutineDispatcher,
  override val usageTracker: NetworkInspectorTracker
) : NetworkInspectorServices {
  override val updater = Updater(timer)
}

/** For tests only. */
class TestNetworkInspectorServices(
  override val navigationProvider: CodeNavigationProvider,
  timer: StopwatchTimer,
  override val client: NetworkInspectorClient =
    object : NetworkInspectorClient {
      override suspend fun getStartTimeStampNs() = 0L
      override suspend fun interceptResponse(command: NetworkInspectorProtocol.InterceptCommand) =
        Unit
    },
  override val usageTracker: NetworkInspectorTracker = StubNetworkInspectorTracker()
) : NetworkInspectorServices {
  override val updater = Updater(timer)
  override val workerDispatcher = MoreExecutors.directExecutor().asCoroutineDispatcher()
  override val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()
}
