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
package com.android.tools.idea.nav.safeargs.module

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.nav.safeargs.SafeArgsMode
import com.android.tools.idea.nav.safeargs.project.SafeArgsModeTrackerProjectService
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicReference

/**
 * Component that owns and updates a module's [SafeArgsMode] state.
 *
 * See also: [SafeArgsModeTrackerProjectService]
 * See also: [safeArgsMode]
 */
class SafeArgsModeModuleService(val module: Module) {
  companion object {
    fun getInstance(module: Module): SafeArgsModeModuleService = module.getService(SafeArgsModeModuleService::class.java)
  }

  private val atomicSafeArgsMode = AtomicReference(SafeArgsMode.NONE)

  internal var safeArgsMode: SafeArgsMode
    get() = atomicSafeArgsMode.get()
    set(value) {
      if (!StudioFlags.NAV_SAFE_ARGS_SUPPORT.get()) return
      if (atomicSafeArgsMode.getAndSet(value) != value) {
        SafeArgsModeTrackerProjectService.getInstance(module.project).tracker.incModificationCount()
      }
    }

  init {
    if (StudioFlags.NAV_SAFE_ARGS_SUPPORT.get()) {
      // As this class is a (lazily instantiated) service, it's possible Gradle was already
      // initialized before here, so call update immediately just in case.
      updateSafeArgsMode()

      GradleSyncState.subscribe(module.project, object : GradleSyncListener {
        override fun syncSucceeded(project: Project) {
          updateSafeArgsMode()
        }

        override fun syncFailed(project: Project, errorMessage: String) {
          updateSafeArgsMode()
        }

        override fun syncSkipped(project: Project) {
          updateSafeArgsMode()
        }
      }, module)
    }
  }

  private fun updateSafeArgsMode() {
    val gradleFacet = GradleFacet.getInstance(module)
    this.safeArgsMode = gradleFacet?.gradleModuleModel?.toSafeArgsMode() ?: SafeArgsMode.NONE
  }

  private fun GradleModuleModel.toSafeArgsMode(): SafeArgsMode {
    return when {
      hasSafeArgsKotlinPlugin() -> SafeArgsMode.KOTLIN
      hasSafeArgsJavaPlugin() -> SafeArgsMode.JAVA
      else -> SafeArgsMode.NONE
    }
  }
}
