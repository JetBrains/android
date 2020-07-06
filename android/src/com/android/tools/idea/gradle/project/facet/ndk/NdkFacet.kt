/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.facet.ndk

import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.intellij.ProjectTopics
import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetTypeId
import com.intellij.facet.FacetTypeRegistry
import com.intellij.facet.impl.FacetUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.WriteExternalException
import com.intellij.psi.PsiDocumentManager

class NdkFacet(module: Module, name: String, configuration: NdkFacetConfiguration
) : Facet<NdkFacetConfiguration?>(facetType, module, name, configuration, null) {
  var ndkModuleModel: NdkModuleModel? = null
    private set

  override fun initFacet() {
    // TODO(tgeng): Remove this listener and write to disk directly after the selected varaint ABI is moved from NdkModuleModel to here.
    val connection = module.messageBus.connect(this)
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) = ApplicationManager.getApplication().invokeLater(
        {
          if (isDisposed) {
            return@invokeLater
          }
          PsiDocumentManager.getInstance(module.project).commitAllDocuments()
          writeConfigurationToDisk()
        }, ModalityState.NON_MODAL)
    })
    writeConfigurationToDisk()
  }

  private fun writeConfigurationToDisk() {
    try {
      FacetUtil.saveFacetConfiguration(configuration)
    }
    catch (e: WriteExternalException) {
      Logger.getInstance(NdkFacet::class.java).error("Unable to save contents of '$facetName' facet.", e)
    }
  }

  fun setNdkModuleModel(ndkModuleModel: NdkModuleModel) {
    this.ndkModuleModel = ndkModuleModel
    configuration.SELECTED_BUILD_VARIANT = ndkModuleModel.selectedVariant.name
  }

  companion object {
    @JvmStatic
    val facetId: String = "native-android-gradle"

    @JvmStatic
    val facetName: String = "Native-Android-Gradle"

    @JvmStatic
    val facetTypeId = FacetTypeId<NdkFacet>(facetId)

    @JvmStatic
    fun getInstance(module: Module): NdkFacet? {
      return FacetManager.getInstance(module).getFacetByType(facetTypeId)
    }

    @JvmStatic
    val facetType: NdkFacetType
      get() {
        val facetType = FacetTypeRegistry.getInstance().findFacetType(facetId)
        assert(facetType is NdkFacetType)
        return facetType as NdkFacetType
      }
  }
}