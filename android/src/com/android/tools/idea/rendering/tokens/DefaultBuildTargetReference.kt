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
import com.android.tools.idea.projectsystem.ClassContent
import com.android.tools.idea.projectsystem.ClassFileFinder
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildServices
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildTargets
import com.android.tools.idea.run.deployment.liveedit.tokens.ApplicationLiveEditServices
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.application
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtFile

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

    override fun buildArtifacts(buildTargets: Collection<DefaultBuildTargetReference>) {
      error("Building artifacts for rendering is not supported in this project")
    }
  }

  override fun getRenderingServices(buildTargetReference: DefaultBuildTargetReference): BuildSystemFilePreviewServices.RenderingServices {
    return object: BuildSystemFilePreviewServices.RenderingServices {
      override val classFileFinder: ClassFileFinder?
        get() {
          val module = buildTargetReference.moduleIfNotDisposed ?: return null
          val moduleSystem = module.getModuleSystem()
          return moduleSystem.moduleClassFileFinder
        }
    }
  }

  override fun getApplicationLiveEditServices(buildTargetReference: DefaultBuildTargetReference): ApplicationLiveEditServices {
    if (application.isUnitTestMode) {
      return ApplicationLiveEditServices.LegacyForTests(buildTargetReference.project);
    }
    return object: ApplicationLiveEditServices {
      override fun getClassContent(
        file: VirtualFile,
        className: String,
      ): ClassContent? = null

      override fun getKotlinCompilerConfiguration(ktFile: KtFile): CompilerConfiguration = CompilerConfiguration.EMPTY
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

data class DefaultBuildTargetReference internal constructor(private val moduleRef: Module) : BuildTargetReference {
  override val moduleIfNotDisposed: Module?
    get() = moduleRef.takeUnless { it.isDisposed }
  override val module: Module
    get() = moduleIfNotDisposed ?: throw AlreadyDisposedException("Already disposed: $moduleRef")
}