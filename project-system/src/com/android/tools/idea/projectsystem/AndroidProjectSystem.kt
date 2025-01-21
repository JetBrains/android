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
@file:JvmName("ProjectSystemUtil")

package com.android.tools.idea.projectsystem

import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.ClassJarProvider
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ApkProvisionException
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.ValidationError
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFinder
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.android.facet.AndroidFacet

/**
 * Provides a build-system-agnostic interface to the build system. Instances of this interface
 * only apply to a specific [Project].  Instantiating a generalized instance of this interface must, with
 * when considering the wider system, be idempotent with respect to instantiating other instances for the
 * same project: any state needed by the project system must be encapsulated for example in project
 * services.
 */
interface AndroidProjectSystem: ModuleHierarchyProvider {
  /** The IDE project this project system describes */
  val project: Project

  /**
   * Are the android-plugin tools likely to do anything useful for this project?
   */
  fun isAndroidProject(): Boolean

  /**
   * Returns path to android.jar
   */
  fun getBootClasspath(module: Module): Collection<String>

  /**
   * Returns true if the project allows adding new modules.
   */
  fun allowsFileCreation(): Boolean

  /**
   * Returns an interface for interacting with the given module.
   */
  fun getModuleSystem(module: Module): AndroidModuleSystem

  /**
   * Returns the best effort [ApplicationIdProvider] for the given project and [runConfiguration].
   *
   * NOTE: The returned application id provider represents the current build configuration and may become invalid if it changes,
   *       hence this reference should not be cached.
   *
   * Some project systems may be unable to retrieve the package name if no [runConfiguration] is provided or before
   * the project has been successfully built. The returned [ApplicationIdProvider] will throw [ApkProvisionException]'s
   * or return a name derived from incomplete configuration in this case.
   */
  fun getApplicationIdProvider(runConfiguration: RunConfiguration): ApplicationIdProvider? = null

  /**
   * Returns the [ApkProvider] for the given [runConfiguration].
   *
   * NOTE: The returned apk provider represents the current build configuration and may become invalid if it changes,
   *       hence this reference should not be cached.
   *
   * Returns `null`, if the project system does not recognize the [runConfiguration] as a supported one.
   */
  fun getApkProvider(runConfiguration: RunConfiguration): ApkProvider? = null

  fun validateRunConfiguration(runConfiguration: RunConfiguration): List<ValidationError> {
    return validateRunConfiguration(runConfiguration, null)
  }

  fun validateRunConfiguration(runConfiguration: RunConfiguration, quickFixCallback: Runnable?): List<ValidationError> {
    return listOf(ValidationError.fatal("Run configuration ${runConfiguration.name} is not supported in this project"))
  }

  /**
   * Returns an instance of [ProjectSystemSyncManager] that applies to the project.
   */
  fun getSyncManager(): ProjectSystemSyncManager

  fun getBuildManager(): ProjectSystemBuildManager

  /**
   * [PsiElementFinder]s used with the given build system, e.g. for the R classes.
   *
   * These finders should not be registered as extensions
   */
  fun getPsiElementFinders(): Collection<PsiElementFinder>

  /**
   * [LightResourceClassService] instance used by this project system (if used at all).
   */
  fun getLightResourceClassService(): LightResourceClassService

  /**
   * [SourceProvidersFactory] instance used by the project system internally to re-instantiate the cached instance
   * when the structure of the project changes.
   */
  fun getSourceProvidersFactory(): SourceProvidersFactory

  /**
   * Returns a source provider describing build configuration files.
   */
  fun getBuildConfigurationSourceProvider(): BuildConfigurationSourceProvider? = null

  /**
   * @return A provider for finding .class output files and external .jars.
   */
  fun getClassJarProvider(): ClassJarProvider

  /**
   * Returns a collection of [AndroidFacet]s corresponding to project system entities.  Note that this can be different from the
   * collection of all [AndroidFacet]s in the project, if (for example) the project system represents a project system entity with
   * more than one module, each with their own [AndroidFacet].
   */
  fun getAndroidFacets(): Collection<AndroidFacet> = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID)

  /**
   * Returns a collection of [AndroidFacet]s by given package name.
   */
  fun getAndroidFacetsWithPackageName(packageName: String): Collection<AndroidFacet>

  /**
   * Returns true if the given [packageName] is either one of the namespaces, or a parent.
   *
   * For example, if the project contains `com.example.myapp` and `com.example.mylib`, this
   * would return true for exactly
   *    `com`, `com.example`, `com.example.myapp` and `com.example.mylib`.
   *
   * This method may return false for packages that do exist if it is called when project
   * sync has failed, or for non-gradle build systems if it is called before indexes are ready,
   * but it should never throw IndexNotReadyException.
   */
  fun isNamespaceOrParentPackage(packageName: String): Boolean

  @Deprecated("Replaced by method without the project parameter", replaceWith = ReplaceWith("getKnownApplicationIds()"))
  fun getKnownApplicationIds(project: Project): Set<String> = getKnownApplicationIds()

  /**
   * @return all the application IDs of artifacts this project module is known to produce.
   */
  fun getKnownApplicationIds(): Set<String>

  /**
   * Finds modules by application ID.
   *
   * The application ID might be from a different variant than the currently selected variant,
   * e.g. if an app is set up to have release variant application id com.example.myapp,
   * but the debug variant application id is com.example.myapp.debug, this method will
   * return the app main module for both of those application IDs, irrespective of which
   * is the currently active variant in the IDE.
   *
   * @return Candidate modules to use for attachment configuration.
   *         The collection may be empty if no suitable module exists.
   */
  fun findModulesWithApplicationId(applicationId: String): Collection<Module>

  /**
   * @return true if the project's build system supports building the app with a profiling mode flag (profileable, debuggable, etc.).
   */
  fun supportsProfilingMode() = false

  /**
   * Return a [Comparator] for (partially) ordering modules in a project-system-specific way, suitable for use by [minWith] to
   * find the most production-relevant module(s).  (This is inherently a vague specification; callers should not be critically
   * dependent on the order returned from this [Comparator].)
   */
  fun getProjectSystemModuleTypeComparator(): Comparator<Module> = defaultProjectSystemModuleTypeComparator
}

