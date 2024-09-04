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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.qsync.java.cc.CcCompilationInfoOuterClass.CcToolchainInfo;
import com.google.idea.blaze.qsync.project.ProjectPath;

/**
 * Information about a C/C++ toolchain. The information is extracted from the build at build deps
 * time.
 */
@AutoValue
public abstract class CcToolchain {

  /** Unique ID for this toolchain. */
  public abstract String id();

  /** The compiler name, as reported by bazel API {@code CcToolchainInfo.compiler}. */
  public abstract String compiler();

  /**
   * Path to the compiler executable, as reported by bazel API {@code
   * CcToolchainInfo.compiler_executable}.
   */
  public abstract ProjectPath compilerExecutable();

  /** Target CPU of the C++ toolchain, as reported by bazel API {@code CcToolchainInfo.cpu}. */
  public abstract String cpu();

  /** As reported by bazel API {@code CcToolchainInfo.target_gnu_system_name}. */
  public abstract String targetGnuSystemName();

  /** As reported by bazel API {@code CcToolchainInfo.built_in_include_directories}. */
  public abstract ImmutableList<ProjectPath> builtInIncludeDirectories();

  /**
   * C compiler options, as reported by bazel API {@code
   * cc_common.get_memory_inefficient_command_line} for {@code action_name=C_COMPILE_ACTION_NAME}.
   */
  public abstract ImmutableList<String> cOptions();

  /**
   * C++ compiler options, as reported by bazel API {@code
   * cc_common.get_memory_inefficient_command_line} for {@code action_name=CPP_COMPILE_ACTION_NAME}.
   */
  public abstract ImmutableList<String> cppOptions();

  public static Builder builder() {
    return new AutoValue_CcToolchain.Builder();
  }

  public static CcToolchain create(CcToolchainInfo proto) {
    return builder()
        .id(proto.getId())
        .compiler(proto.getCompiler())
        .compilerExecutable(ProjectPath.workspaceRelative(proto.getCompilerExecutable()))
        .cpu(proto.getCpu())
        .targetGnuSystemName(proto.getTargetName())
        .builtInIncludeDirectories(
            proto.getBuiltInIncludeDirectoriesList().stream()
                .map(ArtifactDirectories::forCcInclude)
                .collect(toImmutableList()))
        .cOptions(ImmutableList.copyOf(proto.getCOptionsList()))
        .cppOptions(ImmutableList.copyOf(proto.getCppOptionsList()))
        .build();
  }

  /** Builder for {@link CcToolchain}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder id(String value);

    public abstract Builder compiler(String value);

    public abstract Builder compilerExecutable(ProjectPath value);

    public abstract Builder cpu(String value);

    public abstract Builder targetGnuSystemName(String value);

    public abstract Builder builtInIncludeDirectories(ImmutableList<ProjectPath> value);

    public abstract Builder builtInIncludeDirectories(ProjectPath... value);

    public abstract Builder cOptions(ImmutableList<String> value);

    public abstract Builder cOptions(String... value);

    public abstract Builder cppOptions(ImmutableList<String> value);

    public abstract Builder cppOptions(String... value);

    public abstract CcToolchain build();
  }
}
