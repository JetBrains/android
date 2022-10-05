/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.build.attribution

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import io.ktor.util.collections.ConcurrentList

data class BuildDescriptorImpl(
  override var buildSessionID: String,
  override var buildFinishedTimestamp: Long,
  override var totalBuildTimeMs: Long
) : BuildDescriptor {
  /**
   * Default constructor is needed for serialization
   */
  private constructor() : this("", 0, 0)
}

@State(name = "BuildDescriptorStorageService",
       storages = [Storage("buildDescriptorStorageService.xml", roamingType = RoamingType.DISABLED)])
class BuildDescriptorStorageService(
  val project: Project
) : PersistentStateComponent<BuildDescriptorStorageService.State> {
  private var buildDescriptorsState = State()

  companion object {
    @JvmStatic
    fun getInstance(project: Project): BuildDescriptorStorageService =
      project.getService(BuildDescriptorStorageService::class.java)
  }

  data class State(
    var descriptors: ConcurrentList<BuildDescriptorImpl> = ConcurrentList()
  )

  override fun getState(): State = buildDescriptorsState

  override fun loadState(state: State) {
    buildDescriptorsState = state
  }
}