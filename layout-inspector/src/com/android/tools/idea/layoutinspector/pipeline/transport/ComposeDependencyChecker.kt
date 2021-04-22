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
package com.android.tools.idea.layoutinspector.pipeline.transport

import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatistics
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService.LearnMoreAction
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.util.addDependenciesWithUiConfirmation
import com.android.tools.idea.util.dependsOn
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.util.projectStructure.allModules

private const val LEARN_MORE_LINK = "https://d.android.com/r/studio-ui/layout-inspector-add-dependency"

/**
 * The layout inspector can only show compose nodes if the compose tooling library is
 * included in the app. The layout inspector is able to show more detailed parameter
 * information if the kotlin reflection library.
 */
class ComposeDependencyChecker(
  private val project: Project,
  private val stats: SessionStatistics
) {

  fun performCheck(client: InspectorClient) {
    if (!isRunningCurrentProject(client)) {
      return
    }
    var addToolingLibrary = false
    var addReflectionLibrary = false
    val operations = mutableListOf<() -> Unit>()
    project.allModules().forEach next@{ module ->
      val moduleSystem = module.getModuleSystem()
      val runtimeVersion = moduleSystem.getResolvedDependency(GoogleMavenArtifactId.COMPOSE_RUNTIME.getCoordinate("+"))?.version
      if (!moduleSystem.usesCompose || runtimeVersion == null) {
        return@next
      }
      val missing = mutableListOf<GradleCoordinate>()
      if (!module.dependsOn(GoogleMavenArtifactId.COMPOSE_TOOLING)) {
        // Do not add a version that doesn't exist (the compose tooling library was renamed for 1.0.0-alpha08)
        if (runtimeVersion.isAtLeast(1, 0, 0, "alpha", 8, false)) {
          missing.add(GoogleMavenArtifactId.COMPOSE_TOOLING.getCoordinate(runtimeVersion.toString()))
          addToolingLibrary = true
        }
      }
      if (!module.dependsOn(GoogleMavenArtifactId.KOTLIN_REFLECT)) {
        val kotlinVersion = moduleSystem.getResolvedDependency(GoogleMavenArtifactId.KOTLIN_STDLIB.getCoordinate("+"))?.version
        missing.add(GoogleMavenArtifactId.KOTLIN_REFLECT.getCoordinate(kotlinVersion?.toString() ?: "+"))
        addReflectionLibrary = true
        stats.compose.reflectionLibraryAvailable = false
      }
      if (missing.isNotEmpty()) {
        // Only sync once:
        // Add the first operation with requestSync=true and perform the operation in reversed order below.

        val sync = operations.isEmpty()
        operations.add { module.addDependenciesWithUiConfirmation(missing, promptUserBeforeAdding = false, requestSync = sync) }
      }
    }

    if (operations.isEmpty()) {
      return
    }

    val message = createMessage(addToolingLibrary, addReflectionLibrary)
    val bannerService = InspectorBannerService.getInstance(project)
    val addToProject = object : AnAction("Add to Project") {
      override fun actionPerformed(event: AnActionEvent) {
        ApplicationManager.getApplication().invokeAndWait {
          operations.reversed().forEach { it() }
          bannerService.notification = null
        }
      }
    }
    bannerService.setNotification(message, listOf(addToProject, LearnMoreAction(LEARN_MORE_LINK), bannerService.DISMISS_ACTION))
  }

  private fun createMessage(addToolingLibrary: Boolean, addReflectionLibrary: Boolean): String {
    val toolingLibrary = "the compose tooling library"
    val reflectionLibrary = "the Kotlin reflection library"
    val libraries = listOfNotNull(toolingLibrary.takeIf { addToolingLibrary },
                                  reflectionLibrary.takeIf { addReflectionLibrary }).joinToString(" and ")
    return "To fully support inspecting Compose layouts, your app project should include $libraries."
  }

  private fun isRunningCurrentProject(client: InspectorClient): Boolean =
    project.allModules().any { module ->
      val facet = AndroidFacet.getInstance(module)
      val manifest = facet?.let { Manifest.getMainManifest(it) }
      val packageName = manifest?.`package`?.stringValue
      packageName == client.process.name
    }
}
