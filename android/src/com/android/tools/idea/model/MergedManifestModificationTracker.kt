/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.model

import com.android.tools.idea.projectsystem.ProjectSyncModificationTracker
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.module.Module
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker

/**
 * A module-wide modification tracker whose modification count is a value
 * incremented by any modifications of corresponding android manifest files .
 * Also, it can be incremented after any project syncs.
 */
class MergedManifestModificationTracker(val module: Module) : ModificationTracker {
  private val manifestContributorTracker = SimpleModificationTracker()
  private val LOG: Logger get() = Logger.getInstance(MergedManifestModificationTracker::class.java)

  init {
    // If query happens before indexing when project just starts up, invalid queried results are cached.
    // So we need to explicitly update tracker to ensure another index query, instead of providing stale cached results.
    StartupManager.getInstance(module.project).runAfterOpened {
      manifestChanged()
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(module: Module): MergedManifestModificationTracker = module.getService(MergedManifestModificationTracker::class.java)
  }

  /**
   * Returns count from merged manifest contributors change + project sync count
   */
  override fun getModificationCount(): Long {
    return manifestContributorTracker.modificationCount + ProjectSyncModificationTracker.getInstance(module.project).modificationCount
  }

  /**
   * This is invoked when MergedManifestRefreshListener detects
   * a manifest contributor has been changed for this module
   * or one of its transitive dependencies
   */
  fun manifestChanged() {
    manifestContributorTracker.incModificationCount()
    LOG.debug { "MergedManifest Modification Tracker of $module is updated to $modificationCount" }
  }
}