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
package com.android.tools.idea.lint.model

import com.android.AndroidProjectTypes
import com.android.builder.model.LintOptions
import com.android.ide.common.repository.AgpVersion
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.gradle.model.ARTIFACT_NAME_ANDROID_TEST
import com.android.tools.idea.gradle.model.ARTIFACT_NAME_TEST_FIXTURES
import com.android.tools.idea.gradle.model.ARTIFACT_NAME_UNIT_TEST
import com.android.tools.idea.gradle.model.IdeAaptOptions
import com.android.tools.idea.gradle.model.IdeAndroidArtifact
import com.android.tools.idea.gradle.model.IdeAndroidLibrary
import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeApiVersion
import com.android.tools.idea.gradle.model.IdeArtifactLibrary
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeBaseArtifact
import com.android.tools.idea.gradle.model.IdeBuildType
import com.android.tools.idea.gradle.model.IdeClassField
import com.android.tools.idea.gradle.model.IdeExtraSourceProvider
import com.android.tools.idea.gradle.model.IdeJavaArtifact
import com.android.tools.idea.gradle.model.IdeJavaLibrary
import com.android.tools.idea.gradle.model.IdeLibrary
import com.android.tools.idea.gradle.model.IdeLintOptions
import com.android.tools.idea.gradle.model.IdeModuleLibrary
import com.android.tools.idea.gradle.model.IdeMultiVariantData
import com.android.tools.idea.gradle.model.IdeSourceProvider
import com.android.tools.idea.gradle.model.IdeUnknownLibrary
import com.android.tools.idea.gradle.model.IdeVariant
import com.android.tools.lint.model.DefaultLintModelAndroidArtifact
import com.android.tools.lint.model.DefaultLintModelAndroidLibrary
import com.android.tools.lint.model.DefaultLintModelBuildFeatures
import com.android.tools.lint.model.DefaultLintModelDependencies
import com.android.tools.lint.model.DefaultLintModelDependency
import com.android.tools.lint.model.DefaultLintModelDependencyGraph
import com.android.tools.lint.model.DefaultLintModelJavaArtifact
import com.android.tools.lint.model.DefaultLintModelJavaLibrary
import com.android.tools.lint.model.DefaultLintModelLibraryResolver
import com.android.tools.lint.model.DefaultLintModelLintOptions
import com.android.tools.lint.model.DefaultLintModelMavenName
import com.android.tools.lint.model.DefaultLintModelModule
import com.android.tools.lint.model.DefaultLintModelModuleLibrary
import com.android.tools.lint.model.DefaultLintModelResourceField
import com.android.tools.lint.model.DefaultLintModelSourceProvider
import com.android.tools.lint.model.DefaultLintModelVariant
import com.android.tools.lint.model.LintModelAndroidArtifact
import com.android.tools.lint.model.LintModelArtifact
import com.android.tools.lint.model.LintModelArtifactType
import com.android.tools.lint.model.LintModelBuildFeatures
import com.android.tools.lint.model.LintModelDependencies
import com.android.tools.lint.model.LintModelDependency
import com.android.tools.lint.model.LintModelJavaArtifact
import com.android.tools.lint.model.LintModelLibrary
import com.android.tools.lint.model.LintModelLibraryResolver
import com.android.tools.lint.model.LintModelLintOptions
import com.android.tools.lint.model.LintModelMavenName
import com.android.tools.lint.model.LintModelMavenName.Companion.NON_MAVEN
import com.android.tools.lint.model.LintModelModule
import com.android.tools.lint.model.LintModelModuleLoader
import com.android.tools.lint.model.LintModelModuleType
import com.android.tools.lint.model.LintModelNamespacingMode
import com.android.tools.lint.model.LintModelResourceField
import com.android.tools.lint.model.LintModelSerialization
import com.android.tools.lint.model.LintModelSeverity
import com.android.tools.lint.model.LintModelSourceProvider
import com.android.tools.lint.model.LintModelVariant
import com.android.utils.FileUtils
import java.io.File

/** Converter from the builder model library to lint's own model. */
class LintModelFactory : LintModelModuleLoader {

  private val libraryResolverMap = mutableMapOf<String, LintModelLibrary>()
  private val libraryResolver = DefaultLintModelLibraryResolver(libraryResolverMap)

  /**
   * Factory from an XML folder to a [LintModelModule]. The files were previously saved by
   * [LintModelSerialization.writeModule].
   */
  fun create(source: File): LintModelModule = LintModelSerialization.readModule(source)

