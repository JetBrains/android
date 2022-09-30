/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model

import com.android.ide.common.repository.GradleVersion.AgpVersion
import com.android.tools.idea.gradle.model.IdeAndroidArtifactCore
import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeLibraryModelResolver
import com.android.tools.idea.gradle.model.IdeSourceProvider
import com.android.tools.idea.gradle.model.IdeVariant
import com.android.tools.idea.gradle.model.IdeVariantCore
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantCoreImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantImpl
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import java.io.File

private const val ourAndroidSyncVersion = "2022-07-26/1"

interface GradleAndroidModelData : ModuleModel {
  val androidSyncVersion: String
  val rootDirPath: File
  val androidProject: IdeAndroidProject
  val variants: Collection<IdeVariantCore>
  val selectedVariantName: String
  val agpVersion: AgpVersion
  val selectedVariantCore: IdeVariantCore
  val mainArtifactCore: IdeAndroidArtifactCore
  fun getJavaLanguageLevel(): LanguageLevel?
  fun selectedVariant(resolver: IdeLibraryModelResolver): IdeVariant
  fun getTestSourceProviders(artifactName: IdeArtifactName): List<IdeSourceProvider>
  fun findVariantCoreByName(variantName: String): IdeVariantCore?
}

data class GradleAndroidModelDataImpl(
  override val androidSyncVersion: String,
  private val moduleName: String,
  override val rootDirPath: File,
  override val androidProject: IdeAndroidProjectImpl,
  override val variants: Collection<IdeVariantCoreImpl>,
  override val selectedVariantName: String
) : GradleAndroidModelData {
  init {
    require(androidSyncVersion == ourAndroidSyncVersion) {
      String.format(
        "Attempting to deserialize a model of incompatible version (%s)",
        androidSyncVersion
      )
    }
  }

  override fun getModuleName(): String = moduleName

  override val agpVersion: AgpVersion get() = AgpVersion.parse(androidProject.agpVersion)

  override fun findVariantCoreByName(variantName: String): IdeVariantCore? {
    // Note, when setting up projects models contain just one variant.
    return variants.find { it.name == variantName }
  }

  override val selectedVariantCore: IdeVariantCoreImpl
    get() {
      // Note, when setting up projects models contain just one variant.
      return variants.single { it.name == selectedVariantName }
    }

  override fun selectedVariant(resolver: IdeLibraryModelResolver): IdeVariantImpl {
    return IdeVariantImpl(selectedVariantCore, resolver)
  }

  override val mainArtifactCore: IdeAndroidArtifactCore get() = selectedVariantCore.mainArtifact

  override fun getJavaLanguageLevel(): LanguageLevel? {
    val compileOptions = androidProject.javaCompileOptions
    val sourceCompatibility = compileOptions.sourceCompatibility
    return LanguageLevel.parse(sourceCompatibility)
  }

  override fun getTestSourceProviders(artifactName: IdeArtifactName): List<IdeSourceProvider> {
    return when (artifactName) {
      IdeArtifactName.ANDROID_TEST -> androidTestSourceProviders
      IdeArtifactName.UNIT_TEST -> unitTestSourceProviders
      IdeArtifactName.MAIN -> emptyList()
      IdeArtifactName.TEST_FIXTURES -> emptyList()
    }
  }

  companion object {
    fun findFromModuleDataNode(dataNode: DataNode<*>): GradleAndroidModelData? {
      return when (dataNode.key) {
        ProjectKeys.MODULE -> ExternalSystemApiUtil.find(dataNode, AndroidProjectKeys.ANDROID_MODEL)?.data
        GradleSourceSetData.KEY -> dataNode.parent?.let { findFromModuleDataNode(it) }
        else -> null
      }
    }

    @JvmStatic
    fun create(
      moduleName: String,
      rootDirPath: File,
      androidProject: IdeAndroidProjectImpl,
      cachedVariants: Collection<IdeVariantCoreImpl>,
      variantName: String
    ): GradleAndroidModelData {
      return GradleAndroidModelDataImpl(
        ourAndroidSyncVersion,
        moduleName,
        rootDirPath,
        androidProject,
        cachedVariants,
        variantName
      )
    }
  }
}
