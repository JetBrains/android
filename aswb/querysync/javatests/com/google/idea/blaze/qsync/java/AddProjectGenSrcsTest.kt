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

import com.google.common.base.Joiner
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.truth.Truth
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.NoopContext
import com.google.idea.blaze.qsync.QuerySyncTestUtils
import com.google.idea.blaze.qsync.TestDataSyncRunner
import com.google.idea.blaze.qsync.artifacts.BuildArtifact
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.deps.DependencyBuildContext
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate
import com.google.idea.blaze.qsync.deps.TargetBuildInfo
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.project.ProjectProto.ArtifactDirectoryContents
import com.google.idea.blaze.qsync.testdata.TestData
import com.google.protobuf.TextFormat
import java.nio.file.Path
import java.time.Instant
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
  @get:Rule
  val mockito: MockitoRule = MockitoJUnit.rule()

  @Mock
  var context: Context<*>? = null

  private val syncer =
    TestDataSyncRunner(NoopContext(), QuerySyncTestUtils.PATH_INFERRING_PACKAGE_READER)

  private val javaSourcePackageExtractor = JavaSourcePackageExtractor(null)

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
          DependencyBuildContext.NONE
        )
      )

    val addGensrcs =
      AddProjectGenSrcs(original.queryData().projectDefinition(), javaSourcePackageExtractor)

    val update =
      ProjectProtoUpdate(original.project(), original.graph(), context)
    addGensrcs.update(update, artifactState, context!!)
    val newProject = update.build()

    val workspace = newProject.getModules(0)
    // check our above assumption:
    Truth.assertThat(workspace.getName()).isEqualTo(".workspace")
    Truth.assertThat(workspace.getContentEntriesList())
      .contains(
        TextFormat.parse(
          Joiner.on("\n")
            .join(
              "root {",
              "      path: \".bazel/gensrc/java\"",
              "      base: PROJECT",
              "    }",
              "    sources {",
              "      is_generated: true",
              "      project_path {",
              "        path: \".bazel/gensrc/java\"",
              "        base: PROJECT",
              "      }",
              "    }"
            ),
          ProjectProto.ContentEntry::class.java
        )
      )
    Truth.assertThat(newProject.getArtifactDirectories().getDirectoriesMap())
      .containsEntry(
        ".bazel/gensrc/java",
        TextFormat.parse(
          Joiner.on("\n")
            .join(
              "contents {",
              "      key: \"com/org/Class.java\"",
              "      value {",
              "        transform: COPY",
              "        build_artifact {",
              "          digest: \"gensrcdigest\"",
              "        }",
              "        target: \"" + testData.assumedOnlyLabel + "\"",
              "      }",
              "    }"
            ),
          ArtifactDirectoryContents::class.java
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
                "gensrc2",
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
      AddProjectGenSrcs(original.queryData().projectDefinition(), javaSourcePackageExtractor)

    val update =
      ProjectProtoUpdate(original.project(), original.graph(), context)
    addGenSrcs.update(update, artifactState, context!!)
    val newProject = update.build()

    val workspace = newProject.getModules(0)
    // check our above assumption:
    Truth.assertThat(workspace.getName()).isEqualTo(".workspace")

    Truth.assertThat(workspace.getContentEntriesList())
      .contains(
        TextFormat.parse(
          Joiner.on("\n")
            .join(
              "root {",
              "      path: \".bazel/gensrc/java\"",
              "      base: PROJECT",
              "    }",
              "    sources {",
              "      is_generated: true",
              "      project_path {",
              "        path: \".bazel/gensrc/java\"",
              "        base: PROJECT",
              "      }",
              "    }"
            ),
          ProjectProto.ContentEntry::class.java
        )
      )
    Truth.assertThat(newProject.getArtifactDirectories().getDirectoriesMap())
      .containsEntry(
        ".bazel/gensrc/java",
        TextFormat.parse(
          Joiner.on("\n")
            .join(
              "contents {",
              "      key: \"com/org/Class.java\"",
              "      value {",
              "        transform: COPY",
              "        build_artifact {",
              "          digest: \"gensrc2\"",
              "        }",
              "        target: \"" + genSrc2Label + "\"",
              "      }",
              "    }"
            ),
          ArtifactDirectoryContents::class.java
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
      AddProjectGenSrcs(original.queryData().projectDefinition(), javaSourcePackageExtractor)

    val update =
      ProjectProtoUpdate(original.project(), original.graph(), context)
    addGenSrcs.update(update, artifactState, context!!)
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
          DependencyBuildContext.NONE
        )
      )

    val addGensrcs =
      AddProjectGenSrcs(original.queryData().projectDefinition(), javaSourcePackageExtractor)

    val update =
      ProjectProtoUpdate(original.project(), original.graph(), context)
    addGensrcs.update(update, artifactState, context!!)
    val newProject = update.build()

    val workspace = newProject.getModules(0)
    // check our above assumption:
    Truth.assertThat(workspace.getName()).isEqualTo(".workspace")
    Truth.assertThat(
      workspace.getContentEntriesList()
        .flatMap { it!!.getSourcesList() }
        .filter { it!!.getIsGenerated() })
      .isEmpty()
    Truth.assertThat(
      newProject.getArtifactDirectories().getDirectoriesMap().values
        .flatMap { it!!.getContentsMap().entries }
      .isEmpty())
    Mockito.verify(context)!!.setHasWarnings()
  }
}
