/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("UnstableApiUsage")

package com.google.idea.blaze.base.project.startup

import com.android.annotations.concurrency.WorkerThread
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * An adapter for Java implementations of [ProjectActivit].
 *
 * Note: Prefer implementing [ProjectActivity] directly in Kotlin as it utilizes resources more efficiently.
 */
abstract class ProjectActivityJavaShim: ProjectActivity {
  final override suspend fun execute(project: Project) {
    blockingContext {
      runActivity(project)
    }
  }

  @WorkerThread
  abstract fun runActivity(project: Project)
}