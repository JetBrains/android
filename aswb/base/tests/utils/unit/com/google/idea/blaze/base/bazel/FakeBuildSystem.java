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
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.qsync.BazelQueryRunner;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.openapi.project.Project;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Fake implementation of {@link BuildSystem} for use in unit tests.
 *
 * <p>You must provide a {@link BuildSystemName} to use this class; reasonable defaults are provided
 * for all other values.
 *
 * <p>To modify the behaviour of a real instance of {@link BuildSystem}, consider using {@link
 * com.google.idea.blaze.base.bazel.BuildSystemProviderWrapper.BuildSystemWrapper} instead.
 */
@AutoValue
public abstract class FakeBuildSystem implements BuildSystem {

  public static Builder builder(BuildSystemName name) {
    return new AutoValue_FakeBuildSystem.Builder()
        .setName(name)
        .setSyncStrategy(SyncStrategy.SERIAL);
  }

  @Override
  public abstract BuildSystemName getName();

  @Nullable
  abstract BuildInvoker getBuildInvoker();

  @Override
  public BuildInvoker getBuildInvoker(Project project, BlazeContext context) {
    return getBuildInvoker();
  }

  abstract Optional<BuildInvoker> getParallelBuildInvoker();

  abstract Optional<BuildInvoker> getLocalBuildInvoker();

  @Override
  public Optional<BuildInvoker> getLocalBuildInvoker(Project project, BlazeContext context) {
    return getParallelBuildInvoker();
  }

  @Override
  public Optional<BuildInvoker> getParallelBuildInvoker(Project project, BlazeContext context) {
    return getParallelBuildInvoker();
  }

  @Override
  public SyncStrategy getSyncStrategy(Project project) {
    return getSyncStrategy();
  }

  protected abstract SyncStrategy getSyncStrategy();

  @Override
  public void populateBlazeVersionData(
      WorkspaceRoot workspaceRoot, BlazeInfo blazeInfo, BlazeVersionData.Builder builder) {}

  abstract Optional<String> getBazelVersionString();

  @Override
  public Optional<String> getBazelVersionString(BlazeInfo blazeInfo) {
    return getBazelVersionString();
  }

  @Override
  public BazelQueryRunner createQueryRunner(Project project) {
    return null;
  }

  /**
   * Builder for {@link FakeBuildSystem}. Use {@link FakeBuildSystem#builder(BuildSystemName)} to
   * get an instance.
   */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract FakeBuildSystem build();

    public abstract Builder setName(BuildSystemName value);

    public abstract Builder setBuildInvoker(BuildInvoker value);

    public abstract Builder setParallelBuildInvoker(Optional<BuildInvoker> value);

    public abstract Builder setLocalBuildInvoker(Optional<BuildInvoker> value);

    public abstract Builder setBazelVersionString(Optional<String> value);

    public abstract Builder setSyncStrategy(SyncStrategy value);
  }
}
