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
package com.android.tools.idea.nav.safeargs.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker

/**
 * A project-wide modification tracker whose modification count is a value incremented by any
 * modifications of corresponding navigation resource files.
 */
class ProjectNavigationResourceModificationTracker(project: Project) :
  ModificationTracker, Disposable.Default {
  private val navigationModificationTracker = SimpleModificationTracker()

  init {
    project.messageBus
      .connect(this)
      .subscribe(
        NAVIGATION_RESOURCES_CHANGED,
        NavigationResourcesChangeListener { navigationChanged() }
      )
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project) =
      project.getService(ProjectNavigationResourceModificationTracker::class.java)!!
  }

  override fun getModificationCount() = navigationModificationTracker.modificationCount

  /**
   * This is invoked when NavigationModificationListener detects a navigation file has been changed
   * or added or deleted for this project
   */
  private fun navigationChanged() {
    navigationModificationTracker.incModificationCount()
  }
}
