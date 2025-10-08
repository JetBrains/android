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
import com.google.common.collect.ImmutableMap
import com.google.common.truth.Truth
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.NoopContext
import com.google.idea.blaze.qsync.QuerySyncTestUtils
import com.google.idea.blaze.qsync.TestDataSyncRunner
import com.google.idea.blaze.qsync.artifacts.BuildArtifact
import com.google.idea.blaze.qsync.deps.ArtifactDirectories
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.deps.DependencyBuildContext
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdate
import com.google.idea.blaze.qsync.deps.TargetBuildInfo
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.project.ProjectProto.ArtifactDirectoryContents
import com.google.idea.blaze.qsync.testdata.TestData
import com.google.idea.common.experiments.ExperimentService
import com.google.idea.common.experiments.MockExperimentService
import com.google.idea.testing.IntellijRule
import java.nio.file.Path
import java.time.Instant
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

@RunWith(JUnit4::class)
class AddProjectGenSrcsTest {
  val buildTimestamp = Instant.now()

  @get:Rule
  val mockito: MockitoRule = MockitoJUnit.rule()

  companion object {
    @JvmField
    @ClassRule
    val intellij = IntellijRule()
  }

  @Mock
  lateinit var context: Context<*>

  private val syncer =
    TestDataSyncRunner(NoopContext(), QuerySyncTestUtils.PATH_INFERRING_PREFIX_READER)

  private val javaSourcePackageExtractor = JavaSourcePackageExtractor(null)

  @Before
  fun setUp() {
    intellij.registerApplicationService(ExperimentService::class.java, MockExperimentService())
  }

  @Test
  @Throws(Exception::class)
  fun generated_source_added() {
    val testData = TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY
    val original = syncer.sync(testData)

    val artifactState =
      ArtifactTracker.State.forTargets(
        TargetBuildInfo.forJavaTarget(
          JavaArtifactInfo.empty(testData.assumedOnlyLabel).toBuilder()
            .setGenSrcs(
                BuildArtifact.create(
                  "gensrcdigest",
                  Path.of("output/path/com/org/Class.java"),
                  testData.assumedOnlyLabel
                )
                  .withMetadata(JavaArtifactMetadata.JavaSourcePackage("com.org"))
            )
            .build(),
          DependencyBuildContext.create("", buildTimestamp)
        )
      )

    val addGensrcs =
      AddProjectGenSrcs(original.queryData.projectDefinition(), javaSourcePackageExtractor)

    val update =
      ProjectProtoUpdate(original.project)
    addGensrcs.update(update, original.graph, artifactState, context)
    val newProject = update.build()

    val workspace = newProject.modules.single()
    // check our above assumption:
    Truth.assertThat(workspace.name).isEqualTo(".workspace")
    Truth.assertThat(workspace.contentEntries.values)
      .contains(
        ProjectProto.ContentEntry(
          root = ProjectPath.projectRelative(Path.of(".bazel/gensrc/java")),
          sourceFolders = listOf(
            ProjectProto.SourceFolder(
              projectPath = ProjectPath.projectRelative(Path.of(".bazel/gensrc/java")),
              isGenerated = true,
              isTest = false,
              packagePrefix = "",
            ),
          ),
          excludes = listOf(),
        )
      )
    Truth.assertThat(newProject.artifactDirectories.directoriesMap)
      .containsEntry(
        ArtifactDirectories.JAVA_GEN_SRC,
        ArtifactDirectoryContents(
          contents = mapOf(
            "com/org/Class.java" to ProjectProto.ProjectArtifact(
              target = testData.assumedOnlyLabel,
              buildArtifact = ProjectProto.BuildArtifact("gensrcdigest"),
              fromBuild = buildTimestamp,
              transform = ProjectProto.ProjectArtifact.ArtifactTransform.COPY,
            )
          )
        )
      )
    Mockito.verify(context, Mockito.never())!!.setHasWarnings()
  }

