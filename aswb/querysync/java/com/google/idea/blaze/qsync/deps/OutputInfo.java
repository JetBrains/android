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

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.google.idea.blaze.qsync.java.JavaTargetInfo.JavaArtifacts;
import com.google.idea.blaze.qsync.java.cc.CcCompilationInfoOuterClass.CcCompilationInfo;

/** A data class that collecting and converting output group artifacts. */
@AutoValue
public abstract class OutputInfo {

  @VisibleForTesting
  public static final OutputInfo EMPTY =
      create(
          ArrayListMultimap.create(),
          ImmutableSet.of(),
          ImmutableSet.of(),
          ImmutableSet.of(),
          0,
          DependencyBuildContext.NONE);

  /** Returns the proto containing details of artifacts per target produced by the aspect. */
  public abstract ImmutableSet<JavaArtifacts> getArtifactInfo();

  public abstract ImmutableSet<CcCompilationInfo> getCcCompilationInfo();

  public abstract ImmutableListMultimap<OutputGroup, OutputArtifact> getOutputGroups();

  public abstract ImmutableSet<Label> getTargetsWithErrors();

  public abstract int getExitCode();

  public abstract DependencyBuildContext getBuildContext();

  public ImmutableList<OutputArtifact> get(OutputGroup group) {
    return getOutputGroups().get(group);
  }

  public ImmutableList<OutputArtifact> getJars() {
    return getOutputGroups().get(OutputGroup.JARS);
  }

  public ImmutableList<OutputArtifact> getAars() {
    return getOutputGroups().get(OutputGroup.AARS);
  }

  public ImmutableList<OutputArtifact> getGeneratedSources() {
    return getOutputGroups().get(OutputGroup.GENSRCS);
  }

  public boolean isEmpty() {
    return getOutputGroups().isEmpty();
  }

  @VisibleForTesting
  public abstract Builder toBuilder();

  @VisibleForTesting
  public static Builder builder() {
    return EMPTY.toBuilder();
  }

  public static OutputInfo create(
      Multimap<OutputGroup, OutputArtifact> allArtifacts,
      ImmutableSet<JavaArtifacts> artifacts,
      ImmutableSet<CcCompilationInfo> ccInfo,
      ImmutableSet<Label> targetsWithErrors,
      int exitCode,
      DependencyBuildContext buildContext) {
    return new AutoValue_OutputInfo.Builder()
        .setArtifactInfo(artifacts)
        .setCcCompilationInfo(ccInfo)
        .setOutputGroups(ImmutableListMultimap.copyOf(allArtifacts))
        .setTargetsWithErrors(targetsWithErrors)
        .setExitCode(exitCode)
        .setBuildContext(buildContext)
        .build();
  }

  /** Builder for {@link OutputInfo}. */
  @VisibleForTesting
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setArtifactInfo(ImmutableSet<JavaArtifacts> value);

    public abstract Builder setArtifactInfo(JavaArtifacts... values);

    public abstract Builder setCcCompilationInfo(ImmutableSet<CcCompilationInfo> value);

    public abstract Builder setOutputGroups(
        ImmutableListMultimap<OutputGroup, OutputArtifact> artifacts);

    public abstract Builder setTargetsWithErrors(ImmutableSet<Label> value);

    public abstract Builder setTargetsWithErrors(Label... values);

    public abstract Builder setExitCode(int value);

    public abstract Builder setBuildContext(DependencyBuildContext buildContext);

    public abstract OutputInfo build();
  }
}