  /**
   * Converter from the builder model library to lint's own model. If [deep] is true, it will create
   * a deep copy; otherwise, it will create wrapper objects. The advantage of a shallow copy is that
   * some expensive fields are only computed lazily (such as all the variant data, which may not be
   * needed in the IDE when running on the fly analysis). The advantage of a deep copy is that (at
   * least during testing) all fields are accessed so we can make sure there are no inconvertible
   * data, and when all the data is going to be used anyway there's no benefit in the additional
   * overhead of lazy lookup.
   */
  fun create(
    project: IdeAndroidProject,
    variants: Collection<IdeVariant>,
    multiVariantData: IdeMultiVariantData,
    dir: File,
    deep: Boolean = true,
  ): LintModelModule {
    val agpVersion = getAgpVersion(project)

    return if (deep) {
      val variantList = mutableListOf<LintModelVariant>()
      val module =
        DefaultLintModelModule(
          loader = this,
          dir = dir,
          modulePath = project.projectPath.projectPath,
          type = getModuleType(project.projectType),
          mavenName = getMavenName(project),
          agpVersion = agpVersion,
          buildFolder = project.buildFolder,
          lintOptions = getLintOptions(project),
          lintRuleJars = project.getLintRuleJarsForAnyAgpVersion(),
          resourcePrefix = project.resourcePrefix,
          dynamicFeatures = project.dynamicFeatures,
          bootClassPath = project.bootClasspath.map { File(it) },
          javaSourceLevel = project.javaCompileOptions.sourceCompatibility,
          compileTarget = project.compileTarget,
          neverShrinking = isNeverShrinking(project),
          variants = variantList,
        )

      for (variant in variants) {
        variantList.add(getVariant(module, project, variant, multiVariantData))
      }

      module
    } else {
      LazyLintModelModule(
        loader = this,
        project = project,
        projectVariants = variants,
        multiVariantData = multiVariantData,
        dir = dir,
        agpVersion = agpVersion,
      )
    }
  }

  /** Returns the list of Lint Rule file, no matter what the AGP version is. */
  private fun IdeAndroidProject.getLintRuleJarsForAnyAgpVersion() =
    lintChecksJars
      ?: listOf(
        FileUtils.join(buildFolder, "intermediates", "lint", "lint.jar"),
        FileUtils.join(buildFolder, "intermediates", "lint_jar", "lint.jar"),
        FileUtils.join(
          buildFolder,
          "intermediates",
          "lint_jar",
          "global",
          "prepareLintJar",
          "lint.jar",
        ),
      )

  /**
   * Ensures that the given [library] (if applicable, module or artifact) has a corresponding object
   * within [libraryResolverMap]. If one already exists this method does nothing otherwise we create
   * a [LintModelLibrary] for the given [IdeLibrary].
   */
  private fun maybeRegisterLintModelLibrary(library: IdeLibrary, isProvided: Boolean): Boolean {
    if (
      !(library is IdeModuleLibrary ||
        (library is IdeArtifactLibrary && library.artifactAddress.isNotEmpty()))
    )
      return false

    libraryResolverMap[library.getIdentifier()]?.let {
      return true
    }

    val lintLibrary =
      when (library) {
        is IdeAndroidLibrary ->
          DefaultLintModelAndroidLibrary(
            identifier = library.getIdentifier(),
            manifest = library.manifest,
            // TODO - expose compile jar vs impl jar?
            jarFiles = library.runtimeJarFiles,
            folder = library.folder!!, // Needed for workaround for b/66166521
            resFolder = library.resFolder,
            assetsFolder = library.assetsFolder,
            lintJar = library.lintJar,
            publicResources = library.publicResources,
            symbolFile = library.symbolFile,
            externalAnnotations = library.externalAnnotations,
            provided = isProvided,
            resolvedCoordinates = getMavenName(library),
            proguardRules = library.proguardRules,
          )
        is IdeJavaLibrary ->
          DefaultLintModelJavaLibrary(
            identifier = library.getIdentifier(),
            // TODO - expose compile jar vs impl jar?
            jarFiles = listOf(library.artifact),
            provided = isProvided,
            resolvedCoordinates = getMavenName(library),
          )
        is IdeModuleLibrary ->
          DefaultLintModelModuleLibrary(
            identifier = library.getIdentifier(),
            projectPath = library.projectPath,
            lintJar = library.lintJar,
            provided = false,
          )
        is IdeUnknownLibrary -> null
      } ?: return false

    libraryResolverMap[library.getIdentifier()] = lintLibrary
    return true
  }

