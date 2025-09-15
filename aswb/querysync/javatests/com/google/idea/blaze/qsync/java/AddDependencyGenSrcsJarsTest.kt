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
import com.google.idea.blaze.common.artifact.BuildArtifactCache
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot
import com.google.idea.blaze.qsync.QuerySyncTestUtils
import com.google.idea.blaze.qsync.TestDataSyncRunner
import com.google.idea.blaze.qsync.artifacts.BuildArtifact
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.deps.DependencyBuildContext
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate
import com.google.idea.blaze.qsync.deps.TargetBuildInfo
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata.SrcJarJavaPackageRoots
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.testdata.TestData
import java.nio.file.Path
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

@RunWith(JUnit4::class)
class AddDependencyGenSrcsJarsTest {
  @get:Rule
  val mockito: MockitoRule = MockitoJUnit.rule()

  @Mock
  var cache: BuildArtifactCache? = null

  private val syncer =
    TestDataSyncRunner(NoopContext(), QuerySyncTestUtils.PATH_INFERRING_PACKAGE_READER)

  private val original: QuerySyncProjectSnapshot =
    syncer.sync(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY)

  private val innerRootsMetadata = SrcJarPackageRootsExtractor(null)

  @Test
  @Throws(Exception::class)
  fun no_deps_built() {
    val addGenSrcJars =
      AddDependencyGenSrcsJars(original.queryData().projectDefinition(), innerRootsMetadata)
    no_deps_built(addGenSrcJars)
  }

  @Throws(Exception::class)
  private fun no_deps_built(addGenSrcJars: AddDependencyGenSrcsJars) {
    val update =
      ProjectProtoUpdate(original.project(), original.graph(), NoopContext())

    addGenSrcJars.update(update, ArtifactTracker.State.EMPTY, NoopContext())

    val newProject = update.build()

    Truth.assertThat(newProject.getLibraryList()).isEqualTo(original.project().getLibraryList())
    Truth.assertThat(newProject.getModulesList()).isEqualTo(original.project().getModulesList())
    Truth.assertThat(newProject.getArtifactDirectories().getDirectoriesMap().keys).isEmpty()
  }

  @Test
  @Throws(Exception::class)
  fun project_gensrcs_ignored() {
    val addGenSrcJars =
      AddDependencyGenSrcsJars(original.queryData().projectDefinition(), innerRootsMetadata)
    project_gensrcs_ignored(addGenSrcJars)
  }

  @Throws(Exception::class)
  private fun project_gensrcs_ignored(addGenSrcJars: AddDependencyGenSrcsJars) {
    val testProject = TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY

    val artifactState =
      ArtifactTracker.State.forJavaArtifacts(
        JavaArtifactInfo.empty(testProject.getAssumedOnlyLabel()).toBuilder()
          .setGenSrcs(
            ImmutableList.of(
              BuildArtifact.create(
                "srcjardigest",
                Path.of("output/path/to/in_project.srcjar"),
                testProject.getAssumedOnlyLabel()
              )
            )
          )
          .build()
      )

    val update =
      ProjectProtoUpdate(original.project(), original.graph(), NoopContext())
    addGenSrcJars.update(update, artifactState, NoopContext())
    val newProject = update.build()

    Mockito.verify<BuildArtifactCache?>(cache, Mockito.never()).get(ArgumentMatchers.any())

    Truth.assertThat(newProject.getLibraryList()).isEqualTo(original.project().getLibraryList())
    Truth.assertThat(newProject.getModulesList()).isEqualTo(original.project().getModulesList())
    Truth.assertThat(newProject.getArtifactDirectories().getDirectoriesMap().keys).isEmpty()
  }

  @Test
  @Throws(Exception::class)
  fun external_gensrcs_added() {
    val addGenSrcJars =
      AddDependencyGenSrcsJars(original.queryData().projectDefinition(), innerRootsMetadata)
    external_gensrcs_added(
      addGenSrcJars,
      ProjectProto.Library.newBuilder().setName("//java/com/google/common/collect:collect")
        .addSources(
          ProjectProto.LibrarySource.newBuilder()
            .setSrcjar(
              ProjectProto.ProjectPath.newBuilder()
                .setBase(ProjectProto.ProjectPath.Base.PROJECT)
                .setPath(".bazel/buildout/output/path/to/external.srcjar")
                .setInnerPath("root")
            )
            .build()
        )
        .addSources(
          ProjectProto.LibrarySource.newBuilder()
            .setSrcjar(
              ProjectProto.ProjectPath.newBuilder()
                .setBase(ProjectProto.ProjectPath.Base.PROJECT)
                .setPath(".bazel/buildout/output/path/to/external.srcjar")
                .setInnerPath("root2")
            )
            .build()
        )
        .build()
    )
  }

  @Throws(Exception::class)
  private fun external_gensrcs_added(
    addGenSrcJars: AddDependencyGenSrcsJars,
    vararg expectedLibraries: ProjectProto.Library?
  ) {
    val artifactState =
      ArtifactTracker.State.forTargets(
        TargetBuildInfo.forJavaTarget(
          JavaArtifactInfo.empty(of("//java/com/google/common/collect:collect"))
            .toBuilder()
            .setGenSrcs(
              ImmutableList.of(
                BuildArtifact.create(
                  "srcjardigest",
                  Path.of("output/path/to/external.srcjar"),
                  of("//java/com/google/common/collect:collect")
                )
                  .withMetadata(
                    SrcJarJavaPackageRoots(
                      ImmutableSet.of(Path.of("root"), Path.of("root2"))
                    )
                  )
              )
            )
            .build(),
          DependencyBuildContext.NONE
        )
      )

    val update =
      ProjectProtoUpdate(original.project(), original.graph(), NoopContext())
    addGenSrcJars.update(update, artifactState, NoopContext())
    val newProject = update.build()

    Truth.assertThat(newProject.getLibraryList()).containsExactly(*expectedLibraries)
  }


  @Test
  @Throws(Exception::class)
  fun no_metadata_present() {
    val addGenSrcJars =
      AddDependencyGenSrcsJars(original.queryData().projectDefinition(), innerRootsMetadata)
    no_metadata_present(
      addGenSrcJars,
      ProjectProto.Library.newBuilder().setName("//java/com/google/common/collect:collect")
        .addSources(
          ProjectProto.LibrarySource.newBuilder()
            .setSrcjar(
              ProjectProto.ProjectPath.newBuilder()
                .setBase(ProjectProto.ProjectPath.Base.PROJECT)
                .setPath(".bazel/buildout/output/path/to/external.srcjar")
            )
            .build()
        )
        .build()
    )
  }

  @Throws(Exception::class)
  private fun no_metadata_present(
    addGenSrcJars: AddDependencyGenSrcsJars,
    vararg expectedLibraries: ProjectProto.Library?
  ) {
    val artifactState =
      ArtifactTracker.State.forTargets(
        TargetBuildInfo.forJavaTarget(
          JavaArtifactInfo.empty(of("//java/com/google/common/collect:collect"))
            .toBuilder()
            .setGenSrcs(
              ImmutableList.of(
                BuildArtifact.create(
                  "srcjardigest",
                  Path.of("output/path/to/external.srcjar"),
                  of("//java/com/google/common/collect:collect")
                )
              )
            )
            .build(),
          DependencyBuildContext.NONE
        )
      )

    val update =
      ProjectProtoUpdate(original.project(), original.graph(), NoopContext())
    addGenSrcJars.update(update, artifactState, NoopContext())
    val newProject = update.build()

    Truth.assertThat(newProject.getLibraryList()).containsExactly(*expectedLibraries)
  }
}
