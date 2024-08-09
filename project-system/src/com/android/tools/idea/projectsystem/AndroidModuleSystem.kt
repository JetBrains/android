/*
 * Copyright (C) 2017 The Android Open Source Project
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
@file:JvmName("ModuleSystemUtil")

package com.android.tools.idea.projectsystem

import com.android.AndroidProjectTypes
import com.android.ide.common.repository.GradleCoordinate
import com.android.manifmerger.ManifestSystemProperty
import com.android.projectmodel.ExternalAndroidLibrary
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.run.ApkProvisionException
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.util.androidFacet
import com.android.tools.module.ModuleDependencies
import com.google.wireless.android.sdk.stats.TestLibraries
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import java.nio.file.Path

/**
 * Provides a build-system-agnostic interface to the build system. Instances of this interface
 * contain methods that apply to a specific [Module].
 */
interface AndroidModuleSystem: SampleDataDirectoryProvider, ModuleHierarchyProvider {

  enum class Type {
    TYPE_NON_ANDROID,
    TYPE_APP,
    TYPE_LIBRARY,
    TYPE_TEST,
    TYPE_ATOM,
    TYPE_INSTANTAPP,
    TYPE_FEATURE,
    TYPE_DYNAMIC_FEATURE

  }

  val type: Type
    get() = when (module.androidFacet?.properties?.PROJECT_TYPE) {
      AndroidProjectTypes.PROJECT_TYPE_APP -> Type.TYPE_APP
      AndroidProjectTypes.PROJECT_TYPE_LIBRARY -> Type.TYPE_LIBRARY
      AndroidProjectTypes.PROJECT_TYPE_TEST -> Type.TYPE_TEST
      AndroidProjectTypes.PROJECT_TYPE_ATOM -> Type.TYPE_ATOM
      AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP -> Type.TYPE_INSTANTAPP
      AndroidProjectTypes.PROJECT_TYPE_FEATURE -> Type.TYPE_FEATURE
      AndroidProjectTypes.PROJECT_TYPE_DYNAMIC_FEATURE -> Type.TYPE_DYNAMIC_FEATURE
      null -> Type.TYPE_NON_ANDROID
      else -> Type.TYPE_NON_ANDROID
    }

  /** [Module] that this [AndroidModuleSystem] handles. */
  val module: Module

  /** [ClassFileFinder] that uses this module as scope for the search. */
  val moduleClassFileFinder: ClassFileFinder

  /**
   * Optional method to implement by [AndroidModuleSystem] implementations that allows scoping the search to a specific
   * origin source file to allow for disambiguation.
   * If the given [sourceFile] is null, this method will return the [moduleClassFileFinder] for the [Module].
   */
  fun getClassFileFinderForSourceFile(sourceFile: VirtualFile?) = moduleClassFileFinder

  /**
   * Requests information about the folder layout for the module. This can be used to determine
   * where files of various types should be written.
   *
   * TODO: Figure out and document the rest of the contracts for this method, such as how targetDirectory is used,
   * what the source set names are used for, and why the result is a list
   *
   * @param targetDirectory to filter the relevant source providers from the android facet.
   * @return a list of templates created from each of the android facet's source providers.
   * In cases where the source provider returns multiple paths, we always take the first match.
   */
  fun getModuleTemplates(targetDirectory: VirtualFile?): List<NamedModuleTemplate>

  /**
   * Analyzes the compatibility of the [dependenciesToAdd] with the existing artifacts in the project.
   *
   * The version component of each of the coordinates in [dependenciesToAdd] are disregarded.
   * The result is a triplet consisting of:
   * <ul>
   *   <li>A list of coordinates including a valid version found in the repository</li>
   *   <li>A list of coordinates that were missing from the repository</li>
   *   <li>A warning string describing the compatibility issues that could not be resolved if any</li>
   * </ul>
   *
   * An incompatibility warning is either a compatibility with problem among the already existing artifacts,
   * or a compatibility problem with one of the [dependenciesToAdd]. In the latter case the coordinates in
   * the found coordinates are simply the latest version of the libraries, which may or may not cause build
   * errors if they are added to the project.
   * <p>
   * An empty warning value and an empty missing list of coordinates indicates a successful result.
   * <p>
   * **Note**: This function may cause the parsing of build files and as such should not be called from the UI thread.
   */
  fun analyzeDependencyCompatibility(dependenciesToAdd: List<GradleCoordinate>)
    : Triple<List<GradleCoordinate>, List<GradleCoordinate>, String>

