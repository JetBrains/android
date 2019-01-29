/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.issues

import com.android.builder.model.SyncIssue
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

/**
 * A project based component that stores a map from modules to sync issues. These are registered during sync (module setup) and are reported
 * shortly afterward. The map is cleared at the start of each sync.
 */
class SyncIssueRegister {
  private val _syncIssueMap: MutableMap<Module, MutableList<SyncIssue>> = HashMap()
  val syncIssueMap: Map<Module, List<SyncIssue>> = _syncIssueMap // projection to read-only

  fun register(module: Module, syncIssues: Collection<SyncIssue>) {
    _syncIssueMap.computeIfAbsent(module) { ArrayList() }.addAll(syncIssues)
  }

  fun clear() {
    _syncIssueMap.clear()
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SyncIssueRegister {
      return ServiceManager.getService(project, SyncIssueRegister::class.java)
    }
  }
}