private val defaultProjectSystemModuleTypeComparator: Comparator<Module> = Comparator.comparingInt { 0 }

val EP_NAME = ExtensionPointName<AndroidProjectSystemProvider>("com.android.project.projectsystem")

/**
 * Returns the instance of {@link AndroidProjectSystem} that applies to the given {@link Project}.
 */
fun Project.getProjectSystem(): AndroidProjectSystem {
  return ProjectSystemService.getInstance(this).projectSystem
}

/**
 * Returns the instance of [ProjectSystemSyncManager] that applies to the given [Project].
 */
fun Project.getSyncManager(): ProjectSystemSyncManager {
  return getProjectSystem().getSyncManager()
}

/**
 * Returns the instance of [AndroidModuleSystem] that applies to the given [Module].
 */
fun Module.getModuleSystem(): AndroidModuleSystem {
  return project.getProjectSystem().getModuleSystem(this)
}

/**
 * Returns the instance of [AndroidModuleSystem] that applies to the given [AndroidFacet].
 */
fun AndroidFacet.getModuleSystem(): AndroidModuleSystem {
  return module.getModuleSystem()
}

/**
 * Returns the instance of [AndroidModuleSystem] that applies to the given [PsiElement], if it can be determined.
 */
fun PsiElement.getModuleSystem(): AndroidModuleSystem? = ModuleUtilCore.findModuleForPsiElement(this)?.getModuleSystem()


/**
 * Returns a list of all Android holder modules. For Gradle projects, these are the intellij [Module] objects that correspond to
 * an emptyish (no roots/deps) module that contains the other source set modules as children. If you need to obtain the module for
 * the currently active production source set, use [AndroidModuleSystem.getProductionAndroidModule] on the returned [Module] objects.
 */
fun Project.getAndroidModulesForDisplay(): List<Module> = getProjectSystem().getAndroidFacets().map { it.module }

/**
 * Returns a list of the substantively-distinct [AndroidFacet]s in the project.need
 *
 * Note: there might be modules in the project that the project system associates with [AndroidFacet], which are not returned from this
 * method; for example, representing individual SourceSets as IDEA [Module]s each with an [AndroidFacet] attached.  Facets corresponding
 * to these subsidiary modules are filtered out here by the Project System.
 */
fun Project.getAndroidFacets(): List<AndroidFacet> = getProjectSystem().getAndroidFacets().toList()


/**
 * Indicates whether the given project has at least one module backed by build models.
 */
fun Project.requiresAndroidModel(): Boolean {
  val androidFacets: List<AndroidFacet> = ProjectFacetManager.getInstance(this).getFacets(AndroidFacet.ID)
  return ContainerUtil.exists(androidFacets) { facet: AndroidFacet -> AndroidModel.isRequired(facet) }
}

fun isAndroidTestFile(project: Project, file: VirtualFile?): Boolean = ReadAction.nonBlocking<Boolean> {
  val module = file?.let { ProjectFileIndex.getInstance(project).getModuleForFile(file) }
  module?.let { TestArtifactSearchScopes.getInstance(module)?.isAndroidTestSource(file) } ?: false
}.executeSynchronously()

fun isUnitTestFile(project: Project, file: VirtualFile?): Boolean = ReadAction.nonBlocking<Boolean> {
  val module = file?.let { ProjectFileIndex.getInstance(project).getModuleForFile(file) }
  module?.let { TestArtifactSearchScopes.getInstance(module)?.isUnitTestSource(file) } ?: false
}.executeSynchronously()

fun isScreenshotTestFile(project: Project, file: VirtualFile?): Boolean = ReadAction.nonBlocking<Boolean> {
  val module = file?.let { ProjectFileIndex.getInstance(project).getModuleForFile(file) }
  module?.let { TestArtifactSearchScopes.getInstance(module)?.isScreenshotTestSource(file) } ?: false
}.executeSynchronously()

fun isTestFile(project: Project, file: VirtualFile?) =
  isUnitTestFile(project, file) || isAndroidTestFile(project, file) || isScreenshotTestFile(project, file)
