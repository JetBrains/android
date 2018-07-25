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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder

import com.android.builder.model.ProductFlavor
import com.android.builder.model.SourceProvider
import com.android.ide.common.gradle.model.level2.IdeDependencies
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.*
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.variant.getLevel2Dependencies

private fun <T> notNullCount(vararg values: T?) = values.fold(0) { count, t -> if (t != null) count + 1 else count }

private infix fun <T> Collection<T>.zipSameSizeOrNull(other: Collection<T>): Collection<Pair<T, T>>? {
  if (this.size != other.size) return null
  return (this zip other)
}

private infix fun <T> Collection<T>.sameContentWith(other: Collection<T>?): Boolean {
  return other?.let { this.size == it.size && this.containsAll(it) } ?: false
}

infix fun OldAaptOptions.essentiallyEquals(other: OldAaptOptions): Boolean {
  return this.namespacing == other.namespacing
}

infix fun OldAndroidArtifact.essentiallyEquals(other: OldAndroidArtifact): Boolean {
  when (notNullCount(testOptions, other.testOptions)) {
    1 -> return false
    2 -> if (!(testOptions!! essentiallyEquals other.testOptions!!)) return false
  }
  when (notNullCount(abiFilters, other.abiFilters)) {
    1 -> return false
    2 -> if (!(abiFilters!! sameContentWith other.abiFilters!!)) return false
  }

  return (this as OldBaseArtifact) essentiallyEquals other &&
         isSigned == other.isSigned &&
         signingConfigName == other.signingConfigName &&
         applicationId == other.applicationId &&
         sourceGenTaskName == other.sourceGenTaskName &&
         instantRun essentiallyEquals other.instantRun &&
         additionalRuntimeApks sameContentWith other.additionalRuntimeApks &&
         resValues essentiallyEquals other.resValues &&
         generatedResourceFolders sameContentWith other.generatedResourceFolders &&
         bundleTaskName == other.bundleTaskName &&
         apkFromBundleTaskName == other.apkFromBundleTaskName &&
         instrumentedTestTaskName == other.instrumentedTestTaskName
  // TODO(qumeric) nativeLibraries == other.nativeLibraries
}

private typealias OldResValues = Map<String, OldClassField>

infix fun OldResValues.essentiallyEquals(other: OldResValues): Boolean {
  if (keys != other.keys) {
    return false
  }
  for (key in keys) {
    if (!(this[key]!! essentiallyEquals other[key]!!)) {
      return false
    }
  }
  return true
}

// There is no special method for OldJavaArtifact because mockablePlatformJar is not cached (but set to null)
infix fun OldBaseArtifact.essentiallyEquals(other: OldBaseArtifact): Boolean {
  when (notNullCount(variantSourceProvider, other.variantSourceProvider)) {
    1 -> return false
    2 -> if (!(variantSourceProvider!! essentiallyEquals other.variantSourceProvider!!)) return false
  }

  return name == other.name &&
         compileTaskName == other.compileTaskName &&
         assembleTaskName == other.assembleTaskName &&
         classesFolder == other.classesFolder &&
         additionalClassesFolders sameContentWith other.additionalClassesFolders &&
         javaResourcesFolder == other.javaResourcesFolder &&
         ideSetupTaskNames sameContentWith other.ideSetupTaskNames &&
         generatedSourceFolders sameContentWith other.generatedSourceFolders &&
         getLevel2Dependencies() essentiallyEquals other.getLevel2Dependencies()
}

infix fun IdeDependencies.essentiallyEquals(other: IdeDependencies): Boolean {
  return androidLibraries sameContentWith other.androidLibraries &&
         javaLibraries sameContentWith other.javaLibraries &&
         moduleDependencies sameContentWith other.moduleDependencies
}

// It does not compare signingConfigs because there are not used in shipped sync
// Also it only comparing existing variants so [release, debug] == [debug] if debug == debug
// It works this way for Single Variant Sync support
infix fun OldAndroidProject.essentiallyEquals(other: OldAndroidProject): Boolean {
  // Do not confuse with variantNames, which contains all the variants
  val thisVariantNames = variants.map { it.name }.toSet()
  val otherVariantNames = other.variants.map { it.name }.toSet()
  val commonVariantNames = thisVariantNames intersect otherVariantNames
  val thisVariants = variants.filter { it.name in commonVariantNames }
  val otherVariants = other.variants.filter { it.name in commonVariantNames }
  val zippedVariants = thisVariants zipSameSizeOrNull otherVariants // TODO(qumeric): sort variants first

  if (commonVariantNames.isEmpty()) {
    return false
  }

  return modelVersion == other.modelVersion &&
         apiVersion == other.apiVersion &&
         name == other.name &&
         projectType == other.projectType &&
         variantNames sameContentWith other.variantNames &&
         compileTarget == other.compileTarget &&
         bootClasspath sameContentWith other.bootClasspath &&
         aaptOptions essentiallyEquals other.aaptOptions &&
         syncIssues sameContentWith other.syncIssues &&
         javaCompileOptions essentiallyEquals other.javaCompileOptions &&
         buildFolder == other.buildFolder &&
         isBaseSplit == other.isBaseSplit &&
         dynamicFeatures sameContentWith other.dynamicFeatures &&
         zippedVariants?.all { it.first essentiallyEquals it.second } ?: false
  // TODO(qumeric): nativeToolchains == other.nativeToolchains
}

