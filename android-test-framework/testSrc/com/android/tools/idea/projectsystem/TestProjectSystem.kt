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
package com.android.tools.idea.projectsystem

import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.ide.common.resources.AndroidManifestPackageNameUtils
import com.android.ide.common.util.PathString
import com.android.projectmodel.ExternalAndroidLibrary
import com.android.tools.idea.model.ClassJarProvider
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager.BuildMode
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager.BuildStatus
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncReason
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.run.ApkProvisionException
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.util.androidFacet
import com.google.common.collect.HashMultimap
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.AppUIUtil
import org.jetbrains.android.facet.AndroidFacet
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CountDownLatch

/**
 * This implementation of AndroidProjectSystem is used during integration tests and includes methods
 * to stub project system functionalities.
 */
class TestProjectSystem @JvmOverloads constructor(
  val project: Project,
  availableDependencies: List<GradleCoordinate> = listOf(),
  private var sourceProvidersFactoryStub: SourceProvidersFactory = SourceProvidersFactoryStub(),
  @Volatile private var lastSyncResult: SyncResult = SyncResult.SUCCESS,
  private val androidLibraryDependencies: Collection<ExternalAndroidLibrary> = emptySet()
) : AndroidProjectSystem {

  /**
   * Injects this project system into the [project] it was created for.
   */
  fun useInTests() {
    ProjectSystemService.getInstance(project).replaceProjectSystemForTests(this)
  }

  private val dependenciesByModule: HashMultimap<Module, GradleCoordinate> = HashMultimap.create()
  private val availablePreviewDependencies: List<GradleCoordinate>
  private val availableStableDependencies: List<GradleCoordinate>
  private val incompatibleDependencyPairs: HashMap<GradleCoordinate, GradleCoordinate>
  private val coordinateToFakeRegisterDependencyError: HashMap<GradleCoordinate, String>
  var namespace: String? = null
  var manifestOverrides = ManifestOverrides()

  init {
    val sortedHighToLowDeps = availableDependencies.sortedWith(GradleCoordinate.COMPARE_PLUS_HIGHER).reversed()
    val (previewDeps, stableDeps) = sortedHighToLowDeps.partition(GradleCoordinate::isPreview)
    availablePreviewDependencies = previewDeps
    availableStableDependencies = stableDeps
    incompatibleDependencyPairs = HashMap()
    coordinateToFakeRegisterDependencyError = HashMap()
  }

  /**
   * Adds the given artifact to the given module's list of dependencies.
   */
  fun addDependency(artifactId: GoogleMavenArtifactId, module: Module, mavenVersion: GradleVersion) {
    val coordinate = artifactId.getCoordinate(mavenVersion.toString())
    dependenciesByModule.put(module, coordinate)
  }

  /**
   * @return the set of dependencies added to the given module.
   */
  fun getAddedDependencies(module: Module): Set<GradleCoordinate> = dependenciesByModule.get(module)

  /**
   * Mark a pair of dependencies as incompatible so that [AndroidModuleSystem.analyzeDependencyCompatibility]
   * will return them as incompatible dependencies.
   */
  fun addIncompatibleDependencyPair(dep1: GradleCoordinate, dep2: GradleCoordinate) {
    incompatibleDependencyPairs[dep1] = dep2
  }

  /**
   * Add a fake error condition for [coordinate] such that calling [AndroidModuleSystem.registerDependency] on the
   * coordinate will throw a [DependencyManagementException] with error message set to [errorMessage].
   */
  fun addFakeErrorForRegisteringDependency(coordinate: GradleCoordinate, errorMessage: String) {
    coordinateToFakeRegisterDependencyError[coordinate] = errorMessage
  }

  override fun getModuleSystem(module: Module): AndroidModuleSystem {
    class TestAndroidModuleSystemImpl : AndroidModuleSystem {
      override val module = module

      override val moduleClassFileFinder: ClassFileFinder = object : ClassFileFinder {
        override fun findClassFile(fqcn: String): VirtualFile? = null
      }

      override fun analyzeDependencyCompatibility(dependenciesToAdd: List<GradleCoordinate>)
        : Triple<List<GradleCoordinate>, List<GradleCoordinate>, String> {
        val found = mutableListOf<GradleCoordinate>()
        val missing = mutableListOf<GradleCoordinate>()
        var compatibilityWarningMessage = ""
        for (dependency in dependenciesToAdd) {
          val wildcardCoordinate = GradleCoordinate(dependency.groupId!!, dependency.artifactId!!, "+")
          val lookup = availableStableDependencies.firstOrNull { it.matches(wildcardCoordinate) }
                       ?: availablePreviewDependencies.firstOrNull { it.matches(wildcardCoordinate) }
          if (lookup != null) {
            found.add(lookup)
            if (incompatibleDependencyPairs[lookup]?.let { dependenciesToAdd.contains(it) } == true) {
              compatibilityWarningMessage += "$lookup is not compatible with ${incompatibleDependencyPairs[lookup]}\n"
            }
          }
          else {
            missing.add(dependency)
            compatibilityWarningMessage += "Can't find $dependency\n"
          }
        }
        return Triple(found, missing, compatibilityWarningMessage)
      }

      override fun getAndroidLibraryDependencies(scope: DependencyScopeType): Collection<ExternalAndroidLibrary> {
        return androidLibraryDependencies
      }

      override fun canRegisterDependency(type: DependencyType): CapabilityStatus {
        return CapabilitySupported()
      }

      override fun getResourceModuleDependencies() = emptyList<Module>()

      override fun getDirectResourceModuleDependents() = emptyList<Module>()

      override fun registerDependency(coordinate: GradleCoordinate) {
        registerDependency(coordinate, DependencyType.IMPLEMENTATION)
      }

      override fun registerDependency(coordinate: GradleCoordinate, type: DependencyType) {
        coordinateToFakeRegisterDependencyError[coordinate]?.let {
          throw DependencyManagementException(it, DependencyManagementException.ErrorCodes.INVALID_ARTIFACT)
        }
        dependenciesByModule.put(module, coordinate)
      }

      override fun getRegisteredDependency(coordinate: GradleCoordinate): GradleCoordinate? =
        dependenciesByModule[module].firstOrNull { it.matches(coordinate) }

      override fun getResolvedDependency(coordinate: GradleCoordinate, scope: DependencyScopeType): GradleCoordinate? =
        dependenciesByModule[module].firstOrNull { it.matches(coordinate) }

      override fun getModuleTemplates(targetDirectory: VirtualFile?): List<NamedModuleTemplate> {
        return emptyList()
      }

      override fun canGeneratePngFromVectorGraphics(): CapabilityStatus {
        return CapabilityNotSupported()
      }

      override fun getOrCreateSampleDataDirectory(): PathString? = null

      override fun getSampleDataDirectory(): PathString? = null

      override fun getPackageName(): String? {
        if (namespace != null) {
          return namespace
        }
        val facet = module.androidFacet ?: return null
        val primaryManifest = facet.sourceProviders.mainManifestFile ?: return null
        return AndroidManifestPackageNameUtils.getPackageNameFromManifestFile(PathString(primaryManifest.path))
      }

      override fun getManifestOverrides() = manifestOverrides

      override fun getResolveScope(scopeType: ScopeType): GlobalSearchScope {
        return module.getModuleWithDependenciesAndLibrariesScope(scopeType != ScopeType.MAIN)
      }

      override fun getDependencyPath(coordinate: GradleCoordinate): Path? = null
    }

    return TestAndroidModuleSystemImpl()
  }

  override fun getApplicationIdProvider(runConfiguration: RunConfiguration): ApplicationIdProvider {
    return object : ApplicationIdProvider {
      override fun getPackageName(): String = (runConfiguration as? ModuleBasedConfiguration<*, *>)?.configurationModule?.module?.let { module ->
        getModuleSystem(module).getPackageName()
      } ?: throw ApkProvisionException("Not supported run configuration")

      override fun getTestPackageName(): String? = null
    }
  }

  fun emulateSync(result: SyncResult) {
    val latch = CountDownLatch(1)

    AppUIUtil.invokeLaterIfProjectAlive(project) {
      lastSyncResult = result
      project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(result)
      latch.countDown()
    }

    latch.await()
  }

  override fun getSyncManager(): ProjectSystemSyncManager = object : ProjectSystemSyncManager {
    override fun syncProject(reason: SyncReason): ListenableFuture<SyncResult> {
      emulateSync(SyncResult.SUCCESS)
      return Futures.immediateFuture(SyncResult.SUCCESS)
    }

    override fun isSyncInProgress() = false

    override fun isSyncNeeded() = !lastSyncResult.isSuccessful

    override fun getLastSyncResult() = lastSyncResult
  }

  override fun getBuildManager(): ProjectSystemBuildManager = buildManager

  private val buildManager = TestProjectSystemBuildManager(ensureClockAdvancesWhileBuilding = false)

  override fun getDefaultApkFile(): VirtualFile? {
    error("not supported for the test implementation")
  }

  override fun getPathToAapt(): Path {
    error("not supported for the test implementation")
  }

  override fun allowsFileCreation(): Boolean {
    error("not supported for the test implementation")
  }

  override fun supportsProfilingMode(): Boolean {
    error("not supported for the test implementation")
  }

  override fun getPsiElementFinders() = emptyList<PsiElementFinder>()

  override fun getLightResourceClassService(): LightResourceClassService {
    return object : LightResourceClassService {
      override fun getLightRClasses(qualifiedName: String, scope: GlobalSearchScope) = emptyList<PsiClass>()
      override fun getLightRClassesAccessibleFromModule(module: Module, includeTests: Boolean) = emptyList<PsiClass>()
      override fun getLightRClassesContainingModuleResources(module: Module) = emptyList<PsiClass>()
      override fun findRClassPackage(qualifiedName: String): PsiPackage? = null
      override fun getAllLightRClasses() = emptyList<PsiClass>()
      override fun getLightRClassesDefinedByModule(module: Module, includeTestClasses: Boolean) = emptyList<PsiClass>()
    }
  }

  override fun getClassJarProvider(): ClassJarProvider {
    return object: ClassJarProvider {
      override fun getModuleExternalLibraries(module: Module): List<File> = emptyList()
    }
  }

  override fun getSourceProvidersFactory(): SourceProvidersFactory = sourceProvidersFactoryStub

  override fun getAndroidFacetsWithPackageName(project: Project, packageName: String): List<AndroidFacet> {
    return emptyList()
  }
}