  /**
   * Returns the dependency accessible to sources contained in this module referenced by its [GradleCoordinate] as registered with the
   * build system (e.g. build.gradle for Gradle, BUILD for bazel, etc). Build systems such as Gradle allow users to specify a dependency
   * such as x.y.+, which it will resolve to a specific version at sync time. This method returns the version registered in the build
   * script.
   * <p>
   * This method will find a dependency that matches the given query coordinate. For example:
   * Query coordinate a:b:+ will return a:b:+ if a:b:+ is registered with the build system.
   * Query coordinate a:b:+ will return a:b:123 if a:b:123 is registered with the build system.
   * Query coordinate a:b:456 will return null if a:b:456 is not registered, even if a:b:123 is.
   * Use [AndroidModuleSystem.getResolvedDependency] if you want the resolved dependency.
   * <p>
   * **Note**: This function may perform read actions and may cause the parsing of build files, as such should not be called from
   * the UI thread.
   */
  @Throws(DependencyManagementException::class)
  fun getRegisteredDependency(coordinate: GradleCoordinate): GradleCoordinate?

  /**
   * Returns the dependency accessible to sources contained in this module referenced by its [GradleCoordinate].
   * <p>
   * This method will resolve version information to what is resolved. For example:
   * Query coordinate a:b:+ will return a:b:123 if version 123 of that artifact is a resolved dependency.
   * Query coordinate a:b:123 will return a:b:123 if version 123 of that artifact is a resolved dependency.
   * Query coordinate a:b:456 will return null if version 123 is a resolved dependency but not version 456.
   * Use [AndroidModuleSystem.getRegisteredDependency] if you want the registered dependency.
   * <p>
   * **Note**: This function will not acquire any locks during its operation.
   */
  @Throws(DependencyManagementException::class)
  fun getResolvedDependency(coordinate: GradleCoordinate): GradleCoordinate? = getResolvedDependency(coordinate, DependencyScopeType.MAIN)

  @Throws(DependencyManagementException::class)
  fun getResolvedDependency(coordinate: GradleCoordinate, scope: DependencyScopeType): GradleCoordinate?

  /** Whether this module system supports adding dependencies of the given type via [registerDependency] */
  fun canRegisterDependency(type: DependencyType = DependencyType.IMPLEMENTATION): CapabilityStatus

  /**
   * Register a requested dependency with the build system. Note that the requested dependency won't be available (a.k.a. resolved)
   * until the next sync. To ensure the dependency is resolved and available for use, sync the project after calling this function.
   * This method throws [DependencyManagementException] for any errors that occur when adding the dependency.
   * <p>
   * **Note**: This function will perform a write action.
   */
  @Throws(DependencyManagementException::class)
  fun registerDependency(coordinate: GradleCoordinate)

  /**
   * Like [registerDependency] where you can specify the type of dependency to add.
   * This method throws [DependencyManagementException] for any errors that occur when adding the dependency.
   */
  @Throws(DependencyManagementException::class)
  fun registerDependency(coordinate: GradleCoordinate, type: DependencyType)

  /**
   * Updates any coordinates to the versions specified in the [toVersions] list.
   * For example, if you pass it [com.android.support.constraint:constraint-layout:1.0.0-alpha2],
   * it will find any constraint layout occurrences of 1.0.0-alpha1 and replace them with 1.0.0-alpha2.
   */
  @Throws(DependencyManagementException::class)
  fun updateLibrariesToVersion(toVersions: List<GradleCoordinate>) : Unit = throw UnsupportedOperationException()

  /**
   * Returns the resolved libraries that this module depends on.
   * <p>
   * **Note**: This function will not acquire read/write locks during its operation.
   */
  fun getAndroidLibraryDependencies(scope: DependencyScopeType): Collection<ExternalAndroidLibrary>

