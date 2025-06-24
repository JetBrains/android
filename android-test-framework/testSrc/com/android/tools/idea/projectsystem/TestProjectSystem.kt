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

import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.WellKnownMavenArtifactId
import com.android.ide.common.resources.AndroidManifestPackageNameUtils
import com.android.ide.common.util.PathString
import com.android.projectmodel.ExternalAndroidLibrary
import com.android.tools.idea.model.ClassJarProvider
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager.BuildMode
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager.BuildStatus
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncReason
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.gradle.getAndroidTestModule
import com.android.tools.idea.rendering.StudioModuleDependencies
import com.android.tools.idea.run.ApkProvisionException
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.util.androidFacet
import com.android.tools.idea.util.toIoFile
import com.android.tools.module.ModuleDependencies
import com.google.common.collect.HashMultimap
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.AppUIUtil
import org.jetbrains.android.facet.AndroidFacet
import java.io.File
import java.util.concurrent.CountDownLatch

/**
 * This implementation of AndroidProjectSystem is used during integration tests and includes methods
 * to stub project system functionalities.
 */
@Deprecated("Recommended replacement: use AndroidProjectRule.withAndroidModels which gives a more realistic project structure and project system behaviors while still not requiring a 'real' synced project")
class TestProjectSystem @JvmOverloads constructor(
  override val project: Project,
  availableDependencies: List<GradleCoordinate> = listOf(),
  private var sourceProvidersFactoryStub: SourceProvidersFactory = SourceProvidersFactoryStub(),
  @Volatile private var lastSyncResult: SyncResult = SyncResult.SUCCESS,
  private val androidLibraryDependencies: Collection<ExternalAndroidLibrary> = emptySet()
) : AndroidProjectSystem {

  data class Dependency(val type: DependencyType, val coordinate: GradleCoordinate)

  interface TestModuleSystem: AndroidModuleSystem, RegisteringModuleSystem<TestRegisteredDependencyQueryId, TestRegisteredDependencyId>

  override fun isAndroidProject(): Boolean {
    return ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)
  }

  override fun getBootClasspath(module: Module): Collection<String> {
    return emptyList()
  }

  /**
   * Injects this project system into the [project] it was created for.
   */
  @Deprecated("Recommended replacement: use AndroidProjectRule.withAndroidModels which gives a more realistic project structure and project system behaviors while still not requiring a 'real' synced project")
  fun useInTests() {
    ProjectSystemService.getInstance(project).replaceProjectSystemForTests(this)
    val provider = object : ApplicationProjectContextProvider<AndroidProjectSystem>, TestToken {
      override val expectedInstance: TestProjectSystem = this@TestProjectSystem

      override fun computeApplicationProjectContext(projectSystem: AndroidProjectSystem, info: ApplicationProjectContextProvider.RunningApplicationIdentity): ApplicationProjectContext {
        return TestApplicationProjectContext(info.applicationId ?: error("applicationId must not be empty"))
      }
    }
    ApplicationManager.getApplication().extensionArea.getExtensionPoint(ApplicationProjectContextProvider.Companion.EP_NAME)
      .registerExtension(provider, project)
  }

  private val dependenciesByModule: HashMultimap<Module, Dependency> = HashMultimap.create()
  private val availablePreviewDependencies: List<GradleCoordinate>
  private val availableStableDependencies: List<GradleCoordinate>
  private val incompatibleDependencyPairs: HashMap<GradleCoordinate, GradleCoordinate>
  private val coordinateToFakeRegisterDependencyError: HashMap<GradleCoordinate, String>
  var namespace: String? = null
  var manifestOverrides = ManifestOverrides()
  var useAndroidX: Boolean = false
  var usesCompose: Boolean = false

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
  fun addDependency(artifactId: GoogleMavenArtifactId, module: Module, testVersion: TestVersion) {
    val coordinate = artifactId.getCoordinate(testVersion.toString())
    dependenciesByModule.put(module, Dependency(DependencyType.IMPLEMENTATION, coordinate))
  }

  /**
   * @return the set of dependencies added to the given module.
   */
  fun getAddedDependencies(module: Module): Set<Dependency> = dependenciesByModule.get(module)

  /**
   * Mark a pair of dependencies as incompatible so that [RegisteringModuleSystem.analyzeDependencyCompatibility]
   * will return them as incompatible dependencies.
   */
  fun addIncompatibleDependencyPair(dep1: GradleCoordinate, dep2: GradleCoordinate) {
    incompatibleDependencyPairs[dep1] = dep2
  }

  /**
   * Add a fake error condition for [coordinate] such that calling [RegisteringModuleSystem.registerDependency] on the
   * coordinate will throw a [DependencyManagementException] with error message set to [errorMessage].
   */
  fun addFakeErrorForRegisteringDependency(coordinate: GradleCoordinate, errorMessage: String) {
    coordinateToFakeRegisterDependencyError[coordinate] = errorMessage
  }

  data class TestRegisteredDependencyQueryId(val coordinate: GradleCoordinate): RegisteredDependencyQueryId {
    override fun toString(): String = coordinate.toString()
  }
  data class TestRegisteredDependencyId(val coordinate: GradleCoordinate): RegisteredDependencyId {
    override fun toString(): String = coordinate.toString()
  }
  override fun getModuleSystem(module: Module): TestModuleSystem {
    class TestAndroidModuleSystemImpl : TestModuleSystem {
      override val module = module

      override val moduleClassFileFinder: ClassFileFinder = object : ClassFileFinder {
        override fun findClassFile(fqcn: String): ClassContent? = null
      }

      override fun analyzeDependencyCompatibility(dependencies: List<TestRegisteredDependencyId>): ListenableFuture<RegisteredDependencyCompatibilityResult<TestRegisteredDependencyId>> {
        val found = mutableListOf<TestRegisteredDependencyId>()
        val missing = mutableListOf<TestRegisteredDependencyId>()
        var compatibilityWarningMessage = ""
        for (dependency in dependencies) {
          val wildcardCoordinate = dependency.coordinate.let { GradleCoordinate(it.groupId, it.artifactId, "+") }
          val lookup = availableStableDependencies.firstOrNull { it.matches(wildcardCoordinate) }
                       ?: availablePreviewDependencies.firstOrNull { it.matches(wildcardCoordinate) }
          if (lookup != null) {
            found.add(TestRegisteredDependencyId(lookup))
            incompatibleDependencyPairs[lookup]?.let { value ->
              if (value.let { dependencies.map { it.coordinate }.contains(it) } == true) {
                compatibilityWarningMessage += "$lookup is not compatible with ${value}\n"
              }
            }
          }
          else {
            missing.add(dependency)
            compatibilityWarningMessage += "Can't find $dependency\n"
          }
        }
        return Futures.immediateFuture<RegisteredDependencyCompatibilityResult<TestRegisteredDependencyId>>(
          RegisteredDependencyCompatibilityResult(found, missing, compatibilityWarningMessage)
        )
      }

      override fun getAndroidLibraryDependencies(scope: DependencyScopeType): Collection<ExternalAndroidLibrary> {
        return androidLibraryDependencies
      }

      override fun getResourceModuleDependencies() = emptyList<Module>()

      override fun getDirectResourceModuleDependents() = emptyList<Module>()

      override fun registerDependency(dependency: TestRegisteredDependencyId, type: DependencyType) {
        coordinateToFakeRegisterDependencyError[dependency.coordinate]?.let {
          throw DependencyManagementException(it, DependencyManagementException.ErrorCodes.INVALID_ARTIFACT)
        }
        dependenciesByModule.put(module, Dependency(type, dependency.coordinate))
      }

      override fun getRegisteredDependencyQueryId(id: WellKnownMavenArtifactId): TestRegisteredDependencyQueryId =
        TestRegisteredDependencyQueryId(id.getCoordinate("+"))

      private fun getRegisteredDependencyQueryId(coordinate: GradleCoordinate): TestRegisteredDependencyQueryId =
        TestRegisteredDependencyQueryId(coordinate)

      override fun getRegisteredDependencyId(id: WellKnownMavenArtifactId): TestRegisteredDependencyId =
        TestRegisteredDependencyId(id.getCoordinate("+"))

      private fun getRegisteredDependencyId(coordinate: GradleCoordinate): TestRegisteredDependencyId =
        TestRegisteredDependencyId(coordinate)

      override fun getRegisteredDependency(id: TestRegisteredDependencyQueryId): TestRegisteredDependencyId? =
        dependenciesByModule[module].firstOrNull { it.coordinate.matches(id.coordinate) }?.let {
          TestRegisteredDependencyId(it.coordinate)
        }

      override fun hasResolvedDependency(id: WellKnownMavenArtifactId, scope: DependencyScopeType): Boolean =
        id.getCoordinate("+").let { coordinate ->
          dependenciesByModule[module].map { it.coordinate }.firstOrNull { it.matches(coordinate) } != null
        }

      override fun getModuleTemplates(targetDirectory: VirtualFile?): List<NamedModuleTemplate> =
        listOfNotNull(
          NamedModuleTemplate(
            "main",
            AndroidModulePathsImpl(
              ModuleRootManager.getInstance(module).sourceRoots.first().parent.toIoFile(),
              null,
              ModuleRootManager.getInstance(module).sourceRoots.first().toIoFile(),
              null,
              null,
              null,
              emptyList(),
              emptyList()
            )
          ),
          // Fake an androidTest sourceSet
          module.getAndroidTestModule()?.let { testModule ->
            NamedModuleTemplate(
              "androidTest",
              AndroidModulePathsImpl(
                ModuleRootManager.getInstance(module).sourceRoots.first().parent.toIoFile(),
                null,
                null,
                null,
                ModuleRootManager.getInstance(testModule).sourceRoots.first().parent.toIoFile(),
                null,
                emptyList(),
                emptyList()
              )
            )
          }
        )

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

      override val useAndroidX: Boolean
        get() = this@TestProjectSystem.useAndroidX

      override val moduleDependencies: ModuleDependencies
        get() = StudioModuleDependencies(module)

      override val usesCompose: Boolean
        get() = this@TestProjectSystem.usesCompose
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
    override fun requestSyncProject(reason: SyncReason): ListenableFuture<SyncResult> {
      emulateSync(SyncResult.SUCCESS)
      return Futures.immediateFuture(SyncResult.SUCCESS)
    }

    override fun isSyncInProgress() = false

    override fun isSyncNeeded() = !lastSyncResult.isSuccessful

    override fun getLastSyncResult() = lastSyncResult
  }

  override fun getBuildManager(): ProjectSystemBuildManager = buildManager

  private val buildManager = TestProjectSystemBuildManager(ensureClockAdvancesWhileBuilding = false)


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
      override fun getLightRClassesAccessibleFromModule(module: Module) = emptyList<PsiClass>()
      override fun getLightRClassesContainingModuleResources(module: Module) = emptyList<PsiClass>()
      override fun findRClassPackage(qualifiedName: String): PsiPackage? = null
      override fun getAllLightRClasses() = emptyList<PsiClass>()
      override fun getLightRClassesDefinedByModule(module: Module) = emptyList<PsiClass>()
    }
  }

  override fun getClassJarProvider(): ClassJarProvider {
    return object: ClassJarProvider {
      override fun getModuleExternalLibraries(module: Module): List<File> = emptyList()
    }
  }

  override fun getSourceProvidersFactory(): SourceProvidersFactory = sourceProvidersFactoryStub

  override fun getAndroidFacetsWithPackageName(packageName: String): List<AndroidFacet> {
    return emptyList()
  }

  override fun isNamespaceOrParentPackage(packageName: String): Boolean {
    return false
  }

  override fun getKnownApplicationIds(): Set<String> {
    return emptySet()
  }

  override fun findModulesWithApplicationId(applicationId: String): Collection<Module> {
    return emptyList()
  }
}

