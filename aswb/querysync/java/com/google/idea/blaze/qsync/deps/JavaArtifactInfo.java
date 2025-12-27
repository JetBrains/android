/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.idea.blaze.common.Interners;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.artifacts.DigestMap;
import com.google.idea.blaze.qsync.java.JavaTargetInfo;
import com.google.idea.blaze.qsync.project.ProjectPath;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/** Information about a project dependency that is calculated when the dependency is built. */
@AutoValue
public abstract class JavaArtifactInfo {

  /** Build label for the dependency. */
  public abstract Label label();

  /** Whether the target is in project view. */
  public abstract boolean isExternalDependency();

  /** Whether the target is a Kotlin toolchain. */
  public abstract boolean isKotlinToolchain();

  /**
   * The jar artifacts relative path (blaze-out/xxx) that can be used to retrieve local copy in the
   * cache.
   */
  public abstract ImmutableSet<BuildArtifact> jars();

  /** The jar that in target's java_output. */
  public abstract ImmutableSet<BuildArtifact> outputJars();

  /**
   * The aar artifacts relative path (blaze-out/xxx) that can be used to retrieve local copy in the
   * cache.
   */
  @Nullable  public abstract BuildArtifact ideAar();

  /**
   * The gensrc artifacts relative path (blaze-out/xxx) that can be used to retrieve local copy in
   * the cache.
   */
  public abstract ImmutableSet<BuildArtifact> genSrcs();
  public abstract ImmutableSet<BuildArtifact> genAndroidRes();
  public abstract ImmutableSet<BuildArtifact> protoSrcjars();

  /** Workspace relative sources for this dependency, extracted at dependency build time. */
  public abstract ImmutableSet<ProjectPath> sources();

  public abstract ImmutableSet<ProjectPath> srcJars();

  public abstract String androidResourcesPackage();
  public abstract ImmutableList<String> kotlinCompilerFlags();

  public abstract Builder toBuilder();

  public JavaArtifactInfo withMetadata(
      ImmutableSetMultimap<BuildArtifact, ArtifactMetadata> metadata) {
    if (metadata.isEmpty()) {
      return this;
    }
    return toBuilder()
        .setGenSrcs(BuildArtifact.addMetadata(genSrcs(), metadata))
        .setGenAndroidRes(BuildArtifact.addMetadata(genAndroidRes(), metadata))
        .setProtoSrcjars(BuildArtifact.addMetadata(protoSrcjars(), metadata))
        .setIdeAar(ideAar() != null ?ideAar().withMetadata(metadata.get(ideAar())) : null)
        .setJars(BuildArtifact.addMetadata(jars(), metadata))
        .setOutputJars(BuildArtifact.addMetadata(outputJars(), metadata))
        .build();
  }

  public static Builder builder() {
    return new AutoValue_JavaArtifactInfo.Builder();
  }

  public static JavaArtifactInfo create(JavaTargetInfo.JavaArtifacts proto,
                                        DigestMap digestMap,
                                        ProjectPath.ExternalRepositoryFinder externalRepositoryFinder) {
    // Note, the proto contains a list of sources, we take the parent as we want directories instead
    Label target = Label.of(proto.getTarget());
    Builder builder = builder();
    if (proto.hasIdeAar()) {
      digestMap.createBuildArtifact(Interners.pathOf(proto.getIdeAar().getFile()), target).ifPresent(builder::setIdeAar);
    }
    return builder()
        .setLabel(target)
        .setIsExternalDependency(proto.getIsExternalDependency())
        .setJars(BuildArtifact.fromProtos(proto.getJarsList(), digestMap, target))
        .setOutputJars(BuildArtifact.fromProtos(proto.getOutputJarsList(), digestMap, target))
        .setGenSrcs(BuildArtifact.fromProtos(proto.getGenSrcsList(), digestMap, target))
        .setGenAndroidRes(BuildArtifact.fromProtos(proto.getGenAndroidResList(), digestMap, target))
        .setProtoSrcjars(BuildArtifact.fromProtos(proto.getProtoSrcjarsList(), digestMap, target))
        .setSources(proto.getSrcsList().stream()
                      .map(it -> ProjectPath.workspaceRelative(Interners.pathOf(it), externalRepositoryFinder))
                      .collect(toImmutableSet()))
        .setSrcJars(
            proto.getSrcjarsList().stream()
              .map(it -> ProjectPath.workspaceRelative(Interners.pathOf(it), externalRepositoryFinder))
              .collect(toImmutableSet()))
        .setAndroidResourcesPackage(proto.getAndroidResourcesPackage())
        .setKotlinCompilerFlags(ImmutableList.copyOf(proto.getKotlinCompilerFlagsList()))
        .setIsKotlinToolchain(proto.getIsKotlinToolchain())
        .build();
  }

  public static JavaArtifactInfo empty(Label target) {
    return builder()
        .setLabel(target)
        .setIsExternalDependency(false)
        .setJars(ImmutableList.of())
        .setOutputJars(ImmutableList.of())
        .setGenSrcs(ImmutableList.of())
        .setGenAndroidRes(ImmutableList.of())
        .setProtoSrcjars(ImmutableList.of())
        .setSources(ImmutableSet.of())
        .setSrcJars(ImmutableSet.of())
        .setAndroidResourcesPackage("")
        .setKotlinCompilerFlags(ImmutableList.of())
        .setIsKotlinToolchain(false)
        .build();
  }

  /** Builder for {@link JavaArtifactInfo}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setLabel(Label value);

    public abstract Builder setIsExternalDependency(boolean value);

    public abstract Builder setIsKotlinToolchain(boolean value);

    public abstract Builder setJars(List<BuildArtifact> value);

    public abstract ImmutableSet.Builder<BuildArtifact> jarsBuilder();

    public abstract Builder setOutputJars(List<BuildArtifact> value);

    public abstract ImmutableSet.Builder<BuildArtifact> outputJarsBuilder();

    public abstract Builder setIdeAar(@Nullable BuildArtifact value);

    public abstract Builder setGenSrcs(List<BuildArtifact> value);

    public abstract Builder setGenSrcs(BuildArtifact... value);
    public abstract Builder setGenAndroidRes(List<BuildArtifact> value);
    public abstract ImmutableSet.Builder<BuildArtifact> genAndroidResBuilder();

    public abstract Builder setProtoSrcjars(List<BuildArtifact> value);

    public abstract Builder setSources(Set<ProjectPath> value);

    public abstract Builder setSrcJars(Set<ProjectPath> value);

    public abstract Builder setAndroidResourcesPackage(String value);

    public abstract Builder setKotlinCompilerFlags(ImmutableList<String> value);

    public abstract JavaArtifactInfo build();
  }
}
