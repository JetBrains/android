/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.bazel;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.RuleDefinition;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/**
 * Fake implementation of {@link BuildSystemProvider} for use in unit tests.
 *
 * <p>Note, if you want to modify the behaviour of a real {@link BuildSystemProvider}, consider
 * using {@link BuildSystemProviderWrapper} instead.
 */
@AutoValue
public abstract class FakeBuildSystemProvider implements BuildSystemProvider {

  public static Builder builder() {
    return new AutoValue_FakeBuildSystemProvider.Builder()
        .setBuildArtifactDirectoriesFunction(s -> ImmutableList.of())
        .setRuleDocumentationUrlFunction(r -> "")
        .setLanguageSupportDocumentationUrlFunction(s -> "")
        .setPossibleBuildFileNames(ImmutableList.of())
        .setPossibleWorkspaceFileNames(ImmutableList.of())
        .setpossibleModuleFileNames(ImmutableList.of());
  }

  @Override
  public abstract BuildSystem getBuildSystem();

  @Override
  @Nullable
  public abstract WorkspaceRootProvider getWorkspaceRootProvider();

  abstract Function<WorkspaceRoot, ImmutableList<String>> getBuildArtifactDirectoriesFunction();

  @Override
  public ImmutableList<String> buildArtifactDirectories(WorkspaceRoot root) {
    return getBuildArtifactDirectoriesFunction().apply(root);
  }

  abstract Function<RuleDefinition, String> getRuleDocumentationUrlFunction();

  @Nullable
  @Override
  public String getRuleDocumentationUrl(RuleDefinition rule) {
    return getRuleDocumentationUrlFunction().apply(rule);
  }

  @Nullable
  @Override
  public abstract String getProjectViewDocumentationUrl();

  abstract Function<String, String> getLanguageSupportDocumentationUrlFunction();

  @Nullable
  @Override
  public String getLanguageSupportDocumentationUrl(String relativeDocName) {
    return getLanguageSupportDocumentationUrlFunction().apply(relativeDocName);
  }

  public abstract ImmutableList<String> getPossibleBuildFileNames();

  @Override
  public ImmutableList<String> possibleBuildFileNames() {
    return getPossibleBuildFileNames();
  }

  public abstract ImmutableList<String> getPossibleWorkspaceFileNames();

  @Override
  public ImmutableList<String> possibleWorkspaceFileNames() {
    return getPossibleWorkspaceFileNames();
  }

  public abstract ImmutableList<String> getPossibleModuleFileNames();

  @Override
  public ImmutableList<String> possibleModuleFileNames() {
    return getPossibleModuleFileNames();
  }

  /**
   * Builder for {@link FakeBuildSystemProvider}.
   *
   * <p>Note that you must set a {@link BuildSystem} on this before calling {@link #build()}.
   *
   * @see {@link FakeBuildSystem} and {@link
   *     com.google.idea.blaze.base.bazel.BuildSystemProviderWrapper.BuildSystemWrapper}.
   */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract FakeBuildSystemProvider build();

    public abstract Builder setBuildSystem(BuildSystem value);

    public abstract Builder setWorkspaceRootProvider(WorkspaceRootProvider value);

    public abstract Builder setBuildArtifactDirectoriesFunction(
        Function<WorkspaceRoot, ImmutableList<String>> value);

    public abstract Builder setRuleDocumentationUrlFunction(Function<RuleDefinition, String> value);

    public abstract Builder setProjectViewDocumentationUrl(String value);

    public abstract Builder setLanguageSupportDocumentationUrlFunction(
        Function<String, String> value);

    public abstract Builder setPossibleBuildFileNames(ImmutableList<String> value);

    public abstract Builder setPossibleWorkspaceFileNames(ImmutableList<String> value);

    public abstract Builder setpossibleModuleFileNames(ImmutableList<String> value);
  }
}
