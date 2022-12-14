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
package com.android.tools.idea.gradle.structure.daemon.analysis

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.PsPathRenderer
import com.android.tools.idea.gradle.structure.model.PsGeneralIssue
import com.android.tools.idea.gradle.structure.model.PsIssue
import com.android.tools.idea.gradle.structure.model.PsIssueType
import com.android.tools.idea.gradle.structure.model.PsQuickFix
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsBuildType
import com.android.tools.idea.gradle.structure.model.android.PsDeclaredModuleAndroidDependency
import com.android.tools.idea.gradle.structure.model.android.PsFlavorDimension
import com.android.tools.idea.gradle.structure.model.android.PsProductFlavor
import com.android.tools.idea.gradle.structure.model.meta.maybeValue
import com.intellij.openapi.application.ApplicationManager

fun analyzeModuleDependencies(androidModule: PsAndroidModule, pathRenderer: PsPathRenderer): Sequence<PsIssue> =
  androidModule.dependencies.modules.asSequence().flatMap { analyzeModuleDependency(it, pathRenderer) }

fun analyzeModuleDependency(dependency: PsDeclaredModuleAndroidDependency, pathRenderer: PsPathRenderer): Sequence<PsIssue> =
  with(pathRenderer) {
    val sourceModule = dependency.parent
    val targetModule = dependency.parent.parent.findModuleByGradlePath(dependency.gradlePath) as? PsAndroidModule ?: return emptySequence()

    fun analyzeBuildTypes(): Sequence<PsGeneralIssue> {
      fun targetBuildTypeExists(buildTypeName: String) = targetModule.findBuildType(buildTypeName) != null

      return sourceModule.buildTypes.items.asSequence().mapNotNull { sourceBuildType ->
        if (targetBuildTypeExists(sourceBuildType.name)) return@mapNotNull null
        if (sourceBuildType.matchingFallbacks.maybeValue?.any { fallback ->
            targetBuildTypeExists(fallback)
          } == true) return@mapNotNull null

        PsGeneralIssue(
          "No build type in module '${targetModule.path.renderNavigation { buildTypesPath }}' " +
          "matches build type '${sourceBuildType.path.renderNavigation()}'.",
          "",
          dependency.path,
          PsIssueType.PROJECT_ANALYSIS,
          PsIssue.Severity.ERROR,
          listOf(PsMissingBuildTypeQuickFix(targetModule, sourceBuildType), PsMissingBuildTypeFallbackQuickFix(sourceBuildType))
        )
      }
    }

    fun analyzeProductFlavors(): Sequence<PsIssue> {
      return sourceModule.flavorDimensions.asSequence().flatMap forEachDimension@{ sourceDimension ->
        if (targetModule.findFlavorDimension(sourceDimension.name) == null) return@forEachDimension emptySequence<PsIssue>()

        sourceModule.productFlavors.items.asSequence()
          .filter { it.effectiveDimension == sourceDimension.name }
          .mapNotNull forEachFlavor@{ sourceProductFlavor ->
            if (targetModule.findProductFlavor(sourceDimension.name, sourceProductFlavor.name) != null) return@forEachFlavor null
            if (sourceProductFlavor.matchingFallbacks.maybeValue?.any { fallback ->
                (targetModule.findProductFlavor(sourceDimension.name, fallback) != null)
              } == true) return@forEachFlavor null

            PsGeneralIssue(
              "No product flavor in module '${targetModule.path.renderNavigation { productFlavorsPath }}' " +
              "matches product flavor '${sourceProductFlavor.path.renderNavigation()}' " +
              "in dimension '${sourceDimension.path.renderNavigation()}'.",
              "",
              dependency.path,
              PsIssueType.PROJECT_ANALYSIS,
              PsIssue.Severity.ERROR,
              listOf(PsMissingProductFlavorQuickFix(targetModule, sourceProductFlavor),
                     PsMissingProductFlavorFallbackQuickFix(sourceProductFlavor))
            )
          }
      }
    }

    fun analyzeFlavorDimensions(): Sequence<PsGeneralIssue> {
      return targetModule.flavorDimensions.items.asSequence()
        .filter { targetDimension -> targetModule.productFlavors.items.count { it.effectiveDimension == targetDimension.name } > 1 }
        .filter { targetDimension ->
          sourceModule.findFlavorDimension(targetDimension.name) == null
          && (sourceModule.parsedModel?.android()?.defaultConfig()?.missingDimensionStrategies().orEmpty()
            .all { strategy -> strategy.toList()?.firstOrNull()?.toString() != targetDimension.name })
        }
        .map { targetDimension ->
          PsGeneralIssue(
            "No flavor dimension in module '${sourceModule.path.renderNavigation { productFlavorsPath }}' matches " +
            "dimension '${targetDimension.path.renderNavigation()}' " +
            "from module ${targetModule.path.renderNavigation { productFlavorsPath }} on which " +
            "module '${sourceModule.path.renderNavigation(specificPlace = dependency.path)}' depends.",
            dependency.path,
            PsIssueType.PROJECT_ANALYSIS,
            PsIssue.Severity.ERROR,
            // TODO(b/120551319): Uncomment as a secondary fix when "add missing dimension strategy" is implemented.
            null // PsMissingFlavorDimensionQuickFix(sourceModule, targetDimension)
          )
        }
    }

    analyzeBuildTypes() + analyzeProductFlavors() + analyzeFlavorDimensions()
  }

