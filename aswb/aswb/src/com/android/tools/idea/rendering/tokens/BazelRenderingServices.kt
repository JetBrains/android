/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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

package com.android.tools.idea.rendering.tokens

import com.android.tools.idea.projectsystem.ClassFileFinder
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.RenderingServices
import com.android.tools.idea.run.classes.BuildOutcome
import java.nio.file.Path

internal class BazelRenderingServices(
  private val buildServices: BazelBuildServices,
  private val target: BazelBuildTargetReference
) : RenderingServices {
  override val classFileFinder: ClassFileFinder?
    get() = getBuildOutcome()?.classFileFinder

  override val externalLibraries: Collection<Path>
    get() = getBuildOutcome()?.externalJars ?: emptyList()

  private fun getBuildOutcome(): BuildOutcome? {
    val label = target.toPreferredLabel() ?: return null
    return buildServices.getBuildOutcome(label)
  }
}
