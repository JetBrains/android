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
package com.android.tools.idea.testing

import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.projectsystem.getMainModule
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.JavaCodeInsightTestFixtureImpl
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.testFramework.runInEdtAndWait
import java.io.File
import java.nio.file.Path

/**
 * Opens a project using [openProjectImplementation], supplements it with `JavaCodeInsightTestFixture` and runs the `testBody`.
 */
internal fun <T> openProjectAndRunTestWithTestFixturesAvailable(
  openProjectImplementation: ((project: Project, projectRoot: File) -> T) -> T,
  testBody: PreparedTestProject.Context.(project: Project) -> T
): T {
  aggregateAndThrowIfAny {
    var currentProject: Project? = null
    var currentModule: Module? = null
    val rootDisposable = Disposer.newDisposable()

    // This test fixture is used just to provide context to `JavaCodeInsightTestFixtureImpl`. The actual life-cycle is provided by our
    // caller's `IdeaProjectTestFixture`.
    // Note: `projectTestFixture`'s `setUp` and `tearDown` are invoked from `JavaCodeInsightTestFixtureImpl`.
    val projectTestFixture = object : IdeaProjectTestFixture {
      override fun setUp() = Unit  // Invoked by JavaCodeInsightTestFixtureImpl.setUp()
      override fun tearDown() = Unit  // Invoked by JavaCodeInsightTestFixtureImpl.tearDown()
      override fun getProject(): Project = currentProject ?: error("Unexpected: project must have been initialized by now")
      override fun getModule(): Module? = currentModule ?: project.gradleModule(":app")?.getMainModule()
      override fun getTestRootDisposable(): Disposable = rootDisposable
    }

    return try {
      openProjectImplementation { project: Project, projectRoot: File ->
        currentProject = project

        // This test fixture is used just to provide context to `JavaCodeInsightTestFixtureImpl`. The actual life-cycle is provided by our
        // caller's `TempDirTestFixture`.
        // Note: `tempDirFixture`'s `setUp` and `tearDown` are invoked from JavaCodeInsightTestFixtureImpl.
        val tempDirFixture = object : TempDirTestFixtureImpl() {
          override fun deleteOnTearDown(): Boolean = false
          override fun doCreateTempDirectory(): Path = projectRoot.toPath()
        }

        val codeInsightTestFixture = JavaCodeInsightTestFixtureImpl(projectTestFixture, tempDirFixture)
        usingIdeaTestFixture(codeInsightTestFixture) {
          val preparedProjectContext = object : PreparedTestProject.Context {
            override val fixture: JavaCodeInsightTestFixture = codeInsightTestFixture
            override val project: Project = project
            override val projectRoot: File = projectRoot
            override fun selectModule(module: Module) {
              currentModule = module
            }
          }
          testBody(preparedProjectContext, project)
        }
      }
    } finally {
      runInEdtAndWait { runCatchingAndRecord { Disposer.dispose(projectTestFixture.testRootDisposable) } }
    }
  }
}

