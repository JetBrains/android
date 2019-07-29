/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.templates

import com.android.builder.model.AndroidProject.PROJECT_TYPE_DYNAMIC_FEATURE
import com.android.tools.idea.gradle.util.DynamicAppUtils
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.npw.ThemeHelper
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.projectsystem.AndroidModulePaths
import com.android.tools.idea.templates.TemplateAttributes.ATTR_APP_THEME_APP_BAR_OVERLAY
import com.android.tools.idea.templates.TemplateAttributes.ATTR_APP_THEME_NO_ACTION_BAR
import com.android.tools.idea.templates.TemplateAttributes.ATTR_APP_THEME_POPUP_OVERLAY
import com.android.tools.idea.wizard.template.BaseFeature
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageName
import com.android.tools.idea.wizard.template.ThemesData
import com.intellij.openapi.module.Module
import com.android.tools.idea.wizard.template.ThemeData
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidRootUtil
import org.jetbrains.android.facet.SourceProviderManager
import java.io.File

/**
 * Helper method for converting two paths relative to one another into a String path, since this
 * ends up being a common pattern when creating values to put into our template's data model.
 *
 * Note: Use [FileUtil.getRelativePath] (String, String, Char) instead of [FileUtil.getRelativePath] (File, File), because the second
 * version will use [File.getParent] if base directory is not yet created (when adding a new module, the directory is created later)
 */
private fun getRelativePath(base: File, file: File): String? =
  FileUtil.getRelativePath(FileUtil.toSystemIndependentName(base.path), FileUtil.toSystemIndependentName(file.path), '/')

/**
 * Builder for [ModuleTemplateData].
 *
 * Extracts information from various data sources.
 */
class ModuleTemplateDataBuilder(private val isNewProject: Boolean) {
  var projectTemplateDataBuilder = ProjectTemplateDataBuilder(isNewProject)
  var srcDir: File? = null
  var resDir: File? = null
  var manifestDir: File? = null
  var testDir: File? = null
  var aidlDir: File? = null
  var projectOut: File? = null
  var themeExists: Boolean = false
  var isNew: Boolean? = null
  var hasApplicationTheme: Boolean = true
  var name: String? = null
  var isLibrary: Boolean? = null
  var packageName: PackageName? = null
  var formFactor: FormFactor? = null
  var themesData: ThemesData? = null
  var baseFeature: BaseFeature? = null

  /**
   * Adds common module roots template values like [projectOut], [srcDir], etc.
   *
   * @param paths       Project paths
   * @param packageName Package Name for the module
   */
  fun setModuleRoots(paths: AndroidModulePaths, projectPath: String, moduleName: String, packageName: String) {
    val moduleRoot = paths.moduleRoot!!

    // Register the resource directories associated with the active source provider
    projectOut = File(FileUtil.toSystemIndependentName(moduleRoot.absolutePath))

    srcDir = paths.getSrcDirectory(packageName)
    testDir = paths.getTestDirectory(packageName)
    resDir = paths.resDirectories.firstOrNull()
    manifestDir = paths.manifestDirectory
    aidlDir = paths.getAidlDirectory(packageName)

    projectTemplateDataBuilder.topOut = File(projectPath)
    name = moduleName.trimStart(':') // The templates already add an initial ":"
    this.packageName = packageName
  }

  /**
   * Sets information available in the facet.
   *
   * Used when the Module exists.
   */
  fun setFacet(facet: AndroidFacet) {
    projectTemplateDataBuilder.setFacet(facet)
    isNew = false
    isLibrary = facet.configuration.isLibraryProject

    val appTheme = MergedManifestManager.getMergedManifestSupplier(facet.module).now
    hasApplicationTheme = appTheme != null

    setApplicationTheme(facet)

    if (facet.configuration.projectType == PROJECT_TYPE_DYNAMIC_FEATURE) {
      val baseFeature = DynamicAppUtils.getBaseFeature(facet.module)
                        ?: throw RuntimeException("Dynamic Feature Module '${facet.module.name}' has no Base Module")

      setBaseFeature(baseFeature)
    }
  }

  /**
   * Adds information about base feature.
   *
   * Used only by dynamic modules.
   */
  fun setBaseFeature(baseFeature: Module) {

    fun String.toPath() = VfsUtilCore.urlToPath(this)

    val androidFacet = AndroidFacet.getInstance(baseFeature)!!
    val gradleFacet = GradleFacet.getInstance(baseFeature)!!
    val mainSourceProvider = SourceProviderManager.getInstance(androidFacet).mainIdeaSourceProvider
    val baseModuleResourceRootPath = mainSourceProvider.resDirectories.firstOrNull()?.path
                                     ?: mainSourceProvider.resDirectoryUrls.first().toPath()

    this.baseFeature = BaseFeature(
      gradleFacet.gradleModuleModel?.moduleName.orEmpty(),
      AndroidRootUtil.findModuleRootFolderPath(baseFeature)!!,
      File(baseModuleResourceRootPath) // Put the new resources in any of the available res directories
    )
  }

  /**
   * Same as [setFacet], but uses a [AndroidVersionsInfo.VersionItem].
   * This version is used when the Module is not created yet.
   *
   * @param buildVersion Build version information for the new Module being created.
   * @param project      Used to find the Gradle Dependencies versions.
   */
  fun setBuildVersion(buildVersion: AndroidVersionsInfo.VersionItem, project: Project) {
    projectTemplateDataBuilder.setBuildVersion(buildVersion, project)
    isNew = true
    themeExists = true // New modules always have a theme (unless its a library, but it will have no activity)
  }

  /**
   * Adds information about application theme.
   */
  fun setApplicationTheme(facet: AndroidFacet) {
    val module = facet.module
    val projectFile = module.project.projectFile ?: return
    val helper = ThemeHelper(module)
    val themeName = helper.appThemeName ?: return
    val configuration = ConfigurationManager.getOrCreateInstance(module).getConfiguration(projectFile)
    val hasActionBar = ThemeHelper.hasActionBar(configuration, themeName)

    fun getDerivedTheme(themeName: String, derivedThemeName: String, useBaseThemeAsDerivedTheme: Boolean): ThemeData {
      val fullThemeName = if (useBaseThemeAsDerivedTheme) themeName else "$themeName.$derivedThemeName"
      val exists = ThemeHelper.themeExists(configuration, fullThemeName)
      if (!exists && !helper.isLocalTheme(themeName)) {
        return ThemeData(derivedThemeName, helper.isLocalTheme(derivedThemeName))
      }

      return ThemeData(fullThemeName, exists)
    }

    themesData = ThemesData(
      ThemeData(themeName, true),
      getDerivedTheme(themeName, ATTR_APP_THEME_NO_ACTION_BAR, hasActionBar == false),
      getDerivedTheme(themeName, ATTR_APP_THEME_APP_BAR_OVERLAY, false),
      getDerivedTheme(themeName, ATTR_APP_THEME_POPUP_OVERLAY, false)
    )
  }

  internal fun build() = ModuleTemplateData(
    projectTemplateDataBuilder.build(),
    srcDir!!,
    resDir!!,
    manifestDir!!,
    testDir!!,
    aidlDir!!,
    projectOut!!,
    themeExists,
    isNew!!,
    hasApplicationTheme,
    name!!,
    isLibrary!!,
    packageName!!,
    formFactor!!,
    themesData ?: ThemesData(),
    baseFeature
  )
}
