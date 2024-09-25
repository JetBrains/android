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
package org.jetbrains.kotlin.android.configure

//import com.android.tools.idea.apk.ApkFacet
import com.intellij.facet.FacetManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleSourceRootGroup
import org.jetbrains.kotlin.idea.configuration.ConfigureKotlinStatus
import org.jetbrains.kotlin.idea.configuration.KotlinProjectConfigurator
import org.jetbrains.kotlin.idea.projectConfiguration.LibraryJarDescriptor
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms

class KotlinApkModuleConfigurator : KotlinProjectConfigurator {
    override val name = "android-apk"
    override val presentableText = "Android APK"
    override val targetPlatform = JvmPlatforms.defaultJvmPlatform

    override fun addLibraryDependency(module: Module,
                                      element: PsiElement,
                                      library: ExternalLibraryDescriptor,
                                      libraryJarDescriptor: LibraryJarDescriptor,
                                      scope: DependencyScope) {
        return
    }

    override fun changeGeneralFeatureConfiguration(module: Module,
                                                   feature: LanguageFeature,
                                                   state: LanguageFeature.State,
                                                   forTests: Boolean) {
        return
    }

    override fun configure(project: Project, excludeModules: Collection<Module>) {
        return
    }

    override fun getStatus(moduleSourceRootGroup: ModuleSourceRootGroup) =
      when {
          isApplicable(moduleSourceRootGroup.baseModule) -> ConfigureKotlinStatus.CONFIGURED
          else -> ConfigureKotlinStatus.NON_APPLICABLE
      }

    // TODO(b/320447078): this might be better either as a Project System call of some kind (though testing for identity is not something
    //  generally supported), or, like in KotlinWithGradleConfigurator, maybe should test for the name of the facet ("APK" in this case).
    //  We will have to revisit this as and when the ApkProjectSystem is integrated.
    override fun isApplicable(module: Module) = false//FacetManager.getInstance(module).allFacets.any { it is ApkFacet }

    override fun updateLanguageVersion(module: Module,
                                       languageVersion: String?,
                                       apiVersion: String?,
                                       requiredStdlibVersion: ApiVersion,
                                       forTests: Boolean) {
        return
    }
}