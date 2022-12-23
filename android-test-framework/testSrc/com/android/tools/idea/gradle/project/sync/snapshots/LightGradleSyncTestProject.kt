/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.snapshots

import com.android.tools.idea.gradle.project.sync.idea.GradleSyncExecutor
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.IntegrationTestEnvironment
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.ModuleModelBuilder
import com.android.tools.idea.testing.OpenPreparedProjectOptions
import com.android.tools.idea.testing.setupTestProjectFromAndroidModel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.runInEdtAndWait
import java.io.File
import java.nio.file.Path

interface LightGradleSyncTestProject : TestProjectDefinition {
  val templateProject: TemplateBasedTestProject
  val modelBuilders: List<ModuleModelBuilder>

  override val isCompatibleWith: (AgpVersionSoftwareEnvironmentDescriptor) -> Boolean
    get() = { true }

  override fun prepareTestProject(
    integrationTestEnvironment: IntegrationTestEnvironment,
    name: String,
    agpVersion: AgpVersionSoftwareEnvironmentDescriptor,
    ndkVersion: String?
  ): PreparedTestProject {
    val preparedProject = templateProject.prepareTestProject(integrationTestEnvironment, name, agpVersion, ndkVersion)
    preparedProject.root.resolve(".gradle").mkdir()
    return object: PreparedTestProject {
      override fun <T> open(
        updateOptions: (OpenPreparedProjectOptions) -> OpenPreparedProjectOptions,
        body: PreparedTestProject.Context.(Project) -> T
      ): T {
        val options = createTestOpenProjectOptions(true)
        if (ApplicationManager.getApplication().isDispatchThread) {
          PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        }
        val project =
          ProjectManagerEx.getInstanceEx()
            .openProject(
              Path.of(integrationTestEnvironment.getBaseTestPath(), name).parent.resolve(name),
              options.copy(beforeInit = {
                it.putUserData(
                  GradleSyncExecutor.ALWAYS_SKIP_SYNC, true
                )
              })
            )
            ?: error("Failed to open a test project")
        try {
          invokeAndWaitIfNeeded {
            setupTestProjectFromAndroidModel(project, preparedProject.root, *modelBuilders.toTypedArray())
          }
          val context = object: PreparedTestProject.Context {
            override val project: Project = project
          }
          return body(context, project)
        }
        finally {
          runInEdtAndWait {
            ProjectManagerEx.getInstanceEx().forceCloseProject(project)
          }
        }
      }

      override val root: File
        get() = preparedProject.root
    }
  }
}

enum class LightGradleSyncTestProjects(
  override val templateProject: TemplateBasedTestProject,
  override val modelBuilders: List<ModuleModelBuilder>

) : LightGradleSyncTestProject {
  SIMPLE_APPLICATION(
    AndroidCoreTestProject.SIMPLE_APPLICATION,
    listOf(
      JavaModuleModelBuilder.rootModuleBuilder,
      AndroidModuleModelBuilder(
        gradlePath = ":app",
        selectedBuildVariant = "debug",
        projectBuilder = AndroidProjectBuilder(
          namespace = { "google.simpleapplication" }
        ).build(),
      )
    )
  );
}