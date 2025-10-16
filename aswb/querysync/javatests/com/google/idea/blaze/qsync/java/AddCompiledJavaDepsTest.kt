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
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.Label.Companion.of
import com.google.idea.blaze.common.NoopContext
import com.google.idea.blaze.qsync.artifacts.BuildArtifact
import com.google.idea.blaze.qsync.deps.ArtifactDirectories
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.deps.DependencyBuildContext
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdate
import com.google.idea.blaze.qsync.project.BuildGraphData
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectArtifact.ArtifactTransform
import com.google.idea.blaze.qsync.testdata.ProjectProtos
import com.google.idea.blaze.qsync.testdata.TestData
import java.nio.file.Path
import java.time.Instant
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AddCompiledJavaDepsTest {
  val buildTimestamp = Instant.now()

  @Test
  @Throws(Exception::class)
  fun no_deps_built() {
    val javaDeps = AddCompiledJavaDeps(ImmutableSet.of())
    no_deps_built(javaDeps)
  }

  @Throws(Exception::class)
  private fun no_deps_built(javaDeps: AddCompiledJavaDeps) {
    val original = ProjectProtos.forTestProject(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY)

    val update = ProjectProtoUpdate(original)
    javaDeps.update(update, BuildGraphData.EMPTY, ArtifactTracker.State.EMPTY, NoopContext(),
                    ProjectPath.ExternalRepositoryFinder.createEmptyForTests())
    val newProject = update.build()
    Truth.assertThat(newProject.libraries).isEqualTo(original.libraries)
    Truth.assertThat(newProject.modules).isEqualTo(original.modules)
    Truth.assertThat(newProject.artifactDirectories.directoriesMap.keys).doesNotContain(ArtifactDirectories.JAVADEPS)
  }

  @Test
  @Throws(Exception::class)
  fun dep_built() {
    val javaDeps = AddCompiledJavaDeps(ImmutableSet.of())
    val artifactState = ArtifactTracker.State.forJavaArtifacts(
      DependencyBuildContext.create("", buildTimestamp),
      JavaArtifactInfo.empty(of("//java/com/google/common/collect:collect")).toBuilder()
        .setJars(
          ImmutableList.of(
            BuildArtifact.create(
              "jardigest",
              Path.of("build-out/java/com/google/common/collect/libcollect.jar"),
              of("//java/com/google/common/collect:collect")
            )
          )
        )
        .build()
    )
    val expectedLibraries =
      arrayOf(
        ProjectProto.Library(
          name = Label.of("//java/com/google/common/collect:collect"),
          classesJarList =  listOf(ProjectPath.projectRelative(Path.of(".bazel/javadeps/build-out/java/com/google/common/collect/libcollect.jar"))),
          sourcesList = emptyList()
        )
      )
    val original =
      ProjectProtos.forTestProject(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY)

    val update =
      ProjectProtoUpdate(original)
    javaDeps.update(update, BuildGraphData.EMPTY, artifactState, NoopContext(), ProjectPath.ExternalRepositoryFinder.createEmptyForTests())
    val newProject = update.build()
    Truth.assertThat(newProject.libraries.values).containsExactly(*expectedLibraries)
    Truth.assertThat(newProject.artifactDirectories.directoriesMap.keys)
      .containsExactly(ArtifactDirectories.JAVADEPS)
    Truth.assertThat(
      newProject
        .artifactDirectories
        .directoriesMap[ArtifactDirectories.JAVADEPS]!!
        .contents
    )
      .containsExactly(
        "build-out/java/com/google/common/collect/libcollect.jar",
        ProjectProto.ProjectArtifact(
          target = Label.of("//java/com/google/common/collect:collect"),
          buildArtifact = ProjectProto.BuildArtifact("jardigest"),
          fromBuild = buildTimestamp,
          transform = ArtifactTransform.COPY
        )
      )
  }

  @Test
  @Throws(Exception::class)
  fun dep_built_empty_jar() {
    val javaDeps = AddCompiledJavaDeps(ImmutableSet.of("empty_jar_digest"))
    val artifactState = ArtifactTracker.State.forJavaArtifacts(
      DependencyBuildContext.create("", buildTimestamp),
      JavaArtifactInfo
        .empty(of("//java/com/google/common/collect:collect"))
        .toBuilder()
        .setJars(
          ImmutableList.of(
            BuildArtifact.create(
              "empty_jar_digest",
              Path.of("build-out/java/com/google/common/collect/libcollect.jar"),
              of("//java/com/google/common/collect:collect")
            )
          )
        )
        .build()
    )
    val expectedLibraries = arrayOf<ProjectProto.Library>()
    val original =
      ProjectProtos.forTestProject(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY)

    val update =
      ProjectProtoUpdate(original)
    javaDeps.update(update, BuildGraphData.EMPTY, artifactState, NoopContext(), ProjectPath.ExternalRepositoryFinder.createEmptyForTests())
    val newProject = update.build()
    Truth.assertThat(newProject.libraries.values).containsExactly(*expectedLibraries)
    Truth.assertThat(newProject.artifactDirectories.directoriesMap.keys)
      .doesNotContain(ArtifactDirectories.JAVADEPS)
  }
}
