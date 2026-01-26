/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync

import com.android.tools.idea.testing.registerServiceInstance
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.io.ByteSource
import com.google.common.truth.Truth
import com.google.idea.blaze.android.AswbTestUtils
import com.google.idea.blaze.base.BlazeIntegrationTestCase
import com.google.idea.blaze.base.MockProjectViewManager
import com.google.idea.blaze.base.bazel.BazelBuildSystemProvider
import com.google.idea.blaze.base.bazel.LocalBazelInvoker
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.projectview.ProjectViewSet
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager
import com.google.idea.blaze.base.vcs.BlazeVcsHandlerProvider
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.Label.Companion.of
import com.google.idea.blaze.qsync.artifacts.MockArtifactCache
import com.google.idea.blaze.qsync.deps.OutputGroup
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.common.experiments.ExperimentService
import com.google.idea.common.experiments.MockExperimentService
import com.intellij.testFramework.registerServiceInstance
import com.intellij.util.application
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Optional
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BazelDependencyBuilderTest : BlazeIntegrationTestCase() {
  @get:Rule
  val temporaryFolder: TemporaryFolder = TemporaryFolder()
  private val experimentService = MockExperimentService()
  private val snapshotHolder = SnapshotHolder()

  @Before
  fun before() {
    experimentService.setExperimentString(
      AspectFiles.aspectLocation,
      AswbTestUtils.getRunfilesWorkspaceRoot().toPath().resolve("tools/adt/idea/aswb").toString()
    )
    application.registerServiceInstance(
      ExperimentService::class.java,
      experimentService
    )
    val mockProjectDataManager: BlazeProjectDataManager =
      MockBlazeProjectDataManager(MockBlazeProjectDataBuilder.builder(workspaceRoot).build())
    registerProjectService(BlazeProjectDataManager::class.java, mockProjectDataManager
    )
  }

  @Test
  @Throws(IOException::class)
  fun generatesValidAspectConfiguration() {
    val dependencyBuilder =
      BazelDependencyBuilder(
        getProject(),
        BazelBuildSystemProvider().getBuildSystem(),
        ProjectDefinition(
          projectIncludes = emptySet(),
          projectExcludes = emptySet(),
          deriveTargetsFromDirectories = false,
          targetPatterns = emptyList(),
          systemExcludes = emptySet(),
          testSources = emptySet(),
          languageClasses = emptySet(),
        ),
        snapshotHolder,
        WorkspaceRoot(temporaryFolder.getRoot()),
        Optional.empty<BlazeVcsHandlerProvider.BlazeVcsHandler?>(),
        MockArtifactCache(temporaryFolder.newFolder().toPath()),
        ImmutableSet.of("always_build_rule1", "always_build_rule2")
      )

    val generatedAspectName = String.format("qs-%s.bzl", dependencyBuilder.getProjectHash())
    val invocationFiles = dependencyBuilder.getInvocationFiles(
      ImmutableSet.of(of("//target1:target1"), of("//target2:target2")),
      LocalBazelInvoker.CAPABILITIES,
      BazelDependencyBuilder.BuildDependencyParameters(
        ImmutableList.of("dir1", "dir2"),
        ImmutableList.of("dir1/sub1"),
        ImmutableList.of("always_build_rule1", "always_build_rule2"),
        true,
        false
      )
    )
    Truth.assertThat(invocationFiles.aspectFileLabel)
      .isEqualTo(String.format("//.aswb:qs-%s.bzl", dependencyBuilder.getProjectHash()))
    Truth.assertThat(
      String(
        invocationFiles.files.get(Path.of(".aswb", generatedAspectName))!!
          .openStream()
          .readAllBytes(), StandardCharsets.UTF_8
      )
    )
      .isEqualTo(
        """
                   load(':build_dependencies.bzl', _collect_dependencies = 'collect_dependencies', _package_dependencies = 'package_dependencies')
                   _config = struct(
                     include = [
                       "dir1",
                       "dir2",
                     ],
                     exclude = [
                       "dir1/sub1",
                     ],
                     always_build_rules = [
                       "always_build_rule1",
                       "always_build_rule2",
                     ],
                     generate_aidl_classes = True,
                     use_generated_srcjars = False,
                   )
 
                   collect_dependencies = _collect_dependencies(_config)
                   package_dependencies = _package_dependencies(_config)

                   """.trimIndent()
      )
  }

  @Test
  @Throws(IOException::class)
  fun generatesValidTargetPatternFile() {
    experimentService.setExperiment(BazelDependencyBuilder.buildUseTargetPatternFile, true)
    MockProjectViewManager(getProject()).setProjectView(ProjectViewSet(emptyList()))
    val dependencyBuilder =
      BazelDependencyBuilder(
        getProject(),
        BazelBuildSystemProvider().getBuildSystem(),
        ProjectDefinition(
          projectIncludes = emptySet(),
          projectExcludes = emptySet(),
          deriveTargetsFromDirectories = false,
          targetPatterns = emptyList(),
          systemExcludes = emptySet(),
          testSources = emptySet(),
          languageClasses = emptySet(),
        ),
        snapshotHolder,
        WorkspaceRoot(temporaryFolder.getRoot()),
        Optional.empty<BlazeVcsHandlerProvider.BlazeVcsHandler?>(),
        MockArtifactCache(temporaryFolder.newFolder().toPath()),
        ImmutableSet.of("always_build_rule1", "always_build_rule2")
      )

    val targets = ImmutableSet.of<Label>(of("//target1:target1"), of("//target2:target2"))
    val generatedTargetPatternName =
      of(String.format("//.aswb:targets-%s.txt", dependencyBuilder.getProjectHash())).name

    val invocationInfo =
      dependencyBuilder.getInvocationInfo(
        BlazeContext.create(), targets, LocalBazelInvoker.CAPABILITIES,
        ImmutableSet.of(OutputGroup.ARTIFACT_INFO_FILE),
        replaceOutputGroups
      )
    val invocationFiles = invocationInfo.invocationWorkspaceFiles
    Truth.assertThat(
      String(
        invocationFiles.get(Path.of(".aswb", generatedTargetPatternName))!!
          .openStream()
          .readAllBytes(), StandardCharsets.UTF_8
      )
    )
      .isEqualTo(
        """
                   //target1:target1
                   //target2:target2
                   """.trimIndent()
      )
    Truth.assertThat(invocationInfo.argsAndFlags)
      .contains("--target_pattern_file=.aswb/" + generatedTargetPatternName)
  }
}
