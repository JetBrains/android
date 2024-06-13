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

import com.android.tools.idea.nav.safeargs.project.NAVIGATION_RESOURCES_CHANGED
import com.android.tools.idea.nav.safeargs.project.NavigationResourcesChangeListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker

/**
 * A module-wide modification tracker whose modification count is a value incremented by any
 * modifications of corresponding navigation resource files.
 */
class ModuleNavigationResourcesModificationTracker(val module: Module) :
  ModificationTracker, Disposable {
  private val navigationModificationTracker = SimpleModificationTracker()

  init {
    module.project.messageBus
      .connect(this)
      .subscribe(
        NAVIGATION_RESOURCES_CHANGED,
        NavigationResourcesChangeListener { changedModule ->
          if (changedModule == null || changedModule == module) {
            navigationChanged()
          }
        },
      )
  }

  companion object {
    @JvmStatic
    fun getInstance(module: Module) =
      module.getService(ModuleNavigationResourcesModificationTracker::class.java)!!
  }

  override fun getModificationCount() = navigationModificationTracker.modificationCount

  override fun dispose() {}

  /**
   * This is invoked when NavigationModificationListener detects a navigation file has been changed
   * or added or deleted for this module
   */
  private fun navigationChanged() {
    navigationModificationTracker.incModificationCount()
    logger<ModuleNavigationResourcesModificationTracker>().debug {
      "Navigation Modification Tracker of $module is updated to $modificationCount"
    }
  }
}