  private fun IdeLibrary.getArtifactName(): String =
    when (this) {
      is IdeArtifactLibrary -> getMavenName(this).let { "${it.groupId}:${it.artifactId}" }
      is IdeModuleLibrary -> "artifacts:$projectPath"
      else -> throw IllegalArgumentException("The library $this can't produce an artifact name")
    }

  private fun IdeLibrary.getIdentifier(): String =
    when (this) {
      is IdeModuleLibrary -> "$projectPath@${sourceSet.sourceSetName}"
      is IdeArtifactLibrary -> name
      else -> throw IllegalArgumentException("The library $this can't produce an identifier")
    }

  private fun getDependencies(artifact: IdeBaseArtifact): LintModelDependencies {
    val compileItems = mutableListOf<LintModelDependency>()
    val packagedItems = mutableListOf<LintModelDependency>()
    val dependencies = artifact.compileClasspath
    val runtime = artifact.runtimeClasspath

    for (library in dependencies.libraries) {
      val isProvided = !runtime.libraries.contains(library)
      val packaged = library is IdeModuleLibrary || !isProvided
      if (maybeRegisterLintModelLibrary(library, isProvided)) {
        val lintDependency =
          DefaultLintModelDependency(
            identifier = library.getIdentifier(),
            artifactName = library.getArtifactName(),
            requestedCoordinates =
              null, // Always null in builder models and not present in Ide* models.
            // Deep copy
            dependencies = emptyList(), // Dependency hierarchy is not yet supported.
            libraryResolver = libraryResolver,
          )

        compileItems.add(lintDependency)
        if (packaged) {
          packagedItems.add(lintDependency)
        }
      }
    }

    val compileDependencies = DefaultLintModelDependencyGraph(compileItems, libraryResolver)
    val packageDependencies = DefaultLintModelDependencyGraph(packagedItems, libraryResolver)

    return DefaultLintModelDependencies(
      compileDependencies = compileDependencies,
      packageDependencies = packageDependencies,
      libraryResolver = libraryResolver,
    )
  }

  private fun getArtifact(
    artifact: IdeAndroidArtifact,
    type: LintModelArtifactType,
  ): LintModelAndroidArtifact {
    return DefaultLintModelAndroidArtifact(
      applicationId =
        artifact.applicationId ?: "", // TODO(b/234146319): This should probably be optional
      dependencies = getDependencies(artifact),
      generatedSourceFolders = artifact.generatedSourceFolders,
      generatedResourceFolders = artifact.generatedResourceFolders,
      classOutputs = artifact.classesFolder.toList(),
      desugaredMethodsFiles = artifact.desugaredMethodsFiles,
      type = type,
    )
  }

  private fun getArtifact(
    artifact: IdeJavaArtifact,
    type: LintModelArtifactType,
  ): LintModelJavaArtifact {
    return DefaultLintModelJavaArtifact(
      dependencies = getDependencies(artifact),
      classFolders = artifact.classesFolder.toList(),
      type = type,
    )
  }

  private fun getBuildType(
    multiVariantData: IdeMultiVariantData,
    variant: IdeVariant,
  ): IdeBuildType {
    val buildTypeName = variant.buildType
    return multiVariantData.buildTypes.first { it.buildType.name == buildTypeName }.buildType
  }

  private fun getVariant(
    module: LintModelModule,
    project: IdeAndroidProject,
    variant: IdeVariant,
    multiVariantData: IdeMultiVariantData,
  ): LintModelVariant {
    val buildType = getBuildType(multiVariantData, variant)
    return DefaultLintModelVariant(
      module = module,
      name = variant.name,
      useSupportLibraryVectorDrawables = useSupportLibraryVectorDrawables(variant),
      mainArtifactOrNull = getArtifact(variant.mainArtifact, LintModelArtifactType.MAIN),
      testArtifact =
        getUnitTestArtifact(
          variant
        ), // TODO(karimai): we need to change the Lint models to add screenshot Test  artifact.
      androidTestArtifact = getAndroidTestArtifact(variant),
      testFixturesArtifact = getTestFixturesArtifact(variant),
      mergedManifest = null, // Injected elsewhere by the legacy Android Gradle Plugin lint runner
      manifestMergeReport =
        null, // Injected elsewhere by the legacy Android Gradle Plugin lint runner
      `package` = null, // not in the old builder model
      minSdkVersion = variant.minSdkVersion.toAndroidVersion(),
      targetSdkVersion = variant.targetSdkVersion?.toAndroidVersion(),
      resValues = variant.resValues.mapValues { it.value.toResourceField() },
      manifestPlaceholders = variant.manifestPlaceholders,
      resourceConfigurations = variant.resourceConfigurations,
      proguardFiles = variant.proguardFiles,
      consumerProguardFiles = variant.consumerProguardFiles,
      sourceProviders = computeSourceProviders(project, variant),
      testSourceProviders = computeTestSourceProviders(project, variant),
      testFixturesSourceProviders = computeTestFixturesSourceProviders(project, variant),
      debuggable = buildType.isDebuggable,
      shrinkable = buildType.isMinifyEnabled,
      buildFeatures = getBuildFeatures(project, module.agpVersion),
      libraryResolver = libraryResolver,
      partialResultsDir = null,
      desugaredMethodsFiles = variant.desugaredMethodsFiles,
    )
  }

