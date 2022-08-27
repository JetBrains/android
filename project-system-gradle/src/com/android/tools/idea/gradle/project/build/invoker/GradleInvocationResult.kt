/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.project.build.invoker

import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.gradle.util.GradleUtil
import org.gradle.tooling.BuildCancelledException
import org.jetbrains.annotations.TestOnly
import java.io.File

interface GradleBuildResult {
  val isBuildSuccessful: Boolean
  val isBuildCancelled: Boolean
}

class GradleInvocationResult @JvmOverloads constructor(
  val rootProjectPath: File,
  val tasks: List<String>,

  /**
   * In production, the build error is intentionally wrapped, with relevant information exposed
   * only through a public API, but for tests it could be useful to access it directly.
   */
  @get:TestOnly val buildError: Throwable?,

  val model: Any? = null
) : GradleBuildResult {

  override val isBuildCancelled: Boolean get() = buildError != null && GradleUtil.hasCause(buildError, BuildCancelledException::class.java)
  override val isBuildSuccessful: Boolean get() = buildError == null
}

class GradleMultiInvocationResult(
  val invocations: List<GradleInvocationResult>
) : GradleBuildResult {
  override val isBuildSuccessful: Boolean get() = invocations.all { it.isBuildSuccessful }
  override val isBuildCancelled: Boolean get() = invocations.all { it.isBuildCancelled }

  val models: List<Any> get() = invocations.mapNotNull { it.model }
}

class AssembleInvocationResult(
  val invocationResult: GradleMultiInvocationResult,
  val buildMode: BuildMode
) : GradleBuildResult by invocationResult
