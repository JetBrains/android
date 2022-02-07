/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.android.configure

import com.android.tools.idea.gradle.model.IdeModuleSourceSet
import com.android.tools.idea.gradle.project.sync.IdeAndroidModels
import com.android.utils.appendCapitalized
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.externalSystem.util.Order
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinSourceSetData
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMPPGradleProjectResolver
import org.jetbrains.kotlin.idea.gradleJava.configuration.getMppModel
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModelBuilder
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilation
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet
import org.jetbrains.kotlin.idea.projectModel.KotlinTarget
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

@Order(ExternalSystemConstants.UNORDERED - 1)
class KotlinAndroidMPPGradleProjectResolver : AbstractProjectResolverExtension() {
  override fun getToolingExtensionsClasses(): Set<Class<out Any>> {
    return setOf(KotlinMPPGradleModelBuilder::class.java, KotlinTarget::class.java, Unit::class.java)
  }

  override fun getExtraProjectModelClasses(): Set<Class<out Any>> {
    return setOf(KotlinMPPGradleModel::class.java, KotlinTarget::class.java)
  }

  override fun createModule(gradleModule: IdeaModule, projectDataNode: DataNode<ProjectData>): DataNode<ModuleData> {
    return super.createModule(gradleModule, projectDataNode)!!.also {
      maybeAttachKotlinSourceSetData(gradleModule, it)
    }
  }

  private fun maybeAttachKotlinSourceSetData(
    gradleModule: IdeaModule,
    ideModule: DataNode<ModuleData>
  ) {
    val mppModel = resolverCtx.getMppModel(gradleModule) ?: return
    val androidModels = resolverCtx.getExtraProject(gradleModule, IdeAndroidModels::class.java) ?: return
    val selectedVariantName = androidModels.selectedVariantName
    val sourceSetByName = ideModule.sourceSetsByName()

    for ((sourceSetDesc, compilation) in mppModel.androidCompilationsForVariant(selectedVariantName)) {
      val kotlinSourceSetInfo = KotlinMPPGradleProjectResolver.createSourceSetInfo(compilation, gradleModule, resolverCtx) ?: continue
      val androidGradleSourceSetDataNode = sourceSetByName[sourceSetDesc.sourceSetName] ?: continue

      androidGradleSourceSetDataNode.createChild(KotlinSourceSetData.KEY, KotlinSourceSetData(kotlinSourceSetInfo))
    }
  }

  override fun populateModuleDependencies(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>, ideProject: DataNode<ProjectData>) {
    super.populateModuleDependencies(gradleModule, ideModule, ideProject)

    val mppModel = resolverCtx.getMppModel(gradleModule) ?: return
    val androidModels = resolverCtx.getExtraProject(gradleModule, IdeAndroidModels::class.java) ?: return
    val selectedVariantName = androidModels.selectedVariantName
    val sourceSetByName = ideModule.sourceSetsByName()

    for ((sourceSetDesc, compilation) in mppModel.androidCompilationsForVariant(selectedVariantName)) {
      val androidGradleSourceSetDataNode = sourceSetByName[sourceSetDesc.sourceSetName] ?: continue
      val kotlinSourceSet = sourceSetDesc.getRootKotlinSourceSet(compilation) ?: continue

      for (dependsOn in kotlinSourceSet.declaredDependsOnSourceSets) {
        val dependsOnGradleSourceSet = sourceSetByName[dependsOn] ?: continue
        androidGradleSourceSetDataNode.createChild(
          ProjectKeys.MODULE_DEPENDENCY,
          ModuleDependencyData(androidGradleSourceSetDataNode.data, dependsOnGradleSourceSet.data).also {
            // Set up dependencies as exported since when an Android module depends on MPP module it depends on its `main` module only.
            it.isExported = true
          }
        )
      }
    }
  }
}

/**
 * Returns all Android compilations for the given [variant].
 */
private fun KotlinMPPGradleModel.androidCompilationsForVariant(
  variant: String
): List<Pair<IdeModuleSourceSet, KotlinCompilation>> {
  return targets
    .asSequence()
    .flatMap { it.compilations.asSequence() }
    .filter { it.platform == KotlinPlatform.ANDROID }
    .mapNotNull { androidKotlinCompilation ->
      val sourceSet =
        IdeModuleSourceSet.values().find { variant + it.androidCompilationNameSuffix() == androidKotlinCompilation.name }
          ?: return@mapNotNull null
      sourceSet to androidKotlinCompilation
    }
    .toList()
}

private fun DataNode<ModuleData>.sourceSetsByName(): Map<String, DataNode<GradleSourceSetData>> {
  return ExternalSystemApiUtil.findAll(this, GradleSourceSetData.KEY).associateBy { it.data.moduleName }
}

/**
 * Returns the main [KotlinSourceSet] representing this [IdeModuleSourceSet] in [compilation].
 *
 * Usually there are multiple Kotlin source sets representing one [IdeModuleSourceSet]. For example, for [IdeModuleSourceSet.ANDROID_TEST]
 * there might be `androidAndroidTest` and `androidAndroidTestDebug`.
 */
private fun IdeModuleSourceSet.getRootKotlinSourceSet(compilation: KotlinCompilation): KotlinSourceSet? {
  val sourceSetName = compilation.disambiguationClassifier.orEmpty().appendCapitalized(sourceSetName)
  return compilation.declaredSourceSets.singleOrNull { it.name == sourceSetName }
}

private fun IdeModuleSourceSet.androidCompilationNameSuffix() = when (this) {
  IdeModuleSourceSet.MAIN -> ""
  IdeModuleSourceSet.ANDROID_TEST -> "AndroidTest"
  IdeModuleSourceSet.UNIT_TEST -> "UnitTest"
  IdeModuleSourceSet.TEST_FIXTURES -> "TestFixtures"
}
