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
package com.android.tools.idea.stats

import com.android.tools.idea.diagnostics.AndroidStudioSystemHealthMonitor
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.sun.management.GcInfo
import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicBoolean
import javax.management.NotificationEmitter
import javax.management.openmbean.CompositeData

private val initialized = AtomicBoolean()

/** Collects GC-pause statistics. */
class GcPauseWatcher : ProjectActivity {

  override suspend fun execute(project: Project) {
    // Note: IntelliJ strongly discourages running code during IDE startup,
    // so instead we initialize these GC listeners during first project open.
    if (initialized.compareAndSet(false, true)) {
      ManagementFactory.getGarbageCollectorMXBeans().forEach { bean ->
        (bean as NotificationEmitter).addNotificationListener(
          { notification, _ ->
            val data = notification.userData as CompositeData
            val duration = GcInfo.from(data.get("gcInfo") as CompositeData).duration
            AndroidStudioSystemHealthMonitor.recordGcPauseTime(bean.name, duration)
           }, null, null)
      }
    }
  }
}