fun analyzeProductFlavors(model: PsAndroidModule, pathRenderer: PsPathRenderer): Sequence<PsGeneralIssue> =
  with(pathRenderer) {
    model.productFlavors.asSequence().filter { it.effectiveDimension == null }.map {
      val configuredFlavorDimension = it.configuredDimension.maybeValue
      PsGeneralIssue(
        when {
          configuredFlavorDimension.isNullOrEmpty() -> "Flavor '${it.path.renderNavigation()}' has no flavor dimension."
          else -> "Flavor '${it.path.renderNavigation()}' has unknown dimension '$configuredFlavorDimension'."
        },
        it.path,
        PsIssueType.PROJECT_ANALYSIS,
        PsIssue.Severity.ERROR,
        null
      )
    }
  }


data class PsMissingBuildTypeQuickFix(val moduleGradlePath: String, val buildTypeName: String) : PsQuickFix {
  constructor (module: PsAndroidModule, buildType: PsBuildType) : this(module.gradlePath, buildType.name)

  override val text: String get() = "Add Build Type"

  override fun execute(context: PsContext) {
    val targetModule = context.project.findModuleByGradlePath(moduleGradlePath) as PsAndroidModule
    val newBuildType = targetModule.addNewBuildType(buildTypeName)
    ApplicationManager.getApplication().invokeLater {
      context
        .mainConfigurable
        .navigateTo(newBuildType.path.getPlaceDestination(context), true)
    }
  }
}

data class PsMissingBuildTypeFallbackQuickFix(val moduleGradlePath: String, val buildTypeName: String) : PsQuickFix {
  constructor (buildType: PsBuildType) : this(buildType.parent.gradlePath, buildType.name)

  override val text: String get() = "Add Fallback"

  override fun execute(context: PsContext) {
    val sourceModule = context.project.findModuleByGradlePath(moduleGradlePath) as PsAndroidModule
    val buildType = sourceModule.findBuildType(buildTypeName) ?: return
    ApplicationManager.getApplication().invokeLater {
      context
        .mainConfigurable
        .navigateTo(
          buildType.path.property(PsBuildType.BuildTypeDescriptors.matchingFallbacks).getPlaceDestination(context), true)
    }
  }
}

data class PsMissingFlavorDimensionQuickFix(val moduleGradlePath: String, val newDimensionName: String) : PsQuickFix {
  constructor (module: PsAndroidModule, dimension: PsFlavorDimension) : this(module.gradlePath, dimension.name)

  override val text: String get() = "Add Flavor Dimension"

  override fun execute(context: PsContext) {
    val targetModule = context.project.findModuleByGradlePath(moduleGradlePath) as PsAndroidModule
    val newFlavorDimension = targetModule.addNewFlavorDimension(newDimensionName)
    ApplicationManager.getApplication().invokeLater {
      context
        .mainConfigurable
        .navigateTo(newFlavorDimension.path.getPlaceDestination(context), true)
    }
  }
}

data class PsMissingProductFlavorQuickFix(val moduleGradlePath: String, val dimension: String, val productFlavorName: String) : PsQuickFix {
  constructor (module: PsAndroidModule, productFlavor: PsProductFlavor) :
    this(module.gradlePath, productFlavor.effectiveDimension.orEmpty(), productFlavor.name)

  override val text: String get() = "Add Product Flavor"

  override fun execute(context: PsContext) {
    val targetModule = context.project.findModuleByGradlePath(moduleGradlePath) as PsAndroidModule
    val newProductFlavor = targetModule.addNewProductFlavor(dimension, productFlavorName)
    ApplicationManager.getApplication().invokeLater {
      context
        .mainConfigurable
        .navigateTo(newProductFlavor.path.getPlaceDestination(context), true)
    }
  }
}

data class PsMissingProductFlavorFallbackQuickFix(
  val moduleGradlePath: String,
  val dimension: String,
  val productFlavorName: String
) : PsQuickFix {
  constructor (productFlavor: PsProductFlavor) : this(productFlavor.parent.gradlePath, productFlavor.effectiveDimension.orEmpty(),
                                                      productFlavor.name)

  override val text: String get() = "Add Fallback"

  override fun execute(context: PsContext) {
    val sourceModule = context.project.findModuleByGradlePath(moduleGradlePath) as PsAndroidModule
    val productFlavor = sourceModule.findProductFlavor(dimension, productFlavorName) ?: return
    ApplicationManager.getApplication().invokeLater {
      context
        .mainConfigurable
        .navigateTo(
          productFlavor.path.property(PsProductFlavor.ProductFlavorDescriptors.matchingFallbacks).getPlaceDestination(context), true)
    }
  }
}

