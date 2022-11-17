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
package com.android.tools.idea.lint

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.lint.common.LintIdeClient
import com.android.tools.idea.lint.common.LintIgnoredResult
import com.android.tools.idea.lint.common.LintResult
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class AndroidLintIdeProjectGradleIntegrationTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  var expect = Expect.createAndEnableStackTrace()

  @Test
  fun test() {
    val result: LintResult = LintIgnoredResult()
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.TRANSITIVE_DEPENDENCIES)
    preparedProject.open { ideProject ->
      val root = preparedProject.root
      val client: LintIdeClient = AndroidLintIdeClient(ideProject, result)
      val projects = AndroidLintIdeProject.create(client, null, *ModuleManager.getInstance(ideProject).modules)
      assertThat(
        projects
          .map { lintProject ->
            flattenDag(
              lintProject,
              getId = { it.dir },
              getChildren = {
                it.directLibraries.filter { dependency -> dependency.buildModule != null }
              }
            )
          }
          .flatten() // Modules may be repeated here if a dependency is shared between roots.
          .map { it.dir }
          .distinct()
      )
        .containsExactly(
          root,
          root.resolve("app"),
          root.resolve("library1"),
          root.resolve("library2"),
          root.resolve("javalib1"),
          root.resolve("javalib2"),
        )
    }
  }
}

private fun <T : Any> flattenDag(root: T, getId: (T) -> Any = { it }, getChildren: (T) -> List<T>): List<T> = sequence {
  val seen = HashSet<Any>()
  val queue = ArrayDeque(listOf(root))

  while (queue.isNotEmpty()) {
    val item = queue.removeFirst()
    if (seen.add(getId(item))) {
      queue.addAll(getChildren(item))
      yield(item)
    }
  }
}
  .toList()

