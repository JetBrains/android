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

import com.google.auto.value.AutoValue;
import java.util.Optional;

/** Information about a target that was extracted from the build at dependencies build time. */
@AutoValue
public abstract class TargetBuildInfo {
  public static final TargetBuildInfo EMPTY =
      builder().buildContext(DependencyBuildContext.NONE).build();

  public abstract Optional<JavaArtifactInfo> javaInfo();

  public abstract Optional<CcCompilationInfo> ccInfo();

  public abstract DependencyBuildContext buildContext();

  public static TargetBuildInfo forJavaTarget(
      JavaArtifactInfo javaInfo, DependencyBuildContext buildContext) {
    return builder().buildContext(buildContext).javaInfo(javaInfo).build();
  }

  public static TargetBuildInfo forCcTarget(
      CcCompilationInfo targetInfo, DependencyBuildContext buildContext) {
    return builder().buildContext(buildContext).ccInfo(targetInfo).build();
  }

  static Builder builder() {
    return new AutoValue_TargetBuildInfo.Builder();
  }

  /** Builder for {@link TargetBuildInfo}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder javaInfo(JavaArtifactInfo javaInfo);

    public abstract Builder ccInfo(CcCompilationInfo ccInfo);

    public abstract Builder buildContext(DependencyBuildContext buildContext);

    public abstract TargetBuildInfo build();
  }
}
