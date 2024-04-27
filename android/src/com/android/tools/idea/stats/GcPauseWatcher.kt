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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.sun.management.GcInfo
import java.lang.management.ManagementFactory
import javax.management.NotificationEmitter
import javax.management.openmbean.CompositeData

@Service
private class GcPauseWatcher {
  init {
    ManagementFactory.getGarbageCollectorMXBeans().forEach { bean ->
      (bean as NotificationEmitter).addNotificationListener(
        { notification, _ ->
          val data = notification.userData as CompositeData
          val duration = GcInfo.from(data.get("gcInfo") as CompositeData).duration
          AndroidStudioSystemHealthMonitor.recordGcPauseTime(bean.name, duration)
         }, null, null)
    }
  }

  companion object {
    fun getInstance() : GcPauseWatcher {
      return ApplicationManager.getApplication().getService(GcPauseWatcher::class.java)
    }
  }

  private class Initializer : StartupActivity.Background {
    override fun runActivity(project: Project) {
      // Access the application instance to trigger its initialization
      getInstance()
    }
  }
}