  /**
   * Returns the Android modules that this module transitively depends on for resources.
   * As Android modules, each module in the returned list will have an associated AndroidFacet.
   *
   * Where supported, the modules will be returned in overlay order to help with resource resolution,
   * but this is only to support legacy callers. New callers should avoid making such assumptions and
   * instead determine the overlay order explicitly if necessary.
   *
   * TODO(b/118317486): Remove this API once resource module dependencies can accurately
   * be determined from order entries for all supported build systems.
   */
  fun getResourceModuleDependencies(): List<Module>

  /**
   * Returns the Android modules that this module's `androidTest` module depends on for resources.
   */
  fun getAndroidTestDirectResourceModuleDependencies(): List<Module> = emptyList()

  /**
   * Returns the Android modules that directly depend on this module for resources.
   * As Android modules, each module in the returned list will have an associated AndroidFacet.
   *
   * TODO(b/118317486): Remove this API once resource module dependencies can accurately
   * be determined from order entries for all supported build systems.
   */
  fun getDirectResourceModuleDependents(): List<Module>

  /**
   * Determines whether or not the underlying build system is capable of generating a PNG
   * from vector graphics.
   */
  fun canGeneratePngFromVectorGraphics(): CapabilityStatus

  /**
   * Returns the overrides that the underlying build system applies when computing the module's
   * merged manifest.
   *
   * @see ManifestOverrides
   */
  fun getManifestOverrides(): ManifestOverrides

  /**
   * Returns the manifest placeholders that the underlying build system applies when computing the module's
   * merged manifest.
   *
   * This is a light version of [getManifestOverrides] and the returned value is supposed to be equal to
   * `getManifestOverrides().placeholders`.
   */
  fun getManifestPlaceholders(): Map<String, String> = getManifestOverrides().placeholders

  /**
   * Returns a structure describing the manifest files contributing to the module's merged manifest.
   */
  fun getMergedManifestContributors(): MergedManifestContributors = defaultGetMergedManifestContributors()

  /**
   * Returns the module's resource package name, or null if it could not be determined.
   *
   * The resource package name is equivalent to the "package" attribute of the module's
   * merged manifest once it has been built. Depending on the build system, however,
   * this method may be optimized to avoid the costs of merged manifest computation.
   *
   * The returned package name is guaranteed to reflect the latest contents of the Android
   * manifests including changes that haven't been saved yet but is NOT guaranteed to reflect
   * the latest contents of the build configuration if the project hasn't been re-synced with
   * the build configuration yet.
   */
  fun getPackageName(): String?

  /**
   * Returns the module's resource test package name, or null if it could not be determined.
   *
   * The returned package name is guaranteed to reflect the latest contents of the Android
   * manifests including changes that haven't been saved yet but is NOT guaranteed to reflect
   * the latest contents of the build configuration if the project hasn't been re-synced with
   * the build configuration yet.
   */
  fun getTestPackageName(): String? = null

  /**
   * Returns the [ApplicationIdProvider] for the given module.
   *
   * Some project systems may be unable to retrieve the package name before the project has been successfully built.
   * The returned [ApplicationIdProvider] will throw [ApkProvisionException]'s or return a name derived from incomplete configuration
   * in this case.
   *
   * Some project system may allow multiple applications in one IDE module. The behavior in this case is defined by the specific project
   * system.
   */
  fun getApplicationIdProvider(): ApplicationIdProvider = object : ApplicationIdProvider {
    val androidModel = AndroidModel.get(module)
    override fun getPackageName(): String =
      androidModel?.applicationId?.takeUnless { it == AndroidModel.UNINITIALIZED_APPLICATION_ID }
      ?: throw ApkProvisionException("The project system cannot obtain the package name at this moment.")

    override fun getTestPackageName(): String =
      throw ApkProvisionException("This (${this::class.java.simpleName}) project system cannot obtain the test package name.")
  }

  /**
   * Returns the [GlobalSearchScope] for a given module that should be used to resolving references.
   *
   * This is a seam for [Module.getModuleWithDependenciesAndLibrariesScope] that allows project systems that have not expressed their
   * module level dependencies accurately to IntelliJ (typically for performance reasons) to provide a different scope than what the
   * module itself would.
   */
  fun getResolveScope(scopeType: ScopeType): GlobalSearchScope