  private fun getTestFixturesArtifact(variant: IdeVariant): LintModelAndroidArtifact? {
    val artifact = variant.testFixturesArtifact ?: return null
    return getArtifact(artifact, LintModelArtifactType.TEST_FIXTURES)
  }

  private fun getAndroidTestArtifact(variant: IdeVariant): LintModelAndroidArtifact? {
    val artifact =
      variant.deviceTestArtifacts.find { it.name == IdeArtifactName.ANDROID_TEST } ?: return null
    return getArtifact(artifact, LintModelArtifactType.INSTRUMENTATION_TEST)
  }

  private fun getUnitTestArtifact(variant: IdeVariant): LintModelJavaArtifact? {
    val artifact =
      variant.hostTestArtifacts.find { it.name == IdeArtifactName.UNIT_TEST } ?: return null
    return getArtifact(artifact, LintModelArtifactType.UNIT_TEST)
  }

  private fun computeSourceProviders(
    project: IdeAndroidProject,
    variant: IdeVariant,
  ): List<LintModelSourceProvider> {
    val providers = mutableListOf<LintModelSourceProvider>()

    // if we have variant, than the main sourceset is present
    providers.add(getSourceProvider(project.defaultSourceProvider.sourceProvider!!))

    for (flavorContainer in project.multiVariantData?.productFlavors.orEmpty()) {
      if (variant.productFlavors.contains(flavorContainer.productFlavor.name)) {
        providers.add(getSourceProvider(flavorContainer.sourceProvider!!))
      }
    }

    val mainArtifact = variant.mainArtifact
    mainArtifact.multiFlavorSourceProvider?.let { sourceProvider ->
      providers.add(getSourceProvider(sourceProvider))
    }

    var debugVariant = false
    for (buildTypeContainer in project.multiVariantData?.buildTypes.orEmpty()) {
      if (variant.buildType == buildTypeContainer.buildType.name) {
        debugVariant = buildTypeContainer.buildType.isDebuggable
        providers.add(
          getSourceProvider(
            provider = buildTypeContainer.sourceProvider!!,
            debugOnly = debugVariant,
          )
        )
      }
    }

    mainArtifact.variantSourceProvider?.let { sourceProvider ->
      providers.add(getSourceProvider(provider = sourceProvider, debugOnly = debugVariant))
    }
    return providers
  }

  private fun IdeExtraSourceProvider.isTest(): Boolean {
    return isUnitTest() || isInstrumentationTest()
  }

  private fun IdeExtraSourceProvider.isTestFixtures(): Boolean {
    return ARTIFACT_NAME_TEST_FIXTURES == artifactName
  }

  private fun IdeExtraSourceProvider.isUnitTest(): Boolean {
    return ARTIFACT_NAME_UNIT_TEST == artifactName
  }

  private fun IdeExtraSourceProvider.isInstrumentationTest(): Boolean {
    return ARTIFACT_NAME_ANDROID_TEST == artifactName
  }

  private fun computeExtraSourceProviders(
    project: IdeAndroidProject,
    variant: IdeVariant,
    filter: (IdeExtraSourceProvider) -> Boolean,
  ): List<LintModelSourceProvider> {
    val providers = mutableListOf<LintModelSourceProvider>()

    project.defaultSourceProvider.extraSourceProviders
      .filter { filter(it) }
      .forEach { extra -> getSourceProvider(extra)?.let { providers.add(it) } }

    project.multiVariantData?.let { multiVariantData ->
      for (flavorContainer in multiVariantData.productFlavors) {
        if (variant.productFlavors.contains(flavorContainer.productFlavor.name)) {
          flavorContainer.extraSourceProviders
            .filter { filter(it) }
            .forEach { extra -> getSourceProvider(extra)?.let { providers.add(it) } }
        }
      }

      for (buildTypeContainer in multiVariantData.buildTypes) {
        if (variant.buildType == buildTypeContainer.buildType.name) {
          buildTypeContainer.extraSourceProviders
            .filter { filter(it) }
            .forEach { extra ->
              getSourceProvider(
                  providerContainer = extra,
                  debugOnly = buildTypeContainer.buildType.isDebuggable,
                )
                ?.let { providers.add(it) }
            }
        }
      }
    }

    return providers
  }

