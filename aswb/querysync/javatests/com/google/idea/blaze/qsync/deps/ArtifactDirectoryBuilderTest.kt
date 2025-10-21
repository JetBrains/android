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
package com.google.idea.blaze.qsync.deps

import com.google.common.truth.Truth
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.qsync.artifacts.BuildArtifact
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdate
import java.nio.file.Path
import java.time.Instant
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ArtifactDirectoryBuilderTest {
  val artifactDirProjectPath = ProjectPath.projectRelative(Path.of("artifactDir"))
  val update = ProjectProtoUpdate(ProjectProto.Project.getDefaultInstance())
  val buildTimestamp1 = Instant.EPOCH.plusSeconds(1)
  val buildTimestamp2 = Instant.EPOCH.plusSeconds(2)

  @Test
  fun test_add_single() {
    update.artifactDirectory(artifactDirProjectPath) {

      val added =
        addIfNewer(
          Path.of("path/to/artifact"),
          BuildArtifact.create(
            "digest", Path.of("build-out/path/to/artifact"), Label.of("//path/to:target")
          ),
          DependencyBuildContext.create("build-id", buildTimestamp1)
        )

      Truth.assertThat(added)
        .isEqualTo(ProjectPath.projectRelative(Path.of("artifactDir/path/to/artifact")))
    }
    Truth.assertThat(update.build().artifactDirectories)
      .isEqualTo(
        ProjectProto.ArtifactDirectories(
          directoriesMap =
            mapOf(
              artifactDirProjectPath to
                ProjectProto.ArtifactDirectoryContents(
                  contents =
                    mapOf(
                      "path/to/artifact" to
                        ProjectProto.ProjectArtifact(
                          transform = ProjectProto.ProjectArtifact.ArtifactTransform.COPY,
                          buildArtifact = ProjectProto.BuildArtifact("digest"),
                          fromBuild = buildTimestamp1,
                          target = Label.of("//path/to:target"),
                        )
                    )
                )
            )
        )
      )

  }

  @Test
  fun test_add_conflicting_newer() {
    update.artifactDirectory(ProjectPath.projectRelative(Path.of("artifactDir"))) {

      addIfNewer(
        Path.of("path/to/artifact"),
        BuildArtifact.create(
          "digest1", Path.of("build-out/path/to/artifact"), Label.of("//path/to:target")
        ),
        DependencyBuildContext.create("build-id", buildTimestamp1)
      )

      val added =
        addIfNewer(
          Path.of("path/to/artifact"),
          BuildArtifact.create(
            "digest2", Path.of("build-out/path/to/newartifact"), Label.of("//path/to:target")
          ),
          DependencyBuildContext.create("build-id", buildTimestamp2)
        )

      Truth.assertThat(added)
        .isEqualTo(ProjectPath.projectRelative(Path.of("artifactDir/path/to/artifact")))
    }
    Truth.assertThat(update.build().artifactDirectories)
      .isEqualTo(
        ProjectProto.ArtifactDirectories(
          directoriesMap =
            mapOf(
              artifactDirProjectPath to
                ProjectProto.ArtifactDirectoryContents(
                  contents =
                    mapOf(
                      "path/to/artifact" to
                        ProjectProto.ProjectArtifact(
                          transform = ProjectProto.ProjectArtifact.ArtifactTransform.COPY,
                          buildArtifact = ProjectProto.BuildArtifact("digest2"),
                          fromBuild = buildTimestamp2,
                          target = Label.of("//path/to:target"),
                        )
                    )
                )
            )
        )
      )
  }

  @Test
  fun test_add_conflicting_older() {
    update.artifactDirectory(ProjectPath.projectRelative(Path.of("artifactDir"))) {

      addIfNewer(
        Path.of("path/to/artifact"),
        BuildArtifact.create(
          "digest1", Path.of("build-out/path/to/artifact"), Label.of("//path/to:target")
        ),
        DependencyBuildContext.create("build-id", buildTimestamp2)
      )

      val added =
        addIfNewer(
          Path.of("path/to/artifact"),
          BuildArtifact.create(
            "digest2", Path.of("build-out/path/to/newartifact"), Label.of("//path/to:target")
          ),
          DependencyBuildContext.create("build-id", buildTimestamp1)
        )

      Truth.assertThat(added).isNull()
    }

    Truth.assertThat(update.build().artifactDirectories)
      .isEqualTo(
        ProjectProto.ArtifactDirectories(
          directoriesMap =
            mapOf(
              artifactDirProjectPath to
                ProjectProto.ArtifactDirectoryContents(
                  contents =
                    mapOf(
                      "path/to/artifact" to
                        ProjectProto.ProjectArtifact(
                          transform = ProjectProto.ProjectArtifact.ArtifactTransform.COPY,
                          buildArtifact = ProjectProto.BuildArtifact("digest1"),
                          fromBuild = buildTimestamp2,
                          target = Label.of("//path/to:target"),
                        )
                    )
                )
            )
        )
      )
  }

  @Test
  fun add_to_proto_existing_entries() {
    val update = ProjectProtoUpdate(
      this.update.build().copy(
        // pre-existing state.
        artifactDirectories = ProjectProto.ArtifactDirectories(
          directoriesMap =
            mapOf(
              artifactDirProjectPath to
                ProjectProto.ArtifactDirectoryContents(
                  contents =
                    mapOf(
                      "path/to/existingartifact" to
                        ProjectProto.ProjectArtifact(
                          transform = ProjectProto.ProjectArtifact.ArtifactTransform.COPY,
                          buildArtifact = ProjectProto.BuildArtifact("digest2"),
                          fromBuild = buildTimestamp1,
                          target = Label.of("//project:othertarget"),
                        )
                    )
                )
            )
        )
      )
    )
    update.artifactDirectory(ProjectPath.projectRelative(Path.of("artifactDir"))) {

      addIfNewer(
        Path.of("path/to/artifact"),
        BuildArtifact.create(
          "digest1", Path.of("build-out/path/to/artifact"), Label.of("//path/to:target")
        ),
        DependencyBuildContext.create("build-id", buildTimestamp1)
      )
    }
      Truth.assertThat(update.build().artifactDirectories)
        .isEqualTo(
          ProjectProto.ArtifactDirectories(
            directoriesMap =
              mapOf(
                artifactDirProjectPath to
                  ProjectProto.ArtifactDirectoryContents(
                    contents =
                      mapOf(
                        "path/to/existingartifact" to
                          ProjectProto.ProjectArtifact(
                            transform = ProjectProto.ProjectArtifact.ArtifactTransform.COPY,
                            buildArtifact = ProjectProto.BuildArtifact("digest2"),
                            fromBuild = buildTimestamp1,
                            target = Label.of("//project:othertarget"),
                          ),
                        "path/to/artifact" to
                          ProjectProto.ProjectArtifact(
                            transform = ProjectProto.ProjectArtifact.ArtifactTransform.COPY,
                            buildArtifact = ProjectProto.BuildArtifact("digest1"),
                            fromBuild = buildTimestamp1,
                            target = Label.of("//path/to:target"),
                          ),
                      )
                  )
              )
          )
        )
  }
}
