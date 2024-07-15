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
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildServices
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildTargets
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.openapi.vfs.VirtualFile
import kotlin.reflect.jvm.jvmName

/**
 * An implementation of [BuildSystemFilePreviewServices] for [GradleProjectSystem].
 */
class GradleBuildSystemFilePreviewServices : BuildSystemFilePreviewServices<GradleProjectSystem, GradleBuildTargetReference>, GradleToken {
  override fun isApplicable(buildTargetReference: BuildTargetReference): Boolean = buildTargetReference is GradleBuildTargetReference

  override val buildTargets: BuildTargets = object: BuildTargets {
    override fun from(module: Module, targetFile: VirtualFile): BuildTargetReference {
      return fromModuleOnly(module)
    }

    override fun fromModuleOnly(module: Module): BuildTargetReference {
      return GradleBuildTargetReference(module)
    }
  }

  override val buildServices: BuildServices<GradleBuildTargetReference> = object: BuildServices<GradleBuildTargetReference> {
    override fun getLastCompileStatus(buildTarget: GradleBuildTargetReference): ProjectSystemBuildManager.BuildStatus {
      return getBuildServicesStatus(buildTarget).lastCompileStatus
    }
  }

  private fun getBuildServicesStatus(buildTarget: GradleBuildTargetReference): GradleBuildServicesStatus {
    val module = buildTarget.module
    return module.getOrCreateUserData(GradleBuildServicesStatus.KEY) { GradleBuildServicesStatus(module) }
  }
}

/**
 * An implementation of [BuildSystemFilePreviewServices.BuildServices] for a [GradleBuildTargetReference].
 *
 * In Gradle a build target (at least for the purpose of building artifacts for rendering) is represented in the IDE as an IDE module. For
 * production code it is the `mainModule` of the module group that represents a Gradle project (i.e. `:app` etc.)
 *
 * [GradleBuildServices] holds a reference to the [module] that represents the build target the services are supposed to handle.
 */
private class GradleBuildServicesStatus(private val module: Module) {
  private val projectSystem = module.project.getProjectSystem()
  val lastCompileStatus: ProjectSystemBuildManager.BuildStatus
    get() {
      val last = projectSystem.getBuildManager().getLastBuildResult()
      return when (last.mode) {
        ProjectSystemBuildManager.BuildMode.UNKNOWN -> ProjectSystemBuildManager.BuildStatus.UNKNOWN
        ProjectSystemBuildManager.BuildMode.COMPILE_OR_ASSEMBLE -> last.status
        ProjectSystemBuildManager.BuildMode.CLEAN -> ProjectSystemBuildManager.BuildStatus.UNKNOWN
      }
    }

  companion object {
    val KEY = Key.create<GradleBuildServicesStatus>(GradleBuildServicesStatus::class.jvmName)
  }
}

data class GradleBuildTargetReference internal constructor(override val module: Module) : BuildTargetReference
