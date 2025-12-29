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
package com.android.tools.idea.rendering.tokens

import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.RenderingServices
import com.android.tools.idea.run.deployment.liveedit.tokens.ApplicationLiveEditServices
import com.google.idea.blaze.android.projectsystem.BazelProjectSystem
import com.google.idea.blaze.android.projectsystem.BazelToken
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

internal class BazelBuildSystemFilePreviewServices
  : BuildSystemFilePreviewServices<BazelProjectSystem, BazelBuildTargetReference>, BazelToken {
  override val buildServices: BazelBuildServices = BazelBuildServices()

  override fun isApplicable(buildTargetReference: BuildTargetReference): Boolean {
    return buildTargetReference is BazelBuildTargetReference
  }

  override fun getRenderingServices(target: BazelBuildTargetReference): RenderingServices {
    return buildServices.getRenderingServices(target)
  }

  override fun getApplicationLiveEditServices(buildTargetReference: BazelBuildTargetReference): ApplicationLiveEditServices {
    return BazelApplicationLiveEditServices(buildTargetReference, buildServices)
  }

  override fun subscribeBuildListener(
    project: Project,
    parentDisposable: Disposable,
    listener: BuildSystemFilePreviewServices.BuildListener
  ) {
    buildServices.add(listener)
    Disposer.register(parentDisposable) { buildServices.remove(listener) }
  }

  override val buildTargets: BuildSystemFilePreviewServices.BuildTargets = BazelBuildTargets()
}
