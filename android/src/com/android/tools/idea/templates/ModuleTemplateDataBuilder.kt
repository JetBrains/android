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

import com.android.AndroidProjectTypes.PROJECT_TYPE_DYNAMIC_FEATURE
import com.android.SdkConstants.FD_TEST
import com.android.SdkConstants.FD_UNIT_TEST
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.AndroidVersion
import com.android.sdklib.AndroidVersion.VersionCodes.P
import com.android.tools.idea.gradle.util.DynamicAppUtils
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.model.AndroidModuleInfo
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.npw.ThemeHelper
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.template.hasAndroidxSupport
import com.android.tools.idea.projectsystem.AndroidModulePaths
import com.android.tools.idea.templates.TemplateAttributes.ATTR_APP_THEME_APP_BAR_OVERLAY
import com.android.tools.idea.templates.TemplateAttributes.ATTR_APP_THEME_NO_ACTION_BAR
import com.android.tools.idea.templates.TemplateAttributes.ATTR_APP_THEME_POPUP_OVERLAY
import com.android.tools.idea.wizard.template.ApiTemplateData
import com.android.tools.idea.wizard.template.BaseFeature
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageName
import com.android.tools.idea.wizard.template.ThemesData
import com.intellij.openapi.module.Module
import com.android.tools.idea.wizard.template.ThemeData
import com.android.tools.idea.wizard.template.Version
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidRootUtil
import org.jetbrains.android.facet.SourceProviderManager
import org.jetbrains.android.sdk.AndroidPlatform
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
class ModuleTemplateDataBuilder(val projectTemplateDataBuilder: ProjectTemplateDataBuilder) {
  var srcDir: File? = null
  var resDir: File? = null
  var manifestDir: File? = null
  var testDir: File? = null
  var unitTestDir: File? = null
  var aidlDir: File? = null
  var rootDir: File? = null
  var themeExists: Boolean = false
  var isNew: Boolean? = null
  var hasApplicationTheme: Boolean = true
  var name: String? = null
  var isLibrary: Boolean? = null
  var packageName: PackageName? = null
  var formFactor: FormFactor? = null
  var themesData: ThemesData? = null
  var baseFeature: BaseFeature? = null
  var apis: ApiTemplateData? = null

  /**
   * Adds common module roots template values like [projectOut], [srcDir], etc
   * @param paths       Project paths
   * @param packageName Package Name for the module
   */
  fun setModuleRoots(paths: AndroidModulePaths, projectPath: String, moduleName: String, packageName: String) {
    val moduleRoot = paths.moduleRoot!!

    // Register the resource directories associated with the active source provider
    rootDir = File(FileUtil.toSystemIndependentName(moduleRoot.absolutePath))
    srcDir = paths.getSrcDirectory(packageName)
    testDir = paths.getTestDirectory(packageName)
    unitTestDir = paths.getUnitTestDirectory(packageName)
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
    projectTemplateDataBuilder.setEssentials(facet.module.project)

    val target = AndroidPlatform.getInstance(facet.module)?.target
    val moduleInfo = AndroidModuleInfo.getInstance(facet)
    val minSdkVersion = moduleInfo.minSdkVersion

    apis = ApiTemplateData(
      minSdkVersion.apiString,
      minSdkVersion.featureLevel,
      target?.version?.featureLevel?.coerceIfNeeded(facet.module.project),
      moduleInfo.targetSdkVersion.apiLevel,
      moduleInfo.targetSdkVersion.codename,
      target?.version?.toApiString(),
      if (target?.version?.isPreview == true) target.revision else 0
    )

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
    val androidFacet = AndroidFacet.getInstance(baseFeature)!!
    val mainSourceProvider = SourceProviderManager.getInstance(androidFacet).mainIdeaSourceProvider
    val baseModuleResourceRootPath = mainSourceProvider.resDirectories.firstOrNull()?.path
                                     ?: VfsUtilCore.urlToPath(mainSourceProvider.resDirectoryUrls.first())

    this.baseFeature = BaseFeature(
      GradleUtil.getGradlePath(baseFeature).orEmpty(),
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

    apis = ApiTemplateData(
      buildVersion.minApiLevelStr,
      buildVersion.minApiLevel,
      buildVersion.buildApiLevel.coerceIfNeeded(project),
      buildVersion.targetApiLevel,
      buildVersion.targetApiLevelStr,
      buildVersion.buildApiLevelStr,
      // Note here that the target is null for a non-preview release, see VersionItem.getAndroidTarget()
      buildVersion.androidTarget?.revision ?: 0
    )
  }

  /**
   * Adds information about application theme.
   */
  private fun setApplicationTheme(facet: AndroidFacet) {
    val module = facet.module
    val projectFile = module.project.projectFile ?: return
    val helper = ThemeHelper(module)
    val themeName = helper.appThemeName ?: return
    val configuration = ConfigurationManager.getOrCreateInstance(module).getConfiguration(projectFile)

    fun getDerivedTheme(themeName: String, derivedThemeName: String, useBaseThemeAsDerivedTheme: Boolean): ThemeData {
      val fullThemeName = if (useBaseThemeAsDerivedTheme) themeName else "$themeName.$derivedThemeName"
      val exists = ThemeHelper.themeExists(configuration, fullThemeName)
      if (!exists && !helper.isLocalTheme(themeName)) {
        return ThemeData(derivedThemeName, helper.isLocalTheme(derivedThemeName))
      }

      return ThemeData(fullThemeName, exists)
    }

    ApplicationManager.getApplication().runReadAction {
      val hasActionBar = ThemeHelper.hasActionBar(configuration, themeName)
      themesData = ThemesData(
        ThemeData(themeName, true),
        getDerivedTheme(themeName, ATTR_APP_THEME_NO_ACTION_BAR, hasActionBar == false),
        getDerivedTheme(themeName, ATTR_APP_THEME_APP_BAR_OVERLAY, false),
        getDerivedTheme(themeName, ATTR_APP_THEME_POPUP_OVERLAY, false)
      )
    }
  }

  private fun Version.coerceIfNeeded(project: Project, isNewProject: Boolean = false) =
    if (project.hasAndroidxSupport(isNewProject))
      this
    else
      this.coerceAtMost(P) // The highest supported/recommended appCompact version is P(28)

  fun build() = ModuleTemplateData(
    projectTemplateDataBuilder.build(),
    srcDir!!,
    resDir!!,
    manifestDir!!,
    testDir ?: srcDir!!.resolve(FD_TEST),
    unitTestDir ?: srcDir!!.resolve(FD_UNIT_TEST),
    aidlDir!!,
    rootDir!!,
    themeExists,
    isNew!!,
    hasApplicationTheme,
    name!!,
    isLibrary!!,
    packageName!!,
    formFactor!!,
    themesData ?: ThemesData(),
    baseFeature,
    apis!!
  )
}

/**
 * Computes a suitable build api string, e.g. for API level 18 the build API string is "18".
 */
fun AndroidVersion.toApiString(): String =
  if (isPreview) AndroidTargetHash.getPlatformHashString(this) else apiString
