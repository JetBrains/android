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
import com.android.adblib.AdbServerChannelProvider
import com.android.adblib.AdbSession
import com.android.adblib.AdbSessionHost
import com.android.adblib.tools.debugging.processinventory.ProcessInventoryJdwpProcessPropertiesCollectorFactory
import com.android.adblib.tools.debugging.processinventory.server.ProcessInventoryServerConfiguration
import com.android.ddmlib.DdmPreferences
import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.flags.StudioFlags
import com.intellij.application.subscribe
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
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
   * The custom [AdbServerChannelProvider] that ensures `adb` is started before opening [AdbChannel].
   */
  private val channelProvider = AndroidAdbServerChannelProvider(host)

  /**
   * A [AdbSession] customized to work in the Android plugin.
   */
  val session = AdbSession.create(
    host = host,
    channelProvider = channelProvider,
    connectionTimeout = Duration.ofMillis(DdmPreferences.getTimeOut().toLong())
  ).also { session ->
    // Note: We need to install a ProcessInventoryServerJdwpPropertiesCollectorFactory instance on
    //   the *application* AdbSession only (i.e. this one), because all JdwpProcess instances
    //   are delegated to this AdbSession.

    val enabled = {
      StudioFlags.ADBLIB_MIGRATION_DDMLIB_CLIENT_MANAGER.get() &&
      StudioFlags.ADBLIB_USE_PROCESS_INVENTORY_SERVER.get()
    }

    ProcessInventoryJdwpProcessPropertiesCollectorFactory.installForSession(
      session,
      enabled = enabled,
      config = StudioProcessInventoryServerConfiguration(),
    )
  }

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
   * The [StartupActivity] that registers [Project] instance to the [AndroidAdbServerChannelProvider].
   */
  class MyStartupActivity : StartupActivity.DumbAware {
    override fun runActivity(project: Project) {
      // Startup activities run quite late when opening a project
      instance.channelProvider.registerProject(project)
    }
  }

  private class StudioProcessInventoryServerConfiguration : ProcessInventoryServerConfiguration {

    private var _clientDescription: String? = null
    override val clientDescription: String
      get() {
        // Note: "Cheap" lazy implementation
        return _clientDescription ?: "ProcessInventory(role='client', ${applicationInfo()})".also {
          _clientDescription = it
        }
      }

    private var _serverDescription: String? = null
    override val serverDescription: String
      get() {
        // Note: "Cheap" lazy implementation
        return _serverDescription ?: "ProcessInventory(role='server', ${applicationInfo()})".also {
          _serverDescription = it
        }
      }

    private fun applicationInfo(): String {
      return "product='${ApplicationInfo.getInstance().fullApplicationName}', " +
             "pathSelector='${PathManager.getPathsSelector()}'"
    }
  }

  companion object {
    @JvmStatic
    val instance: AdbLibApplicationService
      get() = ApplicationManager.getApplication().getService(AdbLibApplicationService::class.java)


    /**
     * Returns the [DeviceProvisioner] best matching the [session]. This method is needed
     * because some components (e.g. ddmlib compatibility layer) uses the [AdbSession] from
     * [AdbLibApplicationService.session], so there is no [Project] for the passed in session,
     * meaning there is no [DeviceProvisioner] readily available.
     */
    @JvmStatic
    fun getDeviceProvisionerForSession(session: AdbSession): DeviceProvisioner? {
      val projects = ProjectManager.getInstance().openProjects.toList()

      return if (session === instance.session) {
        // If application service session, use the first available device provisioner
        projects.firstNotNullOfOrNull { project ->
          project.serviceIfCreated<DeviceProvisionerService>()?.deviceProvisioner
        }
      }
      else {
        // Find project corresponding to the adblib session
        projects.firstNotNullOfOrNull { project ->
          val projectSession = project.serviceIfCreated<AdbLibService>()?.session
          if (session === projectSession) {
            project.service<DeviceProvisionerService>().deviceProvisioner
          }
          else {
            null
          }
        }
      }
    }
  }
}
