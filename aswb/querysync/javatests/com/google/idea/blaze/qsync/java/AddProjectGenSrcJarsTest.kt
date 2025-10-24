/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.java

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth
import com.google.idea.blaze.common.Label.Companion.of
import com.google.idea.blaze.common.NoopContext
import com.google.idea.blaze.qsync.QuerySyncTestUtils
import com.google.idea.blaze.qsync.TestDataSyncRunner
import com.google.idea.blaze.qsync.artifacts.BuildArtifact
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.deps.DependencyBuildContext
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdate
import com.google.idea.blaze.qsync.deps.TargetBuildInfo
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata.SrcJarPrefixedJavaPackageRoots
import com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder.JarPath
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.testdata.TestData
import com.google.idea.common.experiments.ExperimentService
import com.google.idea.common.experiments.MockExperimentService
import com.google.idea.testing.IntellijRule
import java.nio.file.Path
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

@RunWith(JUnit4::class)
class AddProjectGenSrcJarsTest {
  @get:Rule
  val mockito: MockitoRule = MockitoJUnit.rule()

  companion object {
    @JvmField
    @ClassRule
    val intellij = IntellijRule()
  }

  private val syncer =
    TestDataSyncRunner(NoopContext(), QuerySyncTestUtils.PATH_INFERRING_PREFIX_READER)

  private val innerPathsMetadata = SrcJarPrefixedPackageRootsExtractor(null)

  @Before
  fun setUp() {
    intellij.registerApplicationService(ExperimentService::class.java, MockExperimentService())
  }

  @Test
  @Throws(Exception::class)
  fun external_srcjar_ignored() {
    val original = syncer.sync(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY)

    val artifactState =
      ArtifactTracker.State.forJavaArtifacts(
        DependencyBuildContext.NONE,
        JavaArtifactInfo.empty(of("//java/com/google/common/collect:collect")).toBuilder()
          .setGenSrcs(
            ImmutableList.of(
              BuildArtifact.create(
                "srcjardigest",
                Path.of("output/path/to/external.srcjar"),
                of("//java/com/google/common/collect:collect")
              )
            )
          )
          .build()
      )

    val javaDeps =
      AddProjectGenSrcJars(original.queryData.projectDefinition(), innerPathsMetadata)

    val update = ProjectProtoUpdate(original.project)
    javaDeps.update(update, artifactState, NoopContext(), ProjectPath.ExternalRepositoryFinder.createEmptyForTests())
    val newProject = update.build()
    Truth.assertThat(newProject.libraries).isEqualTo(original.project.libraries)
    Truth.assertThat(newProject.modules).isEqualTo(original.project.modules)
    Truth.assertThat(newProject.artifactDirectories.directoriesMap.keys).isEmpty()
  }

  @Test
  @Throws(Exception::class)
  fun project_srcjar_added() {
    val testData = TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY
    val original = syncer.sync(testData)

    val artifactState =
      ArtifactTracker.State.forTargets(
        TargetBuildInfo.forJavaTarget(
          JavaArtifactInfo.empty(testData.getAssumedOnlyLabel()).toBuilder()
            .setGenSrcs(
              ImmutableList.of(
                BuildArtifact.create(
                  "srcjardigest",
                  Path.of("output/path/to/project.srcjar"),
                  testData.getAssumedOnlyLabel()
                )
                  .withMetadata(
                    SrcJarPrefixedJavaPackageRoots(
                      ImmutableSet.of(JarPath.create("root", ""))
                    )
                  )
              )
            )
            .build(),
          DependencyBuildContext.NONE
        )
      )

    val javaDeps =
      AddProjectGenSrcJars(original.queryData.projectDefinition(), innerPathsMetadata)

    val update = ProjectProtoUpdate(original.project)
    javaDeps.update(update, artifactState, NoopContext(), ProjectPath.ExternalRepositoryFinder.createEmptyForTests())
    val newProject = update.build()
    Truth.assertThat(newProject.libraries).isEqualTo(original.project.libraries)
    val workspace = newProject.modules[0]
    // check our assumptions:
    Truth.assertThat(workspace.name).isEqualTo(".workspace")

    Truth.assertThat(workspace.contentEntries.values)
      .contains(
        ProjectProto.ContentEntry(
          root = ProjectPath.projectRelative(Path.of(".bazel/gensrc/java/output/path/to/project.srcjar/src")),
          sourceFolders = listOf(
            ProjectProto.SourceFolder(
              projectPath = ProjectPath.projectRelative(Path.of(".bazel/gensrc/java/output/path/to/project.srcjar/src/root")),
              isGenerated = true,
              isTest = false,
              packagePrefix = "",
            ),
          ),
          excludes = listOf(),
        )
      )
  }

