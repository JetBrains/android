/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea

import com.android.AndroidProjectTypes
import com.android.builder.model.v2.ide.ProjectType
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.intellij.facet.FacetConfiguration
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetTypeId
import com.intellij.facet.impl.FacetUtil
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.FacetEntityTypeId
import com.intellij.platform.workspace.jps.entities.FacetId
import com.intellij.platform.workspace.jps.entities.ModifiableFacetEntity
import com.intellij.platform.workspace.jps.entities.ModifiableModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.modifyFacetEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

internal fun SyncContributorAndroidProjectContext.createOrUpdateAndroidGradleFacet(
  storage: MutableEntityStorage,
  moduleEntity: ModifiableModuleEntity
) {
  val configuration = GradleFacet.getFacetType().createDefaultConfiguration().apply {
    LAST_KNOWN_AGP_VERSION = versions.agpVersionAsString
  }
  val facetId = FacetId(
    GradleFacet.ANDROID_GRADLE_FACET_NAME,
    FacetEntityTypeId(GradleFacet.getFacetType().stringId),
    ModuleId(moduleEntity.name)
  )
  withExistingFacetFromStorageOrNewBuilder(storage, moduleEntity.entitySource, facetId) {
    updateFacet(GradleFacet.getFacetTypeId(), configuration, moduleEntity, it)
  }
}

internal fun SyncContributorAndroidProjectContext.createOrUpdateAndroidFacet(
  storage: MutableEntityStorage,
  moduleEntity: ModifiableModuleEntity
) {
  val configuration = AndroidFacet.getFacetType().createDefaultConfiguration().apply {
    state.apply {
      @Suppress("DEPRECATION") // One of the legitimate assignments to the property.
      ALLOW_USER_CONFIGURATION = false

      PROJECT_TYPE = when (basicAndroidProject.projectType) {
        ProjectType.APPLICATION -> AndroidProjectTypes.PROJECT_TYPE_APP
        ProjectType.LIBRARY -> AndroidProjectTypes.PROJECT_TYPE_LIBRARY
        ProjectType.DYNAMIC_FEATURE -> AndroidProjectTypes.PROJECT_TYPE_DYNAMIC_FEATURE
        ProjectType.TEST -> AndroidProjectTypes.PROJECT_TYPE_TEST
        ProjectType.FUSED_LIBRARY -> AndroidProjectTypes.PROJECT_TYPE_FUSED_LIBRARY
      }

      val modulePath = projectModel.projectDirectory
      // Copied from data service logic, not using File.separator but explicitly "/", not sure why (also the case in the XML representation)
      val separator = "/"
      fun String.prependSeparator() = if (this.startsWith(separator)) this else separator + this
      fun File?.relativizeOrEmpty(): String = this?.relativeTo(modulePath)?.path?.prependSeparator().orEmpty()

      // Note: Full sync also sets GradleAndroidModel on the facet, but we can't do that here.
      val sourceProvider = basicAndroidProject.mainSourceSet?.sourceProvider
      MANIFEST_FILE_RELATIVE_PATH = sourceProvider?.manifestFile.relativizeOrEmpty()
      RES_FOLDER_RELATIVE_PATH = sourceProvider?.resDirectories?.firstOrNull().relativizeOrEmpty()
      ASSETS_FOLDER_RELATIVE_PATH = sourceProvider?.assetsDirectories?.firstOrNull().relativizeOrEmpty()
      SELECTED_BUILD_VARIANT = variantName
    }
  }

  val facetId = FacetId(
    AndroidFacet.NAME,
    FacetEntityTypeId(AndroidFacet.getFacetType().stringId),
    ModuleId(moduleEntity.name)
  )

  withExistingFacetFromStorageOrNewBuilder(storage, moduleEntity.entitySource, facetId) {
    updateFacet(AndroidFacet.getFacetType().id, configuration, moduleEntity, it)
  }
}

private fun SyncContributorAndroidProjectContext.updateFacet(
  facetTypeId: FacetTypeId<*>,
  configuration: FacetConfiguration,
  moduleEntity: ModifiableModuleEntity,
  existingFacet: ModifiableFacetEntity) {
  // Set external source for facet, as this is not serialized
  registerModuleAction(moduleEntity.name) { module ->
    val facet = FacetManager.getInstance(module).getFacetByType(facetTypeId) ?: return@registerModuleAction
    facet.externalSource = ExternalProjectSystemRegistry.getInstance().getSourceById(GradleConstants.SYSTEM_ID.id)
  }

  // Associate facet with the module entity
  existingFacet.module = moduleEntity

  // Set facet configuration XML
  val configurationString = JDOMUtil.write(FacetUtil.saveFacetConfiguration(configuration) ?: return)
  if (existingFacet.configurationXmlTag != configurationString) {
    existingFacet.configurationXmlTag = configurationString
  }
}

private fun withExistingFacetFromStorageOrNewBuilder(
  storage: MutableEntityStorage,
  entitySource: EntitySource,
  facetId: FacetId,
  facetUpdater: (facetEntity: ModifiableFacetEntity) -> Unit
) {
  storage.resolve(facetId)?.let {
    storage.modifyFacetEntity(it, facetUpdater)
  } ?: facetUpdater(FacetEntity(facetId.parentId, facetId.name, facetId.type, entitySource))
}
