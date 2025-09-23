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
import com.google.common.truth.Truth
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.NoopContext
import com.google.idea.blaze.qsync.QuerySyncTestUtils
import com.google.idea.blaze.qsync.TestDataSyncRunner
import com.google.idea.blaze.qsync.artifacts.BuildArtifact
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.deps.DependencyBuildContext
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate
import com.google.idea.blaze.qsync.deps.TargetBuildInfo
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata.AarResPackage
import com.google.idea.blaze.qsync.project.ProjectPath
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
class AddDependencyAarsTest {
  @get:Rule
  val mockito: MockitoRule = MockitoJUnit.rule()

  private val syncer =
    TestDataSyncRunner(NoopContext(), QuerySyncTestUtils.PATH_INFERRING_PACKAGE_READER)

  private val aarPackageMetadata = AarPackageNameExtractor(null)

  @Test
  @Throws(Exception::class)
  fun no_deps_built() {
    val original = syncer.sync(TestData.ANDROID_LIB_QUERY)

    val addAars =
      AddDependencyAars(original.queryData().projectDefinition(), aarPackageMetadata)

    val update =
      ProjectProtoUpdate(original.project(), original.graph(), NoopContext())

    addAars.update(update, ArtifactTracker.State.EMPTY, NoopContext())
    val newProject = update.build()

    Truth.assertThat(newProject.libraries).isEqualTo(original.project().libraries)
    Truth.assertThat(newProject.modules).isEqualTo(original.project().modules)
    Truth.assertThat(newProject.artifactDirectories.directoriesMap.keys).isEmpty()
  }

  @Test
  @Throws(Exception::class)
  fun dep_aar_added() {
    val original = syncer.sync(TestData.ANDROID_LIB_QUERY)

    val addAars =
      AddDependencyAars(original.queryData().projectDefinition(), aarPackageMetadata)

    val update =
      ProjectProtoUpdate(original.project(), original.graph(), NoopContext())

    addAars.update(
      update,
      ArtifactTracker.State.forTargets(
        TargetBuildInfo.forJavaTarget(
          JavaArtifactInfo.empty(Label.of("//path/to:dep")).toBuilder()
            .setIdeAars(
              ImmutableList.of(
                BuildArtifact.create(
                  "aardigest",
                  Path.of("path/to/dep.aar"),
                  Label.of("//path/to:dep")
                )
                  .withMetadata(
                    AarResPackage(
                      "com.google.idea.blaze.qsync.testdata.android"
                    )
                  )
              )
            )
            .build(),
          DependencyBuildContext.NONE
        )
      ), NoopContext()
    )
    val newProject = update.build()

    Truth.assertThat(newProject.libraries).isEqualTo(original.project().libraries)
    Truth.assertThat(
      newProject.modules.singleOrNull()?.androidExternalLibraries?.singleOrNull()
    )
      .isEqualTo(
        ProjectProto.ExternalAndroidLibrary(
          name = "path_to_dep.aar",
          location = ProjectPath.projectRelative(Path.of(".bazel/buildout/path/to/dep.aar")),
          manifestFile = ProjectPath.projectRelative(Path.of(".bazel/buildout/path/to/dep.aar/AndroidManifest.xml")),
          resFolder = ProjectPath.projectRelative(Path.of(".bazel/buildout/path/to/dep.aar/res")),
          symbolFile = ProjectPath.projectRelative(Path.of(".bazel/buildout/path/to/dep.aar/R.txt")),
          packageName = "com.google.idea.blaze.qsync.testdata.android"
        )
      )

    Truth.assertThat(newProject.artifactDirectories)
      .isEqualTo(
        ProjectProto.ArtifactDirectories(
          directoriesMap = mapOf(
            ".bazel/buildout" to ProjectProto.ArtifactDirectoryContents(
              contents = mapOf(
                "path/to/dep.aar" to ProjectProto.ProjectArtifact(
                  target = Label.of("//path/to:dep"),
                  buildArtifact = ProjectProto.BuildArtifact("aardigest"),
                  transform = ProjectProto.ProjectArtifact.ArtifactTransform.UNZIP,
                )
              )
            ),
          )
        )
      )
  }

  @Test
  @Throws(Exception::class)
  fun dep_aar_no_package_name_added() {
    val original = syncer.sync(TestData.ANDROID_LIB_QUERY)

    val addAars =
      AddDependencyAars(original.queryData().projectDefinition(), aarPackageMetadata)

    val update =
      ProjectProtoUpdate(original.project(), original.graph(), NoopContext())

    addAars.update(
      update,
      ArtifactTracker.State.forJavaArtifacts(
        ImmutableList.of(
          JavaArtifactInfo.empty(Label.of("//path/to:dep")).toBuilder()
            .setIdeAars(
              ImmutableList.of(
                BuildArtifact.create(
                  "aardigest",
                  Path.of("path/to/dep.aar"),
                  Label.of("//path/to:dep")
                )
              )
            )
            .build()
        )
      ), NoopContext()
    )
    val newProject = update.build()

    Truth.assertThat(newProject.libraries).isEqualTo(original.project().libraries)
    Truth.assertThat(
      newProject.modules.singleOrNull()?.androidExternalLibraries?.singleOrNull()?.packageName
    ).isEmpty()

    Truth.assertThat(newProject.artifactDirectories)
      .isEqualTo(
        ProjectProto.ArtifactDirectories(
          directoriesMap = mapOf(
            ".bazel/buildout" to ProjectProto.ArtifactDirectoryContents(
              contents = mapOf(
                "path/to/dep.aar" to ProjectProto.ProjectArtifact(
                  target = Label.of("//path/to:dep"),
                  buildArtifact = ProjectProto.BuildArtifact("aardigest"),
                  transform = ProjectProto.ProjectArtifact.ArtifactTransform.UNZIP,

                  )
              )
            )
          )
        )
      )
  }
}
