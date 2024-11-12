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
package com.android.tools.idea.insights.persistence

import com.google.gct.login2.GoogleLoginService
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@Service
@State(name = "AppInsightsTos", storages = [Storage("appInsightsTos.xml")])
class TosPersistence : PersistentStateComponent<TosPersistenceState> {

  private var tosPersistenceState = TosPersistenceState()

  override fun getState(): TosPersistenceState = tosPersistenceState

  override fun loadState(state: TosPersistenceState) {
    tosPersistenceState = state
  }

  fun isTosAccepted(firebaseProject: String) =
    GoogleLoginService.instance.getEmail()?.let { email ->
      state.userProjectMap.getOrDefault(email, mutableSetOf()).contains(firebaseProject)
    } == true

  companion object {
    fun getInstance() = service<TosPersistence>()
  }
}

/**
 * Map of user to Firebase project ids that have been accepted by the user.
 *
 * The key is the user's email address and the value is a set of project ids.
 */
data class TosPersistenceState(
  var userProjectMap: MutableMap<String, MutableSet<String>> = mutableMapOf()
)
