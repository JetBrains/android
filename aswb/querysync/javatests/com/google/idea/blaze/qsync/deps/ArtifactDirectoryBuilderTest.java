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
package com.google.idea.blaze.qsync.deps;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.common.base.Joiner;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.protobuf.TextFormat;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ArtifactDirectoryBuilderTest {

  @Test
  public void test_add_single() throws Exception {
    ArtifactDirectoryBuilder adb = new ArtifactDirectoryBuilder(Path.of("artifactDir"));

    Optional<ProjectPath> added =
        adb.addIfNewer(
            Path.of("path/to/artifact"),
            BuildArtifact.create(
                "digest", Path.of("build-out/path/to/artifact"), Label.of("//path/to:target")),
            DependencyBuildContext.create(
                "build-id", Instant.EPOCH.plusSeconds(1)));

    assertThat(added).hasValue(ProjectPath.projectRelative("artifactDir/path/to/artifact"));

    ProjectProto.ArtifactDirectories.Builder protoBuilder =
        ProjectProto.ArtifactDirectories.newBuilder();
    adb.addTo(protoBuilder);

    assertThat(protoBuilder.build())
        .isEqualTo(
            TextFormat.parse(
                Joiner.on("")
                    .join(
                        "directories {",
                        "  key: \"artifactDir\"",
                        "  value {",
                        "    contents {",
                        "      key: \"path/to/artifact\"",
                        "      value {",
                        "        transform: COPY",
                        "        build_artifact {",
                        "          digest: \"digest\"",
                        "        }",
                        "        target: \"//path/to:target\"",
                        "      }",
                        "    }",
                        "  }",
                        "}"),
                ProjectProto.ArtifactDirectories.class));
  }

  @Test
  public void test_add_conflicting_newer() {
    ArtifactDirectoryBuilder adb = new ArtifactDirectoryBuilder(Path.of("artifactDir"));

    adb.addIfNewer(
        Path.of("path/to/artifact"),
        BuildArtifact.create(
            "digest1", Path.of("build-out/path/to/artifact"), Label.of("//path/to:target")),
        DependencyBuildContext.create("build-id", Instant.EPOCH.plusSeconds(1)));

    Optional<ProjectPath> added =
        adb.addIfNewer(
            Path.of("path/to/artifact"),
            BuildArtifact.create(
                "digest2", Path.of("build-out/path/to/newartifact"), Label.of("//path/to:target")),
            DependencyBuildContext.create(
                "build-id", Instant.EPOCH.plusSeconds(2)));

    assertThat(added).hasValue(ProjectPath.projectRelative("artifactDir/path/to/artifact"));

    ProjectProto.ArtifactDirectories.Builder protoBuilder =
        ProjectProto.ArtifactDirectories.newBuilder();
    adb.addTo(protoBuilder);

    ProjectProto.ArtifactDirectories proto = protoBuilder.build();
    assertThat(
            proto
                .getDirectoriesMap()
                .get("artifactDir")
                .getContentsMap()
                .get("path/to/artifact")
                .getBuildArtifact()
                .getDigest())
        .isEqualTo("digest2");
  }

  @Test
  public void test_add_conflicting_older() {
    ArtifactDirectoryBuilder adb = new ArtifactDirectoryBuilder(Path.of("artifactDir"));

    adb.addIfNewer(
        Path.of("path/to/artifact"),
        BuildArtifact.create(
            "digest1", Path.of("build-out/path/to/artifact"), Label.of("//path/to:target")),
        DependencyBuildContext.create("build-id", Instant.EPOCH.plusSeconds(2)));

    Optional<ProjectPath> added =
        adb.addIfNewer(
            Path.of("path/to/artifact"),
            BuildArtifact.create(
                "digest2", Path.of("build-out/path/to/newartifact"), Label.of("//path/to:target")),
            DependencyBuildContext.create(
                "build-id", Instant.EPOCH.plusSeconds(1)));

    assertThat(added).isEmpty();

    ProjectProto.ArtifactDirectories.Builder protoBuilder =
        ProjectProto.ArtifactDirectories.newBuilder();
    adb.addTo(protoBuilder);
    ProjectProto.ArtifactDirectories proto = protoBuilder.build();
    assertThat(
            proto
                .getDirectoriesMap()
                .get("artifactDir")
                .getContentsMap()
                .get("path/to/artifact")
                .getBuildArtifact()
                .getDigest())
        .isEqualTo("digest1");
  }

  @Test
  public void add_to_proto_existing_entries() throws Exception {
    ArtifactDirectoryBuilder adb = new ArtifactDirectoryBuilder(Path.of("artifactDir"));

    adb.addIfNewer(
        Path.of("path/to/artifact"),
        BuildArtifact.create(
            "digest1", Path.of("build-out/path/to/artifact"), Label.of("//path/to:target")),
        DependencyBuildContext.create("build-id", Instant.EPOCH.plusSeconds(1)));

    ProjectProto.ArtifactDirectories.Builder protoBuilder =
        TextFormat.parse(
            Joiner.on("")
                .join(
                    "directories {",
                    "  key: \"artifactDir\"",
                    "  value {",
                    "    contents {",
                    "      key: \"path/to/existingartifact\"",
                    "      value {",
                    "        transform: COPY",
                    "        build_artifact {",
                    "          digest: \"digest2\"",
                    "        }",
                    "        target: \"//project:othertarget\"",
                    "      }",
                    "    }",
                    "  }",
                    "}"),
            ProjectProto.ArtifactDirectories.class)
            .toBuilder();

    adb.addTo(protoBuilder);

    assertThat(protoBuilder.build())
        .isEqualTo(
            TextFormat.parse(
                Joiner.on("")
                    .join(
                        "directories {",
                        "  key: \"artifactDir\"",
                        "  value {",
                        "    contents {",
                        "      key: \"path/to/existingartifact\"",
                        "      value {",
                        "        transform: COPY",
                        "        build_artifact {",
                        "          digest: \"digest2\"",
                        "        }",
                        "        target: \"//project:othertarget\"",
                        "      }",
                        "    }",
                        "    contents {",
                        "      key: \"path/to/artifact\"",
                        "      value {",
                        "        transform: COPY",
                        "        build_artifact {",
                        "          digest: \"digest1\"",
                        "        }",
                        "        target: \"//path/to:target\"",
                        "      }",
                        "    }",
                        "  }",
                        "}"),
                ProjectProto.ArtifactDirectories.class));
  }
}
