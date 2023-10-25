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
package com.intellij.testFramework

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.ThrowableRunnable
import com.intellij.util.application

// This is a partial copy of DumbModeTestUtils from IntelliJ 2023.3,
// used to ease the migration away from DumbServiceImpl in tests.
// TODO(gharrma): delete this class after merging IntelliJ 2023.3.
object DumbModeTestUtils {

  init {
    if (ApplicationInfo.getInstance().build.baselineVersion >= 233) {
      error("DumbModeTestUtils should be deleted from platform/tools/adt/idea after updating to IntelliJ 2023.3")
    }
  }

  @JvmStatic
  fun runInDumbModeSynchronously(project: Project, runnable: ThrowableRunnable<in Throwable>) {
    computeInDumbModeSynchronously(project) {
      runnable.run()
    }
  }

  @Suppress("UnstableApiUsage")
  @JvmStatic
  fun <T> computeInDumbModeSynchronously(project: Project, computable: ThrowableComputable<T, in Throwable>): T {
    val service = DumbServiceImpl.getInstance(project)
    application.invokeAndWait { service.isDumb = true }
    try {
      return computable.compute()
    }
    finally {
      application.invokeAndWait { service.isDumb = false }
    }
  }
}
