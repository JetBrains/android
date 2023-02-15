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
package com.android.tools.idea.templates.recipe

import com.android.SdkConstants.ATTR_CONTEXT
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.GRADLE_API_CONFIGURATION
import com.android.SdkConstants.GRADLE_IMPLEMENTATION_CONFIGURATION
import com.android.SdkConstants.TOOLS_URI
import com.android.ide.common.repository.GradleCoordinate
import com.android.resources.ResourceFolderType
import com.android.support.AndroidxNameUtils
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.ANDROID_TEST_API
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.ANDROID_TEST_COMPILE
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.ANDROID_TEST_IMPLEMENTATION
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.API
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.APK
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.CLASSPATH
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.COMPILE
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.IMPLEMENTATION
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.PROVIDED
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.RUNTIME
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.TEST_API
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.TEST_COMPILE
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.TEST_IMPLEMENTATION
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.api.java.LanguageLevelPropertyModel
import com.android.tools.idea.gradle.dsl.api.settings.PluginsModel
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpecImpl
import com.android.tools.idea.gradle.dsl.parser.semantics.AndroidGradlePluginVersion
import com.android.tools.idea.gradle.project.GradleVersionCatalogDetector
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.templates.TemplateUtils
import com.android.tools.idea.templates.TemplateUtils.checkDirectoryIsWriteable
import com.android.tools.idea.templates.TemplateUtils.checkedCreateDirectoryIfMissing
import com.android.tools.idea.templates.TemplateUtils.hasExtension
import com.android.tools.idea.templates.TemplateUtils.readTextFromDisk
import com.android.tools.idea.templates.TemplateUtils.readTextFromDocument
import com.android.tools.idea.templates.determineVersionCatalogUseForNewModule
import com.android.tools.idea.templates.resolveDependency
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.ProjectTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.SourceSetType
import com.android.tools.idea.wizard.template.findResource
import com.android.tools.idea.wizard.template.withoutSkipLines
import com.android.utils.XmlUtils.XML_PROLOG
import com.android.utils.findGradleBuildFile
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Charsets
import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy.IGNORE_WHITESPACES
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.openapi.vfs.VfsUtil.findFileByIoFile
import com.intellij.openapi.vfs.VfsUtil.findFileByURL
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.XmlElementFactory
import java.io.File
import com.android.tools.idea.templates.mergeXml as mergeXmlUtil

/**
 * Executor support for recipe instructions.
 *
 * Note: it tries to use [GradleBuildModel] for merging of Gradle files, but falls back on simple merging if it is unavailable.
 */
