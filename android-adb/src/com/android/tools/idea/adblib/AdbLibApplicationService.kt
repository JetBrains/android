/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.adblib

import com.android.adblib.AdbChannel
import com.android.adblib.AdbChannelProvider
import com.android.adblib.AdbSession
import com.android.adblib.AdbSessionHost
import com.android.ddmlib.DdmPreferences
import com.intellij.application.subscribe
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.startup.StartupActivity
import java.time.Duration

/**
 * Application service that provides access to the implementation of [AdbSession] and [AdbSessionHost]
 * that integrate with the IntelliJ/Android Studio platform.
 *
 * Note: Prefer using [AdbLibService] if a [Project] instance is available, as [Application] and [Project]
 * could be using different SDKs. A [Project] should only use the ADB provided by the SDK used in the [Project].
 */
@Service
class AdbLibApplicationService : Disposable {
  /**
   * The [AndroidAdbSessionHost] for this [session]
   */
  private val host = AndroidAdbSessionHost()

  /**
   * The custom [AdbChannelProvider] that ensures `adb` is started before opening [AdbChannel].
   */
  private val channelProvider = AndroidAdbChannelProvider(host)

  /**
   * A [AdbSession] customized to work in the Android plugin.
   */
  val session = AdbSession.create(
    host = host,
    channelProvider = channelProvider,
    connectionTimeout = Duration.ofMillis(DdmPreferences.getTimeOut().toLong())
  )

  init {
    // Listen to "project closed" events to unregister projects
    ProjectManager.TOPIC.subscribe(this, object: ProjectManagerListener {
      override fun projectClosed(project: Project) {
        channelProvider.unregisterProject(project)
      }
    })
  }

  internal fun registerProject(project: Project): Boolean {
    return channelProvider.registerProject(project)
  }

  override fun dispose() {
    session.close()
    host.close()
  }

  /**
   * The [StartupActivity] that registers [Project] instance to the [AndroidAdbChannelProvider].
   */
  class MyStartupActivity : StartupActivity.DumbAware {
    override fun runActivity(project: Project) {
      // Startup activities run quite late when opening a project
      instance.channelProvider.registerProject(project)
    }
  }

  companion object {
    @JvmStatic
    val instance: AdbLibApplicationService
      get() = ApplicationManager.getApplication().getService(AdbLibApplicationService::class.java)
  }
}