  /** Returns an [TestArtifactSearchScopes] instance for a given module, if multiple test types are supported. */
  fun getTestArtifactSearchScopes(): TestArtifactSearchScopes? = null

  /** Whether the Jetpack Compose feature is enabled for this module. */
  val usesCompose: Boolean get() = false

  /** Shrinker type in selected variant or null if minification is disabled or shrinker cannot be determined.**/
  val codeShrinker: CodeShrinker? get() = null

  val supportsAndroidResources: Boolean get() = true

  /**
   * Whether the R class generated for this module is transitive.
   *
   * If it is transitive it will contain all of the resources defined in its transitive dependencies alongside those defined in this
   * module. If non-transitive it will only contain the resources defined in this module.
   */
  val isRClassTransitive: Boolean get() = true

  /**
   * Returns a list of dynamic feature modules for this module
   */
  fun getDynamicFeatureModules(): List<Module> = emptyList()

  /**
   * Returns the base feature module for this module, if it is a dynamic feature module.
   */
  fun getBaseFeatureModule(): Module? = null

  /** Whether the ML model binding feature is enabled for this module. */
  val isMlModelBindingEnabled: Boolean get() = false

  /** Whether the view binding feature is enabled for this module. */
  val isViewBindingEnabled: Boolean get() = false

  /** Whether the data binding feature is enabled for this module. */
  val isDataBindingEnabled: Boolean get() = false

  /** Whether KAPT is enabled for this module. */
  val isKaptEnabled: Boolean get() = false

  /**
   * Whether the application is debuggable.
   */
  val isDebuggable:Boolean get() = AndroidModel.get(module)?.isDebuggable ?: false

  /**
   * Whether the R class in applications and dynamic features are constant.
   *
   * If they are constant they can be inlined by the java compiler and used in places that
   * require constants such as annotations and cases of switch statements.
   */
  val applicationRClassConstantIds: Boolean get() = true

  /**
   * Whether the R class in instrumentation tests are constant.
   *
   * If they are constant they can be inlined by the java compiler and used in places that
   * require constants such as annotations and cases of switch statements.
   */
  val testRClassConstantIds: Boolean get() = true

  fun getTestLibrariesInUse(): TestLibraries? = null
  fun getModuleNameForCompilation(virtualFile: VirtualFile): String = module.name

  /** Whether AndroidX libraries should be used instead of legacy support libraries. */
  val useAndroidX: Boolean get() = true

  /** Whether [desugarLibraryConfigFiles] can be determined for this AGP version */
  val desugarLibraryConfigFilesKnown: Boolean get() = false

  /** A user visible message, such as 'Only supported for project using Android Gradle plugin '8.1.0-alpha05' and above' */
  val desugarLibraryConfigFilesNotKnownUserMessage: String? get() = "Only supported for Gradle projects"

  val desugarLibraryConfigFiles: List<Path> get() = listOf()

  val moduleDependencies: ModuleDependencies get() = error("Not implemented")

  /** Return a string suitable for presenting to the user to identify this Module System's module. */
  fun getDisplayNameForModule(): String = module.name
}

/**
 * Overrides to be applied when computing the merged manifest, as determined by the build system.
 *
 * These overrides are divided into two categories: [directOverrides], known properties of the merged manifest
 * that are directly overridden (e.g. the application ID), and [placeholders], identifiers in the contributing
 * manifest which the build system replaces with arbitrary plain text during merged manifest computation.
 */
data class ManifestOverrides(
  val directOverrides: Map<ManifestSystemProperty, String> = mapOf(),
  val placeholders: Map<String, String> = mapOf()
) {
  companion object {
    private val PLACEHOLDER_REGEX = Regex("\\$\\{([^}]*)}") // e.g. matches "${placeholder}" and extracts "placeholder"
  }
  fun resolvePlaceholders(string: String) = string.replace(PLACEHOLDER_REGEX) { placeholders[it.groupValues[1]].orEmpty() }
}

/** Types of dependencies that [AndroidModuleSystem.registerDependency] can add */
enum class DependencyType {
  IMPLEMENTATION,
  DEBUG_IMPLEMENTATION,
  // TODO: Add "API," & support in build systems
  ANNOTATION_PROCESSOR
}