class DefaultRecipeExecutor(
  private val context: RenderingContext,
  private val versionCatalogDetector: GradleVersionCatalogDetector = GradleVersionCatalogDetector.getInstance(
    context.project)) : RecipeExecutor {
  private val project: Project get() = context.project
  private val referencesExecutor: FindReferencesRecipeExecutor = FindReferencesRecipeExecutor(context)
  private val io: RecipeIO = if (context.dryRun) DryRunRecipeIO() else RecipeIO()
  private val readonlyStatusHandler: ReadonlyStatusHandler = ReadonlyStatusHandler.getInstance(project)

  private val projectTemplateData: ProjectTemplateData get() = context.projectTemplateData
  private val moduleTemplateData: ModuleTemplateData? get() = context.moduleTemplateData
  private val repositoryUrlManager: RepositoryUrlManager by lazy { RepositoryUrlManager.get() }

  @VisibleForTesting
  val projectBuildModel: ProjectBuildModel? by lazy {
    ProjectBuildModel.getOrLog(project)
      ?.also { it.context.agpVersion = AndroidGradlePluginVersion.parse(projectTemplateData.gradlePluginVersion) }
  }
  private val projectSettingsModel: GradleSettingsModel? by lazy { projectBuildModel?.projectSettingsModel }
  private val projectGradleBuildModel: GradleBuildModel? by lazy { projectBuildModel?.projectBuildModel }
  private val moduleGradleBuildModel: GradleBuildModel? by lazy {
    when {
      context.module != null -> projectBuildModel?.getModuleBuildModel(context.module)
      context.moduleRoot != null -> getBuildModel(findGradleBuildFile(context.moduleRoot), project, projectBuildModel)
      else -> null
    }
  }
  private val useVersionCatalog: Boolean by lazy {
    determineVersionCatalogUseForNewModule(project, versionCatalogDetector)
  }

  override fun hasDependency(mavenCoordinate: String, moduleDir: File?): Boolean {
    val buildModel =
      if (moduleDir != null) {
        projectBuildModel?.getModuleBuildModel(moduleDir)
      }
      else {
        moduleGradleBuildModel
      } ?: return false

    if (buildModel.getDependencyConfiguration(mavenCoordinate) != null) {
      return true
    }

    return GradleCoordinate.parseCoordinateString(mavenCoordinate)
      ?.let { gradleCoordinate -> context.module?.getModuleSystem()?.getRegisteredDependency(gradleCoordinate) } != null
  }

  /**
   * Identifies a configuration that the given maven coordinate is used in, or null if none does.
   * Returns [OTHER_CONFIGURATION] if it is used in an unknown configuration.
   */
  private fun GradleBuildModel.getDependencyConfiguration(mavenCoordinate: String): String? {
    val configurationsToCheck = listOf(
        ANDROID_TEST_API,
        ANDROID_TEST_COMPILE,
        ANDROID_TEST_IMPLEMENTATION,
        API,
        APK,
        CLASSPATH,
        COMPILE,
        IMPLEMENTATION,
        PROVIDED,
        RUNTIME,
        TEST_API,
        TEST_COMPILE,
        TEST_IMPLEMENTATION
    )

    fun checkForConfiguration(configuration: String?): Boolean {
      val artifacts = dependencies().run { if (configuration == null) artifacts() else artifacts(configuration) }

      val existingArtifacts = artifacts.map {
        ArtifactDependencySpec.create(it.name().toString(), it.group().toString(), it.version().toString())
      }

      val artifactToAdd = ArtifactDependencySpec.create(mavenCoordinate)!!

      return existingArtifacts.any { it.equalsIgnoreVersion(artifactToAdd) }
    }

    return configurationsToCheck.firstOrNull { checkForConfiguration(it) } ?: OTHER_CONFIGURATION.takeIf { checkForConfiguration(null) }
  }

  /**
   * Merges the given XML file into the given destination file (or copies it over if the destination file does not exist).
   */
  override fun mergeXml(source: String, to: File) {
    val content = source.withoutSkipLines()
    val targetFile = getTargetFile(to)
    require(hasExtension(targetFile, DOT_XML)) { "Only XML files can be merged at this point: $targetFile" }

    val targetText = readTargetText(targetFile) ?: run {
      save(content, to)
      return
    }

    val contents = mergeXmlUtil(context, content, targetText, targetFile)

    writeTargetFile(this, contents, targetFile)
  }

  override fun open(file: File) {
    context.filesToOpen.add(file)
  }

  override fun applyPlugin(plugin: String, revision: String?, minRev: String?) {
    referencesExecutor.applyPlugin(plugin, revision, minRev)

    val buildModel = moduleGradleBuildModel ?: return
    applyPluginInBuildModel(plugin, buildModel, revision, minRev)
  }

  override fun applyPluginInModule(plugin: String, module: Module, revision: String?, minRev: String?) {
    referencesExecutor.applyPluginInModule(plugin, module, revision, minRev)

    val buildModel = projectBuildModel?.getModuleBuildModel(module) ?: return
    applyPluginInBuildModel(plugin, buildModel, revision, minRev)
  }

  private fun applyPluginInBuildModel(plugin: String, buildModel: GradleBuildModel, revision: String?, minRev: String?) {
    buildModel.applyPluginIfNone(plugin)

    if (revision != null) {
      val (pluginsBlockToModify, applyFlag) = maybeGetPluginsFromSettings()?.let { Pair(it, null) }
                                              ?: maybeGetPluginsFromProject()?.let { Pair(it, false) }
                                              ?: return

      val pluginCoordinate = "$plugin:$plugin.gradle.plugin:$revision"
      val resolvedVersion = resolveDependency(repositoryUrlManager, pluginCoordinate, minRev).lowerBoundVersion.toString()
      val targetPluginModel = pluginsBlockToModify.plugins().firstOrNull { it.name().toString() == plugin }

      if (targetPluginModel == null) {
        pluginsBlockToModify.applyPlugin(plugin, resolvedVersion, applyFlag)
      }
    }
  }

  override fun addClasspathDependency(mavenCoordinate: String, minRev: String?, forceAdding: Boolean) {
    if (!forceAdding && (maybeGetPluginsFromSettings() != null || maybeGetPluginsFromProject() != null)) {
      // If plugins are being declared on Settings or using plugins block in top-level build.gradle,
      // we skip this since all work is handled in [applyPlugin]
      return
    }

    val resolvedCoordinate = resolveDependency(repositoryUrlManager, convertToAndroidX(mavenCoordinate), minRev).toString()

    referencesExecutor.addClasspathDependency(resolvedCoordinate, minRev)

    val toBeAddedDependency = ArtifactDependencySpec.create(resolvedCoordinate)
    check(toBeAddedDependency != null) { "$resolvedCoordinate is not a valid classpath dependency" }

    val buildModel = projectGradleBuildModel ?: return

    val buildscriptDependencies = buildModel.buildscript().dependencies()
    val targetDependencyModel = buildscriptDependencies.artifacts(CLASSPATH_CONFIGURATION_NAME).firstOrNull {
      toBeAddedDependency.equalsIgnoreVersion(it.spec)
    }
    if (targetDependencyModel == null) {
      buildscriptDependencies.addArtifact(CLASSPATH_CONFIGURATION_NAME, toBeAddedDependency)
    }
  }

  private fun maybeGetPluginsFromSettings(): PluginsModel? {
    return projectSettingsModel?.pluginManagement()?.plugins()?.takeIf { it.psiElement != null }
  }

  private fun maybeGetPluginsFromProject(): PluginsModel? {
    return projectGradleBuildModel?.takeIf { it.pluginsPsiElement != null }
  }

  /**
   * Add a library dependency into the project.
   */
  override fun addDependency(mavenCoordinate: String, configuration: String, minRev: String?, moduleDir: File?, toBase: Boolean) {
    // Translate from "compile" to "implementation" based on the parameter map context
    val newConfiguration = GradleUtil.mapConfigurationName(configuration, projectTemplateData.gradlePluginVersion, false)
    referencesExecutor.addDependency(newConfiguration, mavenCoordinate, minRev, moduleDir, toBase)

    val baseFeature = context.moduleTemplateData?.baseFeature

    val buildModel = when {
      moduleDir != null -> {
        projectBuildModel?.getModuleBuildModel(moduleDir)
      }
      baseFeature == null || !toBase -> {
        moduleGradleBuildModel
      }
      else -> {
        projectBuildModel?.getModuleBuildModel(baseFeature.dir)
      }
    } ?: return

    var resolvedConfiguration = GradleUtil.mapConfigurationName(configuration, projectTemplateData.gradlePluginVersion, false)

    val resolvedMavenCoordinate =
      when {
        // For coordinates that don't specify a version, we expect that version to be supplied by a platform dependency (i.e. a BOM).
        // These coordinates can't be parsed by GradleCoordinate, and don't need to be resolved, so leave them as-is.
        ArtifactDependencySpecImpl.create(mavenCoordinate)?.version == null -> mavenCoordinate
        else -> resolveDependency(repositoryUrlManager, convertToAndroidX(mavenCoordinate), minRev).toString()
      }

    // If a Library (e.g. Google Maps) Manifest references its own resources, it needs to be added to the Base, otherwise aapt2 will fail
    // during linking. Since we don't know the libraries Manifest references, we declare this libraries in the base as "api" dependencies.
    if (baseFeature != null && toBase && resolvedConfiguration == GRADLE_IMPLEMENTATION_CONFIGURATION) {
        resolvedConfiguration = GRADLE_API_CONFIGURATION
    }

    if (useVersionCatalog) {
      val catalogModel = projectBuildModel?.versionCatalogsModel?.getVersionCatalogModel("libs")
      val referenceToDepToAdd = addDependencyToVersionCatalog(catalogModel, resolvedMavenCoordinate)
      if (buildModel.getDependencyConfiguration(resolvedMavenCoordinate) == null) {
        buildModel.dependencies().addArtifact(resolvedConfiguration, referenceToDepToAdd)
      }
    } else {
      if (buildModel.getDependencyConfiguration(resolvedMavenCoordinate) == null) {
        buildModel.dependencies().addArtifact(resolvedConfiguration, resolvedMavenCoordinate)
      }
    }
  }

  override fun addPlatformDependency(mavenCoordinate: String, configuration: String, enforced: Boolean) {
    // TODO: Delete this once we no longer support ancient Gradle plugins
    val newConfiguration = GradleUtil.mapConfigurationName(configuration, projectTemplateData.gradlePluginVersion, false)
    require(configuration == newConfiguration) { "Platform dependencies are not supported in Gradle plugin < 3.0" }

    referencesExecutor.addPlatformDependency(configuration, mavenCoordinate, enforced)

    val buildModel = moduleGradleBuildModel ?: return
    val resolvedMavenCoordinate = resolveDependency(repositoryUrlManager, mavenCoordinate).toString()

    // Note that unlike in addDependency, we allow adding a dependency to multiple configurations,
    // e.g. "implementation" and "androidTestImplementation". This is necessary to apply BOM versions
    // to dependencies in each configuration.
    buildModel.dependencies().addPlatformArtifact(configuration, resolvedMavenCoordinate, enforced)
  }

  override fun addModuleDependency(configuration: String, moduleName: String, toModule: File) {
    require(moduleName.isNotEmpty() && moduleName.first() != ':') {
      "incorrect module name (it should not be empty or include first ':')"
    }
    val resolvedConfiguration = GradleUtil.mapConfigurationName(configuration, projectTemplateData.gradlePluginVersion, false)

    val buildModel = projectBuildModel?.getModuleBuildModel(toModule) ?: return
    buildModel.dependencies().addModule(resolvedConfiguration, ":$moduleName")
  }

  /**
   * Copies the given source file into the given destination file (where the source
   * is allowed to be a directory, in which case the whole directory is copied recursively)
   */
  override fun copy(from: File, to: File) {
    val sourceUrl = findResource(context.templateData.javaClass, from)
    val target = getTargetFile(to)

    val sourceFile = findFileByURL(sourceUrl) ?: error("$from ($sourceUrl)")
    sourceFile.refresh(false, false)
    val destPath = if (sourceFile.isDirectory) target else target.parentFile
    when {
      sourceFile.isDirectory -> copyDirectory(sourceFile, destPath)
      target.exists() -> if (!sourceFile.contentEquals(target)) {
        addFileAlreadyExistWarning(target)
      }
      else -> {
        val document = FileDocumentManager.getInstance().getDocument(sourceFile)
        if (document != null) {
          io.writeFile(this, document.text, target, project)
        }
        else {
          io.copyFile(this, sourceFile, destPath, target.name)
        }
        referencesExecutor.addTargetFile(target)
      }
    }
  }

  /**
   * Instantiates the given template file into the given output file.
   * Note: It removes trailing whitespace both from beginning and end of source.
   *       Also, it replaces any 2+ consequent empty lines with single empty line.
   */
  override fun save(source: String, to: File) {
    val targetFile = getTargetFile(to)
    val content = extractFullyQualifiedNames(to, source.withoutSkipLines()).trim().squishEmptyLines()

    if (targetFile.exists()) {
      if (!targetFile.contentEquals(content)) {
        addFileAlreadyExistWarning(targetFile)
      }
      return
    }
    io.writeFile(this, content, targetFile, project)
    referencesExecutor.addTargetFile(targetFile)
  }

  override fun createDirectory(at: File) {
    io.mkDir(getTargetFile(at))
  }

  override fun addSourceSet(type: SourceSetType, name: String, dir: File) {
    val buildModel = moduleGradleBuildModel ?: return
    val sourceSet = buildModel.android().addSourceSet(name)
    val relativeDir = dir.toRelativeString(moduleTemplateData!!.rootDir)

    if (type == SourceSetType.MANIFEST) {
      sourceSet.manifest().srcFile().setValue(relativeDir)
      return
    }

    val srcDirsModel = with(sourceSet) {
      when (type) {
        SourceSetType.AIDL -> aidl()
        SourceSetType.ASSETS -> assets()
        SourceSetType.JAVA -> java()
        SourceSetType.JNI -> jni()
        SourceSetType.RENDERSCRIPT -> renderscript()
        SourceSetType.RES -> res()
        SourceSetType.RESOURCES -> resources()
        SourceSetType.MANIFEST -> throw RuntimeException("manifest should have been handled earlier")
      }
    }.srcDirs()

    val dirExists = srcDirsModel.toList().orEmpty().any { it.toString() == relativeDir }

    if (dirExists) {
      return
    }

    srcDirsModel.addListValue()?.setValue(relativeDir)
  }

  override fun setExtVar(name: String, value: String) {
    if (moduleGradleBuildModel?.dependencies()?.isPropertyInScope(name) == true) {
      return // If in scope, either is a local variable or is already in ext[]
    }
    val buildModel = projectGradleBuildModel ?: return
    val property = buildModel.buildscript().ext().findProperty(name)
    if (property.valueType != ValueType.NONE) {
      return // we do not override property value if it exists.
    }
    property.setValue(value)
  }

  override fun getClasspathDependencyVarName(mavenCoordinate: String, valueIfNotFound: String) : String {
    val mavenDependency = ArtifactDependencySpec.create(mavenCoordinate)
    check(mavenDependency != null) { "$mavenCoordinate is not a valid classpath dependency" }

    val buildScriptDependencies = projectGradleBuildModel?.buildscript()?.dependencies() ?: return valueIfNotFound
    val targetDependencyModel = buildScriptDependencies.artifacts(CLASSPATH_CONFIGURATION_NAME).firstOrNull {
      mavenDependency.equalsIgnoreVersion(it.spec)
    }
    val unresolvedVersionModel = targetDependencyModel?.version()?.unresolvedModel ?: return valueIfNotFound

    if (unresolvedVersionModel.valueType == ValueType.REFERENCE) {
      return unresolvedVersionModel.getValue(GradlePropertyModel.STRING_TYPE) ?: valueIfNotFound
    }

    return valueIfNotFound
  }

  override fun getDependencyVarName(mavenCoordinate: String, valueIfNotFound: String) : String {
    val mavenDependency = ArtifactDependencySpec.create(mavenCoordinate)
    check(mavenDependency != null) { "$mavenCoordinate is not a valid dependency" }

    val settingsModel: GradleSettingsModel = projectSettingsModel ?: return valueIfNotFound
    val moduleModel: GradleBuildModel = moduleGradleBuildModel ?: return valueIfNotFound

    val moduleList = settingsModel.modulePaths().mapNotNull { settingsModel.moduleModel(it) }.toMutableList()
    moduleModel.apply {
      // If the current module is not the first (index is zero), move it to be first.
      val currentModuleIdx = moduleList.map { it.virtualFile }.indexOf(virtualFile)
      if (currentModuleIdx >= 1) {
        moduleList.add(0, moduleList.removeAt(currentModuleIdx))
      }
    }

    val varName = moduleList
      .flatMap { gradleBuildModel -> gradleBuildModel.dependencies().artifacts() }
      .asSequence()
      .filter { artifactDependencyModel -> mavenDependency.equalsIgnoreVersion(artifactDependencyModel.spec) }
      .map { artifactDependencyModel -> artifactDependencyModel.version().unresolvedModel }
      .filter { unresolvedVersionProperty -> unresolvedVersionProperty.valueType == ValueType.REFERENCE }
      .mapNotNull { unresolvedVersionModel -> unresolvedVersionModel.getValue(GradlePropertyModel.STRING_TYPE) }
      .filter { moduleModel.dependencies().isPropertyInScope(it) }
      .firstOrNull()

    return varName ?: valueIfNotFound
  }

  /**
   * Adds a module dependency to global settings.gradle[.kts] file.
   */
  override fun addIncludeToSettings(moduleName: String) {
    projectSettingsModel?.addModulePath(moduleName)
  }

  /**
   * Adds a new build feature to android block. For example, may enable compose.
   */
  override fun setBuildFeature(name: String, value: Boolean) {
    val buildModel = moduleGradleBuildModel ?: return
    val feature = when (name) {
      "compose" -> buildModel.android().buildFeatures().compose()
      "dataBinding" -> buildModel.android().buildFeatures().dataBinding()
      "mlModelBinding" -> buildModel.android().buildFeatures().mlModelBinding()
      "viewBinding" -> buildModel.android().buildFeatures().viewBinding()
      "prefab" -> buildModel.android().buildFeatures().prefab()
      else -> throw IllegalArgumentException("$name is not a supported build feature.")
    }

    if (feature.valueType == ValueType.NONE) {
      feature.setValue(value)
    }
  }

  override fun setViewBinding(value: Boolean) {
    val buildModel = moduleGradleBuildModel ?: return
    buildModel.android().viewBinding().enabled().setValue(value)
  }

  /**
   * Sets Compose Options field values
   */
  override fun setComposeOptions(kotlinCompilerExtensionVersion: String?) {
    val buildModel = moduleGradleBuildModel ?: return
    val composeOptionsModel = buildModel.android().composeOptions()

    if (kotlinCompilerExtensionVersion != null) {
      composeOptionsModel.kotlinCompilerExtensionVersion().setValueIfNone(kotlinCompilerExtensionVersion)
    }

    buildModel.android().defaultConfig().vectorDrawables().useSupportLibrary().setValue(true)
    buildModel.android().packagingOptions().resources().excludes().setValueIfNone("/META-INF/{AL2.0,LGPL2.1}")
  }

  /**
   * Sets Cpp Options field values
   */
  override fun setCppOptions(cppFlags: String, cppPath: String, cppVersion: String) {
    val buildModel = moduleGradleBuildModel ?: return
    buildModel.android().apply {
      if (cppFlags.isNotBlank()) {
        defaultConfig().externalNativeBuild().cmake().cppFlags().setValue(cppFlags)
      }

      externalNativeBuild().cmake().apply {
        path().setValue(cppPath)
        version().setValue(cppVersion)
      }
    }
  }

  /**
   * Sets sourceCompatibility and targetCompatibility in compileOptions and (if needed) jvmTarget in kotlinOptions.
   */
  override fun requireJavaVersion(version: String, kotlinSupport: Boolean) {
    var languageLevel = LanguageLevel.parse(version)!!
    // Kotlin does not support 1.7
    // See https://kotlinlang.org/docs/reference/using-gradle.html#attributes-specific-for-jvm
    if (kotlinSupport && languageLevel == LanguageLevel.JDK_1_7) {
      languageLevel = LanguageLevel.JDK_1_8
    }
    val buildModel = moduleGradleBuildModel ?: return

    fun updateCompatibility(current: LanguageLevelPropertyModel) {
      if (current.valueType == ValueType.NONE || current.toLanguageLevel()?.isAtLeast(languageLevel) != true) {
        current.setLanguageLevel(languageLevel)
      }
    }

    buildModel.android().compileOptions().run {
      updateCompatibility(sourceCompatibility())
      updateCompatibility(targetCompatibility())
    }
    if (kotlinSupport && (context.moduleTemplateData)?.isDynamic != true) {
      updateCompatibility(buildModel.android().kotlinOptions().jvmTarget())
    }
  }

  override fun addDynamicFeature(name: String, toModule: File) {
    require(name.isNotEmpty()) {
      "Module name cannot be empty"
    }
    val gradleName = ':' + name.trimStart(':')
    val buildModel = projectBuildModel?.getModuleBuildModel(toModule) ?: return
    buildModel.android().dynamicFeatures().addListValue()?.setValue(gradleName)
  }

  fun applyChanges() {
    if (!context.dryRun) {
      projectBuildModel?.applyChanges()
    }
  }

  private fun ResolvedPropertyModel.setValueIfNone(value: String) {
    if (valueType == ValueType.NONE) {
      if (value.startsWith('$')) ReferenceTo.createReferenceFromText(value.substring(1), this)?.let { setValue(it) }
      else setValue(value)
    }
  }

  private fun GradleBuildModel.applyPluginIfNone(plugin: String) {
    // b/193012182 - Some plugins have different names but are identical and we don't want to apply them more than once
    fun defaultPluginName(name: String) = when (name) {
      "kotlin-android" -> "org.jetbrains.kotlin.android"
      "kotlin" -> "org.jetbrains.kotlin.jvm"
      else -> name
    }

    val defaultName = defaultPluginName(plugin)
    if (plugins().none { defaultPluginName(it.name().forceString()) == defaultName }) {
      applyPlugin(plugin)
    }
  }

  private fun convertToAndroidX(mavenCoordinate: String): String =
    if (projectTemplateData.androidXSupport)
      AndroidxNameUtils.getVersionedCoordinateMapping(mavenCoordinate)
    else
      mavenCoordinate

  /**
   * [VfsUtil.copyDirectory] messes up the undo stack, most likely by trying to create a directory even if it already exists.
   * This is an undo-friendly replacement.
   */
  private fun copyDirectory(src: VirtualFile, dest: File) = TemplateUtils.copyDirectory(src, dest, ::copyFile)

  private fun copyFile(file: VirtualFile, src: VirtualFile, destinationFile: File): Boolean {
    val relativePath = VfsUtilCore.getRelativePath(file, src, File.separatorChar)
    check(relativePath != null) { "${file.path} is not a child of $src" }
    if (file.isDirectory) {
      io.mkDir(File(destinationFile, relativePath))
      return true
    }
    val target = File(destinationFile, relativePath)
    if (target.exists()) {
      if (!file.contentEquals(target)) {
        addFileAlreadyExistWarning(target)
      }
    }
    else {
      io.copyFile(this, file, target)
      referencesExecutor.addTargetFile(target)
    }
    return true
  }

  /**
   * Returns the absolute path to the file which will get written to.
   */
  private fun getTargetFile(file: File): File = if (file.isAbsolute)
    file
  else
    File(context.outputRoot, file.path)

  private fun readTextFile(file: File): String? =
    if (moduleTemplateData?.isNewModule != false)
      readTextFromDisk(file)
    else
      readTextFromDocument(project, file)

  /**
   * Shorten all fully qualified Layout names that belong to the same package as the manifest's package attribute value.
   *
   * @See [com.android.manifmerger.ManifestMerger2.extractFqcns]
   */
  private fun extractFullyQualifiedNames(to: File, content: String): String {
    if (ResourceFolderType.getFolderType(to.parentFile.name) != ResourceFolderType.LAYOUT) {
      return content
    }

    val packageName: String? = projectTemplateData.applicationPackage ?: moduleTemplateData?.packageName

    val factory = XmlElementFactory.getInstance(project)
    val root = factory.createTagFromText(content)

    // Note: At the moment only root "context:tools" attribute needs to be shorten
    val contextAttr = root.getAttribute(ATTR_CONTEXT, TOOLS_URI)
    val context = contextAttr?.value
    if (packageName == null || context == null || !context.startsWith("$packageName.")) {
      return content
    }

    val newContext = context.substring(packageName.length)
    root.setAttribute(ATTR_CONTEXT, TOOLS_URI, newContext)

    return XML_PROLOG + root.text
  }

  private fun readTargetText(targetFile: File): String? {
    if (!targetFile.exists()) {
      return null
    }
    if (project.isInitialized) {
      val toFile = findFileByIoFile(targetFile, true)
      val status = readonlyStatusHandler.ensureFilesWritable(listOf(toFile!!))
      check(!status.hasReadonlyFiles()) { "Attempt to update file that is readonly: ${targetFile.absolutePath}" }
    }
    return readTextFile(targetFile)
  }

  private fun writeTargetFile(requestor: Any, contents: String?, to: File) {
    io.writeFile(requestor, contents, to, project)
    referencesExecutor.addTargetFile(to)
  }

  private fun VirtualFile.contentEquals(targetFile: File): Boolean =
    if (fileType.isBinary)
      this.contentsToByteArray() contentEquals targetFile.readBytes()
    else
      ComparisonManager.getInstance().isEquals(readTextFromDocument(project, this)!!, readTextFile(targetFile)!!, IGNORE_WHITESPACES)

  private infix fun File.contentEquals(content: String): Boolean =
    ComparisonManager.getInstance().isEquals(content, readTextFile(this)!!, IGNORE_WHITESPACES)

  private fun addFileAlreadyExistWarning(targetFile: File) =
    context.warnings.add("The following file could not be created since it already exists: ${targetFile.path}")

  private open class RecipeIO {
    /**
     * Replaces the contents of the given file with the given string. Outputs
     * text in UTF-8 character encoding. The file is created if it does not
     * already exist.
     */
    open fun writeFile(requestor: Any, contents: String?, to: File, project: Project) {
      if (contents == null) {
        return
      }

      val parentDir = checkedCreateDirectoryIfMissing(to.parentFile)
      val vf = LocalFileSystem.getInstance().findFileByIoFile(to) ?: parentDir.createChildData(requestor, to.name)
      vf.setBinaryContent(contents.toByteArray(Charsets.UTF_8), -1, -1, requestor)

      // ProjectBuildModel uses PSI, let's committed document, since it's illegal to modify PSI on uncommitted Document.
      //FileDocumentManager.getInstance()
      //  .getDocument(vf)
      //  ?.let(PsiDocumentManager.getInstance(project)::commitDocument) <==== DOESN'T WORK!! ::commitDocument thinks doc is already committed
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    open fun copyFile(requestor: Any, file: VirtualFile, toFile: File) {
      val toDir = checkedCreateDirectoryIfMissing(toFile.parentFile)
      VfsUtilCore.copyFile(requestor, file, toDir)
    }

    open fun copyFile(requestor: Any, file: VirtualFile, toFileDir: File, newName: String) {
      val toDir = checkedCreateDirectoryIfMissing(toFileDir)
      VfsUtilCore.copyFile(requestor, file, toDir, newName)
    }

    open fun mkDir(directory: File) {
      checkedCreateDirectoryIfMissing(directory)
    }
  }

  private class DryRunRecipeIO : RecipeIO() {
    override fun writeFile(requestor: Any, contents: String?, to: File, project: Project) {
      checkDirectoryIsWriteable(to.parentFile)
    }

    override fun copyFile(requestor: Any, file: VirtualFile, toFile: File) {
      checkDirectoryIsWriteable(toFile.parentFile)
    }

    override fun copyFile(requestor: Any, file: VirtualFile, toFileDir: File, newName: String) {
      checkDirectoryIsWriteable(toFileDir)
    }

    override fun mkDir(directory: File) {
      checkDirectoryIsWriteable(directory)
    }
  }
}

// used when some configuration is found but it is not in configuration list.
private const val OTHER_CONFIGURATION = "__other__"

/**
 * 'classpath' is the configuration name used to specify buildscript dependencies.
 */
// TODO(qumeric): make private
const val CLASSPATH_CONFIGURATION_NAME = "classpath"

@VisibleForTesting
fun CharSequence.squishEmptyLines(): String {
  var isLastBlank = false
  return this.split("\n").mapNotNull { line ->
    when {
      line.isNotBlank() -> line
      !isLastBlank -> "" // replace blank with empty
      else -> null
    }.also {
      isLastBlank = line.isBlank()
    }
  }.joinToString("\n")
}


fun getBuildModel(buildFile: File, project: Project, projectBuildModel: ProjectBuildModel? = null): GradleBuildModel? {
  if (project.isDisposed || !buildFile.exists()) {
    return null
  }
  val virtualFile = findFileByIoFile(buildFile, true) ?: throw RuntimeException("Failed to find " + buildFile.path)

  // TemplateUtils.writeTextFile saves Documents but doesn't commit them, since there might not be a Project to speak of yet.
  // ProjectBuildModel uses PSI, so let's make sure the Document is committed, since it's illegal to modify PSI for a file with
  // and uncommitted Document.
  FileDocumentManager.getInstance()
    .getCachedDocument(virtualFile)
    ?.let(PsiDocumentManager.getInstance(project)::commitDocument)

  val buildModel = projectBuildModel ?: ProjectBuildModel.getOrLog(project)

  return buildModel?.getModuleBuildModel(virtualFile)
}

