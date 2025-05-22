/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.commit

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@Service
@State(name = "GitMetricsStore", storages = [(Storage("studioGitMetrics.xml", roamingType = RoamingType.LOCAL))])
class GitMetricsStore : SimplePersistentStateComponent<GitMetricsStore.State>(State()) {

  class State : BaseState() {
    var lastLocalCommitTimestamp by property(0L)
    var lastRemoteCommitTimestamp by property(0L)
  }

  var lastLocalCommitTimestamp: Long?
    get() {
      val result = state.lastLocalCommitTimestamp
      if (result == 0L) return null
      return result
    }
    set(value) {
      state.lastLocalCommitTimestamp = value ?: 0L
    }

  var lastRemoteCommitTimestamp: Long?
    get() {
      val result = state.lastRemoteCommitTimestamp
      if (result == 0L) return null
      return result
    }
    set(value) {
      state.lastRemoteCommitTimestamp = value ?: 0L
    }

  companion object {
    @JvmStatic
    fun getInstance(): GitMetricsStore = service()
  }

}