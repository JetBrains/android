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
package com.android.tools.idea.lint.model

import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.lint.LintTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.saveAndDump
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Snapshot tests for 'Lint Models'.
 *
 * These tests convert Lint models to a stable text format which does not depend on local
 * environment (and ideally should not depend on the versions of irrelevant libraries) and compare them to pre-recorded golden
 * results.
 *
 * The pre-recorded sync results can be found in [snapshotDirectoryWorkspaceRelativePath] *.txt files.
 *
 * For instructions on how to update the snapshot files see [SnapshotComparisonTest] and if running from the command-line use
 * target as "//tools/adt/idea/android-lint:intellij.android.lint.tests_tests__all --filter=LintModelSnapshotComparisonTest".
 */

@RunsInEdt
@RunWith(Parameterized::class)
class LintModelSnapshotComparisonTest : SnapshotComparisonTest {

  data class TestProjectDef(val template: LintTestProject) {
    override fun toString(): String = template.template.removePrefix("projects/") + template.pathToOpen
  }

  @JvmField
  @Parameterized.Parameter
  var testProjectName: TestProjectDef? = null

  companion object {
    @Suppress("unused")
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun testProjects(): Collection<*> = listOf(
      TestProjectDef(LintTestProject.SIMPLE_APPLICATION),
      TestProjectDef(LintTestProject.BASIC_CMAKE_APP),
      TestProjectDef(LintTestProject.PSD_SAMPLE_GROOVY),
      TestProjectDef(LintTestProject.MULTI_FLAVOR), // TODO(b/178796251): The snapshot does not include `proguardFiles`.
      TestProjectDef(LintTestProject.COMPOSITE_BUILD),
      TestProjectDef(LintTestProject.NON_STANDARD_SOURCE_SETS),
      TestProjectDef(LintTestProject.LINKED),
      TestProjectDef(LintTestProject.KOTLIN_KAPT),
      TestProjectDef(LintTestProject.LINT_CUSTOM_CHECKS),
      TestProjectDef(LintTestProject.TEST_FIXTURES),
    )
  }

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule
  var testName = TestName()

  override fun getName(): String = testName.methodName

  override val snapshotDirectoryWorkspaceRelativePath: String = "tools/adt/idea/android-lint/testData/snapshots/lintModels"

  @Test
  fun testLintModels() {
    val projectName = testProjectName ?: error("unit test parameter not initialized")
    val preparedProject = projectRule.prepareTestProject(projectName.template)
    preparedProject.open { project ->
      val dump =
        project.saveAndDump(mapOf("ROOT" to preparedProject.root)) { project, projectDumper -> projectDumper.dumpLintModels(project) }
      assertIsEqualToSnapshot(dump)
    }
  }
}
