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
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.java.ArtifactTrackerProto.ArtifactTrackerState;
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata;
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata.AarResPackage;
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata.JavaSourcePackage;
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata.SrcJarJavaPackageRoots;
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata.SrcJarPrefixedJavaPackageRoots;
import com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder.JarPath;
import com.google.idea.blaze.qsync.project.ProjectPath;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    ArtifactTrackerStateDeserializer deserializer =
        new ArtifactTrackerStateDeserializer(new JavaArtifactMetadata.Factory());
    deserializer.visit(proto);
    return ArtifactTracker.State.create(
        ImmutableMap.copyOf(deserializer.getBuiltDepsMap()),
        ImmutableMap.copyOf(deserializer.getCcToolchainMap()));
  }

  @Test
  public void test_empty() {
    ImmutableMap<Label, TargetBuildInfo> depsMap = ImmutableMap.of();
    assertThat(roundTrip(depsMap)).containsExactlyEntriesIn(depsMap);
  }

  @Test
  public void test_java_info() {
    DependencyBuildContext buildContext =
        DependencyBuildContext.create("abc-def", Instant.ofEpochMilli(1000));
    ImmutableMap<Label, TargetBuildInfo> depsMap =
        ImmutableMap.of(
            Label.of("//my/package:target"),
            TargetBuildInfo.forJavaTarget(
                new JavaArtifactInfo(
                    Label.of("//my/package:target"),
                    false,
                    false,
                    Set.of(
                        new BuildArtifact(
                            "jardigest",
                            Path.of("/build/out/classes.jar"),
                            Label.of("//my/package:target"))),
                    Set.of(
                        new BuildArtifact(
                            "outputjardigest",
                            Path.of("/build/out/output_classes.jar"),
                            Label.of("//my/package:target"))),
                    new BuildArtifact(
                        "aardigest",
                        Path.of("/build/out/resources.aar"),
                        Label.of("//my/package:target")),
                    Set.of(
                        new BuildArtifact(
                            "gensrcdigest",
                            Path.of("/build/out/Generated.java"),
                            Label.of("//my/package:target"))),
                    Set.of(),
                    Set.of(
                        new BuildArtifact(
                            "gensrcdigest",
                            Path.of("/build/out/libproto-src.jar"),
                            Label.of("//my/package:target"))),
                    Set.of(
                        ProjectPath.workspaceRelativeForTests(
                            Path.of("/workspace/path/Source.java"))),
                    Set.of(
                        ProjectPath.workspaceRelativeForTests(
                            Path.of("/workspace/path/sources.srcjar"))),
                    "com.my.package",
                    List.of(),
                    Set.of(Label.of("//my/package:dep1"), Label.of("//my/package:dep2"))),
                buildContext));
    assertThat(roundTrip(depsMap)).containsExactlyEntriesIn(depsMap);
  }

  @Test
  public void test_java_info_external() {
    DependencyBuildContext buildContext =
        DependencyBuildContext.create("abc-def", Instant.ofEpochMilli(1000));
    ImmutableMap<Label, TargetBuildInfo> depsMap =
        ImmutableMap.of(
            Label.of("//my/package:target"),
            TargetBuildInfo.forJavaTarget(
                new JavaArtifactInfo(
                    Label.of("//my/package:target"),
                    true,
                    false,
                    Set.of(
                        new BuildArtifact(
                            "jardigest",
                            Path.of("/build/out/classes.jar"),
                            Label.of("//my/package:target"))),
                    Set.of(
                        new BuildArtifact(
                            "outputjardigest",
                            Path.of("/build/out/output_classes.jar"),
                            Label.of("//my/package:target"))),
                    new BuildArtifact(
                        "aardigest",
                        Path.of("/build/out/resources.aar"),
                        Label.of("//my/package:target")),
                    Set.of(
                        new BuildArtifact(
                            "gensrcdigest",
                            Path.of("/build/out/Generated.java"),
                            Label.of("//my/package:target"))),
                    Set.of(),
                    Set.of(
                        new BuildArtifact(
                            "gensrcdigest",
                            Path.of("/build/out/libproto-src.jar"),
                            Label.of("//my/package:target"))),
                    Set.of(
                        ProjectPath.workspaceRelativeForTests(
                            Path.of("/workspace/path/Source.java"))),
                    Set.of(
                        ProjectPath.workspaceRelativeForTests(
                            Path.of("/workspace/path/sources.srcjar"))),
                    "com.my.package",
                    List.of(),
                    Set.of()),
                buildContext));
    assertThat(roundTrip(depsMap)).containsExactlyEntriesIn(depsMap);
  }

  @Test
  public void test_cc_info() {
    DependencyBuildContext buildContext =
        DependencyBuildContext.create("abc-def", Instant.ofEpochMilli(1000));
    ImmutableMap<Label, TargetBuildInfo> depsMap =
        ImmutableMap.of(
            Label.of("//my/package:target"),
            TargetBuildInfo.forCcTarget(
                new CcCompilationInfo(
                    Label.of("//my/package:target"),
                    ImmutableList.of("-DDEF"),
                    ImmutableList.of("-D", "-w"),
                    ImmutableList.of(
                        ProjectPath.projectRelative(Path.of("buildout/include")),
                        ProjectPath.workspaceRelativeForTests(Path.of("src/include")),
                        ProjectPath.absolute(Path.of("/usr/local/include"))),
                    ImmutableList.of(
                        ProjectPath.projectRelative(Path.of("buildout/qinclude")),
                        ProjectPath.workspaceRelativeForTests(Path.of("src/qinclude"))),
                    ImmutableList.of(
                        ProjectPath.projectRelative(Path.of("buildout/sysinclude")),
                        ProjectPath.workspaceRelativeForTests(Path.of("src/sysinclude"))),
                    ImmutableList.of(
                        ProjectPath.projectRelative(Path.of("buildout/fwinclude")),
                        ProjectPath.workspaceRelativeForTests(Path.of("src/fwinclude"))),
                    Set.of(
                        new BuildArtifact(
                            "genhdrdigest",
                            Path.of("/build/out/generated.h"),
                            Label.of("//my/package:target"))),
                    "my-toolchain"),
                buildContext));
    ImmutableMap<String, CcToolchain> toolchainMap =
        ImmutableMap.of(
            "my-toolchain",
            CcToolchain.builder()
                .id("my-toolchain")
                .compiler("clangd")
                .compilerExecutable(
                    ProjectPath.workspaceRelativeForTests(Path.of("path/to/clangd")))
                .cpu("armv8")
                .targetGnuSystemName("gnu-linux-armv8")
                .builtInIncludeDirectories(
                    ImmutableList.of(
                        ProjectPath.projectRelative(Path.of("buildout/builtininclude")),
                        ProjectPath.workspaceRelativeForTests(Path.of("src/builtininclude")),
                        ProjectPath.externalRepositoryRelative(
                            "ndk", Path.of("src/ndk_builtininclude"))))
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
        DependencyBuildContext.create("abc-def", Instant.ofEpochMilli(1000));
    TargetBuildInfo targetInfo =
        TargetBuildInfo.forJavaTarget(
            new JavaArtifactInfo(
                Label.of("//my/package:target"),
                false,
                false,
                Set.of(),
                Set.of(),
                new BuildArtifact(
                        "bcd",
                        Path.of("//my/package/libtarget.aar"),
                        Label.of("//my/package:target"))
                    .withMetadata(new AarResPackage("com.aar.package")),
                Set.of(
                    new BuildArtifact(
                            "abc",
                            Path.of("//my/package/Generated.java"),
                            Label.of("//my/package:target"))
                        .withMetadata(new JavaSourcePackage("com.my.package")),
                    new BuildArtifact(
                            "abc",
                            Path.of("//my/package/libtarget.srcjar"),
                            Label.of("//my/package:target"))
                        .withMetadata(
                            new SrcJarJavaPackageRoots(
                                ImmutableSet.of(Path.of("root1"), Path.of("root2"))),
                            new SrcJarPrefixedJavaPackageRoots(
                                ImmutableSet.of(
                                    JarPath.create("root1", "com.my.package"),
                                    JarPath.create("root2", "com.other.package"))))),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                "",
                List.of(),
                Set.of()),
            buildContext);
    ImmutableMap<Label, TargetBuildInfo> depsMap =
        ImmutableMap.of(Label.of("//my/package:target"), targetInfo);

    assertThat(roundTrip(depsMap)).containsExactlyEntriesIn(depsMap);
  }
}
