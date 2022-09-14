/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.android.configure

import com.android.builder.model.AndroidProject
import com.android.tools.idea.gradle.model.IdeBaseArtifactCore
import com.android.tools.idea.gradle.model.IdeModuleSourceSet
import com.android.tools.idea.gradle.model.IdeModuleWellKnownSourceSet
import com.android.tools.idea.gradle.model.IdeModuleWellKnownSourceSet.ANDROID_TEST
import com.android.tools.idea.gradle.model.IdeModuleWellKnownSourceSet.MAIN
import com.android.tools.idea.gradle.model.IdeModuleWellKnownSourceSet.TEST_FIXTURES
import com.android.tools.idea.gradle.model.IdeModuleWellKnownSourceSet.UNIT_TEST
import com.android.tools.idea.gradle.model.IdeSourceProvider
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.IdeBuildTypeContainerImpl
import com.android.tools.idea.gradle.model.impl.IdeProductFlavorContainerImpl
import com.android.tools.idea.gradle.model.impl.IdeSourceProviderImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantCoreImpl
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
import java.io.File

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
    mppModel.removeWrongCompilationsAndMergeNonNeededSourceSets(selectedVariantName)

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
 * Populates Android variant IDE model with source directories present in the MPP model but absent in the variant model.
 *
 * This is a temporary workaround needed to handle additional Android source sets created by MPP, which we remove from the MPP model
 * since they are not true KMP fragments are just source directories compiled together with all other Android sources.
 *
 * This method is supposed to receive an already patched [mppModel], i.e. where all android source sets are already merged into the root
 * source sets of each Android compilation.
 */
fun IdeVariantCoreImpl.patchFromMppModel(
  androidProject: IdeAndroidProjectImpl,
  mppModel: KotlinMPPGradleModel
): IdeVariantCoreImpl {
  val variantName = this.name

  fun sourceProvidersFor(artifact: IdeModuleWellKnownSourceSet): List<IdeSourceProvider> {
    return listOfNotNull(
      this.artifact(artifact)?.variantSourceProvider,
      this.artifact(artifact)?.multiFlavorSourceProvider,
      *(androidProject.productFlavors.filter { it.productFlavor.name in this.productFlavors }
        .mapNotNull { it.sourceProvider(artifact) }).toTypedArray(),
      *(androidProject.buildTypes.filter { it.buildType.name == this.buildType }.mapNotNull { it.sourceProvider(artifact) }).toTypedArray(),
      androidProject.defaultConfig.sourceProvider(artifact)
    )
  }

  fun sourceSetsFor(artifact: IdeModuleWellKnownSourceSet): List<KotlinSourceSet> {
    return mppModel
      .androidTargets()
      .flatMap { it.androidCompilations() }
      .filter { IdeModuleWellKnownSourceSet.findFor(variantName, it) == artifact }
      .mapNotNull { artifact.getRootKotlinSourceSet(it) }
  }

  fun IdeSourceProviderImpl?.patch(artifact: IdeModuleWellKnownSourceSet): IdeSourceProviderImpl? {
    val root = androidProject.defaultConfig.sourceProvider(artifact)?.manifestFile?.parentFile

    val sourceSets = sourceSetsFor(artifact)
    val sourceProviders = sourceProvidersFor(artifact)

    val missingSourceDirs = sourceSets.flatMap { it.sourceDirs }.toSet() -
      sourceProviders.flatMap { it.javaDirectories + it.kotlinDirectories }.toSet()

    val missingResourceDirs = sourceSets.flatMap { it.resourceDirs }.toSet() -
      sourceProviders.flatMap { it.resourcesDirectories }.toSet()

    if (missingSourceDirs.isEmpty() && missingResourceDirs.isEmpty()) return this

    val thisOrNewProvider = this
      ?: IdeSourceProviderImpl().copy(
        // We cannot use [variantName] directly because it is likely to clash with its build type if the variant specific source provider
        // is null
        myName = "${variantName}_KotlinMPP",
        // The location of this root folder does not really matter. It is used as an anchor for relative paths stored inside the object,
        // but paths returned are absolute anyway. Redirecting it to a non-existent subdirectory allows us to avoid conflicting content
        // roots set up for non-existent manifest files.
        myFolder = root?.resolve("__KotlinMPP__"),

        // This is unfortunately a required property, and it is already meaningless in unit test artifacts. Here, we return a second copy
        // of the same file returned by the default configuration to avoid NPEs in various places.
        myManifestFile = "AndroidManifest.xml"
      )

    return thisOrNewProvider.appendDirectories(
      javaDirectories = missingSourceDirs,
      resourcesDirectories = missingResourceDirs
    )
  }

  return this.copy(
    mainArtifact = mainArtifact.copy(
      variantSourceProvider = mainArtifact.variantSourceProvider.patch(MAIN)
    ),
    androidTestArtifact = androidTestArtifact?.copy(
      variantSourceProvider = androidTestArtifact?.variantSourceProvider.patch(ANDROID_TEST)
    ),
    unitTestArtifact = unitTestArtifact?.copy(
      variantSourceProvider = unitTestArtifact?.variantSourceProvider.patch(UNIT_TEST)
    ),
    testFixturesArtifact = testFixturesArtifact?.copy(
      variantSourceProvider = testFixturesArtifact?.variantSourceProvider.patch(TEST_FIXTURES)
    )
  )
}

