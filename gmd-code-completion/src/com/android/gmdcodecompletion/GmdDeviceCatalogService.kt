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
package com.android.gmdcodecompletion

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Defines and ensures thread safe read and write operations for all GMD code completion device catalog services */
abstract class GmdDeviceCatalogService<T : GmdDeviceCatalogState>(
  private val emptyDeviceCatalogState: T,
  private val myServiceName: String) : PersistentStateComponent<T> {

  private lateinit var myDeviceCatalogState: T

  /**
   * In the load state and get state functions, we are not guaranteed that myDeviceCatalogState
   * is not used / modified in another thread. While syncing device catalog, user can already
   * invoke code completion (indexing is finished by then). Without the lock the myDeviceCatalogState
   * obtained in code completion contributor might be in an undefined state.
   */
  protected val myLock: Lock = ReentrantLock()

  /**
   * This function runs before Task starts in updateDeviceCatalog. Do not call from EDT
   * as it makes expensive calls to [ProjectBuildModel.get]
   *
   * Return false to avoid running the updateDeviceCatalog function. Default is true
   */
  protected open fun shouldUpdate(project: Project) : Boolean { return true }

  // Update corresponding device catalog if necessary (catalog outdated / no catalog found)
  fun updateDeviceCatalog(project: Project) {
    object : Task.Backgroundable(project, "Syncing in $myServiceName", true) {
      override fun run(indicator: ProgressIndicator) {
        if(!shouldUpdate(project)) return
        updateDeviceCatalogTaskAction(project, indicator)
      }
    }.setCancelText("Device catalog sync stopped").queue()
  }

  abstract fun updateDeviceCatalogTaskAction(project: Project, indicator: ProgressIndicator)

  final override fun loadState(state: T) = myLock.withLock { myDeviceCatalogState = state }

  final override fun getState(): T {
    /**
     * Use myLock.tryLock() to avoid freezing UI when user invokes code completion but device catalog sync is still not completed.
     */
    if (myLock.tryLock()) {
      try {
        if (this::myDeviceCatalogState.isInitialized) return myDeviceCatalogState
      }
      finally {
        myLock.unlock()
      }
    }
    return emptyDeviceCatalogState
  }

  protected fun setState(state: T){
    myLock.withLock {
      myDeviceCatalogState = state
    }
  }
}