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
import com.intellij.openapi.roots.DependencyScope
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinSourceSetData
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMPPGradleProjectResolver
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMPPGradleProjectResolver.Companion.createContentRootData
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMPPGradleProjectResolver.Companion.createExternalSourceSet
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMPPGradleProjectResolver.Companion.createGradleSourceSetData
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMPPGradleProjectResolver.Companion.resourceType
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMPPGradleProjectResolver.Companion.sourceType
import org.jetbrains.kotlin.idea.gradleJava.configuration.getMppModel
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.KotlinModuleUtils
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModelBuilder
import org.jetbrains.kotlin.idea.gradleTooling.resolveAllDependsOnSourceSets
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilation
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet
import org.jetbrains.kotlin.idea.projectModel.KotlinTarget
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver
import com.intellij.openapi.util.Pair as IJPair

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
    val sourceSetByName = ideModule.sourceSetsByName()
    val projectDataNode = ExternalSystemApiUtil.findParent(ideModule, ProjectKeys.PROJECT) ?: return
    val sourceSetMap = projectDataNode.getUserData(GradleProjectResolver.RESOLVED_SOURCE_SETS) ?: return

    val androidCompilations = mppModel.androidCompilationsForVariant(androidModels.selectedVariantName)

    /*
    Create 'KotlinSourceSetData' for each compilation (main, unitTest, androidTest)
    */
    androidCompilations.forEach { (sourceSetDesc, compilation) ->
      val kotlinSourceSetInfo = KotlinMPPGradleProjectResolver.createSourceSetInfo(compilation, gradleModule, resolverCtx) ?: return@forEach
      val androidGradleSourceSetDataNode = sourceSetByName[sourceSetDesc.sourceSetName] ?: return@forEach
      androidGradleSourceSetDataNode.createChild(KotlinSourceSetData.KEY, KotlinSourceSetData(kotlinSourceSetInfo))
    }

    /*
    Create modules for all 'root or intermediate' source sets, which are still considered 'android'.
    e.g. this includes commonMain or commonTest if the only target present in the build is 'android' (effectively making
    those two source sets 'android' as well)
    */
    androidCompilations
      .flatMap { (_, compilation) -> mppModel.findRootOrIntermediateAndroidSourceSets(compilation) }.toSet()
      .forEach { kotlinSourceSet ->
        val kotlinSourceSetInfo = KotlinMPPGradleProjectResolver.createSourceSetInfo(mppModel, kotlinSourceSet, gradleModule, resolverCtx)
        if (kotlinSourceSetInfo == null) return@forEach
        val gradleSourceSetData = createGradleSourceSetData(kotlinSourceSet, gradleModule, ideModule, resolverCtx)
        val dataNode = ideModule.createChild(GradleSourceSetData.KEY, gradleSourceSetData)
        dataNode.createChild(KotlinSourceSetData.KEY, KotlinSourceSetData(kotlinSourceSetInfo))

        sourceSetMap[kotlinSourceSetInfo.moduleId ?: return@forEach] = IJPair(
          dataNode, createExternalSourceSet(kotlinSourceSet, gradleSourceSetData, mppModel)
        )
      }
  }

  override fun populateModuleContentRoots(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
    super.populateModuleContentRoots(gradleModule, ideModule)

    val mppModel = resolverCtx.getMppModel(gradleModule) ?: return
    val androidModels = resolverCtx.getExtraProject(gradleModule, IdeAndroidModels::class.java) ?: return

    /*
    Find all special 'root or intermediate' android source sets that got created in 'maybeAttachKotlinSourceSetData'
    and setup their content root data.
     */
    mppModel.androidCompilationsForVariant(androidModels.selectedVariantName)
      .flatMap { (_, compilation) -> mppModel.findRootOrIntermediateAndroidSourceSets(compilation) }
      .forEach { kotlinSourceSet ->
        val gradleSourceSetData = ExternalSystemApiUtil.find(ideModule, GradleSourceSetData.KEY) {
          it.data.id == KotlinModuleUtils.getKotlinModuleId(gradleModule, kotlinSourceSet, resolverCtx)
        } ?: return@forEach

        createContentRootData(
          kotlinSourceSet.sourceDirs,
          kotlinSourceSet.sourceType,
          null,
          gradleSourceSetData
        )

        createContentRootData(
          kotlinSourceSet.resourceDirs,
          kotlinSourceSet.resourceType,
          null,
          gradleSourceSetData
        )
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

      /*
      Add dependencies for inter project 'dependsOn' edges and *Test -> *Main visibilities (additionalVisibleSourceSets)
      e.g. androidMain -> jvmAndAndroidMain -> commonMain
       */
      @Suppress("DEPRECATION")
      for (dependsOn in kotlinSourceSet.allDependsOnSourceSets + kotlinSourceSet.additionalVisibleSourceSets) {
        val dependsOnGradleSourceSet = sourceSetByName[dependsOn] ?: continue
        val moduleDependencyData = ModuleDependencyData(androidGradleSourceSetDataNode.data, dependsOnGradleSourceSet.data)
        moduleDependencyData.scope = if (sourceSetDesc == IdeModuleSourceSet.MAIN) DependencyScope.COMPILE else DependencyScope.TEST
        moduleDependencyData.isExported = true
        androidGradleSourceSetDataNode.createChild(
          ProjectKeys.MODULE_DEPENDENCY, ModuleDependencyData(androidGradleSourceSetDataNode.data, dependsOnGradleSourceSet.data)
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

/**
 * Returns KotlinSourceSets from given Android compilation, if there are "dependsOn" edges to it.
 */
private fun KotlinMPPGradleModel.findRootOrIntermediateAndroidSourceSets(compilation: KotlinCompilation): Set<KotlinSourceSet> =
  compilation.allSourceSets.flatMap { sourceSet -> resolveAllDependsOnSourceSets(sourceSet) }
    .filter { sourceSet -> sourceSet.actualPlatforms.platforms.singleOrNull() == KotlinPlatform.ANDROID }
    .toSet()

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
  /* Supports Multiplatform/Android SourceSetLayout V1 and V2 */
  val knownKotlinSourceSetNameSuffixes = when (this) {
    IdeModuleSourceSet.MAIN -> setOf("main")
    IdeModuleSourceSet.TEST_FIXTURES -> setOf("testFixtures")
    IdeModuleSourceSet.UNIT_TEST -> setOf("test", "unitTest")
    IdeModuleSourceSet.ANDROID_TEST -> setOf("androidTest", "instrumentedTest")
  }

  val potentialSourceSetNames = knownKotlinSourceSetNameSuffixes.map { suffix ->
    compilation.disambiguationClassifier.orEmpty().appendCapitalized(suffix)
  }

  return compilation.declaredSourceSets.singleOrNull { it.name in potentialSourceSetNames }
}

private fun IdeModuleSourceSet.androidCompilationNameSuffix() = when (this) {
  IdeModuleSourceSet.MAIN -> ""
  IdeModuleSourceSet.ANDROID_TEST -> "AndroidTest"
  IdeModuleSourceSet.UNIT_TEST -> "UnitTest"
  IdeModuleSourceSet.TEST_FIXTURES -> "TestFixtures"
}
