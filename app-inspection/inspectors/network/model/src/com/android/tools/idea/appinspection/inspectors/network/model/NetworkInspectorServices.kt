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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

interface NetworkInspectorServices {
  val navigationProvider: CodeNavigationProvider
  val client: NetworkInspectorClient
  val scope: CoroutineScope
  val updater: Updater
  val uiDispatcher: CoroutineDispatcher
}

/**
 * Contains the suite of services on which the network inspector relies. Ex: Timeline and updater.
 */
class NetworkInspectorServicesImpl(
  override val navigationProvider: CodeNavigationProvider,
  override val client: NetworkInspectorClient,
  override val scope: CoroutineScope,
  timer: StopwatchTimer,
  override val uiDispatcher: CoroutineDispatcher
) : NetworkInspectorServices {
  override val updater = Updater(timer)
}