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
package com.android.tools.idea.appinspection.api

import com.android.tools.idea.appinspection.api.process.ProcessNotifier
import com.android.tools.idea.appinspection.internal.AppInspectionProcessDiscovery
import com.android.tools.idea.appinspection.internal.AppInspectionTargetManager
import com.android.tools.idea.appinspection.internal.DefaultAppInspectionApiServices
import com.android.tools.idea.appinspection.internal.DefaultAppInspectorLauncher
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.manager.TransportStreamManager
import com.android.tools.profiler.proto.Common
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ExecutorService

typealias JarCopierCreator = (Common.Device) -> AppInspectionJarCopier?

/**
 * The API surface that is exposed to the AppInspection IDE/UI module. It provides a set of tools that allows the frontend to discover
 * potentially interesting processes and launch inspectors on them.
 *
 * It provides 3 main utilities:
 * 1) Subscription to listening of processes in the transport pipeline via [processNotifier].
 * 2) Launching of an inspector on a process via [launcher].
 * 3) A set of functions to support IDE use cases. Currently, only [disposeClients].
 */
interface AppInspectionApiServices {
  /**
   * Allows for the subscription of listeners to receive and track processes.
   */
  val processNotifier: ProcessNotifier

  /**
   * Use this to launch a new inspector.
   */
  val launcher: AppInspectorLauncher

  /**
   * Disposes all of the currently active inspector clients in [project].
   */
  fun disposeClients(project: String)

  /**
   * A coroutine scope used to launch jobs. This scope is tied to the disposable of the App Inspection IDE service.
   */
  val scope: CoroutineScope

  companion object {
    fun createDefaultAppInspectionApiServices(
      executor: ExecutorService,
      client: TransportClient,
      streamManager: TransportStreamManager,
      scope: CoroutineScope,
      createJarCopier: JarCopierCreator
    ): AppInspectionApiServices {
      val targetManager = AppInspectionTargetManager(client, scope, executor)
      val processNotifier: ProcessNotifier = AppInspectionProcessDiscovery(executor, streamManager)
      processNotifier.addProcessListener(executor, targetManager)
      val launcher = DefaultAppInspectorLauncher(targetManager, processNotifier as AppInspectionProcessDiscovery, createJarCopier)
      return DefaultAppInspectionApiServices(targetManager, processNotifier, launcher, scope)
    }
  }
}