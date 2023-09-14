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
package com.android.tools.idea.nav.safeargs.tracker

import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.withProjectId
import com.android.tools.idea.nav.safeargs.SafeArgsMode
import com.android.tools.idea.nav.safeargs.safeArgsMode
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.NavSafeArgsEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

/** A service which allows tracking safe args related metrics. */
abstract class SafeArgsTracker(private val project: Project) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): SafeArgsTracker {
      return project.getService(SafeArgsTracker::class.java)
    }
  }

  fun trackProjectStats(context: NavSafeArgsEvent.EventContext) {
    runSlowWork {
      val allFacets = project.getAndroidFacets()
      val javaPluginFacets = allFacets.count { it.safeArgsMode == SafeArgsMode.JAVA }
      val kotlinPluginFacets = allFacets.count { it.safeArgsMode == SafeArgsMode.KOTLIN }
      if (javaPluginFacets + kotlinPluginFacets == 0) return@runSlowWork

      val safeArgsEvent =
        NavSafeArgsEvent.newBuilder()
          .setEventContext(context)
          .setProjectMetadata(
            NavSafeArgsEvent.ProjectMetadata.newBuilder()
              .setModuleCount(allFacets.size)
              .setJavaPluginCount(javaPluginFacets)
              .setKotlinPluginCount(kotlinPluginFacets)
          )

      track(safeArgsEvent)
    }
  }

  private fun track(safeArgsEvent: NavSafeArgsEvent.Builder) {
    val studioEvent =
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.NAV_SAFE_ARGS_EVENT)
        .setNavSafeArgsEvent(safeArgsEvent)

    UsageTracker.log(studioEvent.withProjectId(project))
  }

  /**
   * Some of these metrics are collected when Gradle syncs, and we don't want to add any perceived
   * time delay to this critical operations. Therefore, we delegate slow work to some sort of
   * handler (which in production should be a background thread).
   */
  protected abstract fun runSlowWork(block: () -> Unit)
}

class DefaultSafeArgsTracker(private val project: Project) : SafeArgsTracker(project) {
  override fun runSlowWork(block: () -> Unit) {
    ApplicationManager.getApplication().executeOnPooledThread { block() }
  }
}