  /**
   * TODO: This is not correct; this method simultaneously returns both the unit test and
   *   instrumentation test folders. These two are not normally combined in the build system (they
   *   can contain conflicting definitions of the class for example). Lint uses this method in a
   *   couple of different ways: (1) to find all the source files it must analyze in turn (for that
   *   purpose, this method is okay), and (2) to set up the class path in the CLI setup for PSI.
   *   This is problematic, but solving it properly is going to take more work (e.g. we need to do
   *   separate handling for each test target), and since this is the way lint has always worked
   *   we're leaving this brokenness here for now until we address this with the dependency graph
   *   rewrite.
   */
  private fun computeTestSourceProviders(
    project: IdeAndroidProject,
    variant: IdeVariant,
  ): List<LintModelSourceProvider> {
    return computeExtraSourceProviders(project, variant) { it.isTest() }
  }

  private fun computeTestFixturesSourceProviders(
    project: IdeAndroidProject,
    variant: IdeVariant,
  ): List<LintModelSourceProvider> {
    val providers = mutableListOf<LintModelSourceProvider>()

    providers.addAll(computeExtraSourceProviders(project, variant) { it.isTestFixtures() })

    variant.testFixturesArtifact?.let { artifact ->
      artifact.variantSourceProvider?.let { providers.add(getSourceProvider(it)) }
      artifact.multiFlavorSourceProvider?.let {
        providers.add(
          getSourceProvider(
            it,
            debugOnly =
              project.multiVariantData!!
                .buildTypes
                .first { it.buildType.name == variant.buildType }
                .buildType
                .isDebuggable,
          )
        )
      }
    }
    return providers
  }

  private fun getSourceProvider(
    providerContainer: IdeExtraSourceProvider,
    debugOnly: Boolean = false,
  ): LintModelSourceProvider? {
    val provider = providerContainer.sourceProvider ?: return null
    return DefaultLintModelSourceProvider(
      manifestFiles = listOf(provider.manifestFile),
      javaDirectories = (provider.javaDirectories + provider.kotlinDirectories).distinct(),
      resDirectories = provider.resDirectories,
      assetsDirectories = provider.assetsDirectories,
      unitTestOnly = providerContainer.isUnitTest(),
      instrumentationTestOnly = providerContainer.isInstrumentationTest(),
      debugOnly = debugOnly,
      testFixture = providerContainer.isTestFixtures(),
    )
  }

  private fun getSourceProvider(
    provider: IdeSourceProvider,
    unitTestOnly: Boolean = false,
    instrumentationTestOnly: Boolean = false,
    debugOnly: Boolean = false,
    testFixturesOnly: Boolean = false,
  ): LintModelSourceProvider {
    return DefaultLintModelSourceProvider(
      manifestFiles = listOf(provider.manifestFile),
      javaDirectories = (provider.javaDirectories + provider.kotlinDirectories).distinct(),
      resDirectories = provider.resDirectories,
      assetsDirectories = provider.assetsDirectories,
      unitTestOnly = unitTestOnly,
      instrumentationTestOnly = instrumentationTestOnly,
      debugOnly = debugOnly,
      testFixture = testFixturesOnly,
    )
  }

  private fun IdeClassField.toResourceField(): LintModelResourceField {
    return DefaultLintModelResourceField(type = type, name = name, value = value)
  }

  private fun getBuildFeatures(
    project: IdeAndroidProject,
    agpVersion: AgpVersion?,
  ): LintModelBuildFeatures {
    return DefaultLintModelBuildFeatures(
      viewBinding = usesViewBinding(project, agpVersion),
      coreLibraryDesugaringEnabled = project.javaCompileOptions.isCoreLibraryDesugaringEnabled,
      namespacingMode = getNamespacingMode(project),
    )
  }

  private fun usesViewBinding(project: IdeAndroidProject, agpVersion: AgpVersion?): Boolean {
    return if (agpVersion != null && agpVersion.isAtLeast(3, 6, 0)) {
      project.viewBindingOptions?.enabled == true
    } else {
      false
    }
  }

  private fun isNeverShrinking(project: IdeAndroidProject): Boolean {
    return project.multiVariantData!!.buildTypes.none { it.buildType.isMinifyEnabled }
  }