private fun IdeVariantCoreImpl.artifact(artifact: IdeModuleWellKnownSourceSet): IdeBaseArtifactCore? {
  return when (artifact) {
    MAIN -> mainArtifact
    TEST_FIXTURES -> testFixturesArtifact
    UNIT_TEST -> unitTestArtifact
    ANDROID_TEST -> androidTestArtifact
  }
}

private val IdeModuleWellKnownSourceSet.artifactName: String
  get() = when (this) {
    MAIN -> AndroidProject.ARTIFACT_MAIN
    ANDROID_TEST -> AndroidProject.ARTIFACT_ANDROID_TEST
    UNIT_TEST -> AndroidProject.ARTIFACT_UNIT_TEST
    TEST_FIXTURES -> AndroidProject.ARTIFACT_TEST_FIXTURES
  }

private fun IdeBuildTypeContainerImpl.sourceProvider(artifact: IdeModuleWellKnownSourceSet): IdeSourceProviderImpl? {
  return when (artifact) {
    MAIN -> sourceProvider
    TEST_FIXTURES -> extraSourceProviders.singleOrNull { it.artifactName == TEST_FIXTURES.artifactName }?.sourceProvider
    UNIT_TEST -> extraSourceProviders.singleOrNull { it.artifactName == UNIT_TEST.artifactName }?.sourceProvider
    ANDROID_TEST -> extraSourceProviders.singleOrNull { it.artifactName == ANDROID_TEST.artifactName }?.sourceProvider
  }
}

