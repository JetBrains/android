/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.rendering.tokens

import com.android.tools.idea.project.DefaultProjectSystem
import com.android.tools.idea.project.DefaultToken
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildServices
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildTargets
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * An implementation of [BuildSystemFilePreviewServices] for the [DefaultProjectSystem].
 *
 * It supports basic features only. Builds are not available in the default project system.
 */
class DefaultBuildSystemFilePreviewServices : BuildSystemFilePreviewServices<DefaultProjectSystem, DefaultBuildTargetReference>, DefaultToken {
  override fun isApplicable(buildTargetReference: BuildTargetReference): Boolean = buildTargetReference is DefaultBuildTargetReference

  override val buildTargets: BuildTargets = object : BuildTargets {
    override fun from(module: Module, targetFile: VirtualFile): BuildTargetReference {
      return DefaultBuildTargetReference(module)
    }

    override fun fromModuleOnly(module: Module): BuildTargetReference {
      return DefaultBuildTargetReference(module)
    }
  }

  /**
   * An implementation of [BuildSystemFilePreviewServices.BuildServices] for a [DefaultBuildTargetReference].
   *
   * Builds are not supported in the [DefaultProjectSystem].
   */
  override val buildServices = object : BuildServices<DefaultBuildTargetReference> {
    override fun getLastCompileStatus(buildTarget: DefaultBuildTargetReference): ProjectSystemBuildManager.BuildStatus {
      return ProjectSystemBuildManager.BuildStatus.UNKNOWN
    }
  }

  override fun subscribeBuildListener(
    project: Project,
    parentDisposable: Disposable,
    listener: BuildSystemFilePreviewServices.BuildListener
  ) {
    // Do nothing. There are no builds in the DefaultProjectSystem.
  }
}

data class DefaultBuildTargetReference internal constructor(override val module: Module) : BuildTargetReference