  private fun useSupportLibraryVectorDrawables(variant: IdeVariant): Boolean {
    return variant.vectorDrawablesUseSupportLibrary
  }

  private fun getAgpVersion(project: IdeAndroidProject): AgpVersion? {
    return AgpVersion.tryParse(project.agpVersion)
  }

  private fun getNamespacingMode(project: IdeAndroidProject): LintModelNamespacingMode {
    return when (project.aaptOptions.namespacing) {
      IdeAaptOptions.Namespacing.DISABLED -> LintModelNamespacingMode.DISABLED
      IdeAaptOptions.Namespacing.REQUIRED -> LintModelNamespacingMode.REQUIRED
    }
  }

  private fun getMavenName(androidProject: IdeAndroidProject): LintModelMavenName? {
    val groupId = androidProject.groupId ?: return null
    return DefaultLintModelMavenName(groupId, androidProject.projectPath.projectPath, "")
  }

  private fun getLintOptions(project: IdeAndroidProject): LintModelLintOptions =
    getLintOptions(project.lintOptions)

  private fun getLintOptions(options: IdeLintOptions): LintModelLintOptions {
    val severityOverrides =
      options.severityOverrides?.let { source ->
        val map = LinkedHashMap<String, LintModelSeverity>()
        for ((id, severityInt) in source.entries) {
          map[id] = getSeverity(severityInt)
        }
        map
      }

    return DefaultLintModelLintOptions(
      // Not all DSL LintOptions; only some are actually accessed from outside
      // the Gradle/CLI configuration currently
      baselineFile = options.baselineFile,
      lintConfig = options.lintConfig,
      severityOverrides = severityOverrides,
      checkTestSources = options.isCheckTestSources,
      checkDependencies = options.isCheckDependencies,
      disable = options.disable,
      enable = options.enable,
      check = options.check,
      abortOnError = options.isAbortOnError,
      absolutePaths = options.isAbsolutePaths,
      noLines = options.isNoLines,
      quiet = options.isQuiet,
      checkAllWarnings = options.isCheckAllWarnings,
      ignoreWarnings = options.isIgnoreWarnings,
      warningsAsErrors = options.isWarningsAsErrors,
      ignoreTestSources = options.isIgnoreTestSources,
      ignoreTestFixturesSources = options.isIgnoreTestFixturesSources,
      checkGeneratedSources = options.isCheckGeneratedSources,
      explainIssues = options.isExplainIssues,
      showAll = options.isShowAll,
      textReport = options.textReport,
      textOutput = options.textOutput,
      htmlReport = options.htmlReport,
      htmlOutput = options.htmlOutput,
      xmlReport = options.xmlReport,
      xmlOutput = options.xmlOutput,
      sarifReport = options.sarifReport,
      sarifOutput = options.sarifOutput,
      checkReleaseBuilds = options.isCheckReleaseBuilds,
    )
  }

  private fun IdeApiVersion.toAndroidVersion(): AndroidVersion {
    return AndroidVersion(apiLevel, codename)
  }

  /**
   * An [LintModelModule] which holds on to the underlying builder-model and lazily constructs parts
   * of the model less likely to be needed (such as all the variants). This is particularly useful
   * when lint is running on a subset of checks on the fly in the editor in the IDE for example.
   */
  inner class LazyLintModelModule(
    override val loader: LintModelModuleLoader,
    private val project: IdeAndroidProject,
    private val projectVariants: Collection<IdeVariant>,
    private val multiVariantData: IdeMultiVariantData,
    override val dir: File,
    override val agpVersion: AgpVersion?,
  ) : LintModelModule {
    override val modulePath: String
      get() = project.projectPath.projectPath

    override val type: LintModelModuleType
      get() = getModuleType(project.projectType)

    override val mavenName: LintModelMavenName?
      get() = getMavenName(project)

    override val buildFolder: File
      get() = project.buildFolder

    override val resourcePrefix: String?
      get() = project.resourcePrefix

    override val dynamicFeatures: Collection<String>
      get() = project.dynamicFeatures

    override val bootClassPath: List<File>
      get() = project.bootClasspath.map { File(it) }

    override val javaSourceLevel: String
      get() = project.javaCompileOptions.sourceCompatibility

    override val compileTarget: String
      get() = project.compileTarget

    override val lintRuleJars: List<File> = project.getLintRuleJarsForAnyAgpVersion()

    override fun neverShrinking(): Boolean {
      return isNeverShrinking(project)
    }

    // Lazy properties

    private var _lintOptions: LintModelLintOptions? = null
    override val lintOptions: LintModelLintOptions
      get() = _lintOptions ?: getLintOptions(project).also { _lintOptions = it }

    private var _variants: List<LintModelVariant>? = null
    override val variants: List<LintModelVariant>
      // Lazily initialize the _variants property, reusing any already
      // looked up variants from the [variantMap] and also populating that map
      // for latest retrieval
      get() =
        _variants
          ?: projectVariants
            .map { variant ->
              // (Not just using findVariant since that searches linearly
              // through variant list to match by name)
              variantMap[variant.name]
                ?: LazyLintModelVariant(this, project, variant, multiVariantData, libraryResolver)
                  .also { variantMap[it.name] = it }
            }
            .also { _variants = it }

    /** Map from variant name to variant */
    private val variantMap = mutableMapOf<String, LintModelVariant>()

    override fun findVariant(name: String): LintModelVariant? =
      variantMap[name]
        ?: run {
          val buildVariant = projectVariants.firstOrNull { it.name == name }
          buildVariant
            ?.let { LazyLintModelVariant(this, project, it, multiVariantData, libraryResolver) }
            ?.also { variantMap[name] = it }
        }

    override fun defaultVariant(): LintModelVariant? {
      return projectVariants.firstOrNull()?.let { findVariant(it.name) }
    }
  }

