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
package com.android.tools.idea.gradle.dependencies

import com.android.tools.idea.gradle.dsl.android.model.android.android
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.utils.addIfNotNull

/**
 * We assume for now that declarative project is pure (no non-declarative modules)
 * and no version catalog in it.
 */
class DeclarativeDependenciesInserter: DependenciesInserter() {

  /**
   * Adding dependency to flavor/buildType sections, if find proper build type or
   * flavor prefix (requesting model with existing build types and flavors).
   * For example "releaseImplementation"  configurationwill go to
   * `buildTypes.buildType("release").dependency{implementation("...")}`
   * If it cannot find buildTypeName/FlavorName - dependency will be created in
   * defaultConfig section
   */
  override fun addDependency(configuration: String,
                             dependency: String,
                             excludes: List<ArtifactDependencySpec>,
                             parsedModel: GradleBuildModel,
                             matcher: DependencyMatcher,
                             sourceSetName: String?): Set<PsiFile> {
    val existingBuildTypesNames = parsedModel.android().buildTypes().map { it.name() }.toSet()
    val maybeBuildDependencyName = getNameIfBuildDependency(configuration, existingBuildTypesNames)
    if (maybeBuildDependencyName != null) {
      val (buildName, configName) = maybeBuildDependencyName
      return addBuildTypeDependency(buildName, configName, dependency, parsedModel, matcher)
    }

    val existingFlavorNames = parsedModel.android().productFlavors().map { it.name() }.toSet()
    val maybeFlavorDependencyName = getNameIfFlavorDependency(configuration, existingFlavorNames)
    if (maybeFlavorDependencyName != null) {
      val (flavorName, configName) = maybeFlavorDependencyName
      return addFlavorDependency(flavorName, configName, dependency, parsedModel, matcher)
    }

    return addDefaultConfigDependency(configuration, dependency, parsedModel, matcher)
  }

  /**
   * Adding dependency to build type
   */
  fun addBuildTypeDependency(buildType: String,
                                      configuration: String,
                                      dependency: String,
                                      parsedModel: GradleBuildModel,
                                      matcher: DependencyMatcher): Set<PsiFile> {
    val changedFiles = mutableSetOf<PsiFile>()
    val buildType =
      parsedModel.android().buildTypes().firstOrNull { it.name() == buildType } ?: parsedModel.android().addBuildType(buildType)
    val dependenciesModel = buildType.dependencies()
    return addDependency(dependenciesModel, matcher, configuration, dependency, changedFiles)
  }

  /**
   * Adding dependency to flavor
   */
  fun addFlavorDependency(flavorName: String,
                                   configuration: String,
                                   dependency: String,
                                   parsedModel: GradleBuildModel,
                                   matcher: DependencyMatcher): Set<PsiFile> {
    val changedFiles = mutableSetOf<PsiFile>()
    val flavor =
      parsedModel.android().productFlavors().firstOrNull { it.name() == flavorName } ?: parsedModel.android().addProductFlavor(flavorName)
    val dependenciesModel = flavor.dependencies()
    return addDependency(dependenciesModel, matcher, configuration, dependency, changedFiles)
  }

  /**
   * Adding dependency to default config
   */
  fun addDefaultConfigDependency(configuration: String,
                                          dependency: String,
                                          parsedModel: GradleBuildModel,
                                          matcher: DependencyMatcher): Set<PsiFile> {
    val changedFiles = mutableSetOf<PsiFile>()
    val defaultConfig = parsedModel.android().defaultConfig()
    val dependenciesModel = defaultConfig.dependencies()
    return addDependency(dependenciesModel, matcher, configuration, dependency, changedFiles)
  }

  private fun addDependency(
    dependenciesModel: DependenciesModel,
    matcher: DependencyMatcher,
    configuration: String,
    dependency: String,
    changedFiles: MutableSet<PsiFile>
  ): MutableSet<PsiFile> {
    if (!dependenciesModel.hasArtifact(matcher)) {
      dependenciesModel.addArtifact(configuration, dependency).also {
        changedFiles.addIfNotNull(dependenciesModel.holder.getFile())
      }
    }
    return changedFiles
  }

  private fun GradleDslElement.getFile(): PsiFile? {
    val result = psiElement?.containingFile
    return result ?: parent?.getFile()
  }

  override fun addPlatformDependency(configuration: String,
                                     dependency: String,
                                     enforced: Boolean,
                                     parsedModel: GradleBuildModel,
                                     matcher: DependencyMatcher): Set<PsiFile> {
    val changedFiles = mutableSetOf<PsiFile>()
    val buildscriptDependencies = parsedModel.dependencies()

    if (!buildscriptDependencies.hasArtifact(matcher)) {
      buildscriptDependencies.addPlatformArtifact(configuration, dependency, enforced).also {
        changedFiles.addIfNotNull(parsedModel.psiFile)
      }
    }
    return changedFiles
  }

  /**
   * Short version for add dependency to build type
   */
  fun addBuildTypeDependency(buildType: String, configuration: String, dependency: String, parsedModel: GradleBuildModel) =
    addBuildTypeDependency(buildType, configuration, dependency, parsedModel, ExactDependencyMatcher(configuration, dependency))

  /**
   * Short version for add dependency to flavor
   */
  fun addFlavorDependency(flavorName: String, configuration: String, dependency: String, parsedModel: GradleBuildModel) =
    addFlavorDependency(flavorName, configuration, dependency, parsedModel, ExactDependencyMatcher(configuration, dependency))

 companion object {
   fun getNameIfBuildDependency(configuration: String, existingBuildTypes: Set<String>): Pair<String, String>? {
     val prefixes = setOf("release", "debug") + existingBuildTypes
     return findByPrefix(configuration, prefixes)
   }

   private fun findByPrefix(configuration: String, existingPrefixes: Set<String>): Pair<String, String>? {
     if (existingPrefixes.isEmpty()) return null
     for (prefix in existingPrefixes) {
       if (configuration.startsWith(prefix)) {
         val prefixLength = prefix.length
         val suffix = configuration.substring(prefixLength)
         if (suffix.isNotEmpty() && suffix.first().isUpperCase())
           return Pair(prefix, suffix.replaceFirstChar { it.lowercase() })
       }
     }
     return null
   }

   fun getNameIfFlavorDependency(configuration: String, existingFlavors: Set<String>): Pair<String,String>? {
     val findByPrefixResult =findByPrefix(configuration, existingFlavors)
     if(findByPrefixResult != null) return findByPrefixResult
     val knownConfigurations= listOf (
        // order is important to avoid false flavor names for known configurations
        "androidTestApi", "androidTestImplementation", "androidTestCompile", "androidTestUtil",
        "testApi", "testImplementation", "testCompile",
        "feature", "api", "implementation", "compile",
      )
      for (suffix in knownConfigurations) {
        val updatedSuffix = suffix.replaceFirstChar { it.uppercase() }
        if (configuration == suffix) return null
        if (configuration.endsWith(updatedSuffix)) {
          val suffixLength = updatedSuffix.length
          val prefix = configuration.dropLast(suffixLength)
          if(prefix.isNotEmpty())
            return Pair(prefix, suffix)
        }
      }
      return null
    }
  }

}