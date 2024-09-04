/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.ideinfo;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;

/** Represents toolchain info from a cc_toolchain or cc_toolchain_suite */
public final class CToolchainIdeInfo implements ProtoWrapper<IntellijIdeInfo.CToolchainIdeInfo> {

  private final ImmutableList<String> cCompilerOptions;
  private final ImmutableList<String> cppCompilerOptions;
  private final ImmutableList<ExecutionRootPath> builtInIncludeDirectories;
  private final ExecutionRootPath cppExecutable;
  private final String targetName;

  private CToolchainIdeInfo(
      ImmutableList<String> cCompilerOptions,
      ImmutableList<String> cppCompilerOptions,
      ImmutableList<ExecutionRootPath> builtInIncludeDirectories,
      ExecutionRootPath cppExecutable,
      String targetName) {
    this.cCompilerOptions = cCompilerOptions;
    this.cppCompilerOptions = cppCompilerOptions;
    this.builtInIncludeDirectories = builtInIncludeDirectories;
    this.cppExecutable = cppExecutable;
    this.targetName = targetName;
  }

  static CToolchainIdeInfo fromProto(IntellijIdeInfo.CToolchainIdeInfo proto) {
    return new CToolchainIdeInfo(
        ImmutableList.copyOf(proto.getCOptionList()),
        ImmutableList.copyOf(proto.getCppOptionList()),
        ProtoWrapper.map(proto.getBuiltInIncludeDirectoryList(), ExecutionRootPath::fromProto),
        ExecutionRootPath.fromProto(proto.getCppExecutable()),
        proto.getTargetName());
  }

  @Override
  public IntellijIdeInfo.CToolchainIdeInfo toProto() {
    return IntellijIdeInfo.CToolchainIdeInfo.newBuilder()
        .addAllCOption(cCompilerOptions)
        .addAllCppOption(cppCompilerOptions)
        .addAllBuiltInIncludeDirectory(ProtoWrapper.mapToProtos(builtInIncludeDirectories))
        .setCppExecutable(cppExecutable.toProto())
        .setTargetName(targetName)
        .build();
  }

  public ImmutableList<String> getcCompilerOptions() {
    return cCompilerOptions;
  }

  public ImmutableList<String> getCppCompilerOptions() {
    return cppCompilerOptions;
  }

  public ImmutableList<ExecutionRootPath> getBuiltInIncludeDirectories() {
    return builtInIncludeDirectories;
  }

  public ExecutionRootPath getCppExecutable() {
    return cppExecutable;
  }

  public String getTargetName() {
    return targetName;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for c toolchain */
  public static class Builder {
    private final ImmutableList.Builder<String> cCompilerOptions = ImmutableList.builder();
    private final ImmutableList.Builder<String> cppCompilerOptions = ImmutableList.builder();

    private final ImmutableList.Builder<ExecutionRootPath> builtInIncludeDirectories =
        ImmutableList.builder();

    ExecutionRootPath cppExecutable;

    String targetName = "";

    @CanIgnoreReturnValue
    public Builder addCCompilerOptions(Iterable<String> cCompilerOptions) {
      this.cCompilerOptions.addAll(cCompilerOptions);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addCppCompilerOptions(Iterable<String> cppCompilerOptions) {
      this.cppCompilerOptions.addAll(cppCompilerOptions);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addBuiltInIncludeDirectories(
        Iterable<ExecutionRootPath> builtInIncludeDirectories) {
      this.builtInIncludeDirectories.addAll(builtInIncludeDirectories);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setCppExecutable(ExecutionRootPath cppExecutable) {
      this.cppExecutable = cppExecutable;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setTargetName(String targetName) {
      this.targetName = targetName;
      return this;
    }

    public CToolchainIdeInfo build() {
      return new CToolchainIdeInfo(
          cCompilerOptions.build(),
          cppCompilerOptions.build(),
          builtInIncludeDirectories.build(),
          cppExecutable,
          targetName);
    }
  }

  @Override
  public String toString() {
    return "CToolchainIdeInfo{"
        + "\n"
        + "  cCompilerOptions="
        + getcCompilerOptions()
        + "\n"
        + "  cppCompilerOptions="
        + getCppCompilerOptions()
        + "\n"
        + "  builtInIncludeDirectories="
        + getBuiltInIncludeDirectories()
        + "\n"
        + "  cppExecutable='"
        + getCppExecutable()
        + '\''
        + "\n"
        + "  targetName='"
        + getTargetName()
        + '\''
        + "\n"
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CToolchainIdeInfo that = (CToolchainIdeInfo) o;
    return Objects.equal(getcCompilerOptions(), that.getcCompilerOptions())
        && Objects.equal(getCppCompilerOptions(), that.getCppCompilerOptions())
        && Objects.equal(getBuiltInIncludeDirectories(), that.getBuiltInIncludeDirectories())
        && Objects.equal(getCppExecutable(), that.getCppExecutable())
        && Objects.equal(getTargetName(), that.getTargetName());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        getcCompilerOptions(),
        getCppCompilerOptions(),
        getBuiltInIncludeDirectories(),
        getCppExecutable(),
        getTargetName());
  }
}
