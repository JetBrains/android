/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.deps

import com.google.idea.blaze.common.Label

/** Information about a target that was extracted from the build at dependencies build time. */
sealed class TargetBuildInfo {
  abstract val label: Label
  abstract val buildContext: DependencyBuildContext

  data class Java(val javaInfo: JavaArtifactInfo, override val buildContext: DependencyBuildContext) : TargetBuildInfo() {
    override val label: Label
      get() = javaInfo.label
  }

  data class Cc(val ccInfo: CcCompilationInfo, override val buildContext: DependencyBuildContext) : TargetBuildInfo() {
    override val label: Label
      get() = ccInfo.target
  }

  companion object {
    @JvmStatic
    fun forJavaTarget(javaInfo: JavaArtifactInfo, buildContext: DependencyBuildContext): TargetBuildInfo {
      return Java(javaInfo, buildContext)
    }

    @JvmStatic
    fun forCcTarget(ccInfo: CcCompilationInfo, buildContext: DependencyBuildContext): TargetBuildInfo {
      return Cc(ccInfo, buildContext)
    }
  }
}
