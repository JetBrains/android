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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.java.ArtifactTrackerProto;
import com.google.idea.blaze.qsync.java.ArtifactTrackerProto.Artifact;
import com.google.idea.blaze.qsync.java.ArtifactTrackerProto.ArtifactTrackerState;
import com.google.idea.blaze.qsync.java.ArtifactTrackerProto.BuildContext;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.SnapshotSerializer;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/** Serializes {@link NewArtifactTracker} state to a proto. */
public class ArtifactTrackerStateSerializer {

  public static final int VERSION = 2;

  private final ArtifactTrackerProto.ArtifactTrackerState.Builder proto =
      ArtifactTrackerProto.ArtifactTrackerState.newBuilder().setVersion(VERSION);
  private final Set<String> buildIdsSeen = Sets.newHashSet();

  @CanIgnoreReturnValue
  public ArtifactTrackerStateSerializer visitDepsMap(Map<Label, TargetBuildInfo> builtDeps) {
    builtDeps.entrySet().forEach(e -> visitTargetBuildInfo(e.getKey(), e.getValue()));
    return this;
  }

  @CanIgnoreReturnValue
  public ArtifactTrackerStateSerializer visitToolchainMap(Map<String, CcToolchain> toolchainMap) {
    toolchainMap.values().forEach(this::visitCcToolchain);
    return this;
  }

  public ArtifactTrackerState toProto() {
    return proto.build();
  }

  private void visitTargetBuildInfo(Label target, TargetBuildInfo targetBuildInfo) {
    visitBuildContext(targetBuildInfo.buildContext());

    ArtifactTrackerProto.TargetBuildInfo.Builder builder =
        ArtifactTrackerProto.TargetBuildInfo.newBuilder();
    builder.setBuildId(targetBuildInfo.buildContext().buildId());
    targetBuildInfo.javaInfo().ifPresent(ji -> visitJavaInfo(ji, builder));
    targetBuildInfo.ccInfo().ifPresent(cc -> visitCcInfo(cc, builder));
    visitMetadata(targetBuildInfo.artifactMetadata(), builder);
    proto.putBuiltDeps(target.toString(), builder.build());
  }

  private void visitBuildContext(DependencyBuildContext buildContext) {
    if (buildIdsSeen.add(buildContext.buildId())) {
      BuildContext.Builder builder =
          proto
              .addBuildContextsBuilder()
              .setStartTimeMillis(buildContext.startTime().toEpochMilli())
              .setBuildId(buildContext.buildId());
      buildContext
          .vcsState()
          .ifPresent(vcs -> SnapshotSerializer.visitVcsState(vcs, builder.getVcsStateBuilder()));
    }
  }

  private void visitJavaInfo(
      JavaArtifactInfo javaInfo, ArtifactTrackerProto.TargetBuildInfo.Builder builder) {
    builder
        .getJavaArtifactsBuilder()
        .addAllGenSrcs(toProtos(javaInfo.genSrcs()))
        .addAllIdeAars(toProtos(javaInfo.ideAars()))
        .addAllJars(toProtos(javaInfo.jars()))
        .addAllSources(javaInfo.sources().stream().map(Path::toString).collect(toImmutableList()))
        .addAllSrcJars(javaInfo.srcJars().stream().map(Path::toString).collect(toImmutableList()))
        .setAndroidResourcesPackage(javaInfo.androidResourcesPackage());
  }

  private ImmutableList<Artifact> toProtos(ImmutableList<BuildArtifact> artifacts) {
    return artifacts.stream()
        .map(
            artifact ->
                ArtifactTrackerProto.Artifact.newBuilder()
                    .setDigest(artifact.digest())
                    .setArtifactPath(artifact.artifactPath().toString())
                    .build())
        .collect(toImmutableList());
  }

  private void visitCcInfo(
      CcCompilationInfo ccInfo, ArtifactTrackerProto.TargetBuildInfo.Builder builder) {
    builder
        .getCcInfoBuilder()
        .addAllDefines(ccInfo.defines())
        .addAllIncludeDirectories(
            ccInfo.includeDirectories().stream()
                .map(ProjectPath::toProto)
                .collect(toImmutableList()))
        .addAllQuoteIncludeDirectories(
            ccInfo.quoteIncludeDirectories().stream()
                .map(ProjectPath::toProto)
                .collect(toImmutableList()))
        .addAllSysytemIncludeDirectories(
            ccInfo.systemIncludeDirectories().stream()
                .map(ProjectPath::toProto)
                .collect(toImmutableList()))
        .addAllFrameworkIncludeDirectories(
            ccInfo.frameworkIncludeDirectories().stream()
                .map(ProjectPath::toProto)
                .collect(toImmutableList()))
        .addAllGenHeaders(toProtos(ccInfo.genHeaders()))
        .setToolchainId(ccInfo.toolchainId());
  }

  private void visitCcToolchain(CcToolchain toolchain) {
    proto.putCcToolchains(
        toolchain.id(),
        ArtifactTrackerProto.CcToolchain.newBuilder()
            .setCompiler(toolchain.compiler())
            .setCompilerExecutable(toolchain.compilerExecutable().toProto())
            .setCpu(toolchain.cpu())
            .setTargetGnuSystemName(toolchain.targetGnuSystemName())
            .addAllBuiltInIncludeDirectories(
                toolchain.builtInIncludeDirectories().stream()
                    .map(ProjectPath::toProto)
                    .collect(toImmutableList()))
            .addAllCOptions(toolchain.cOptions())
            .addAllCppOptions(toolchain.cppOptions())
            .build());
  }

  private String makeMapKey(TargetBuildInfo.MetadataKey key) {
    return key.metadataId() + ":" + key.artifactPath();
  }

  private void visitMetadata(
      ImmutableMap<TargetBuildInfo.MetadataKey, String> map,
      ArtifactTrackerProto.TargetBuildInfo.Builder builder) {
    for (Map.Entry<TargetBuildInfo.MetadataKey, String> entry : map.entrySet()) {
      builder.putDerivedArtifactMetadata(makeMapKey(entry.getKey()), entry.getValue());
    }
  }
}
