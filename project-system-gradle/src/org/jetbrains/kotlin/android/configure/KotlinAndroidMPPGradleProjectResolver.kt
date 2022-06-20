/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.android.configure

import com.android.tools.idea.gradle.model.IdeModuleSourceSet
import com.android.tools.idea.gradle.model.IdeModuleWellKnownSourceSet
import com.android.tools.idea.gradle.project.sync.IdeAndroidModels
import com.android.utils.appendCapitalized
import com.intellij.openapi.diagnostic.Logger
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
import org.jetbrains.kotlin.idea.gradleTooling.resolveAllDependsOnSourceSets
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

  override fun createModule(gradleModule: IdeaModule, projectDataNode: DataNode<ProjectData>): DataNode<ModuleData>? {
    val androidModels = resolverCtx.getExtraProject(gradleModule, IdeAndroidModels::class.java)
    val mppModel = resolverCtx.getMppModel(gradleModule)

    if (androidModels == null || mppModel == null) return super.createModule(gradleModule, projectDataNode)

    val selectedVariantName = androidModels.selectedVariantName
    mppModel.removeWrongCompilationsAndSourceSets(selectedVariantName)

    // Since Android source set modules (in a form of GradleSourceSetData) are currently created by AndroidGradleProjectResolver but they
    // form a part of a multi-module entity recognised by the KMP, we need to tell the KMP that they are KMP source sets and which KMP
    // source sets they represent.
    return super.createModule(gradleModule, projectDataNode)!!.also { ideModule ->
      val sourceSetByName = ideModule.sourceSetsByName()
      for ((sourceSetDesc, compilation) in mppModel.androidCompilationsForVariant(selectedVariantName)) {
        val kotlinSourceSetInfo = KotlinMPPGradleProjectResolver.createSourceSetInfo(compilation, gradleModule, resolverCtx) ?: continue
        val androidGradleSourceSetDataNode = sourceSetByName[sourceSetDesc.sourceSetName] ?: continue

        androidGradleSourceSetDataNode.createChild(KotlinSourceSetData.KEY, KotlinSourceSetData(kotlinSourceSetInfo))
      }
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

      val resolvedDependsOnSourceSets = mppModel.resolveAllDependsOnSourceSets(kotlinSourceSet)
      for (dependsOn in resolvedDependsOnSourceSets) {
        val dependsOnGradleSourceSet = sourceSetByName[dependsOn.name] ?: continue
        androidGradleSourceSetDataNode.createChild(
          ProjectKeys.MODULE_DEPENDENCY,
          ModuleDependencyData(androidGradleSourceSetDataNode.data, dependsOnGradleSourceSet.data)
        )
      }
    }
  }
}

/**
 * Returns all Android compilations for the given [variant].
 */
private fun KotlinMPPGradleModel.androidCompilationsForVariant(variant: String): List<Pair<IdeModuleWellKnownSourceSet, KotlinCompilation>> {
  return targets
    .asSequence()
    .flatMap { it.androidCompilations() }
    .mapNotNull { androidCompilation ->
      val sourceSet = IdeModuleWellKnownSourceSet.findFor(variant, androidCompilation) ?: return@mapNotNull null
      sourceSet to androidCompilation
    }
    .toList()
}

/**
 * The Kotlin multi-platform plugin at v1.6.x - 1.7.0 populates the [KotlinMPPGradleModel] with some wrong Android compilations and source
 * sets. This happens because the Kotlin multi-platform does neither understand Android variants nor the single-variant sync.
 *
 * This method removes those compilations and source sets by patching internal structures of [KotlinMPPGradleModel] model.
 */
