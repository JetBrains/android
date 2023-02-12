// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.android

import com.android.tools.idea.AndroidStartupActivity
import com.intellij.ProjectTopics
import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetManagerListener
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.android.facet.AndroidFacet

private class AndroidStartupManager : ProjectActivity {
  // A project level service to be used as a parent disposable. Future implementation may reduce the life-time of such disposable to the
  // moment when the last Android facet is removed (from a non-Gradle project).
  @Service(Service.Level.PROJECT)
  class ProjectDisposableScope : Disposable {
    override fun dispose() {
    }
  }

  init {
    if (ApplicationManager.getApplication().isUnitTestMode || ApplicationManager.getApplication().isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    suspend fun runAndroidStartupActivities() {
      for (activity in AndroidStartupActivity.STARTUP_ACTIVITY.extensionList) {
        withContext(Dispatchers.EDT) {
          activity.runActivity(project, project.service<ProjectDisposableScope>())
        }
      }
    }

    if (ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) {
      runAndroidStartupActivities()
      return
    }

    val connection = project.messageBus.simpleConnect()
    connection.subscribe(FacetManager.FACETS_TOPIC, object : FacetManagerListener {
      override fun facetAdded(facet: Facet<*>) {
        if (facet is AndroidFacet) {
          @Suppress("DEPRECATION")
          project.coroutineScope.launch {
            runAndroidStartupActivities()
          }
          connection.disconnect()
        }
      }
    })

    // [facetAdded] is not invoked for a facet if it is added together with a new module that holds it.
    connection.subscribe(ProjectTopics.MODULES, object : ModuleListener {
      override fun modulesAdded(project: Project, modules: List<Module>) {
        for (module in modules) {
          if (AndroidFacet.getInstance(module) != null) {
            @Suppress("DEPRECATION")
            project.coroutineScope.launch {
              runAndroidStartupActivities()
            }
            connection.disconnect()
            break
          }
        }
      }
    })
  }
}