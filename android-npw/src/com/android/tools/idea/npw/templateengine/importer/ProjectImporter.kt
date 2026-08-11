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
package com.android.tools.idea.npw.templateengine.importer

import com.android.template.engine.TemplateMetadata
import com.android.tools.idea.gradle.project.importing.GradleProjectImporter
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import java.io.File

interface ProjectImporter {
  fun importProject(name: String, location: File): Project

  companion object {
    fun createImporter(metadata: TemplateMetadata): ProjectImporter {
      return when {
        metadata.tags.any { it.startsWith("agp") || it.startsWith("gradle") } -> GradleImporter()
        metadata.tags.any { it.startsWith("lightbuild") || it.startsWith("lume") } -> LightbuildImporter()
        else -> throw IllegalArgumentException("No ProjectImporter found for template tags: ${metadata.tags}")
      }
    }
  }
}

internal class GradleImporter : ProjectImporter {
  override fun importProject(name: String, location: File): Project {
    val importer = GradleProjectImporter.getInstance()
    val newProject = importer.createProject(name, location, useDefaultProjectAsTemplate = true)
    GradleProjectImporter.configureNewProject(newProject)
    val request = GradleProjectImporter.Request(newProject).apply { isNewProject = true }
    importer.importProjectNoSync(request)

    val openProjectTask = OpenProjectTask.build().withProject(newProject).withForceOpenInNewFrame(true)
    return ProjectManagerEx.getInstanceEx().openProject(location.toPath(), openProjectTask)
      ?: error("Failed to open Gradle project at $location")
  }
}

internal class LightbuildImporter : ProjectImporter {
  override fun importProject(name: String, location: File): Project {
    val projectManager = ProjectManagerEx.getInstanceEx()
    // First OpenProjectTask configures project creation (marking as new and naming it)
    val openProjectTask = OpenProjectTask.build().asNewProject().withProjectName(name)
    val newProject =
      projectManager.newProject(location.toPath(), openProjectTask) ?: error("Failed to create Lightbuild project at $location")

    // Set the project system provider to Lightbuild
    ProjectSystemService.getInstance(newProject).setProviderId("lightbuild")

    // Second OpenProjectTask configures how the project is opened (attaching the created
    // project and forcing new frame)
    val openTask = OpenProjectTask.build().withProject(newProject).withForceOpenInNewFrame(true)
    return projectManager.openProject(location.toPath(), openTask) ?: error("Failed to open Lightbuild project at $location")
  }
}
