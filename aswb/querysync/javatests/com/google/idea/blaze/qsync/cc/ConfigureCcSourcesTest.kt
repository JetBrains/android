/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.qsync.cc

import com.google.common.collect.Iterables
import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.NoopContext
import com.google.idea.blaze.qsync.JavaPackagePrefixReaderImpl
import com.google.idea.blaze.qsync.QuerySyncTestUtils
import com.google.idea.blaze.qsync.TestDataSyncRunner
import com.google.idea.blaze.qsync.java.PackageStatementParser
import com.google.idea.blaze.qsync.project.BuildGraphData
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectPath.Companion.workspaceRelativeForTests
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.project.ProjectProto.CcWorkspace
import com.google.idea.blaze.qsync.project.QuerySyncLanguage
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdate
import com.google.idea.blaze.qsync.testdata.TestData
import com.google.idea.common.experiments.ExperimentService
import com.google.idea.common.experiments.MockExperimentService
import com.google.idea.testing.IntellijRule
import java.nio.file.Path
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ConfigureCcSourcesTest {
  @get:Rule
  val intellij: IntellijRule = IntellijRule()

  @Before
  fun setUp() {
    intellij.registerApplicationService(ExperimentService::class.java, MockExperimentService())
  }

  private val context: Context<*> = NoopContext()
  private val syncRunner = TestDataSyncRunner(
    context,
    JavaPackagePrefixReaderImpl(
      workspaceRoot = Path.of("/"),
      packageReader = PackageStatementParser(),
      parallelPackageReader = QuerySyncTestUtils.SIMPLE_PARALLEL_PACKAGE_READER,
      fileExistenceCheck = { true }
    )
  )

  @Test
  fun empty() {
    val original = syncRunner.sync(TestData.CC_LIBRARY_QUERY)
    val update = ProjectProtoUpdate(original.project)
    ConfigureCcSources().update(update, BuildGraphData.EMPTY, context)
    val project = update.build()
    assertThat(project.ccWorkspace).isEqualTo(CcWorkspace.getDefaultInstance())
  }

  @Test
  fun emptyArtifactTracker() {
    val original = syncRunner.sync(TestData.CC_LIBRARY_QUERY)
    val update = ProjectProtoUpdate(original.project)
    ConfigureCcSources().update(update, original.graph, context)
    val project = update.build()
    val ccTarget = Label.of("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/cc:cc")
    val testClassCcPath = ProjectPath.WorkspaceRelativeProjectPath(
      Path.of("tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/cc/TestClass.cc"), Path.of(""))
    assertThat(project.ccWorkspace).isEqualTo(
      CcWorkspace.getDefaultInstance()
        .copy(
          targets = mapOf(
            ccTarget to ProjectProto.CcTarget(
              target = ccTarget,
              sources = mapOf(testClassCcPath to ProjectProto.CcSourceFile(testClassCcPath, ProjectProto.CcLanguage.CPP)),
              contexts = emptyMap()
            )
          )
        )
    )
  }

  @Test
  fun basics() {
    val original = syncRunner.sync(TestData.CC_LIBRARY_QUERY)
    val update = ProjectProtoUpdate(original.project)

    ConfigureCcSources().update(update, original.graph, context)

    val project = update.build()

    assertThat(project.activeLanguages).contains(QuerySyncLanguage.CC)
    val workspace = project.ccWorkspace
    val ccTarget = Iterables.getOnlyElement(workspace.targets.values)
    val sourceFile = Iterables.getOnlyElement(ccTarget.sources.values)
    assertThat<ProjectProto.CcLanguage>(sourceFile.language)
      .isEqualTo(ProjectProto.CcLanguage.CPP)
    assertThat(sourceFile.workspacePath)
      .isEqualTo(
        workspaceRelativeForTests(
          TestData.CC_LIBRARY_QUERY.onlySourcePath.resolve("TestClass.cc")
        )
      )
  }
}
