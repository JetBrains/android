/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea.svs

import com.android.builder.model.AndroidProject.PROJECT_TYPE_APP
import com.android.builder.model.ModelBuilderParameter
import com.android.builder.model.NativeVariantAbi
import com.android.builder.model.Variant
import com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId
import com.android.tools.idea.gradle.project.sync.idea.UsedInBuildAction
import com.android.tools.idea.gradle.project.sync.SelectedVariants
import com.android.tools.idea.gradle.project.sync.SyncActionOptions
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.UnsupportedMethodException
import java.util.Collections
import java.util.LinkedList

/**
 * This method requests all of the required [Variant] models from Gradle via the tooling API.
 *
 * Function for choosing the build variant that will be selected in Android Studio (the IDE can only work with one variant at a time.)
 * works as follows:
 *  1. For Android app modules using new versions of the plugin, the [Variant] to select is obtained from the model.
 *     For older plugins, the "debug" variant is selected. If the module doesn't have a variant named "debug", it sorts all the
 *     variant names alphabetically and picks the first one.
 *  2. For Android library modules, it chooses the variant needed by dependent modules. For example, if variant "debug" in module "app"
 *     depends on module "lib" - variant "freeDebug", the selected variant in "lib" will be "freeDebug". If a library module is a leaf
 *     (i.e. no other modules depend on it) a variant will be picked as if the module was an app module.
 *
 *  All of the [Variant] or [NativeVariantAbi] models obtained from Gradle are stored in the [AndroidModule]s [VariantGroup]
 *  once this method returns.
 */
@UsedInBuildAction
fun chooseSelectedVariants(
  controller: BuildController,
  inputModules: List<AndroidModule>,
  syncActionOptions: SyncActionOptions
) {
  val selectedVariants = syncActionOptions.selectedVariants
                         ?: throw IllegalStateException("Single variant sync requested, but SelectedVariants were null!")
  val shouldGenerateSources = syncActionOptions.shouldGenerateSources()
  val modulesById = HashMap<String, AndroidModule>()
  val allModules = LinkedList<String>()
  val visitedModules = HashSet<String>()
  // The module whose variant selection was changed from UI, the dependency modules should be consistent with this module. Achieve this by
  // adding this module to the head of allModules so that its dependency modules are resolved first.
  var moduleWithVariantSwitched: String? = null

  inputModules.filter { it.androidProject.variants.isEmpty() }.forEach { module ->
    val id = createUniqueModuleId(module.gradleProject)
    modulesById[id] = module
    if (id == syncActionOptions.moduleIdWithVariantSwitched) {
      moduleWithVariantSwitched = id
    }
    else {
      // All app modules must be requested first since they are used to work out which variants to request for their dependencies.
      if (module.androidProject.projectType == PROJECT_TYPE_APP) allModules.addFirst(id) else allModules.addLast(id)
    }
  }

  if (moduleWithVariantSwitched != null) allModules.addFirst(moduleWithVariantSwitched)

  // This first starts by requesting models for all the modules that can be reached from the app modules (via dependencies) and then
  // requests any other modules that can't be reached.
  allModules.forEach { moduleId ->
    if (visitedModules.contains(moduleId)) return@forEach

    visitedModules.add(moduleId)
    val module = modulesById[moduleId]!!

    // Request the Variant model for the module
    val variant = selectVariantForAppOrLeaf(controller, module, selectedVariants, moduleId, shouldGenerateSources) ?: return@forEach
    // Request the native Variant model (this won't do any work for non-native projects) and get the name of it's ABI
    val abi = syncAndAddNativeVariantAbi(controller, module, variant.name, selectedVariants.getSelectedAbi(moduleId))
    // Store the requested/obtained information in the IdeaAndroidModule
    module.addSelectedVariant(variant, abi)
    // Request models for the dependencies of this module.
    selectVariantForDependencyModules(controller, module, modulesById, visitedModules, shouldGenerateSources)
  }
}

@UsedInBuildAction
private fun selectVariantForAppOrLeaf(
  controller: BuildController,
  androidModule: AndroidModule,
  selectedVariants: SelectedVariants,
  moduleId: String,
  shouldGenerateSources: Boolean
): Variant? {
  var variant = selectedVariants.getSelectedVariant(moduleId)
  val variantNames = try {
    androidModule.androidProject.variantNames
  } catch (e: UnsupportedMethodException) {
    null
  } ?: return null

  // Check to see if we have a variant selected in the IDE, and that it is still a valid one.
  if (variant == null || !variantNames.contains(variant)) {
    variant = try {
      // Ask Gradle for the defaultVariant
      androidModule.androidProject.defaultVariant
    }
    catch (e: UnsupportedMethodException) {
      // If this is not supported then fallback to picking a default.
      getDefaultOrFirstItem(variantNames, "debug")
    }
  }

  return variant?.let { syncAndAddVariant(controller, androidModule, variant, shouldGenerateSources) }
}

