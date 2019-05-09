/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade

import com.android.builder.model.*
import com.android.ide.common.gradle.model.IdeVariant
import com.android.ide.common.gradle.model.UnusedModelMethodException
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.androidproject.AndroidProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.Variant
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade.stubs.BuildTypeContainerStub
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade.stubs.BuildTypeStub
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade.stubs.LintOptionsStub
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade.stubs.ProductFlavorContainerStub
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.*
import java.io.File
import java.util.function.Consumer

open class LegacyAndroidProject(private val androidProject: AndroidProject, private val variant: Variant) : OldAndroidProject {
  override fun getModelVersion(): String = androidProject.modelVersion
  override fun getApiVersion(): Int = 3
  override fun getName(): String = androidProject.name
  override fun getProjectType(): Int = androidProject.projectType.oldValue
  override fun getVariantNames(): Collection<String> = androidProject.variantNames
  override fun getDefaultVariant(): String?  = androidProject.defaultVariant
  override fun getCompileTarget(): String = androidProject.compileTarget
  override fun getBootClasspath(): Collection<String> = androidProject.bootClasspath.map(File::getAbsolutePath)
  override fun getAaptOptions(): OldAaptOptions = LegacyAaptOptions(androidProject.aaptOptions)
  override fun getSyncIssues(): Collection<SyncIssue> = androidProject.syncIssues
  override fun getJavaCompileOptions(): JavaCompileOptions = LegacyJavaCompileOptions(androidProject.javaCompileOptions)
  override fun getBuildFolder(): File = androidProject.buildFolder
  override fun isBaseSplit(): Boolean = androidProject.isBaseSplit
  override fun getSigningConfigs(): Collection<OldSigningConfig> = androidProject.signingConfigs.map { LegacySigningConfig(it) }
  override fun getDynamicFeatures(): Collection<String> = androidProject.dynamicFeatures
  override fun getVariants(): Collection<com.android.builder.model.Variant> = listOf(LegacyVariant(variant))

  @Deprecated("use project type", ReplaceWith("getProjectType()"))
  override fun isLibrary(): Boolean = androidProject.projectType == AndroidProject.ProjectType.LIBRARY

  override fun getNativeToolchains(): Collection<NativeToolchain> = TODO("native model support")

  override fun getBuildTypes(): Collection<BuildTypeContainer> = throw UnusedModelMethodException("getBuildTypes")
  override fun getProductFlavors(): Collection<ProductFlavorContainer> = throw UnusedModelMethodException("getProductFlavors")
  override fun getDefaultConfig(): ProductFlavorContainer = throw UnusedModelMethodException("getDefaultConfig")
  override fun getLintOptions(): LintOptions = throw UnusedModelMethodException("getLintOptions") // will be replaced with a separate model
  override fun getFlavorDimensions(): Collection<String> = throw UnusedModelMethodException("getFlavorDimensions")
  override fun getPluginGeneration(): Int = throw UnusedModelMethodException("getPluginGeneration")
  override fun getResourcePrefix(): String? = throw UnusedModelMethodException("getResourcePrefix") // use namespacing instead
  override fun getBuildToolsVersion(): String = throw UnusedModelMethodException("getBuildToolsVersion")
  @Deprecated("use sync issues", ReplaceWith("getSyncIssues()"))
  override fun getUnresolvedDependencies(): Collection<String> = throw UnusedModelMethodException("getUnresolvedDependencies")

  override fun getParsedModelVersion(): GradleVersion? = throw UnusedModelMethodException("getParsedModelVersion")
  override fun forEachVariant(action: Consumer<IdeVariant>) = throw UnusedModelMethodException("forEachVariant")
  override fun addVariants(variants: MutableCollection<com.android.builder.model.Variant>, factory: IdeDependenciesFactory) = throw UnusedModelMethodException("addVariants")
  override fun addSyncIssues(syncIssues: MutableCollection<SyncIssue>)  = throw UnusedModelMethodException("addSyncIssues")

  override fun toString(): String = "LegacyAndroidProject{" +
                                    "modelVersion=$modelVersion," +
                                    "apiVersion=$apiVersion," +
                                    "name=$name," +
                                    "projectType=$projectType," +
                                    "variantNames=$variantNames," +
                                    "defaultVariant=$defaultVariant," +
                                    "compileTarget=$compileTarget," +
                                    "bootClasspath=$bootClasspath," +
                                    "aaptOptions=$aaptOptions," +
                                    "syncIssues=$syncIssues," +
                                    "javaCompileOptions=$javaCompileOptions," +
                                    "buildFolder=$buildFolder," +
                                    "isBaseSplit=$isBaseSplit," +
                                    "signingConfigs=$signingConfigs," +
                                    "dynamicFeatures=$dynamicFeatures," +
                                    "variants=$variants" +
                                    // TODO(qumeric): "nativeToolchains=$nativeToolchains," +
                                    "}"

}

class LegacyAndroidProjectStub(
  private val androidProject: AndroidProject,
  private val variant: Variant
) : LegacyAndroidProject(androidProject, variant) {
  override fun getAaptOptions(): OldAaptOptions = LegacyAaptOptionsStub(androidProject.aaptOptions)
  override fun getSigningConfigs(): Collection<OldSigningConfig> = androidProject.signingConfigs.map { LegacySigningConfigStub(it) }
  override fun getLintOptions(): LintOptions = LintOptionsStub()
  override fun getVariants(): Collection<com.android.builder.model.Variant> = listOf(LegacyVariantStub(variant))

  override fun getDefaultConfig(): ProductFlavorContainer = ProductFlavorContainerStub(
    LegacyProductFlavorStub(variant.variantConfig),
    LegacySourceProvider(variant.mainArtifact.mergedSourceProvider.defaultSourceSet),
    listOfNotNull(variant.androidTestArtifact?.toSourceProviderContainerForDefaultConfig(),
                  variant.unitTestArtifact?.toSourceProviderContainerForDefaultConfig())
  )

  override fun getBuildTypes(): Collection<BuildTypeContainer> = listOf(BuildTypeContainerStub(
    BuildTypeStub(variant.variantConfig),
    LegacySourceProvider(variant.mainArtifact.mergedSourceProvider.buildTypeSourceSet),
    listOfNotNull(variant.androidTestArtifact?.toSourceProviderContainerForBuildType(),
                  variant.unitTestArtifact?.toSourceProviderContainerForBuildType())
  ))

  override fun getProductFlavors(): Collection<ProductFlavorContainer> = listOf()
  override fun getBuildToolsVersion(): String = "buildToolsVersion" // TODO(qumeric) Get actual value instead
  override fun getFlavorDimensions(): Collection<String> = listOf()
  override fun getNativeToolchains(): Collection<NativeToolchain> = listOf() // TODO(qumeric): remove when native libraries are supported
  override fun getResourcePrefix(): String? = null
  override fun getPluginGeneration(): Int = com.android.builder.model.AndroidProject.GENERATION_ORIGINAL
  @Deprecated("use sync issues instead", ReplaceWith("getSyncIssues()"))
  override fun getUnresolvedDependencies(): Collection<String> = listOf()
}