  @Test
  @Throws(Exception::class)
  fun project_srcjar_added_java_package_mismatch() {
    val testData = TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY
    val original = syncer.sync(testData)

    val artifactState =
      ArtifactTracker.State.forTargets(
        TargetBuildInfo.forJavaTarget(
          JavaArtifactInfo.empty(testData.getAssumedOnlyLabel()).toBuilder()
            .setGenSrcs(
              ImmutableList.of(
                BuildArtifact.create(
                  "srcjardigest",
                  Path.of("output/path/to/project.srcjar"),
                  testData.getAssumedOnlyLabel()
                )
                  .withMetadata(
                    SrcJarPrefixedJavaPackageRoots(
                      ImmutableSet.of(JarPath.create("root", "com.example"))
                    )
                  )
              )
            )
            .build(),
          DependencyBuildContext.NONE
        )
      )

    val javaDeps =
      AddProjectGenSrcJars(original.queryData.projectDefinition(), innerPathsMetadata)

    val update = ProjectProtoUpdate(original.project)
    javaDeps.update(update, artifactState, NoopContext(), ProjectPath.ExternalRepositoryFinder.createEmptyForTests())
    val newProject = update.build()
    Truth.assertThat(newProject.libraries).isEqualTo(original.project.libraries)
    val workspace = newProject.modules[0]
    // check our assumptions:
    Truth.assertThat(workspace.name).isEqualTo(".workspace")

    Truth.assertThat(workspace.contentEntries.values)
      .contains(
        ProjectProto.ContentEntry(
          root = ProjectPath.projectRelative(Path.of(".bazel/gensrc/java/output/path/to/project.srcjar/src")),
          sourceFolders = listOf(
            ProjectProto.SourceFolder(
              projectPath = ProjectPath.projectRelative(Path.of(".bazel/gensrc/java/output/path/to/project.srcjar/src/root")),
              isGenerated = true,
              isTest = false,
              packagePrefix = "com.example",
            ),
          ),
          excludes = listOf(),
        )
      )
  }

  @Test
  @Throws(Exception::class)
  fun missing_metadata_project_srcjar_added() {
    val testData = TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY
    val original = syncer.sync(testData)

    val artifactState =
      ArtifactTracker.State.forTargets(
        TargetBuildInfo.forJavaTarget(
          JavaArtifactInfo.empty(testData.getAssumedOnlyLabel()).toBuilder()
            .setGenSrcs(
              ImmutableList.of(
                BuildArtifact.create(
                  "srcjardigest",
                  Path.of("output/path/to/project.srcjar"),
                  testData.getAssumedOnlyLabel()
                )
              )
            )
            .build(),
          DependencyBuildContext.NONE
        )
      )

    val javaDeps =
      AddProjectGenSrcJars(original.queryData.projectDefinition(), innerPathsMetadata)

    val update = ProjectProtoUpdate(original.project)
    javaDeps.update(update, artifactState, NoopContext(), ProjectPath.ExternalRepositoryFinder.createEmptyForTests())
    val newProject = update.build()
    Truth.assertThat(newProject.libraries).isEqualTo(original.project.libraries)
    val workspace = newProject.modules[0]
    // check our assumptions:
    Truth.assertThat(workspace.name).isEqualTo(".workspace")

    Truth.assertThat(workspace.contentEntries.values)
      .contains(
        ProjectProto.ContentEntry(
          root = ProjectPath.projectRelative(Path.of(".bazel/gensrc/java/output/path/to/project.srcjar/src")),
          sourceFolders = listOf(
            ProjectProto.SourceFolder(
              projectPath = ProjectPath.projectRelative(Path.of(".bazel/gensrc/java/output/path/to/project.srcjar/src")),
              isGenerated = true,
              isTest = false,
              packagePrefix = "",
            ),
          ),
          excludes = listOf(),
        )
      )
  }
}
