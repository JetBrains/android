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
package com.android.tools.idea.run.deployment.liveedit.tokens

import com.android.tools.idea.projectsystem.ApplicationProjectContext
import com.android.tools.idea.run.classes.BuildOutcome
import com.google.idea.blaze.android.projectsystem.BazelProjectSystem
import com.google.idea.blaze.android.projectsystem.BazelToken
import com.google.idea.blaze.android.run.BazelApplicationProjectContext
import com.google.idea.blaze.common.Label
import com.google.idea.common.experiments.BoolExperiment

class BazelBuildSystemLiveEditServices :  BuildSystemLiveEditServices<BazelProjectSystem, BazelApplicationProjectContext>, BazelToken {

  override fun isApplicable(
    applicationProjectContext: ApplicationProjectContext
  ): Boolean {
    return (applicationProjectContext as? BazelApplicationProjectContext)?.liveEditDataExtractor != null
  }

  override fun getApplicationServices(
    bazelApplicationProjectContext: BazelApplicationProjectContext
  ): ApplicationLiveEditServices {
    val liveEditData = bazelApplicationProjectContext.liveEditDataExtractor ?: error("Internal error: liveEditDataExtractor == null")
    return BazelApplicationLiveEditServices(
      project = bazelApplicationProjectContext.project,
      buildOutcomeProvider = liveEditData::getBuildOutcomeBlocking
    )
  }
  override fun disqualifyingBytecodeTransformation(
    bazelApplicationProjectContext: BazelApplicationProjectContext
  ): BuildSystemBytecodeTransformation? = null
}
