/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.appinspection.internal.AppInspectionTransport
import com.android.tools.idea.appinspection.internal.attachAppInspectionTarget
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor

typealias TargetTerminatedListener = () -> Unit

/**
 * Represents an app-inspection target process (on the device) being connected to from the host.
 */
interface AppInspectionTarget {
  /**
   * Creates an inspector in the connected process.
   *
   * @param [inspectorJar] is the path to a jar on the host filesystem that contains ".dex" code of an inspector and
   * configuration file: META-INF/services/androidx.inspection.InspectorFactory, where a factory for an inspector should be registered.
   * [inspectorJar] will be injected into the app's process and an inspector will be instantiated with the registered factory.
   * @param [inspectorId] an id of an inspector to launch; the factory injected into the app with [inspectorJar] must have the same id as
   * a value passed into this function.
   * @param [creator] a factory lambda to instantiate a [AppInspectorClient] once inspector is successfully created on the device side.
   */
  @WorkerThread
  fun <T : AppInspectorClient> launchInspector(
    inspectorId: String,
    inspectorJar: AppInspectorJar,
    @WorkerThread creator: (AppInspectorClient.CommandMessenger) -> T
  ): ListenableFuture<T>

  /**
   * Adds a listener to be notified (via [executor]) of when this connection is closed.
   *
   * Listener fires immediately if connection is already closed.
   */
  fun addTargetTerminatedListener(executor: Executor, listener: TargetTerminatedListener): TargetTerminatedListener

  val processDescriptor: ProcessDescriptor

  companion object {
    /**
     * Creates an [AppInspectionTarget] for the given [process] on the given device ([stream])
     */
    @VisibleForTesting
    @WorkerThread
    fun attach(
      transport: AppInspectionTransport,
      jarCopier: AppInspectionJarCopier
    ): ListenableFuture<AppInspectionTarget> = attachAppInspectionTarget(transport, jarCopier)
  }
}