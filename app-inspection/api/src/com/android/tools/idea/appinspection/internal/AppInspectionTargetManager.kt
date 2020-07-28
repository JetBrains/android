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
import com.android.tools.idea.appinspection.api.process.ProcessListener
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.internal.process.toTransportImpl
import com.android.tools.idea.concurrency.addCallback
import com.android.tools.idea.concurrency.getDoneOrNull
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.manager.TransportStreamChannel
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

/**
 * A class that exclusively attaches, tracks, and disposes of [AppInspectionTarget].
 */
@AnyThread
internal class AppInspectionTargetManager internal constructor(
  private val executor: ExecutorService,
  private val transportClient: TransportClient,
  private val scope: CoroutineScope
) : ProcessListener {
  @VisibleForTesting
  internal class TargetInfo(val targetFuture: ListenableFuture<AppInspectionTarget>, val projectName: String)

  @VisibleForTesting
  internal val targets = ConcurrentHashMap<ProcessDescriptor, TargetInfo>()

  /**
   * Attempts to connect to a process on device specified by [processDescriptor]. Returns a future of [AppInspectionTarget] which can be
   * used to launch inspector connections.
   */
  internal fun attachToProcess(
    processDescriptor: ProcessDescriptor,
    jarCopier: AppInspectionJarCopier,
    streamChannel: TransportStreamChannel,
    projectName: String
  ): ListenableFuture<AppInspectionTarget> {
    return targets.computeIfAbsent(processDescriptor) {
      val processDescriptor = processDescriptor.toTransportImpl()
      val transport = AppInspectionTransport(transportClient, processDescriptor.stream, processDescriptor.process, executor, streamChannel)
      val targetFuture = attachAppInspectionTarget(transport, jarCopier, scope)
      TargetInfo(targetFuture, projectName)
    }.targetFuture.also {
      it.addCallback(MoreExecutors.directExecutor(), object : FutureCallback<AppInspectionTarget> {
        override fun onSuccess(result: AppInspectionTarget?) {}
        override fun onFailure(t: Throwable) {
          targets.remove(processDescriptor)
        }
      })
    }
  }

  override fun onProcessConnected(descriptor: ProcessDescriptor) {
  }

  override fun onProcessDisconnected(descriptor: ProcessDescriptor) {
    // There is no need to explicitly dispose clients here because they are handled by AppInspectorConnection.
    targets.remove(descriptor)
  }

  // Remove all current clients that belong to the provided project.
  // We dispose targets that were done and attempt to cancel those that are not.
  internal fun disposeClients(project: String) {
    targets.filterValues { targetInfo ->
      targetInfo.projectName == project
    }.keys.forEach { process ->
      val future = targets.remove(process)?.targetFuture ?: return@forEach
      future.getDoneOrNull()?.dispose() ?: future.cancel(false)
    }
  }
}