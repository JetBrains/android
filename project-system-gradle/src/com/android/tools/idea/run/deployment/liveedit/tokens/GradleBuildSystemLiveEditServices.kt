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
package com.android.tools.idea.run.deployment.liveedit.tokens

import com.android.tools.idea.project.FacetBasedApplicationProjectContext
import com.android.tools.idea.projectsystem.ApplicationProjectContext
import com.android.tools.idea.projectsystem.ClassContent
import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.gradle.GradleClassFileFinder
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.projectsystem.gradle.isAndroidTestModule
import com.android.tools.idea.run.deployment.liveedit.setOptions
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2MetadataCompilerArguments
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.psi.KtFile

class GradleBuildSystemLiveEditServices :
  BuildSystemLiveEditServices<GradleProjectSystem, FacetBasedApplicationProjectContext>,
  GradleToken {

  override fun isApplicable(applicationProjectContext: ApplicationProjectContext): Boolean {
    return applicationProjectContext is FacetBasedApplicationProjectContext &&
      applicationProjectContext.facet.module.project.getProjectSystem() is GradleProjectSystem
  }

  override fun getApplicationServices(applicationProjectContext: FacetBasedApplicationProjectContext): ApplicationLiveEditServices {
    return GradleApplicationLiveEditServices(applicationProjectContext.facet.module)
  }
}

internal class GradleApplicationLiveEditServices(module: Module): ApplicationLiveEditServices {
  val classFileFinder = GradleClassFileFinder.createWithoutTests(module)

  override fun getClassContent(
    file: VirtualFile,
    className: String,
  ): ClassContent? {
    return classFileFinder.findClassFile(className)
  }

  override fun getKotlinCompilerConfiguration(ktFile: KtFile): CompilerConfiguration {
    val module = ktFile.module ?: return CompilerConfiguration.EMPTY
    val compilerConfiguration = CompilerConfiguration().apply<CompilerConfiguration> {
      put(
        CommonConfigurationKeys.MODULE_NAME,
        module.name
      )
      KotlinFacet.get(module)?.let { kotlinFacet ->
        val moduleName = when (val compilerArguments = kotlinFacet.configuration.settings.compilerArguments) {
          is K2JVMCompilerArguments -> compilerArguments.moduleName
          is K2MetadataCompilerArguments -> compilerArguments.moduleName
          else -> null
        }
        moduleName?.let {
          put(CommonConfigurationKeys.MODULE_NAME, it)
        }
      }
      setOptions(ktFile.languageVersionSettings)
    }
    return compilerConfiguration
  }
}