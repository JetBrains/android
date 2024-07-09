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
package com.android.tools.idea.gradle.project

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.completeWith

/**
 * Instances of this service exist to allow ProjectActivities related to [AndroidGradleProjectStartupActivity] to
 * manage an ordering between them. The StartupManagerImpl runs all ProjectActivity asynchronously,
 * so this service is what allows us to guarantee that some activities run entirely before
 * [AndroidGradleProjectStartupActivity] and some after.
 */
abstract class AndroidGradleProjectStartupService<T> {
  val deferred = CompletableDeferred<T>()

  suspend fun runInitialization(action : suspend () -> T): T {
    deferred.completeWith(runCatching { action() })
    return deferred.await()
  }

  suspend fun awaitInitialization(): T {
    return deferred.await()
  }
}