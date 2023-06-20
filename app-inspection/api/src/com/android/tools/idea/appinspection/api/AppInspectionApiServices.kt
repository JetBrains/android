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

import com.android.tools.idea.appinspection.api.process.ProcessDiscovery
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.api.launch.LaunchParameters
import com.android.tools.idea.appinspection.inspector.api.launch.LibraryCompatbilityInfo
import com.android.tools.idea.appinspection.inspector.api.launch.LibraryCompatibility
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.internal.AppInspectionProcessDiscovery
import com.android.tools.idea.appinspection.internal.AppInspectionTarget
import com.android.tools.idea.appinspection.internal.AppInspectionTargetManager
import com.android.tools.idea.appinspection.internal.DefaultAppInspectionApiServices
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.manager.TransportStreamManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asExecutor

typealias JarCopierCreator = (DeviceDescriptor) -> AppInspectionJarCopier?

/**
 * The API surface that is exposed to the AppInspection IDE/UI module. It provides a set of tools
 * that allows the frontend to discover potentially interesting processes and launch inspectors on
 * them.
 *
 * It provides the following main utilities: 1) Subscription to listening of processes in the
 * transport pipeline via [processDiscovery]. 2) A set of functions to support IDE use cases, for
 * example disposing clients of a particular project, and launching an inspector.
 */
interface AppInspectionApiServices {
  /** Allows for the subscription of listeners to receive and track processes. */
  val processDiscovery: ProcessDiscovery

  /** Disposes all of the currently active inspector clients in [project]. */
  suspend fun disposeClients(project: String)

  /**
   * Attaches to a running app if not already attached. This has the side effect of setting up
   * transport and app inspection services in the app's process space, and readying the pipeline for
   * communication.
   *
   * Returns an [AppInspectionTarget] which can be used to communicate with the process level
   * service and launch inspectors.
   */
  suspend fun attachToProcess(process: ProcessDescriptor, projectName: String): AppInspectionTarget

  /**
   * Launches an inspector for an app on the device. All launch information are captured in [params]
   * .
   *
   * This has the side effect of setting up transport and app inspection services in the app's
   * process space, similar to calling [attachToProcess].
   */
  suspend fun launchInspector(params: LaunchParameters): AppInspectorMessenger

  /** Stops all inspectors running on [process]. */
  suspend fun stopInspectors(process: ProcessDescriptor)

  companion object {
    fun createDefaultAppInspectionApiServices(
      client: TransportClient,
      streamManager: TransportStreamManager,
      scope: CoroutineScope,
      dispatcher: CoroutineDispatcher,
      createJarCopier: JarCopierCreator
    ): AppInspectionApiServices {
      val targetManager = AppInspectionTargetManager(client, scope)
      val processNotifier = AppInspectionProcessDiscovery(streamManager, scope)
      processNotifier.addProcessListener(dispatcher.asExecutor(), targetManager)
      return DefaultAppInspectionApiServices(targetManager, createJarCopier, processNotifier)
    }
  }
}

/**
 * Given a [process] and a coordinate describing a library, check and return the
 * LibraryCompatbilityInfo with the version being used.
 *
 * For example, "androidx.compose.ui:ui" might return the version "1.0.0-beta02", for example, or a
 * status error if the version is missing.
 */
suspend fun AppInspectionApiServices.checkVersion(
  project: String,
  process: ProcessDescriptor,
  groupId: String,
  artifactId: String,
  expectedClassNames: List<String> = emptyList()
): LibraryCompatbilityInfo? {
  val coordinateAnyVersion =
    ArtifactCoordinate(groupId, artifactId, "0.0.0", ArtifactCoordinate.Type.AAR)
  return attachToProcess(process, project)
    .getLibraryVersions(listOf(LibraryCompatibility(coordinateAnyVersion, expectedClassNames)))
    .singleOrNull()
}
