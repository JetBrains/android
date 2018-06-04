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

import com.android.builder.model.ApiVersion
import com.android.builder.model.ProductFlavor
import com.android.builder.model.VectorDrawablesOptions
import com.android.ide.common.gradle.model.UnusedModelMethodException
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.VariantConfig
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.OldClassField
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.OldSigningConfig
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.toLegacy
import java.io.File

open class LegacyProductFlavor(private val variantConfig: VariantConfig) : ProductFlavor {
  override fun getName(): String = variantConfig.name
  override fun getManifestPlaceholders(): Map<String, Any> = variantConfig.manifestPlaceholders
  override fun getApplicationId(): String? = variantConfig.applicationId
  override fun getVersionName(): String? = variantConfig.versionName
  override fun getMinSdkVersion(): ApiVersion? = variantConfig.minSdkVersion?.toLegacy()
  override fun getVersionCode(): Int? = variantConfig.versionCode
  override fun getResValues(): Map<String, OldClassField> = variantConfig.resValues.mapValues { LegacyClassField(it.value) }
  override fun getConsumerProguardFiles(): Collection<File> = variantConfig.consumerProguardFiles
  override fun getTargetSdkVersion(): ApiVersion? = variantConfig.targetSdkVersion?.toLegacy()
  override fun getResourceConfigurations(): Collection<String> = variantConfig.resourceConfigurations

  override fun getTestApplicationId(): String? = throw UnusedModelMethodException("getTestApplicationId")
  override fun getTestInstrumentationRunner(): String? = throw UnusedModelMethodException("getTestInstrumentationRunner")
  override fun getApplicationIdSuffix(): String? = throw UnusedModelMethodException("getApplicationIdSuffix")
  override fun getVersionNameSuffix(): String? = throw UnusedModelMethodException("getVersionNameSuffix")
  override fun getMultiDexEnabled(): Boolean? = throw UnusedModelMethodException("getMultiDexEnabled")
  override fun getProguardFiles(): Collection<File> = throw UnusedModelMethodException("getProguardFiles")
  override fun getTestInstrumentationRunnerArguments(): Map<String, String> =
    throw UnusedModelMethodException("getTestInstrumentationRunnerArguments")

  override fun getVectorDrawables(): VectorDrawablesOptions = throw UnusedModelMethodException("getVectorDrawables")
  override fun getDimension(): String? = throw UnusedModelMethodException("getDimension")
  override fun getMaxSdkVersion(): Int? = throw UnusedModelMethodException("getMaxSdkVersion")
  override fun getSigningConfig(): OldSigningConfig? = throw UnusedModelMethodException("getSigningConfig")
  override fun getBuildConfigFields(): Map<String, OldClassField> = throw UnusedModelMethodException("getBuildConfigFields")
  override fun getTestProguardFiles(): Collection<File> = throw UnusedModelMethodException("getTestProguardFiles")
  override fun getMultiDexKeepFile(): File? = throw UnusedModelMethodException("getMultiDexKeepFile")
  override fun getMultiDexKeepProguard(): File? = throw UnusedModelMethodException("getMultiDexKeepProguard")
  override fun getRenderscriptTargetApi(): Int? = throw UnusedModelMethodException("getRenderscriptTargetApi")
  override fun getRenderscriptSupportModeEnabled(): Boolean? = throw UnusedModelMethodException("getRenderscriptSupportModeEnabled")
  override fun getRenderscriptSupportModeBlasEnabled(): Boolean? = throw UnusedModelMethodException("getRenderscriptSupportModeBlasEnabled")
  override fun getRenderscriptNdkModeEnabled(): Boolean? = throw UnusedModelMethodException("getRenderscriptNdkModeEnabled")
  override fun getTestHandleProfiling(): Boolean? = throw UnusedModelMethodException("getTestHandleProfiling")
  override fun getTestFunctionalTest(): Boolean? = throw UnusedModelMethodException("getTestFunctionalTest")
  override fun getWearAppUnbundled(): Boolean? = throw UnusedModelMethodException("getWearAppUnbundled")

  override fun toString(): String = "LegacyBaseArtifact{" +
                                    "name=$name," +
                                    "manifestPlaceholders=$manifestPlaceholders," +
                                    "applicationId=$applicationId," +
                                    "versionName=$versionName," +
                                    "minSdkVersion=$minSdkVersion," +
                                    "versionCode=$versionCode," +
                                    "resValues=$resValues," +
                                    "consumerProguardFiles=$consumerProguardFiles," +
                                    "targetSdkVersion=$targetSdkVersion," +
                                    "resourceConfigurations=$resourceConfigurations" +
                                    "}"
}

