/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.android

import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.gradle.structure.configurables.PsContextImpl
import com.android.tools.idea.gradle.structure.model.PsDependencyCollection
import com.android.tools.idea.gradle.structure.model.PsModuleDependency
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.testResolve
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.OpenPreparedProjectOptions
import com.android.tools.idea.testing.requestSyncAndWait
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.android.AndroidTestBase.refreshProjectFiles

internal fun <T> PsDependencyCollection<*, *, *, T>.findModuleDependency(gradlePath: String)
  where T : PsModuleDependency = findModuleDependencies(gradlePath).singleOrNull()

internal fun PsAndroidModule.findVariant(name: String): PsVariant? = resolvedVariants.singleOrNull { it.name == name }

class PsTestContext(val resolvedProject: Project, val context: PsContextImpl, val project: PsProjectImpl)

interface PsTestProject {
  val resolvedProject: Project
  val project: PsProjectImpl
  fun reparse()
  fun requestSyncAndWait()
}

fun <T> IntegrationTestEnvironmentRule.psTestWithContext(
  preparedProject: PreparedTestProject,
  disableAnalysis: Boolean = false,
  resolveModels: Boolean = true,
  body: PsTestContext.() -> T
): T {
  return preparedProject.open { resolvedProject ->
    val project = PsProjectImpl(resolvedProject)
    if (resolveModels) {
      project.testResolve()
    }

    val context = PsContextImpl(
      project,
      testRootDisposable,
      disableAnalysis = disableAnalysis,
      disableResolveModels = !resolveModels
    )
      .also { Disposer.register(testRootDisposable, it) }
    body(PsTestContext(resolvedProject, context, project))
  }
}

fun <T> IntegrationTestEnvironmentRule.psTestWithProject(
  preparedProject: PreparedTestProject,
  resolveModels: Boolean = true,
  expectSyncFailing: Boolean = false,
  body: PsTestProject.() -> T
): T {
  fun updateOptions(options: OpenPreparedProjectOptions): OpenPreparedProjectOptions {
    return options
      .copy(disableKtsRelatedIndexing = true)
      .let {
        if (expectSyncFailing)
          it.copy(
            verifyOpened = { project -> assertThat(project.getProjectSystem().getSyncManager().getLastSyncResult().isSuccessful).isFalse() }
          )
        else it
      }
  }

  return preparedProject.open(
    updateOptions = ::updateOptions
  ) { resolvedProject ->
    var psProject = PsProjectImpl(resolvedProject)
    if (resolveModels) {
      psProject.testResolve()
    }

    body(object: PsTestProject{
      override val resolvedProject: Project = resolvedProject
      override val project: PsProjectImpl
        get() = psProject

      override fun reparse() {
        psProject = PsProjectImpl(resolvedProject)
        if (resolveModels) {
          psProject.testResolve()
        }
      }

      override fun requestSyncAndWait() {
        refreshProjectFiles()
        resolvedProject.requestSyncAndWait()
      }
    })
  }
}
