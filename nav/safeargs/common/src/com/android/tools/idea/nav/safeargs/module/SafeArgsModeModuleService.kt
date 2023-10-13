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

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.android.tools.idea.nav.safeargs.SafeArgsMode
import com.android.tools.idea.nav.safeargs.project.SafeArgsModeTrackerProjectService
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.util.messages.Topic
import java.util.concurrent.atomic.AtomicReference

/**
 * Component that owns and updates a module's [SafeArgsMode] state.
 *
 * See also: [SafeArgsModeTrackerProjectService] See also: [safeArgsMode]
 */
class SafeArgsModeModuleService(val module: Module) : Disposable.Default {
  fun interface SafeArgsModeChangedListener {
    fun onSafeArgsModeChanged(module: Module, mode: SafeArgsMode)
  }

  companion object {
    fun getInstance(module: Module): SafeArgsModeModuleService =
      module.getService(SafeArgsModeModuleService::class.java)

    val MODE_CHANGED: Topic<SafeArgsModeChangedListener> =
      Topic(SafeArgsModeChangedListener::class.java, Topic.BroadcastDirection.TO_CHILDREN, true)
  }

  private val atomicSafeArgsMode = AtomicReference(SafeArgsMode.NONE)

  internal var safeArgsMode: SafeArgsMode
    get() = atomicSafeArgsMode.get()
    set(value) {
      if (atomicSafeArgsMode.getAndSet(value) != value) {
        module.project.messageBus.syncPublisher(MODE_CHANGED).onSafeArgsModeChanged(module, value)
      }
    }

  init {
    // As this class is a (lazily instantiated) service, it's possible the project was already
    // initialized before here, so call update immediately just in case.
    updateSafeArgsMode()
    module.project.messageBus
      .connect(this)
      .subscribe(
        PROJECT_SYSTEM_SYNC_TOPIC,
        ProjectSystemSyncManager.SyncResultListener { updateSafeArgsMode() }
      )
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