class TestProjectSystemBuildManager(
  val ensureClockAdvancesWhileBuilding: Boolean
): ProjectSystemBuildManager {
  companion object {
    @JvmStatic
    fun get(project: Project): TestProjectSystemBuildManager = project.getProjectSystem().getBuildManager() as TestProjectSystemBuildManager
  }

  private val listeners = mutableListOf<ProjectSystemBuildManager.BuildListener>()
  private var lastBuildResult: ProjectSystemBuildManager.BuildResult = ProjectSystemBuildManager.BuildResult.createUnknownBuildResult()
  private var lastBuildMode = ProjectSystemBuildManager.BuildMode.UNKNOWN
  private var _isBuilding = false
  override fun getLastBuildResult(): ProjectSystemBuildManager.BuildResult = lastBuildResult

  override fun compileProject() {
    simulateBuild(BuildMode.ASSEMBLE)
  }

  override fun compileFilesAndDependencies(files: Collection<VirtualFile>) {
    simulateBuild(BuildMode.COMPILE)
  }

  override fun addBuildListener(parentDisposable: Disposable, buildListener: ProjectSystemBuildManager.BuildListener) {
    listeners.add(buildListener)
    Disposer.register(parentDisposable) {
      listeners.remove(buildListener)
    }
  }

  override val isBuilding: Boolean
    get() = _isBuilding

  fun buildStarted(mode: ProjectSystemBuildManager.BuildMode) {
    maybeEnsureClockAdvanced()
    _isBuilding = true
    lastBuildMode = mode
    listeners.forEach {
      it.buildStarted(mode)
    }
    maybeEnsureClockAdvanced()
  }

  fun buildCompleted(status: ProjectSystemBuildManager.BuildStatus) {
    lastBuildResult = ProjectSystemBuildManager.BuildResult(lastBuildMode, status, System.currentTimeMillis())
    maybeEnsureClockAdvanced()
    listeners.forEach {
      it.beforeBuildCompleted(lastBuildResult)
    }
    _isBuilding = false
    listeners.forEach {
      it.buildCompleted(lastBuildResult)
    }
    maybeEnsureClockAdvanced()
  }

  private fun maybeEnsureClockAdvanced() {
    if (ensureClockAdvancesWhileBuilding) {
      Thread.sleep(1)
    }
  }

  private fun simulateBuild(mode: BuildMode) {
    buildStarted(mode)
    buildCompleted(BuildStatus.SUCCESS)
  }
}

private class SourceProvidersFactoryStub : SourceProvidersFactory {
  override fun createSourceProvidersFor(facet: AndroidFacet): SourceProviders? = null
}