private fun KotlinMPPGradleModel.removeWrongCompilationsAndSourceSets(variant: String) {
  val androidTargets: List<KotlinTarget> = targets.filter { it.platform == KotlinPlatform.ANDROID }

  androidTargets.forEach { androidTarget ->
    val wrongCompilations: List<KotlinCompilation> =
      androidTarget
        .androidCompilations()
        .filter { androidCompilation -> IdeModuleWellKnownSourceSet.findFor(variant, androidCompilation) == null }

    androidTarget.removeCompilations(wrongCompilations)
  }

  val validSourceSetNames = androidTargets.asSequence()
    .flatMap { it.androidCompilations() }
    .flatMap { it.androidSourceSets() }
    .map { it.name }
    .toSet()

  val prefixes = androidTargets.asSequence()
    .flatMap { it.androidCompilations() }
    .mapNotNull { it.disambiguationClassifier }
    .toSet()

  // Note, we are filtering source set names by prefixes, because some invalid Android source sets like ones derived from test fixtures are
  // not recognised by the KMP as Android source sets.
  val androidSourceSetNames = sourceSetsByName
    .keys
    .filter { sourceSetName -> prefixes.any { prefix -> sourceSetName.startsWith(prefix) } }
    .toSet()

  val wrongSourceSetNames = androidSourceSetNames - validSourceSetNames
  removeSourceSets(wrongSourceSetNames)
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
private fun IdeModuleWellKnownSourceSet.getRootKotlinSourceSet(compilation: KotlinCompilation): KotlinSourceSet? {
  val sourceSetName = compilation.disambiguationClassifier.orEmpty().appendCapitalized(kmpSourceSetSuffix())
  return compilation.declaredSourceSets.singleOrNull { it.name == sourceSetName }
}

private fun IdeModuleWellKnownSourceSet.androidCompilationNameSuffix() = when (this) {
  IdeModuleWellKnownSourceSet.MAIN -> ""
  IdeModuleWellKnownSourceSet.ANDROID_TEST -> "AndroidTest"
  IdeModuleWellKnownSourceSet.UNIT_TEST -> "UnitTest"
  IdeModuleWellKnownSourceSet.TEST_FIXTURES -> "TestFixtures"
}

private fun IdeModuleWellKnownSourceSet.kmpSourceSetSuffix() = when (this) {
  IdeModuleWellKnownSourceSet.MAIN -> "main"
  IdeModuleWellKnownSourceSet.ANDROID_TEST -> "androidTest"
  IdeModuleWellKnownSourceSet.UNIT_TEST -> "test"
  IdeModuleWellKnownSourceSet.TEST_FIXTURES -> "testFixtures"
}

private fun KotlinTarget.androidCompilations(): List<KotlinCompilation> =
  compilations.filter { it.platform == KotlinPlatform.ANDROID }

private fun KotlinCompilation.androidSourceSets(): List<KotlinSourceSet> =
  IdeModuleWellKnownSourceSet.values().mapNotNull { it.getRootKotlinSourceSet(this) }

private fun KotlinTarget.removeCompilations(compilationsToRemove: Collection<KotlinCompilation>) {
  val mutableCompilations = compilations as? MutableCollection<KotlinCompilation> ?: return
  kotlin.runCatching { compilationsToRemove.forEach(mutableCompilations::remove) }
    .onFailure {
      Logger.getInstance(KotlinAndroidMPPGradleProjectResolver::class.java)
        .error("Failed to remove not necessary Kotlin compilations", it)
    }
}

private fun KotlinMPPGradleModel.removeSourceSets(sourceSetsToRemove: Collection<String>) {
  val mutableSourceSetsByName = sourceSetsByName as? MutableMap<String, KotlinSourceSet> ?: return
  kotlin.runCatching { sourceSetsToRemove.forEach(mutableSourceSetsByName::remove) }
    .onFailure {
      Logger.getInstance(KotlinAndroidMPPGradleProjectResolver::class.java)
        .error("Failed to remove not necessary Kotlin source sets", it)
    }
}

/**
 * Given a currently selected [variant], returns a [IdeModuleWellKnownSourceSet] describing the [compilation] or `null` if the [compilation]
 * does not represent an Android target or the target does not belong to the currently selected [variant].
 */
private fun IdeModuleWellKnownSourceSet.Companion.findFor(variant: String, compilation: KotlinCompilation) =
  IdeModuleWellKnownSourceSet.values().find { variant + it.androidCompilationNameSuffix() == compilation.name }

