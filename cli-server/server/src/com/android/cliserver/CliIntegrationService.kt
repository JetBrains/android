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
import com.google.protobuf.kotlin.toByteString
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectActivity

@Service
class CliIntegrationService : Disposable, ServerInfoProvider {

  companion object {
    fun getInstance() = service<CliIntegrationService>()
  }

  var started = false
  val startedLock = Any()
  var server: CliServer? = null

  fun startIfNeeded() {
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

  class CliIntegrationServiceStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
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
    } else {
      (openProjects.singleOrNull { it.name == projectName })
    }
  // TODO: android-merge; upstream builds the three responses below with the generated Kotlin DSL, `commandResponse { ... }`
  //  and `genericError { ... }`. Those builders are top-level functions of the generated proto, and the studio-platform jar
  //  declares no kotlin_module for com.android.cliserver, so kotlinc cannot see them here. Same messages, built with the
  //  generated Java builders.
  if (project == null) {
    return CommandResponse.newBuilder()
      .setError(
        GenericError.newBuilder()
          .setMessage(
            when {
              openProjects.isEmpty() -> "Studio has no open projects"
              request.project.isNullOrEmpty() && openProjects.size > 1 -> "There are multiple open projects, please specify one"
              else -> "No project with name ${request.project}"
            }
          )
      )
      .build()
  }
  return try {
    val result = handle(project, request.payload.toByteArray())
    CommandResponse.newBuilder().setProject(project.name).setPayload(result.toByteString()).build()
  } catch (e: Exception) {
    CommandResponse.newBuilder()
      .setProject(project.name)
      .setError(GenericError.newBuilder().setMessage(e.message ?: "Unknown error"))
      .build()
  }
}

interface CliActionHandler {
  companion object {
    val EP_NAME = ExtensionPointName.create<CliActionHandler>("com.android.cliserver.requestHandler")
  }

  val type: Int

  fun handle(project: Project, request: ByteArray): ByteArray
}
