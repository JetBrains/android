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

  protected abstract var myDeviceCatalogState: T
  protected val myLock: Lock = ReentrantLock()

  /**
   * This function runs before Task starts in updateDeviceCatalog. Used for tasks that should
   * not run in background thread (e.g. interactions with PSI elements
   */
  protected open fun runBeforeUpdate(project: Project):Boolean { return false }

  // Update corresponding device catalog if necessary (catalog outdated / no catalog found)
  fun updateDeviceCatalog(project: Project, beforeTaskAction: (Project) -> Boolean = ::runBeforeUpdate) {
    if(beforeTaskAction(project)) return
    object : Task.Backgroundable(project, "Syncing in $myServiceName", true) {
      override fun run(indicator: ProgressIndicator) {
        updateDeviceCatalogTaskAction(project, indicator)
      }
    }.setCancelText("Device catalog sync stopped").queue()
  }

  abstract fun updateDeviceCatalogTaskAction(project: Project, indicator: ProgressIndicator)

  final override fun loadState(state: T) = myLock.withLock { myDeviceCatalogState = state }

  final override fun getState(): T {
    if (myLock.tryLock()) {
      try {
        return myDeviceCatalogState
      }
      finally {
        myLock.unlock()
      }
    }
    return emptyDeviceCatalogState
  }
}