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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade.stubs

import com.android.builder.model.BuildType
import com.android.builder.model.SigningConfig
import com.android.ide.common.gradle.model.UnusedModelMethodException
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.VariantConfig
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade.LegacyClassField
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.BUILD_TYPE_NAME
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.OldClassField
import java.io.File

data class BuildTypeStub(
  private val name: String,
  private val resValues: Map<String, OldClassField>,
  //private val proguardFiles: Collection<File>, FIXME(qumeric)
  private val consumerProguardFiles: Collection<File>,
  private val manifestPlaceholders: Map<String, Any>,
  private val isDebuggable: Boolean
) : BuildType {
  constructor(variantConfig: VariantConfig): this(
    BUILD_TYPE_NAME,
    variantConfig.resValues.mapValues { LegacyClassField(it.value) },
    //variantConfig.proguardFiles,
    variantConfig.consumerProguardFiles,
    variantConfig.manifestPlaceholders,
    variantConfig.isDebuggable
  )

  override fun getName(): String = name
  override fun getResValues(): Map<String, OldClassField> = resValues
  override fun getProguardFiles(): Collection<File> = listOf()//proguardFiles
  override fun getConsumerProguardFiles(): Collection<File> = consumerProguardFiles
  override fun getManifestPlaceholders(): Map<String, Any> = manifestPlaceholders
  override fun isDebuggable(): Boolean = isDebuggable

  override fun getApplicationIdSuffix(): String? = null
  override fun getVersionNameSuffix(): String? = null
  override fun getMultiDexEnabled(): Boolean? = null
  override fun isJniDebuggable(): Boolean = false
  override fun isRenderscriptDebuggable(): Boolean = false
  override fun getRenderscriptOptimLevel(): Int = 0
  override fun isMinifyEnabled(): Boolean = false
  override fun isZipAlignEnabled(): Boolean = false

  override fun getBuildConfigFields(): Map<String, OldClassField> = throw UnusedModelMethodException("getBuildConfigFields")
  override fun getTestProguardFiles(): Collection<File> = throw UnusedModelMethodException("getTestProguardFiles")
  override fun getMultiDexKeepFile(): File? = throw UnusedModelMethodException("getMultiDexKeepFile")
  override fun getMultiDexKeepProguard(): File? = throw UnusedModelMethodException("getMultiDexKeepProguard")
  override fun getSigningConfig(): SigningConfig? = throw UnusedModelMethodException("getSigningConfig")
  override fun isTestCoverageEnabled(): Boolean = throw UnusedModelMethodException("isTestCoverageEnabled")
  override fun isPseudoLocalesEnabled(): Boolean = throw UnusedModelMethodException("isPseudoLocalesEnabled")
  override fun isEmbedMicroApp(): Boolean = throw UnusedModelMethodException("isEmbedMicroApp")
}
