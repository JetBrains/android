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
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.java.ArtifactTrackerProto;
import com.google.idea.blaze.qsync.java.ArtifactTrackerProto.Metadata;
import com.google.idea.blaze.qsync.project.ProjectPath;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Deserializes {@link NewArtifactTracker} state from a proto. */
public class ArtifactTrackerStateDeserializer {

  private final ArtifactMetadata.Factory metadataFactory;
  private final ImmutableMap.Builder<Label, TargetBuildInfo> depsMap = ImmutableMap.builder();
  private final ImmutableMap.Builder<String, CcToolchain> ccToolchainMap = ImmutableMap.builder();
  private final Map<String, DependencyBuildContext> buildContexts = Maps.newHashMap();

  public ArtifactTrackerStateDeserializer(ArtifactMetadata.Factory metadataFactory) {
    this.metadataFactory = metadataFactory;
  }

  public void visit(ArtifactTrackerProto.ArtifactTrackerState proto) {
    if (proto.getVersion() != ArtifactTrackerStateSerializer.VERSION) {
      // Skip loading older versions.
      return;
    }
    proto.getBuildContextsList().forEach(this::visitBuildContext);
    proto.getBuiltDepsMap().entrySet().forEach(this::visitTargetBuildInfo);
    proto.getCcToolchainsMap().forEach(this::visitCcToolchain);
  }

  public ImmutableMap<Label, TargetBuildInfo> getBuiltDepsMap() {
    return depsMap.buildOrThrow();
  }

  public ImmutableMap<String, CcToolchain> getCcToolchainMap() {
    return ccToolchainMap.buildOrThrow();
  }

  private void visitBuildContext(ArtifactTrackerProto.BuildContext buildContext) {
    buildContexts.put(
        buildContext.getBuildIdForLogging(),
        DependencyBuildContext.create(
            buildContext.getBuildIdForLogging(),
            Instant.ofEpochMilli(buildContext.getStartTimeMillis())));
  }

  private void visitTargetBuildInfo(Map.Entry<String, ArtifactTrackerProto.TargetBuildInfo> entry) {
    ArtifactTrackerProto.TargetBuildInfo proto = entry.getValue();
    TargetBuildInfo.Builder builder =
        TargetBuildInfo.builder().buildContext(buildContexts.get(proto.getBuildId()));
    Label owner = Label.of(entry.getKey());
    if (proto.hasJavaArtifacts()) {
      builder.javaInfo(convertJavaArtifactInfo(owner, proto.getJavaArtifacts()));
    }
    if (proto.hasCcInfo()) {
      builder.ccInfo(convertCcCompilationInfo(owner, proto.getCcInfo()));
    }
    depsMap.put(owner, builder.build());
  }

  private void visitCcToolchain(String id, ArtifactTrackerProto.CcToolchain proto) {
    ccToolchainMap.put(
        id,
        CcToolchain.builder()
            .id(id)
            .compiler(proto.getCompiler())
            .compilerExecutable(projectPathFrom(proto.getCompilerExecutable()))
            .cpu(proto.getCpu())
            .targetGnuSystemName(proto.getTargetGnuSystemName())
            .builtInIncludeDirectories(
                proto.getBuiltInIncludeDirectoriesList().stream()
                    .map(this::projectPathFrom)
                    .collect(toImmutableList()))
            .cOptions(ImmutableList.copyOf(proto.getCOptionsList()))
            .cppOptions(ImmutableList.copyOf(proto.getCppOptionsList()))
            .build());
  }

  private ProjectPath projectPathFrom(ArtifactTrackerProto.ProjectPath p) {
    return switch(p.getBase()) {
      case UNSPECIFIED -> throw new IllegalStateException("Unexpected value: " + p);
      case WORKSPACE -> ProjectPath.workspaceRelative(Path.of(p.getPath()));
      case PROJECT -> ProjectPath.projectRelative(Path.of(p.getPath()));
      case ABSOLUTE -> ProjectPath.absolute(Path.of(p.getPath()));
      case UNRECOGNIZED -> throw new IllegalStateException("Unexpected value: " + p);
    };
  }

  private JavaArtifactInfo convertJavaArtifactInfo(
      Label owner, ArtifactTrackerProto.JavaArtifacts proto) {
    return JavaArtifactInfo.builder()
        .setLabel(owner)
        .setIsExternalDependency(proto.getIsExternalDependency())
        .setJars(toArtifactList(proto.getJarsList(), owner))
        .setOutputJars(toArtifactList(proto.getOutputJarsList(), owner))
        .setIdeAar(proto.hasIdeAar() ? toArtifact(proto.getIdeAar(), owner) : null)
        .setGenSrcs(toArtifactList(proto.getGenSrcsList(), owner))
        .setSources(proto.getSourcesList().stream().map(Path::of).collect(toImmutableSet()))
        .setSrcJars(proto.getSrcJarsList().stream().map(Path::of).collect(toImmutableSet()))
        .setAndroidResourcesPackage(proto.getAndroidResourcesPackage())
        .build();
  }

  private CcCompilationInfo convertCcCompilationInfo(
      Label owner, ArtifactTrackerProto.CcCompilationInfo proto) {
    return CcCompilationInfo.builder()
        .target(owner)
        .defines(proto.getDefinesList())
        .includeDirectories(
            proto.getIncludeDirectoriesList().stream()
                .map(this::projectPathFrom)
                .collect(toImmutableList()))
        .quoteIncludeDirectories(
            proto.getQuoteIncludeDirectoriesList().stream()
                .map(this::projectPathFrom)
                .collect(toImmutableList()))
        .systemIncludeDirectories(
            proto.getSysytemIncludeDirectoriesList().stream()
                .map(this::projectPathFrom)
                .collect(toImmutableList()))
        .frameworkIncludeDirectories(
            proto.getFrameworkIncludeDirectoriesList().stream()
                .map(this::projectPathFrom)
                .collect(toImmutableList()))
        .genHeaders(toArtifactList(proto.getGenHeadersList(), owner))
        .toolchainId(proto.getToolchainId())
        .build();
  }

  private BuildArtifact toArtifact(ArtifactTrackerProto.Artifact a, Label owner) {
    return BuildArtifact.create(
      a.getDigest(),
      Path.of(a.getArtifactPath()),
      owner,
      toArtifactMap(a.getMetadataList()));
  }
  private ImmutableList<BuildArtifact> toArtifactList(
      List<ArtifactTrackerProto.Artifact> protos, Label owner) {
    return protos.stream()
        .map(a ->toArtifact(a, owner))
        .collect(toImmutableList());
  }

  private ImmutableMap<Class<? extends ArtifactMetadata>, ArtifactMetadata> toArtifactMap(
      List<Metadata> protoList) {
    return protoList.stream()
        .map(metadataFactory::create)
        .filter(Objects::nonNull)
        .collect(toImmutableMap(ArtifactMetadata::getClass, Function.identity()));
  }
}
