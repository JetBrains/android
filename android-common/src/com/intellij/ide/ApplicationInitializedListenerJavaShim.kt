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
package com.intellij.ide

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.progress.blockingContext
import kotlinx.coroutines.CoroutineScope

// This is a temporary backport of ApplicationInitializedListenerJavaShim from IntelliJ 2023.3,
// TODO(gharrma): delete this class after merging IntelliJ 2023.3.
abstract class ApplicationInitializedListenerJavaShim : ApplicationInitializedListener {
  init {
    val build = ApplicationInfo.getInstance().build
    check(build.baselineVersion < 233 || build.isSnapshot) {
      "ApplicationInitializedListenerJavaShim should be deleted from platform/tools/adt/idea after updating to IntelliJ 2023.3"
    }
  }

  final override suspend fun execute(asyncScope: CoroutineScope) {
    blockingContext {
      componentsInitialized()
    }
  }

  abstract override fun componentsInitialized()
}