  inner class LazyLintModelVariant(
    override val module: LintModelModule,
    private val project: IdeAndroidProject,
    private val variant: IdeVariant,
    private val multiVariantData: IdeMultiVariantData,
    override val libraryResolver: LintModelLibraryResolver,
  ) : LintModelVariant {
    private val buildType = getBuildType(multiVariantData, variant)

    override val name: String
      get() = variant.name

    override val useSupportLibraryVectorDrawables: Boolean
      get() = useSupportLibraryVectorDrawables(variant)

    override val mergedManifest: File?
      get() = null // Injected by legacy AGP lint runner

    override val manifestMergeReport: File?
      get() = null // Injected by legacy AGP lint runner

    override val `package`: String?
      get() =
        project.namespace
          // not the same as the namespace, which isn't present, but better than null
          ?: variant.mainArtifact.applicationId

    override val minSdkVersion: AndroidVersion?
      get() = variant.minSdkVersion.toAndroidVersion()

    override val targetSdkVersion: AndroidVersion?
      get() = variant.targetSdkVersion?.toAndroidVersion()

    override val resourceConfigurations: Collection<String>
      get() = variant.resourceConfigurations

    override val debuggable: Boolean
      get() = buildType.isDebuggable

    override val shrinkable: Boolean
      get() = buildType.isMinifyEnabled

    // Lazy properties

    private var _sourceProviders: List<LintModelSourceProvider>? = null
    override val sourceProviders: List<LintModelSourceProvider>
      get() =
        _sourceProviders ?: computeSourceProviders(project, variant).also { _sourceProviders = it }

    private var _testSourceProviders: List<LintModelSourceProvider>? = null
    override val testSourceProviders: List<LintModelSourceProvider>
      get() =
        _testSourceProviders
          ?: computeTestSourceProviders(project, variant).also { _testSourceProviders = it }

    private var _testFixturesSourceProviders: List<LintModelSourceProvider>? = null
    override val testFixturesSourceProviders: List<LintModelSourceProvider>
      get() =
        _testFixturesSourceProviders
          ?: computeTestFixturesSourceProviders(project, variant).also {
            _testFixturesSourceProviders = it
          }

    private var _resValues: Map<String, LintModelResourceField>? = null
    override val resValues: Map<String, LintModelResourceField>
      get() =
        _resValues
          ?: variant.resValues.mapValues { it.value.toResourceField() }.also { _resValues = it }

    private var _manifestPlaceholders: Map<String, String>? = null
    override val manifestPlaceholders: Map<String, String>
      get() =
        _manifestPlaceholders ?: variant.manifestPlaceholders.also { _manifestPlaceholders = it }

    private var _mainArtifact: LintModelAndroidArtifact? = null
    @Deprecated("This property is deprecated.", replaceWith = ReplaceWith("artifact"))
    override val mainArtifact: LintModelAndroidArtifact
      get() =
        _mainArtifact
          ?: getArtifact(variant.mainArtifact, LintModelArtifactType.MAIN).also {
            _mainArtifact = it
          }

    override val artifact: LintModelArtifact
      get() =
        _mainArtifact
          ?: getArtifact(variant.mainArtifact, LintModelArtifactType.MAIN).also {
            _mainArtifact = it
          }

    private var _testArtifact: LintModelJavaArtifact? = null
    override val testArtifact: LintModelJavaArtifact?
      get() = _testArtifact ?: getUnitTestArtifact(variant).also { _testArtifact = it }

    private var _androidTestArtifact: LintModelAndroidArtifact? = null
    override val androidTestArtifact: LintModelAndroidArtifact?
      get() =
        _androidTestArtifact ?: getAndroidTestArtifact(variant).also { _androidTestArtifact = it }

    private var _testFixturesArtifact: LintModelAndroidArtifact? = null
    override val testFixturesArtifact: LintModelAndroidArtifact?
      get() =
        _testFixturesArtifact
          ?: getTestFixturesArtifact(variant).also { _testFixturesArtifact = it }

    private var _proguardFiles: Collection<File>? = null
    override val proguardFiles: Collection<File>
      get() = _proguardFiles ?: variant.proguardFiles.also { _proguardFiles = it }

    private var _consumerProguardFiles: Collection<File>? = null
    override val consumerProguardFiles: Collection<File>
      get() =
        _consumerProguardFiles ?: variant.consumerProguardFiles.also { _consumerProguardFiles = it }

    private var _buildFeatures: LintModelBuildFeatures? = null
    override val buildFeatures: LintModelBuildFeatures
      get() =
        _buildFeatures ?: getBuildFeatures(project, module.agpVersion).also { _buildFeatures = it }

    override val partialResultsDir: File?
      get() = null

    override val desugaredMethodsFiles: Collection<File>
      get() = variant.desugaredMethodsFiles
  }

