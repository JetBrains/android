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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.vcs.VcsState;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.deps.TargetBuildInfo.MetadataKey;
import com.google.idea.blaze.qsync.java.ArtifactTrackerProto.ArtifactTrackerState;
import com.google.idea.blaze.qsync.project.ProjectPath;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ArtifactTrackerStateSerializationTest {

  private ImmutableMap<Label, TargetBuildInfo> roundTrip(Map<Label, TargetBuildInfo> depsMap) {
    return roundTrip(depsMap, ImmutableMap.of()).depsMap();
  }

  private ArtifactTracker.State roundTrip(
      Map<Label, TargetBuildInfo> depsMap, Map<String, CcToolchain> toolchainMap) {
    ArtifactTrackerState proto =
        new ArtifactTrackerStateSerializer()
            .visitDepsMap(depsMap)
            .visitToolchainMap(toolchainMap)
            .toProto();
    ArtifactTrackerStateDeserializer deserializer = new ArtifactTrackerStateDeserializer();
    deserializer.visit(proto);
    return ArtifactTracker.State.create(
        deserializer.getBuiltDepsMap(), deserializer.getCcToolchainMap());
  }

  @Test
  public void test_empty() {
    ImmutableMap<Label, TargetBuildInfo> depsMap = ImmutableMap.of();
    assertThat(roundTrip(depsMap)).containsExactlyEntriesIn(depsMap);
  }

  @Test
  public void test_java_info() {
    DependencyBuildContext buildContext =
        DependencyBuildContext.create(
            "abc-def",
            Instant.ofEpochMilli(1000),
            Optional.of(new VcsState("workspaceId", "12345", ImmutableSet.of(), Optional.empty())));
    ImmutableMap<Label, TargetBuildInfo> depsMap =
        ImmutableMap.of(
            Label.of("//my/package:target"),
            TargetBuildInfo.forJavaTarget(
                JavaArtifactInfo.builder()
                    .setLabel(Label.of("//my/package:target"))
                    .setJars(
                        ImmutableList.of(
                            BuildArtifact.create(
                                "jardigest",
                                Path.of("/build/out/classes.jar"),
                                Label.of("//my/package:target"))))
                    .setIdeAars(
                        ImmutableList.of(
                            BuildArtifact.create(
                                "aardigest",
                                Path.of("/build/out/resources.aar"),
                                Label.of("//my/package:target"))))
                    .setGenSrcs(
                        ImmutableList.of(
                            BuildArtifact.create(
                                "gensrcdigest",
                                Path.of("/build/out/Generated.java"),
                                Label.of("//my/package:target"))))
                    .setSources(ImmutableSet.of(Path.of("/workspace/path/Source.java")))
                    .setSrcJars(ImmutableSet.of(Path.of("/workspace/path/sources.srcjar")))
                    .setAndroidResourcesPackage("com.my.package")
                    .build(),
                buildContext));
    assertThat(roundTrip(depsMap)).containsExactlyEntriesIn(depsMap);
  }

  @Test
  public void test_cc_info() {
    DependencyBuildContext buildContext =
        DependencyBuildContext.create(
            "abc-def",
            Instant.ofEpochMilli(1000),
            Optional.of(new VcsState("workspaceId", "12345", ImmutableSet.of(), Optional.empty())));
    ImmutableMap<Label, TargetBuildInfo> depsMap =
        ImmutableMap.of(
            Label.of("//my/package:target"),
            TargetBuildInfo.forCcTarget(
                CcCompilationInfo.builder()
                    .target(Label.of("//my/package:target"))
                    .defines(ImmutableList.of("-D", "-w"))
                    .includeDirectories(
                        ImmutableList.of(
                            ProjectPath.projectRelative("buildout/include"),
                            ProjectPath.workspaceRelative("src/include"),
                            ProjectPath.absolute("/usr/local/include")))
                    .quoteIncludeDirectories(
                        ImmutableList.of(
                            ProjectPath.projectRelative("buildout/qinclude"),
                            ProjectPath.workspaceRelative("src/qinclude")))
                    .systemIncludeDirectories(
                        ImmutableList.of(
                            ProjectPath.projectRelative("buildout/sysinclude"),
                            ProjectPath.workspaceRelative("src/sysinclude")))
                    .frameworkIncludeDirectories(
                        ImmutableList.of(
                            ProjectPath.projectRelative("buildout/fwinclude"),
                            ProjectPath.workspaceRelative("src/fwinclude")))
                    .genHeaders(
                        ImmutableList.of(
                            BuildArtifact.create(
                                "genhdrdigest",
                                Path.of("/build/out/generated.h"),
                                Label.of("//my/package:target"))))
                    .toolchainId("my-toolchain")
                    .build(),
                buildContext));
    ImmutableMap<String, CcToolchain> toolchainMap =
        ImmutableMap.of(
            "my-toolchain",
            CcToolchain.builder()
                .id("my-toolchain")
                .compiler("clangd")
                .compilerExecutable(
                    ProjectPath.WORKSPACE_ROOT.resolveChild(Path.of("path/to/clangd")))
                .cpu("armv8")
                .targetGnuSystemName("gnu-linux-armv8")
                .builtInIncludeDirectories(
                    ImmutableList.of(
                        ProjectPath.projectRelative("buildout/builtininclude"),
                        ProjectPath.workspaceRelative("src/builtininclude")))
                .cOptions(ImmutableList.of("--copt1"))
                .cppOptions(ImmutableList.of("--ccopt1"))
                .build());
    ArtifactTracker.State newState = roundTrip(depsMap, toolchainMap);
    assertThat(newState.depsMap()).containsExactlyEntriesIn(depsMap);
    assertThat(newState.ccToolchainMap()).containsExactlyEntriesIn(toolchainMap);
  }

  @Test
  public void test_metadata() {
    DependencyBuildContext buildContext =
        DependencyBuildContext.create(
            "abc-def",
            Instant.ofEpochMilli(1000),
            Optional.of(new VcsState("workspaceId", "12345", ImmutableSet.of(), Optional.empty())));
    TargetBuildInfo.Builder targetInfo =
        TargetBuildInfo.forJavaTarget(
            JavaArtifactInfo.empty(Label.of("//my/package:target")), buildContext)
            .toBuilder();
    targetInfo
        .artifactMetadataBuilder()
        .put(
            new MetadataKey("key", Path.of("/build-out/my/package/artifact.txt")),
            "metadata contents");
    targetInfo
        .artifactMetadataBuilder()
        .put(
            new MetadataKey("other-key", Path.of("/build-out/my/package/artifact.txt")),
            "some other metadata contents");
    targetInfo
        .artifactMetadataBuilder()
        .put(
            new MetadataKey("key", Path.of("/build-out/my/package/another-artifact.srcjar")),
            "more metadata contents");
    ImmutableMap<Label, TargetBuildInfo> depsMap =
        ImmutableMap.of(Label.of("//my/package:target"), targetInfo.build());

    assertThat(roundTrip(depsMap)).containsExactlyEntriesIn(depsMap);
  }
}