infix fun OldApiVersion.essentiallyEquals(other: OldApiVersion): Boolean {
  return apiLevel == other.apiLevel &&
         codename == other.codename &&
         apiString == other.apiString
}

infix fun OldClassField.essentiallyEquals(other: OldClassField): Boolean {
  return type == other.type &&
         name == other.name &&
         value == other.value
}

infix fun OldInstantRun.essentiallyEquals(other: OldInstantRun): Boolean {
  return infoFile == other.infoFile &&
         isSupportedByArtifact == other.isSupportedByArtifact &&
         supportStatus == other.supportStatus
}

infix fun OldJavaCompileOptions.essentiallyEquals(other: OldJavaCompileOptions): Boolean {
  return encoding == other.encoding &&
         sourceCompatibility == other.sourceCompatibility &&
         targetCompatibility == other.targetCompatibility
}

infix fun ProductFlavor.essentiallyEquals(other: ProductFlavor): Boolean {
  when (notNullCount(minSdkVersion, other.minSdkVersion)) {
    1 -> return false
    2 -> if (!(minSdkVersion!! essentiallyEquals other.minSdkVersion!!)) return false
  }
  when (notNullCount(targetSdkVersion, targetSdkVersion)) {
    1 -> return false
    2 -> if (!(targetSdkVersion!! essentiallyEquals other.targetSdkVersion!!)) return false
  }
  return name == other.name &&
         manifestPlaceholders == other.manifestPlaceholders && // map equality
         applicationId == other.applicationId &&
         versionName == other.versionName &&
         versionCode == other.versionCode &&
         resValues == other.resValues && // map equality
         consumerProguardFiles sameContentWith other.consumerProguardFiles &&
         resourceConfigurations sameContentWith other.resourceConfigurations
}

infix fun OldSigningConfig.essentiallyEquals(other: OldSigningConfig): Boolean {
  return name == other.name &&
         storeFile == other.storeFile &&
         storePassword == other.storePassword &&
         keyAlias == other.keyAlias
}

infix fun SourceProvider.essentiallyEquals(other: SourceProvider): Boolean {
  return name == other.name &&
         manifestFile == other.manifestFile &&
         javaDirectories sameContentWith other.javaDirectories &&
         resourcesDirectories sameContentWith other.resourcesDirectories &&
         aidlDirectories sameContentWith other.aidlDirectories &&
         renderscriptDirectories sameContentWith other.renderscriptDirectories &&
         cDirectories sameContentWith other.cDirectories &&
         cppDirectories sameContentWith other.cppDirectories &&
         resDirectories sameContentWith other.resDirectories &&
         assetsDirectories sameContentWith other.assetsDirectories &&
         jniLibsDirectories sameContentWith other.jniLibsDirectories &&
         shadersDirectories sameContentWith other.shadersDirectories
}

infix fun OldTestedTargetVariant.essentiallyEquals(other: OldTestedTargetVariant): Boolean {
  return targetProjectPath == other.targetProjectPath &&
         targetVariant == other.targetVariant
}

infix fun OldTestOptions.essentiallyEquals(other: OldTestOptions): Boolean {
  return execution == other.execution
}

infix fun OldVariant.essentiallyEquals(other: OldVariant): Boolean {
  return name == other.name &&
         displayName == other.displayName &&
         mainArtifact essentiallyEquals other.mainArtifact &&
         (extraAndroidArtifacts zipSameSizeOrNull other.extraAndroidArtifacts)?.all { it.first essentiallyEquals it.second } ?: false &&
         (extraJavaArtifacts zipSameSizeOrNull other.extraJavaArtifacts)?.all { it.first essentiallyEquals it.second } ?: false &&
         (testedTargetVariants zipSameSizeOrNull other.testedTargetVariants)?.all { it.first essentiallyEquals it.second } ?: false &&
         mergedFlavor essentiallyEquals other.mergedFlavor
}
