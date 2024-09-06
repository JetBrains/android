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

import com.android.tools.idea.projectsystem.ProjectSystemBuildManager;
import com.android.tools.idea.rendering.BuildTargetReference;
import com.google.idea.blaze.android.projectsystem.BazelProjectSystem;
import com.google.idea.blaze.android.projectsystem.BazelToken;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;


public class BazelBuildSystemFilePreviewServices
  implements BuildSystemFilePreviewServices<BazelProjectSystem, BazelBuildTargetReference>, BazelToken {

  @Override
  public boolean isApplicable(BuildTargetReference buildTargetReference) {
    return buildTargetReference instanceof BazelBuildTargetReference;
  }

  @Override
  public BuildServices<BazelBuildTargetReference> getBuildServices() {
    return new BuildServices<>() {
      @Override
      public @NotNull ProjectSystemBuildManager.BuildStatus getLastCompileStatus(
        @NotNull BazelBuildTargetReference buildTarget) {
        return ProjectSystemBuildManager.BuildStatus.UNKNOWN;
      }

      @Override
      public void buildArtifacts(
        @NotNull Collection<? extends BazelBuildTargetReference> buildTargets) {
        throw new UnsupportedOperationException();
      }
    };
  }

  @Override
  public void subscribeBuildListener(Project project,
                                     Disposable parentDisposable, BuildListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public BuildTargets getBuildTargets() {
    return new BuildTargets() {
      @Override
      public @NotNull BuildTargetReference from(@NotNull Module module,
                                                @NotNull VirtualFile targetFile) {
        return fromModuleOnly(module);
      }

      @Override
      public @NotNull BuildTargetReference fromModuleOnly(@NotNull Module module) {
        return new BazelBuildTargetReference(module);
      }
    };
  }
}