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
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate
import com.google.idea.blaze.qsync.deps.TargetBuildInfo
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata.SrcJarPrefixedJavaPackageRoots
import com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder.JarPath
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.testdata.TestData
import com.google.protobuf.TextFormat
import java.nio.file.Path
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

  private val syncer =
    TestDataSyncRunner(NoopContext(), QuerySyncTestUtils.PATH_INFERRING_PACKAGE_READER)

  private val innerPathsMetadata = SrcJarPrefixedPackageRootsExtractor(null)

  @Test
  @Throws(Exception::class)
  fun external_srcjar_ignored() {
    val original = syncer.sync(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY)

    val artifactState =
      ArtifactTracker.State.forJavaArtifacts(
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
      AddProjectGenSrcJars(original.queryData().projectDefinition(), innerPathsMetadata)

    val update =
      ProjectProtoUpdate(original.project(), original.graph(), NoopContext())
    javaDeps.update(update, artifactState, NoopContext())
    val newProject = update.build()
    Truth.assertThat(newProject.getLibraryList()).isEqualTo(original.project().getLibraryList())
    Truth.assertThat(newProject.getModulesList()).isEqualTo(original.project().getModulesList())
    Truth.assertThat(newProject.getArtifactDirectories().getDirectoriesMap().keys).isEmpty()
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
      AddProjectGenSrcJars(original.queryData().projectDefinition(), innerPathsMetadata)

    val update =
      ProjectProtoUpdate(original.project(), original.graph(), NoopContext())
    javaDeps.update(update, artifactState, NoopContext())
    val newProject = update.build()
    Truth.assertThat(newProject.getLibraryList()).isEqualTo(original.project().getLibraryList())
    val workspace = newProject.getModules(0)
    // check our assumptions:
    Truth.assertThat(workspace.getName()).isEqualTo(".workspace")

    Truth.assertThat(workspace.getContentEntriesList())
      .contains(
        TextFormat.parse(
          Joiner.on("\n")
            .join(
              "root {",
              "      path: \".bazel/gensrc/java/output/path/to/project.srcjar/src\"",
              "      base: PROJECT",
              "    }",
              "    sources {",
              "      is_generated: true",
              "      project_path {",
              "        path: \".bazel/gensrc/java/output/path/to/project.srcjar/src/root\"",
              "        base: PROJECT",
              "      }",
              "    }"
            ),
          ProjectProto.ContentEntry::class.java
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
      AddProjectGenSrcJars(original.queryData().projectDefinition(), innerPathsMetadata)

    val update =
      ProjectProtoUpdate(original.project(), original.graph(), NoopContext())
    javaDeps.update(update, artifactState, NoopContext())
    val newProject = update.build()
    Truth.assertThat(newProject.getLibraryList()).isEqualTo(original.project().getLibraryList())
    val workspace = newProject.getModules(0)
    // check our assumptions:
    Truth.assertThat(workspace.getName()).isEqualTo(".workspace")

    Truth.assertThat(workspace.getContentEntriesList())
      .contains(
        TextFormat.parse(
          Joiner.on("\n")
            .join(
              "root {",
              "      path: \".bazel/gensrc/java/output/path/to/project.srcjar/src\"",
              "      base: PROJECT",
              "    }",
              "    sources {",
              "      is_generated: true",
              "      package_prefix: \"com.example\"",
              "      project_path {",
              "        path: \".bazel/gensrc/java/output/path/to/project.srcjar/src/root\"",
              "        base: PROJECT",
              "      }",
              "    }"
            ),
          ProjectProto.ContentEntry::class.java
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
      AddProjectGenSrcJars(original.queryData().projectDefinition(), innerPathsMetadata)

    val update =
      ProjectProtoUpdate(original.project(), original.graph(), NoopContext())
    javaDeps.update(update, artifactState, NoopContext())
    val newProject = update.build()
    Truth.assertThat(newProject.getLibraryList()).isEqualTo(original.project().getLibraryList())
    val workspace = newProject.getModules(0)
    // check our assumptions:
    Truth.assertThat(workspace.getName()).isEqualTo(".workspace")

    Truth.assertThat(workspace.getContentEntriesList())
      .contains(
        TextFormat.parse(
          Joiner.on("\n")
            .join(
              "root {",
              "      path: \".bazel/gensrc/java/output/path/to/project.srcjar/src\"",
              "      base: PROJECT",
              "    }",
              "    sources {",
              "      is_generated: true",
              "      project_path {",
              "        path: \".bazel/gensrc/java/output/path/to/project.srcjar/src\"",
              "        base: PROJECT",
              "      }",
              "    }"
            ),
          ProjectProto.ContentEntry::class.java
        )
      )
  }
}
