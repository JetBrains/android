// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android

import com.android.tools.idea.AndroidStartupActivity
import com.intellij.ProjectTopics
import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetManagerAdapter
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.android.facet.AndroidFacet

class AndroidStartupManager : StartupActivity {

  // A project level service to be used as a parent disposable. Future implementation may reduce the life-time of such disposable to the
  // moment when the last Android facet is removed (from a non-Gradle project).
  @Service
  class ProjectDisposableScope : Disposable {
    override fun dispose() = Unit
  }

  override fun runActivity(project: Project) {
    fun runAndroidStartupActivities() {
      for (activity in AndroidStartupActivity.STARTUP_ACTIVITY.extensionList) {
        invokeAndWaitIfNeeded {
          activity.runActivity(project, project.getService(ProjectDisposableScope::class.java))
        }
      }
    }

    if (!ApplicationManager.getApplication().isUnitTestMode &&
        !ApplicationManager.getApplication().isHeadlessEnvironment) {
      if (ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) {
        runAndroidStartupActivities()
      }
      else {
        val connection: MessageBusConnection = project.messageBus.connect()
        connection.subscribe(FacetManager.FACETS_TOPIC, object : FacetManagerAdapter() {
          override fun facetAdded(facet: Facet<*>) {
            if (facet is AndroidFacet) {
              runAndroidStartupActivities()
              connection.disconnect()
            }
          }
        })
        // [facetAdded] is not invoked for a facet if it is added together with a new module that holds it.
        connection.subscribe(ProjectTopics.MODULES, object : ModuleListener {
          override fun modulesAdded(project: Project, modules: List<Module>) {
            for (module in modules) {
              if (AndroidFacet.getInstance(module) != null) {
                runAndroidStartupActivities()
                connection.disconnect()
                break
              }
            }
          }
        })
      }
    }
  }
}