enum class DependencyScopeType {
  MAIN,
  UNIT_TEST,
  ANDROID_TEST,
  TEST_FIXTURES,
  SCREENSHOT_TEST,
}

/**
 * Describes the scope that should be used for resolving references in a given file or other context. Can be determined by calling
 * [getScopeType].
 *
 * In project systems that don't have the concept of separate test scopes, [ScopeType.ANDROID_TEST] is the only value used for test sources.
 */
enum class ScopeType {
  MAIN,
  ANDROID_TEST,
  UNIT_TEST,
  TEST_FIXTURES,
  SCREENSHOT_TEST,
  ;

  /** Converts this [ScopeType] to a [Boolean], so it can be used with APIs that don't distinguish between test types. */
  val isForTest
    get() = when (this) {
      MAIN, TEST_FIXTURES -> false
      ANDROID_TEST, UNIT_TEST, SCREENSHOT_TEST -> true
    }

  /** Returns true if this [ScopeType] can contain Android resources. */
  val canHaveAndroidResources
    get() = when (this) {
      TEST_FIXTURES, UNIT_TEST -> false
      MAIN, ANDROID_TEST, SCREENSHOT_TEST -> true
    }
}

fun AndroidModuleSystem.getScopeType(file: VirtualFile, project: Project): ScopeType {
  if (!TestSourcesFilter.isTestSources(file, project)) return ScopeType.MAIN
  val testScopes = getTestArtifactSearchScopes() ?: return ScopeType.ANDROID_TEST

  val inAndroidTest = testScopes.isAndroidTestSource(file)
  val inUnitTest = testScopes.isUnitTestSource(file)

  return when {
    !inUnitTest && inAndroidTest -> ScopeType.ANDROID_TEST
    inUnitTest && !inAndroidTest -> ScopeType.UNIT_TEST
    else -> throw IllegalStateException(
      "Unexpected Test Scope: $file in $project appears to be both in Unit Tests and Android Tests, but should only be part of one.")
  }
}

/**
 * This class, along with the key [LINKED_ANDROID_GRADLE_MODULE_GROUP] is used to track and group modules
 * that are based on the same Gradle project.  In Gradle projects, instances of this class will be attached
 * to all Android modules.  This class should not be accessed directly from outside the Gradle project system (but
 * is unfortunately more widely accessible for historical reasons).
 */
data class LinkedAndroidGradleModuleGroup(
  val holder: Module,
  val main: Module,
  val unitTest: Module?,
  val androidTest: Module?,
  val testFixtures: Module?,
  val screenshotTest: Module?
) {
  fun getModules() = listOfNotNull(holder, main, unitTest, androidTest, testFixtures, screenshotTest)
  override fun toString(): String =
    "holder=${holder.name}, main=${main.name}, unitTest=${unitTest?.name}, " +
    "androidTest=${androidTest?.name}, testFixtures=${testFixtures?.name}, screenshotTest=${screenshotTest?.name}"
}

/**
 * Key used to store [LinkedAndroidGradleModuleGroup] on all modules that are part of the same Gradle project.  This key should
 * not be accessed from outside the Gradle project system (but is unfortunately more widely-accessible for historical reasons.)
 */
val LINKED_ANDROID_GRADLE_MODULE_GROUP = Key.create<LinkedAndroidGradleModuleGroup>("linked.android.gradle.module.group")


fun Module.getHolderModule() : Module = getUserData(LINKED_ANDROID_GRADLE_MODULE_GROUP)?.holder ?: this

fun Module.isHolderModule() : Boolean = getHolderModule() == this

fun Module.getMainModule() : Module = getUserData(LINKED_ANDROID_GRADLE_MODULE_GROUP)?.main ?: this

fun Module.isMainModule() : Boolean = getMainModule() == this

fun Module.getAndroidTestModule() : Module? = getUserData(LINKED_ANDROID_GRADLE_MODULE_GROUP)?.androidTest

fun Module.isAndroidTestModule() : Boolean = getAndroidTestModule() == this

/**
 * Returns the type of Android project this module represents.
 */
fun Module.androidProjectType(): AndroidModuleSystem.Type = getModuleSystem().type