  @Test
  @Throws(Exception::class)
  fun conflict_last_build_taken() {
    val testData = TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY
    val original = syncer.sync(testData)

    val testLabel = testData.assumedOnlyLabel

    val genSrc1 =
      TargetBuildInfo.forJavaTarget(
        JavaArtifactInfo.empty(testLabel.siblingWithName("genSrc1")).toBuilder()
          .setGenSrcs(
            ImmutableList.of(
              BuildArtifact.create(
                "gensrc1",
                Path.of("output/path/com/org/Class.java"),
                testLabel.siblingWithName("genSrc1")
              )
                .withMetadata(JavaArtifactMetadata.JavaSourcePackage("com.org"))
            )
          )
          .build(),
        DependencyBuildContext.create(
          "abc-def", buildTimestamp.minusSeconds(60)
        )
      )

    val genSrc2Label = testData.assumedOnlyLabel.siblingWithName("genSrc2")
    val genSrc2 =
      TargetBuildInfo.forJavaTarget(
        JavaArtifactInfo.empty(testLabel.siblingWithName("genSrc2")).toBuilder()
          .setGenSrcs(
            ImmutableList.of(
              BuildArtifact.create(
                "gensrc2",
                Path.of("output/otherpath/com/org/Class.java"),
                genSrc2Label
              )
                .withMetadata(JavaArtifactMetadata.JavaSourcePackage("com.org"))
            )
          )
          .build(),
        DependencyBuildContext.create("abc-def", buildTimestamp)
      )

    val artifactState =
      ArtifactTracker.State.create(
        ImmutableMap.of(
          genSrc1.label(),
          genSrc1,
          genSrc2.label(),
          genSrc2
        ), ImmutableMap.of()
      )

    val addGenSrcs =
      AddProjectGenSrcs(original.queryData.projectDefinition(), javaSourcePackageExtractor)

    val update =
      ProjectProtoUpdate(original.project)
    addGenSrcs.update(update, original.graph, artifactState, context)
    val newProject = update.build()

    val workspace = newProject.modules.single()
    // check our above assumption:
    Truth.assertThat(workspace.name).isEqualTo(".workspace")

    Truth.assertThat(workspace.contentEntries.values)
      .contains(
        ProjectProto.ContentEntry(
          root = ProjectPath.projectRelative(Path.of(".bazel/gensrc/java")),
          sourceFolders = listOf(
            ProjectProto.SourceFolder(
              projectPath = ProjectPath.projectRelative(Path.of(".bazel/gensrc/java")),
              isGenerated = true,
              isTest = false,
              packagePrefix = "",
            ),
            ),
          excludes = listOf(),
        )
      )
    Truth.assertThat(newProject.artifactDirectories.directoriesMap)
      .containsEntry(
        ArtifactDirectories.JAVA_GEN_SRC,
        ArtifactDirectoryContents(
          contents = mapOf(
            "com/org/Class.java" to ProjectProto.ProjectArtifact(
              target = genSrc2Label,
              buildArtifact = ProjectProto.BuildArtifact("gensrc2"),
              fromBuild = buildTimestamp,
              transform = ProjectProto.ProjectArtifact.ArtifactTransform.COPY,
            )
          )
        )
      )
    Mockito.verify(context)!!.setHasWarnings()
  }

  @Test
  @Throws(Exception::class)
  fun conflict_same_digest_ignored() {
    val testData = TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY
    val original = syncer.sync(testData)
    val testLabel = testData.assumedOnlyLabel

    val genSrc1 =
      TargetBuildInfo.forJavaTarget(
        JavaArtifactInfo.empty(testLabel.siblingWithName("genSrc1")).toBuilder()
          .setGenSrcs(
            ImmutableList.of(
              BuildArtifact.create(
                "samedigest",
                Path.of("output/path/com/org/Class.java"),
                testLabel.siblingWithName("genSrc1")
              )
                .withMetadata(JavaArtifactMetadata.JavaSourcePackage("com.org"))
            )
          )
          .build(),
        DependencyBuildContext.create(
          "abc-def", Instant.now().minusSeconds(60)
        )
      )

    val genSrc2Label = testData.assumedOnlyLabel.siblingWithName("genSrc2")
    val genSrc2 =
      TargetBuildInfo.forJavaTarget(
        JavaArtifactInfo.empty(testLabel.siblingWithName("genSrc2")).toBuilder()
          .setGenSrcs(
            ImmutableList.of(
              BuildArtifact.create(
                "samedigest",
                Path.of("output/otherpath/com/org/Class.java"),
                genSrc2Label
              )
                .withMetadata(JavaArtifactMetadata.JavaSourcePackage("com.org"))
            )
          )
          .build(),
        DependencyBuildContext.create("abc-def", Instant.now())
      )

    val artifactState =
      ArtifactTracker.State.create(
        ImmutableMap.of(
          genSrc1.label(),
          genSrc1,
          genSrc2.label(),
          genSrc2
        ), ImmutableMap.of()
      )

    val addGenSrcs =
      AddProjectGenSrcs(original.queryData.projectDefinition(), javaSourcePackageExtractor)

    val update =
      ProjectProtoUpdate(original.project)
    addGenSrcs.update(update, original.graph, artifactState, context)
    Mockito.verify(context, Mockito.never())!!.setHasWarnings()
  }

  @Test
  @Throws(Exception::class)
  fun generated_source_no_package_name() {
    val testData = TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY
    val original = syncer.sync(testData)

    val artifactState =
      ArtifactTracker.State.forTargets(
        TargetBuildInfo.forJavaTarget(
          JavaArtifactInfo.empty(testData.assumedOnlyLabel).toBuilder()
            .setGenSrcs(
              ImmutableList.of(
                BuildArtifact.create(
                  "gensrcdigest",
                  Path.of("output/path/com/org/Class.java"),
                  testData.assumedOnlyLabel
                )
              )
            )
            .build(),
          DependencyBuildContext.create("", buildTimestamp)
        )
      )

    val addGensrcs =
      AddProjectGenSrcs(original.queryData.projectDefinition(), javaSourcePackageExtractor)

    val update =
      ProjectProtoUpdate(original.project)
    addGensrcs.update(update, original.graph, artifactState, context)
    val newProject = update.build()

    val workspace = newProject.modules.single()
    // check our above assumption:
    Truth.assertThat(workspace.name).isEqualTo(".workspace")
    Truth.assertThat(
      workspace.contentEntries.values
        .flatMap { it.sourceFolders }
        .filter { it.isGenerated })
      .isEmpty()
    Truth.assertThat(
      newProject.artifactDirectories.directoriesMap.values
        .flatMap { it.contents.entries }
      .isEmpty())
    Mockito.verify(context)!!.setHasWarnings()
  }
}