  companion object {
    fun getMavenName(artifact: IdeArtifactLibrary): LintModelMavenName =
      when (val component = artifact.component) {
        null -> DefaultLintModelMavenName(NON_MAVEN, artifact.name)
        else ->
          DefaultLintModelMavenName(component.group, component.name, component.version.toString())
      }

    /**
     * Returns the [LintModelModuleType] for the given [typeId]. Type ids must be one of the values
     * defined by AndroidProjectTypes.PROJECT_TYPE_*.
     */
    @JvmStatic
    fun getModuleType(typeId: Int): LintModelModuleType {
      return when (typeId) {
        AndroidProjectTypes.PROJECT_TYPE_APP -> LintModelModuleType.APP
        AndroidProjectTypes.PROJECT_TYPE_LIBRARY -> LintModelModuleType.LIBRARY
        AndroidProjectTypes.PROJECT_TYPE_TEST -> LintModelModuleType.TEST
        AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP -> LintModelModuleType.INSTANT_APP
        AndroidProjectTypes.PROJECT_TYPE_FEATURE -> LintModelModuleType.FEATURE
        AndroidProjectTypes.PROJECT_TYPE_DYNAMIC_FEATURE -> LintModelModuleType.DYNAMIC_FEATURE
        else -> throw IllegalArgumentException("The value $typeId is not a valid project type ID")
      }
    }

    /** Returns the [LintModelModuleType] for the given [type]. */
    @JvmStatic
    fun getModuleType(type: IdeAndroidProjectType): LintModelModuleType {
      return when (type) {
        IdeAndroidProjectType.PROJECT_TYPE_APP -> LintModelModuleType.APP
        IdeAndroidProjectType.PROJECT_TYPE_LIBRARY -> LintModelModuleType.LIBRARY
        IdeAndroidProjectType.PROJECT_TYPE_TEST -> LintModelModuleType.TEST
        IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP -> LintModelModuleType.INSTANT_APP
        IdeAndroidProjectType.PROJECT_TYPE_FEATURE -> LintModelModuleType.FEATURE
        IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE -> LintModelModuleType.DYNAMIC_FEATURE
        IdeAndroidProjectType.PROJECT_TYPE_ATOM ->
          throw IllegalArgumentException("The value $type is not a valid project type ID")
        IdeAndroidProjectType.PROJECT_TYPE_KOTLIN_MULTIPLATFORM ->
          throw IllegalArgumentException("$type is not yet supported")
      }
    }

    private fun getSeverity(severity: Int): LintModelSeverity =
      when (severity) {
        LintOptions.SEVERITY_FATAL -> LintModelSeverity.FATAL
        LintOptions.SEVERITY_ERROR -> LintModelSeverity.ERROR
        LintOptions.SEVERITY_WARNING -> LintModelSeverity.WARNING
        LintOptions.SEVERITY_INFORMATIONAL -> LintModelSeverity.INFORMATIONAL
        LintOptions.SEVERITY_IGNORE -> LintModelSeverity.IGNORE
        LintOptions.SEVERITY_DEFAULT_ENABLED -> LintModelSeverity.WARNING
        else -> LintModelSeverity.IGNORE
      }
  }
}