@UsedInBuildAction
private fun selectVariantForDependencyModules(
  controller: BuildController,
  androidModule: AndroidModule,
  modulesById: Map<String, AndroidModule>,
  visitedModules: MutableSet<String>,
  shouldGenerateSources: Boolean
) {
  androidModule.moduleDependencies.forEach { dependency ->
    if (visitedModules.contains(dependency.id)) return@forEach
    visitedModules.add(dependency.id)

    if (dependency.variant == null) return@forEach

    val dependencyModule = modulesById[dependency.id] ?: return@forEach
    if (dependencyModule.containsVariant(dependency.variant)) return@forEach

    val dependencyVariant = syncAndAddVariant(controller, dependencyModule, dependency.variant, shouldGenerateSources) ?: return@forEach
    val abiName = syncAndAddNativeVariantAbi(controller, dependencyModule, dependency.variant, dependency.abi)

    dependencyModule.addSelectedVariant(dependencyVariant, abiName)
    selectVariantForDependencyModules(controller, dependencyModule, modulesById, visitedModules, shouldGenerateSources)
  }
}

/**
 * Query Gradle for the [Variant] of the [module] with the given [variantName]. Gradle's parameterized tooling API is used in order
 * to pass the name of the variant and whether or not sources should be generated to the ModelBuilder via the [ModelBuilderParameter].
 *
 * This method adds the resulting [Variant] to the [module]s [VariantGroup] if not null. If no model is returned, nothing is added.
 *
 * @param[module] the module to request a [Variant] for
 * @param[controller] the Gradle [BuildController] that is queried for the model
 * @param[variantName] the name of the [Variant] that should be requested
 * @param[shouldGenerateSources] whether or not the model builder should schedule source generation
 */
@UsedInBuildAction
private fun syncAndAddVariant(
  controller: BuildController,
  module: AndroidModule,
  variantName: String,
  shouldGenerateSources: Boolean
): Variant? = controller.findModel(module.gradleProject, Variant::class.java, ModelBuilderParameter::class.java) { parameter ->
  parameter.setVariantName(variantName)
  parameter.shouldGenerateSources = shouldGenerateSources
}?.also {
  module.variantGroup.variants.add(it)
}

/**
 * Query Gradle for the abi name of the [module] with the given [variantName]. Gradle's parameterized tooling API is used in order
 * to pass the name of the variant and the abi name to the ModelBuilder via the [ModelBuilderParameter].
 *
 * If the passed [module] doesn't have a NativeAndroidProject then this method does nothing.
 *
 * This method adds the resulting [NativeVariantAbi] to the [module]s [VariantGroup] if not null. If no model is returned, nothing is added.
 *
 * @param[module] the module to request a [Variant] for
 * @param[controller] the Gradle [BuildController] that is queried for the model
 * @param[variantName] the name of the [Variant] that should be requested
 * @param[selectedAbi] which abi to select, if null or the abi doesn't exist a default will be picked. This default will be "x86" if
 *                     it exists in the abi names returned by the [NativeAndroidProject] otherwise the first item of this list will be
 *                     chosen.
 */
@UsedInBuildAction
private fun syncAndAddNativeVariantAbi(
  controller: BuildController,
  module: AndroidModule,
  variantName: String,
  selectedAbi: String?
): String? {
  // This module is not a native one, nothing to do
  if (module.nativeAndroidProject == null) return null

  // Attempt to get the list of supported abiNames for this variant from the NativeAndroidProject
  // Otherwise return from this method with a null result as abis are not supported.
  val abiNames = try {
    module.nativeAndroidProject.variantInfos[variantName]?.abiNames
  }
  catch (e: UnsupportedMethodException) {
    null
  } ?: return null

  val abiToRequest = (if (selectedAbi != null && abiNames.contains(selectedAbi)) selectedAbi else getDefaultOrFirstItem(abiNames, "x86"))
                     ?: throw IllegalStateException("No valid Native abi found to request!")

  controller.findModel(module.gradleProject, NativeVariantAbi::class.java, ModelBuilderParameter::class.java) { parameter ->
    parameter.setVariantName(variantName)
    parameter.setAbiName(abiToRequest)
  }?.also {
    module.variantGroup.nativeVariants.add(it)
  }
  return abiToRequest
}

@UsedInBuildAction
private fun getDefaultOrFirstItem(names: Collection<String>, defaultValue: String): String? {
  if (names.isEmpty()) return null
  return if (names.contains(defaultValue)) defaultValue else Collections.min(names, String::compareTo)
}

