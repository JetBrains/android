/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.npw.template

import com.android.AndroidProjectTypes.PROJECT_TYPE_DYNAMIC_FEATURE
import com.android.SdkConstants.FD_TEST
import com.android.SdkConstants.FD_UNIT_TEST
import com.android.sdklib.AndroidVersion.VersionCodes.P
import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_STABLE_API
import com.android.sdklib.SdkVersionInfo.LOWEST_ACTIVE_API
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate
import com.android.tools.idea.gradle.util.DynamicAppUtils
import com.android.tools.idea.hasKotlinFacet
import com.android.tools.idea.model.AndroidModuleInfo
import com.android.tools.idea.npw.ThemeHelper
import com.android.tools.idea.npw.model.isViewBindingSupported
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.projectsystem.AndroidModulePaths
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.android.tools.idea.templates.getAppNameForTheme
import com.android.tools.idea.util.toIoFile
import com.android.tools.idea.wizard.template.ApiTemplateData
import com.android.tools.idea.wizard.template.ApiVersion
import com.android.tools.idea.wizard.template.BaseFeature
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageName
import com.android.tools.idea.wizard.template.ThemeData
import com.android.tools.idea.wizard.template.ThemesData
import com.android.tools.idea.wizard.template.ViewBindingSupport
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidRootUtil
import org.jetbrains.android.facet.SourceProviderManager
import java.io.File

/**
 * Builder for [ModuleTemplateData].
 *
 * Extracts information from various data sources.
 */
class ModuleTemplateDataBuilder(
  val projectTemplateDataBuilder: ProjectTemplateDataBuilder,
  val isNewModule: Boolean,
  private val viewBindingSupport: ViewBindingSupport
) {
  private var srcDir: File? = null
  private var resDir: File? = null
  private var manifestDir: File? = null
  private var testDir: File? = null
  private var unitTestDir: File? = null
  private var aidlDir: File? = null
  var rootDir: File? = null
  var name: String? = null
  var isLibrary: Boolean? = null
  var packageName: PackageName? = null
  var formFactor: FormFactor? = null
  var themesData: ThemesData? = null
  private var baseFeature: BaseFeature? = null
  var apis: ApiTemplateData? = null
  var category: Category? = null
  var isMaterial3: Boolean = false
  var useGenericLocalTests: Boolean = true
  var useGenericInstrumentedTests: Boolean = true

  /**
   * Adds common module roots template values like [rootDir], [srcDir], etc
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

    val moduleInfo = AndroidModuleInfo.getInstance(facet)
    val targetSdkVersion = moduleInfo.targetSdkVersion
    val buildSdkVersion = moduleInfo.buildSdkVersion ?: targetSdkVersion
    val minSdkVersion = moduleInfo.minSdkVersion

    apis = ApiTemplateData(
      buildApi = ApiVersion(buildSdkVersion.featureLevel, buildSdkVersion.apiString),
      targetApi = ApiVersion(targetSdkVersion.featureLevel, targetSdkVersion.apiString),
      minApi = ApiVersion(minSdkVersion.featureLevel, minSdkVersion.apiString),
      // The highest supported/recommended appCompact version is P(28)
      appCompatVersion = targetSdkVersion.featureLevel.coerceAtMost(P)
    )

    isLibrary = facet.configuration.isLibraryProject

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
      // TODO(b/149203281): Fix support for composite builds.
      baseFeature.getGradleProjectPath()?.path.orEmpty(),
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
    projectTemplateDataBuilder.setEssentials(project)
    themesData = ThemesData(appName = getAppNameForTheme(project.name)) // New modules always have a theme (unless its a library, but it will have no activity)

    apis = ApiTemplateData(
      buildApi = ApiVersion(buildVersion.buildApiLevel, buildVersion.buildApiLevelStr),
      targetApi = ApiVersion(buildVersion.targetApiLevel, buildVersion.targetApiLevelStr),
      minApi = ApiVersion(buildVersion.minApiLevel, buildVersion.minApiLevelStr),
      // The highest supported/recommended appCompact version is P(28)
      appCompatVersion = buildVersion.buildApiLevel.coerceAtMost(P)
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

    val noActionBar = "NoActionBar"
    val appBarOverlay = "AppBarOverlay"
    val popupOverlay = "PopupOverlay"
    ApplicationManager.getApplication().runReadAction {
      val hasActionBar = ThemeHelper.hasActionBar(configuration, themeName)
      themesData = ThemesData(
        appName = getAppNameForTheme(module.project.name),
        main = ThemeData(themeName, true),
        noActionBar = getDerivedTheme(themeName, noActionBar, hasActionBar == false),
        appBarOverlay = getDerivedTheme(themeName, appBarOverlay, false),
        popupOverlay = getDerivedTheme(themeName, popupOverlay, false)
      )
    }
  }

  fun build() = ModuleTemplateData(
    projectTemplateDataBuilder.build(),
    srcDir!!,
    resDir!!,
    manifestDir!!,
    testDir ?: srcDir!!.resolve(FD_TEST),
    unitTestDir ?: srcDir!!.resolve(FD_UNIT_TEST),
    aidlDir!!,
    rootDir!!,
    isNewModule,
    name!!,
    isLibrary!!,
    packageName!!,
    formFactor!!,
    themesData ?: ThemesData(appName = getAppNameForTheme(projectTemplateDataBuilder.applicationName!!)),
    baseFeature,
    apis!!,
    viewBindingSupport = viewBindingSupport,
    category!!,
    isMaterial3,
    useGenericLocalTests = useGenericLocalTests,
    useGenericInstrumentedTests = useGenericInstrumentedTests
  )
}

fun getExistingModuleTemplateDataBuilder(module: Module): ModuleTemplateDataBuilder {
  val project = module.project
  val projectStateBuilder = ProjectTemplateDataBuilder(false).apply {
    setProjectDefaults(project)
    language = if (module.hasKotlinFacet()) Language.Kotlin else Language.Java
    topOut = project.guessProjectDir()!!.toIoFile()
    applicationPackage = ""
    overridePathCheck = false
  }

  return ModuleTemplateDataBuilder(projectStateBuilder, true, project.isViewBindingSupported()).apply {
    name = "Fake module state"
    packageName = ""
    val paths = GradleAndroidModuleTemplate.createDefaultModuleTemplate(project, name!!).paths
    setModuleRoots(paths, projectTemplateDataBuilder.topOut!!.path, name!!, packageName!!)
    isLibrary = false
    formFactor = FormFactor.Mobile
    category = Category.Activity
    themesData = ThemesData(appName = getAppNameForTheme(project.name))
    apis = ApiTemplateData(
      buildApi = ApiVersion(HIGHEST_KNOWN_STABLE_API, HIGHEST_KNOWN_STABLE_API.toString()),
      targetApi = ApiVersion(HIGHEST_KNOWN_STABLE_API, HIGHEST_KNOWN_STABLE_API.toString()),
      minApi = ApiVersion(LOWEST_ACTIVE_API, LOWEST_ACTIVE_API.toString()),
      // The highest supported/recommended appCompact version is P(28)
      appCompatVersion = HIGHEST_KNOWN_STABLE_API.coerceAtMost(P)
    )
  }
}

