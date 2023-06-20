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
package com.android.tools.idea.appinspection.internal

import com.android.annotations.concurrency.AnyThread
import com.android.tools.idea.appinspection.api.AppInspectionJarCopier
import com.android.tools.idea.appinspection.api.process.SimpleProcessListener
import com.android.tools.idea.appinspection.inspector.api.AppInspectionProcessNoLongerExistsException
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.concurrency.getCompletedOrNull
import com.android.tools.idea.io.grpc.StatusRuntimeException
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.manager.TransportStreamChannel
import com.google.common.annotations.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/** A class that exclusively attaches, tracks, and disposes of [AppInspectionTarget]. */
@AnyThread
internal class AppInspectionTargetManager
internal constructor(private val transportClient: TransportClient, parentScope: CoroutineScope) :
  SimpleProcessListener() {
  private val scope = parentScope.createChildScope(true)

  @VisibleForTesting
  internal class TargetInfo(
    val targetDeferred: Deferred<AppInspectionTarget>,
    val projectName: String
  )

  @VisibleForTesting internal val targets = ConcurrentHashMap<ProcessDescriptor, TargetInfo>()

  /**
   * Attempts to connect to a process on device specified by [processDescriptor]. Returns a future
   * of [AppInspectionTarget] which can be used to launch inspector connections.
   */
  internal suspend fun attachToProcess(
    processDescriptor: ProcessDescriptor,
    jarCopier: AppInspectionJarCopier,
    streamChannel: TransportStreamChannel,
    projectName: String
  ): AppInspectionTarget {
    val targetInfo =
      targets.computeIfAbsent(processDescriptor) {
        val targetDeferred =
          scope.async {
            val transport =
              AppInspectionTransport(transportClient, processDescriptor, streamChannel)
            attachAppInspectionTarget(transport, jarCopier, scope)
          }
        TargetInfo(targetDeferred, projectName)
      }
    try {
      return targetInfo.targetDeferred.await()
    } catch (e: StatusRuntimeException) {
      // A gRPC exception can be thrown here if the process has ended. We cannot recover from this
      // so we prompt user to restart app.
      targets.remove(processDescriptor)
      throw AppInspectionProcessNoLongerExistsException(
        "Failed to connect to process ${processDescriptor.name}. The process has " +
          "likely ended. Please restart it so App Inspection can reconnect.",
        e
      )
    } catch (e: Throwable) {
      // On any exception, including cancellation, remove the target from the hashmap |targets|.
      targets.remove(processDescriptor)
      throw e
    }
  }

  override fun onProcessConnected(process: ProcessDescriptor) {}

  override fun onProcessDisconnected(process: ProcessDescriptor) {
    // There is no need to explicitly dispose clients here because they are handled by
    // AppInspectorConnection.
    targets.remove(process)?.targetDeferred?.cancel()
  }

  suspend fun getTarget(descriptor: ProcessDescriptor): AppInspectionTarget? {
    return targets[descriptor]?.targetDeferred?.await()
  }

  // Remove all current clients that belong to the provided project.
  // We dispose targets that were done and attempt to cancel those that are not.
  internal suspend fun disposeClients(project: String) {
    targets
      .filterValues { targetInfo -> targetInfo.projectName == project }
      .keys
      .forEach { process ->
        val deferred = targets.remove(process)?.targetDeferred ?: return@forEach
        deferred.cancel()
        deferred.getCompletedOrNull()?.dispose()
      }
  }
}
