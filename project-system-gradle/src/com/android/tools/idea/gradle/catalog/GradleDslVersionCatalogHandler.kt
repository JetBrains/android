// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.gradle.catalog

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.settings.VersionCatalogModel.DEFAULT_CATALOG_NAME
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.gradle.service.resolve.GradleVersionCatalogHandler

/**
 * This is a copy of JetBrains GradleVersionCatalogHandler that provides access to Studio Version Catalog model.
 * Class must be deleted once intellij.gradle.analysis is enabled in Studio or platform navigation relies on
 * gradle.dsl.api module
 */
class GradleDslVersionCatalogHandler : GradleVersionCatalogHandler {
  override fun getExternallyHandledExtension(project: Project): Set<String> {
    return getVersionCatalogFiles(project).keys
  }

  override fun getVersionCatalogFiles(project: Project): Map<String, VirtualFile> {
    return runReadAction {
      ProjectBuildModel.get(project).context.versionCatalogFiles.associate { it.catalogName to it.file }
    }
  }

  override fun getVersionCatalogFiles(module: Module): Map<String, VirtualFile> {
    val buildModel = getBuildModel(module) ?: return emptyMap()
    return buildModel.context.versionCatalogFiles.associate { it.catalogName to it.file }
  }

  override fun getAccessorClass(context: PsiElement, catalogName: String): PsiClass? {
    val project = context.project
    val scope = context.resolveScope
    val module = ModuleUtilCore.findModuleForPsiElement(context) ?: return null
    val buildModel = getBuildModel(module) ?: return null
    val versionCatalogModel = buildModel.versionCatalogsModel
    if (versionCatalogModel.getVersionCatalogModel(catalogName) == null) return null
    return SyntheticVersionCatalogAccessor.create(project, scope, versionCatalogModel, catalogName)
  }

  override fun getAccessorsForAllCatalogs(context: PsiElement): Map<String, PsiClass> {
    return emptyMap()
  }

  fun getDefaultCatalogName(project: Project): String {
    return runReadAction {
      val settingsModel = ProjectBuildModel.get(project).projectSettingsModel
      settingsModel?.dependencyResolutionManagement()?.catalogDefaultName() ?: DEFAULT_CATALOG_NAME
    }
  }

  private fun getBuildModel(module: Module): ProjectBuildModel? {
    val buildPath = ExternalSystemModulePropertyManager.getInstance(module)
                      .getLinkedProjectPath() ?: return null
    return ProjectBuildModel.getForCompositeBuild(module.project, buildPath)
  }
}