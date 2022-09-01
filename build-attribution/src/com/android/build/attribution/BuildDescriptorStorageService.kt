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

import com.android.annotations.concurrency.Slow
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import kotlin.properties.Delegates

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
  var limitSizeHistory by Delegates.notNull<Int>()

  companion object {
    @JvmStatic
    fun getInstance(project: Project): BuildDescriptorStorageService =
      project.getService(BuildDescriptorStorageService::class.java)
  }

  init {
    onSettingsChange()
  }

  data class State(
    var descriptors: MutableSet<BuildDescriptorImpl> = mutableSetOf()
  )

  /**
   * There should be [BuildAnalysisResults] with the same id and stored in [BuildAnalyzerStorageManager]
   * to prevent errors on cleanup
   */
  fun add(buildSessionID: String, buildFinishedTimestamp: Long, totalBuildTimeMs: Long) {
    buildDescriptorsState.descriptors.add(BuildDescriptorImpl(buildSessionID, buildFinishedTimestamp, totalBuildTimeMs))
    deleteOldRecords(BuildAnalyzerStorageManager.getInstance(project))
  }

  /**
   * Clear all build descriptors
   */
  fun clear() =
    buildDescriptorsState.descriptors.clear()

  fun getDescriptors(): Set<BuildDescriptor> = buildDescriptorsState.descriptors

  /**
   * Deletes old records until count of descriptors in list is more than [limitSizeHistory]
   */
  @Slow
  private fun deleteOldRecords(storageManager: BuildAnalyzerStorageManager) {
    require(limitSizeHistory >= 0) { "[limitSizeHistory] should not be less than 0" }
    val descriptors = buildDescriptorsState.descriptors
    while (descriptors.size > limitSizeHistory) {
      val oldestOne = descriptors.minByOrNull { it.buildFinishedTimestamp }
      require(oldestOne != null) { "List of descriptors is empty => 0 is more than [limitSizeHistory]" }
      storageManager.deleteHistoricBuildResultByID(oldestOne.buildSessionID)
      descriptors.remove(oldestOne)
    }
  }

  @Slow
  fun onSettingsChange() {
    limitSizeHistory = BuildAnalyzerSettings.getInstance(project).state.maxNumberOfBuildsStored
    deleteOldRecords(BuildAnalyzerStorageManager.getInstance(project))
  }

  override fun getState(): State = buildDescriptorsState

  override fun loadState(state: State) {
    buildDescriptorsState = state
  }
}