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
package com.android.tools.idea.rendering.tokens;

import com.android.tools.idea.rendering.BuildTargetReference;
import com.android.tools.idea.run.deployment.liveedit.tokens.ApplicationLiveEditServices;
import com.google.idea.blaze.android.projectsystem.BazelProjectSystem;
import com.google.idea.blaze.android.projectsystem.BazelToken;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

final class BazelBuildSystemFilePreviewServices
  implements BuildSystemFilePreviewServices<BazelProjectSystem, BazelBuildTargetReference>, BazelToken {

  private final BazelBuildServices buildServices = new BazelBuildServices();

  @Override
  public boolean isApplicable(@NotNull BuildTargetReference buildTargetReference) {
    return buildTargetReference instanceof BazelBuildTargetReference;
  }

  @Override
  public @NotNull BuildServices<@NotNull BazelBuildTargetReference> getBuildServices() {
    return buildServices;
  }

  @Override
  public @NotNull RenderingServices getRenderingServices(
    @NotNull BazelBuildTargetReference buildTargetReference) {
    return new BazelRenderingServices();
  }

  @Override
  public @NotNull ApplicationLiveEditServices getApplicationLiveEditServices(
    @NotNull BazelBuildTargetReference buildTargetReference) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void subscribeBuildListener(@NotNull Project project, @NotNull Disposable parent, @NotNull BuildListener listener) {
    buildServices.add(listener);
    Disposer.register(parent, () -> buildServices.remove(listener));
  }

  @Override
  public @NotNull BuildTargets getBuildTargets() {
    return new BazelBuildTargets();
  }
}
