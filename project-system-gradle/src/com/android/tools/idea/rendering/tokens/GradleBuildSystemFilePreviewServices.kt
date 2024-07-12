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

import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.rendering.BuildTargetReference
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile

/**
 * An implementation of [BuildSystemFilePreviewServices] for [GradleProjectSystem].
 */
class GradleBuildSystemFilePreviewServices : BuildSystemFilePreviewServices<GradleProjectSystem>, GradleToken {

  override val buildTargets: BuildSystemFilePreviewServices.BuildTargets = object: BuildSystemFilePreviewServices.BuildTargets {
    override fun from(module: Module, targetFile: VirtualFile): BuildTargetReference {
      return fromModuleOnly(module)
    }

    override fun fromModuleOnly(module: Module): BuildTargetReference {
      return GradleBuildTargetReference(module)
    }
  }
}

private data class GradleBuildTargetReference(override val module: Module): BuildTargetReference
