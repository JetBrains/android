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
package com.android.tools.idea.nav.safeargs.kotlin.k2

import com.android.tools.idea.nav.safeargs.module.SafeArgsModeModuleService
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.providers.analysisMessageBus
import org.jetbrains.kotlin.analysis.providers.topics.KotlinModuleStateModificationKind
import org.jetbrains.kotlin.analysis.providers.topics.KotlinTopics

/** Raises module-change events when a module's SafeArgs eligibility state may have changed. */
@Service(Service.Level.PROJECT)
class ChangeListenerProjectService(private val project: Project) : Disposable.Default {

  init {
    project.messageBus.connect(this).apply {
      subscribe(
        SafeArgsModeModuleService.MODE_CHANGED,
        SafeArgsModeModuleService.SafeArgsModeChangedListener { module, mode ->
          dispatchSafeArgsModeChange(module)
        },
      )
      subscribe(
        PROJECT_SYSTEM_SYNC_TOPIC,
        ProjectSystemSyncManager.SyncResultListener { dispatchGradleSync() },
      )
    }
  }

  private fun dispatchSafeArgsModeChange(module: Module) {
    module.fireEvent(KotlinTopics.MODULE_STATE_MODIFICATION) {
      onModification(it, KotlinModuleStateModificationKind.UPDATE)
    }
  }

  private fun dispatchGradleSync() {
    // We never care about non-source modules, so we only dispatch a global source module
    // state-change event here, so we don't unnecessarily invalidate binary module cached data.
    runWriteAction {
      project.analysisMessageBus
        .syncPublisher(KotlinTopics.GLOBAL_SOURCE_MODULE_STATE_MODIFICATION)
        .onModification()
    }
  }

  companion object {
    fun ensureListening(project: Project) {
      // Force creation of the service - it will connect to the message bus in its init block.
      project.getService(ChangeListenerProjectService::class.java)
    }
  }
}
