/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle.sync

import com.intellij.facet.Facet
import com.intellij.facet.FacetConfiguration
import com.intellij.facet.FacetType
import com.intellij.facet.FacetTypeId
import com.intellij.facet.FacetTypeRegistry
import com.intellij.facet.ui.FacetEditorContext
import com.intellij.facet.ui.FacetEditorTab
import com.intellij.facet.ui.FacetValidatorsManager
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.util.Key
import org.jetbrains.android.facet.AndroidFacet

val ANDROID_ARTIFACT_FACET_ID = FacetTypeId<AndroidArtifactFacet>("android-artifact")

class AndroidArtifactFacetConfiguration : FacetConfiguration {
  override fun createEditorTabs(editorContext: FacetEditorContext?, validatorsManager: FacetValidatorsManager?): Array<FacetEditorTab>
    = emptyArray()
}

class AndroidArtifactFacetType : FacetType<AndroidArtifactFacet, AndroidArtifactFacetConfiguration>(
  ANDROID_ARTIFACT_FACET_ID,
  "android-artifact",
  AndroidArtifactFacet.NAME
) {
  override fun createDefaultConfiguration(): AndroidArtifactFacetConfiguration = AndroidArtifactFacetConfiguration()

  override fun createFacet(
    module: Module,
    name: String,
    configuration: AndroidArtifactFacetConfiguration,
    underlyingFacet: Facet<*>?
  ): AndroidArtifactFacet = AndroidArtifactFacet(module, name, configuration)

  override fun isSuitableModuleType(moduleType: ModuleType<*>?): Boolean = moduleType is JavaModuleType
}

// This key will be present on all AndroidArtifactFacets it will link to the AndroidFacet of the parent module
private val ANDROID_FACET_KEY : Key<AndroidFacet> = Key.create(AndroidFacet::class.java.name)

class AndroidArtifactFacet(
  module: Module,
  name: String,
  config: AndroidArtifactFacetConfiguration
) : Facet<AndroidArtifactFacetConfiguration>(getFacetType(), module, name, config, null) {
  companion object {
    val NAME = "Android Artifact"
    fun getFacetType() = FacetTypeRegistry.getInstance().findFacetType(ANDROID_ARTIFACT_FACET_ID) as AndroidArtifactFacetType
  }

  fun linkToAndroidFacet(androidFacet: AndroidFacet) {
    putUserData(ANDROID_FACET_KEY, androidFacet)
  }

  fun getLinkedAndroidFacet() : AndroidFacet? = getUserData(ANDROID_FACET_KEY)
}