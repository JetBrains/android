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
import com.google.errorprone.annotations.MustBeClosed;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.settings.BuildBinaryType;
import com.google.idea.blaze.base.settings.BuildSystemName;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/** Simple implementation of {@link BuildInvoker} for injecting dependencies in test code. */
@AutoValue
public abstract class FakeBuildInvoker implements BuildInvoker {

  public static Builder builder() {
    return new AutoValue_FakeBuildInvoker.Builder()
        .type(BuildBinaryType.NONE)
        .binaryPath("")
        .supportsParallelism(false)
        .buildResultHelperSupplier(() -> null)
        .commandRunner(new FakeBlazeCommandRunner())
        .buildSystem(FakeBuildSystem.builder(BuildSystemName.Blaze).build());
  }

  @Override
  public abstract BuildBinaryType getType();

  @Override
  public abstract String getBinaryPath();

  @Override
  @Nullable
  public abstract BlazeInfo getBlazeInfo();

  public abstract boolean getSupportsParallelism();

  @Override
  public boolean supportsParallelism() {
    return getSupportsParallelism();
  }

  @Override
  @MustBeClosed
  @Nullable
  public BuildResultHelper createBuildResultHelper() {
    return getBuildResultHelperSupplier().get();
  }

  abstract Supplier<BuildResultHelper> getBuildResultHelperSupplier();

  @Override
  public abstract FakeBlazeCommandRunner getCommandRunner();

  /**
   * Builder class for instances of {@link com.google.idea.blaze.base.bazel.FakeBuildInvoker}.
   *
   * <p>Use {@link FakeBuildInvoker#builder()} to get an instance.
   */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract FakeBuildInvoker build();

    public abstract Builder type(BuildBinaryType type);

    public abstract Builder binaryPath(String binaryPath);

    public abstract Builder blazeInfo(BlazeInfo blazeInfo);

    public abstract Builder supportsParallelism(boolean parallel);

    public abstract Builder buildResultHelperSupplier(Supplier<BuildResultHelper> supplier);

    public abstract Builder commandRunner(FakeBlazeCommandRunner runner);

    public abstract Builder buildSystem(BuildSystem buildSystem);
  }

}
