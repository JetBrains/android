/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.cliserver

import com.android.prefs.AndroidLocationsSingleton
import com.android.tools.idea.flags.StudioFlags
import com.google.protobuf.kotlin.toByteString
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import java.nio.file.Paths

@Service
class CliIntegrationService : Disposable, ServerInfoProvider {

  companion object {
    fun getInstance() = service<CliIntegrationService>()
  }

  var started = false
  val startedLock = Any()
  var server: CliServer? = null

  fun startIfNeeded() {
    if (!StudioFlags.ENABLE_CLI_INTEGRATION_SERVER.get()) return

    synchronized(startedLock) {
      if (!started) {
        server = CliServer(CliServerRegistry(AndroidLocationsSingleton.prefsLocation), this)

        CliActionHandler.EP_NAME.extensions.forEach { handler ->
          server?.registerCommandHandler(handler.type) { handler.invokeHandler(it) }
        }
        server?.start()
        started = true
      }
    }
  }

  class CliIntegrationServiceInitializer : ApplicationInitializedListener {
    override suspend fun execute() {
      getInstance().startIfNeeded()
    }
  }

  override fun dispose() {
    synchronized(startedLock) {
      server?.stop()
      server = null
      started = false
    }
  }

  override fun status() =
    ServerInfoProvider.ServerInfo(
      ApplicationInfo.getInstance().fullVersion,
      ProjectManager.getInstance().openProjects.mapNotNull { project ->
        ServerInfoProvider.ProjectInfo(
          project.name,
          project.basePath ?: return@mapNotNull null,
          if (DumbService.getInstance(project).isDumb) ServerInfoProvider.ProjectStatus.NOT_READY
          else ServerInfoProvider.ProjectStatus.READY,
        )
      },
    )
}

internal fun CliActionHandler.invokeHandler(
  request: CommandRequest,
  openProjects: Array<Project> = ProjectManager.getInstance().openProjects,
): CommandResponse {
  val projectName = request.project

  val project =
    if (projectName.isEmpty() && openProjects.size == 1) {
      openProjects[0]
    } else if (projectName.isNotEmpty()) {
      val targetPath =
        try {
          Paths.get(projectName).toAbsolutePath().normalize()
        } catch (_: Exception) {
          null
        }
      openProjects.singleOrNull { p ->
        p.name == projectName ||
          (targetPath != null &&
            p.basePath != null &&
            try {
              Paths.get(p.basePath).toAbsolutePath().normalize().equals(targetPath)
            } catch (_: Exception) {
              false
            })
      }
    } else {
      null
    }

  val projectProvider: () -> Project = {
    project
      ?: throw IllegalStateException(
        when {
          openProjects.isEmpty() -> "Studio has no open projects"
          request.project.isNullOrEmpty() && openProjects.size > 1 -> "There are multiple open projects, please specify one"
          else -> "No project found matching \"${request.project}\""
        }
      )
  }

  return try {
    val result = handle(projectProvider, request.payload.toByteArray())
    commandResponse {
      project?.let { this.project = it.name }
      payload = result.toByteString()
    }
  } catch (e: Exception) {
    commandResponse {
      project?.let { this.project = it.name }
      error = genericError { message = e.message ?: "Unknown error" }
    }
  }
}

interface CliActionHandler {
  companion object {
    val EP_NAME = ExtensionPointName.create<CliActionHandler>("com.android.cliserver.requestHandler")
  }

  val type: Int

  fun handle(projectLookup: () -> Project, request: ByteArray): ByteArray
}
