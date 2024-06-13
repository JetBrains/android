/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.preview.flow

import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.disposableCallbackFlow
import com.android.tools.idea.res.ResourceNotificationManager
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** A [Flow] for receiving [ResourceNotificationManager.ResourceChangeListener] updates. */
fun resourceChangedFlow(
  module: Module,
  parentDisposable: Disposable,
  logger: Logger? = null,
  onConnected: (() -> Unit)? = null,
): Flow<Set<ResourceNotificationManager.Reason>> =
  disposableCallbackFlow("ResourceChangedFlow", logger, parentDisposable) {
    val facet =
      readAction { module.androidFacet }
        ?: run {
          logger?.warn("AndroidFacet not found for $module. Notifications will be ignored")
          return@disposableCallbackFlow
        }
    val resourceChangeListener =
      ResourceNotificationManager.ResourceChangeListener { reason -> trySend(reason) }
    val resourceNotificationManager = ResourceNotificationManager.getInstance(module.project)
    Disposer.register(this.disposable) {
      resourceNotificationManager.removeListener(resourceChangeListener, facet, null, null)
    }
    resourceNotificationManager.addListener(resourceChangeListener, facet, null, null)

    onConnected?.let { launch(AndroidDispatchers.workerThread) { it() } }
  }