sealed interface TestVersion {
  companion object {
    @JvmStatic
    fun create(parts: List<Int>): TestVersion = NumericTestVersion(parts)
    @JvmStatic
    fun create(vararg parts: Int): TestVersion = create(parts.toList())
    @JvmField
    val WILD: TestVersion = SpecialTestVersion.WILD
  }
}

data class NumericTestVersion(val parts: List<Int>): TestVersion {
  override fun toString() = parts.joinToString(".")
}

enum class SpecialTestVersion(val string: String): TestVersion {
  WILD("+")
  ;

  override fun toString() = string
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
    simulateBuild(BuildMode.COMPILE_OR_ASSEMBLE)
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
    // use a copy to avoid concurrent modification
    val listeners = listeners.toList()
    listeners.forEach {
      it.buildStarted(mode)
    }
    maybeEnsureClockAdvanced()
  }

  fun buildCompleted(status: ProjectSystemBuildManager.BuildStatus) {
    lastBuildResult = ProjectSystemBuildManager.BuildResult(lastBuildMode, status)
    maybeEnsureClockAdvanced()
    // use a copy to avoid concurrent modification
    val listeners = listeners.toList()
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

/**
 * An [ApplicationProjectContext] used with the [TestProjectSystem]
 */
data class TestApplicationProjectContext(override val applicationId: String) : ApplicationProjectContext

interface TestToken: ProjectSystemToken {
  override fun isApplicable(projectSystem: AndroidProjectSystem): Boolean = projectSystem == expectedInstance

  val expectedInstance: TestProjectSystem
}