private fun IdeProductFlavorContainerImpl.sourceProvider(artifact: IdeModuleWellKnownSourceSet): IdeSourceProviderImpl? {
  return when (artifact) {
    MAIN -> sourceProvider
    TEST_FIXTURES -> extraSourceProviders.singleOrNull { it.artifactName == TEST_FIXTURES.artifactName }?.sourceProvider
    UNIT_TEST -> extraSourceProviders.singleOrNull { it.artifactName == UNIT_TEST.artifactName }?.sourceProvider
    ANDROID_TEST -> extraSourceProviders.singleOrNull { it.artifactName == ANDROID_TEST.artifactName }?.sourceProvider
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
private fun KotlinMPPGradleModel.removeWrongCompilationsAndMergeNonNeededSourceSets(variant: String) {
  val androidTargets: List<KotlinTarget> = androidTargets()

  androidTargets.forEach { androidTarget ->
    val wrongCompilations: List<KotlinCompilation> =
      androidTarget
        .androidCompilations()
        .filter { androidCompilation -> IdeModuleWellKnownSourceSet.findFor(variant, androidCompilation) == null }

    androidTarget.removeCompilations(wrongCompilations)
  }

  val validAndroidSourceSets = androidTargets.asSequence()
    .flatMap { it.androidCompilations() }
    .flatMap { it.androidSourceSets() }
    .toList()

  val validAndroidSourceSetNames = validAndroidSourceSets
    .map { it.name }
    .toSet()

  val pureAndroidSourceSetNames =
    sourceSetsByName.values
      // Cannot go through declared source sets because KMP wrongly adds commonMain to declared source sets even in presence of the JVM
      // platform.
      .filter { sourceSet -> sourceSet.actualPlatforms.singleOrNull() == KotlinPlatform.ANDROID }
      .map { it.name }
      .toSet()

  val orphanAndroidSourceSetNames =
    sourceSetsByName.values
      .filter { sourceSet -> sourceSet.actualPlatforms.any { it == KotlinPlatform.ANDROID} }
      .map { it.name }
      .toSet() -
      targets
        .flatMap { it.compilations }
        .flatMap { it.allSourceSets }
        .map { it.name }
        .toSet()

  val wrongSourceSetNames = pureAndroidSourceSetNames - validAndroidSourceSetNames + orphanAndroidSourceSetNames

  mergeSourceSets(wrongSourceSetNames, validAndroidSourceSets)
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
  val sourceSetNames = kmpSourceSetSuffix().map { compilation.disambiguationClassifier.orEmpty().appendCapitalized(it) }
  return compilation.declaredSourceSets.singleOrNull { it.name in sourceSetNames }
}

private fun IdeModuleWellKnownSourceSet.androidCompilationNameSuffix() = when (this) {
  IdeModuleWellKnownSourceSet.MAIN -> ""
  IdeModuleWellKnownSourceSet.ANDROID_TEST -> "AndroidTest"
  IdeModuleWellKnownSourceSet.UNIT_TEST -> "UnitTest"
  IdeModuleWellKnownSourceSet.TEST_FIXTURES -> "TestFixtures"
}

// TODO(b/246924347): Add an integration test for KMP v2 source layout.
private fun IdeModuleWellKnownSourceSet.kmpSourceSetSuffix() = when (this) {
  IdeModuleWellKnownSourceSet.MAIN -> setOf("main")
  IdeModuleWellKnownSourceSet.ANDROID_TEST -> setOf("androidTest", "instrumentedTest")
  IdeModuleWellKnownSourceSet.UNIT_TEST -> setOf("test", "unitTest")
  IdeModuleWellKnownSourceSet.TEST_FIXTURES -> setOf("testFixtures")
}

private fun KotlinMPPGradleModel.androidTargets() =
  targets.filter { it.platform == KotlinPlatform.ANDROID }

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

private fun KotlinMPPGradleModel.mergeSourceSets(sourceSetsToRemove: Set<String>, validAndroidSourceSets: List<KotlinSourceSet>) {
  kotlin.runCatching {
    val validAndroidSourceSetNames = validAndroidSourceSets.map { it.name }.toSet()
    androidTargets().flatMap { it.androidCompilations() }.forEach { androidCompilation ->
      val compilationMainSourceSet = androidCompilation.allSourceSets.single {
        it.name in validAndroidSourceSetNames
      }
      val compilationRemovedSourceSets = androidCompilation.allSourceSets.filter { it.name in sourceSetsToRemove }.toSet()
      androidCompilation.allSourceSets.takeUnless { it.isEmpty() }
        ?.castTo<MutableSet<KotlinSourceSet>>()
        ?.removeAll(compilationRemovedSourceSets)
      androidCompilation.declaredSourceSets.takeUnless { it.isEmpty() }
        ?.castTo<MutableSet<KotlinSourceSet>>()
        ?.removeAll(compilationRemovedSourceSets)
      compilationMainSourceSet.sourceDirs.castTo<MutableSet<File>>().addAll(compilationRemovedSourceSets.flatMap { it.sourceDirs })
      compilationMainSourceSet.resourceDirs.castTo<MutableSet<File>>().addAll(compilationRemovedSourceSets.flatMap { it.resourceDirs })
    }

    val mutableSourceSetsByName = sourceSetsByName as? MutableMap<String, KotlinSourceSet> ?: return
    sourceSetsToRemove.forEach { nameToRemove ->
      mutableSourceSetsByName.remove(nameToRemove)
      validAndroidSourceSets.forEach { valid ->
        valid.declaredDependsOnSourceSets.takeUnless { it.isEmpty() }?.castTo<MutableSet<String>>()?.remove(nameToRemove)
        valid.allDependsOnSourceSets.takeUnless { it.isEmpty() }?.castTo<MutableSet<String>>()?.remove(nameToRemove)
        valid.additionalVisibleSourceSets.takeUnless { it.isEmpty() }?.castTo<MutableSet<String>>()?.remove(nameToRemove)
      }
    }
  }
    .onFailure {
      Logger.getInstance(KotlinAndroidMPPGradleProjectResolver::class.java)
        .error("Failed to merge and remove not necessary Kotlin source sets", it)
    }
}

/**
 * Given a currently selected [variant], returns a [IdeModuleWellKnownSourceSet] describing the [compilation] or `null` if the [compilation]
 * does not represent an Android target or the target does not belong to the currently selected [variant].
 */
private fun IdeModuleWellKnownSourceSet.Companion.findFor(variant: String, compilation: KotlinCompilation) =
  IdeModuleWellKnownSourceSet.values().find { variant + it.androidCompilationNameSuffix() == compilation.name }

private inline fun <reified T> Any.castTo(): T = this as T // Throw if it cannot be cast.
