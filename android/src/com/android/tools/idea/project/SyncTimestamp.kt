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
@file:JvmName("SyncTimestampUtil")
package com.android.tools.idea.project

import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResultListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Clock
import java.util.concurrent.atomic.AtomicLong


/**
 * Project component responsible for tracking when the last project sync completed.
 */
class SyncTimestampListener(private val project: Project): SyncResultListener {
  override fun syncEnded(result: SyncResult) {
    if (result != SyncResult.CANCELLED) {
      project.getService(SyncTimestamp::class.java).setLastSyncTimestamp(Clock.getTime())
    }
  }
}

class SyncTimestamp() {
  private val lastSyncTimestamp = AtomicLong(-1L)

  /**
   * @see Project.getLastSyncTimestamp
   */
  fun getLastSyncTimestamp() = lastSyncTimestamp.get()

  fun setLastSyncTimestamp(syncTimestamp: Long) {
    lastSyncTimestamp.set(syncTimestamp)
  }
}

/**
 * Returns the last time that a sync completed for this project in the current session without being
 * cancelled first (i.e. the result wasn't [SyncResult.CANCELLED]). A negative value indicates that
 * the project hasn't been synced yet this session.
 */
fun Project.getLastSyncTimestamp() = getService(SyncTimestamp::class.java)?.getLastSyncTimestamp() ?: -1L
