/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.AndroidManifestPackageNameUtils
import com.android.projectmodel.ExternalAndroidLibrary
import com.android.tools.idea.findAllLibrariesWithResources
import com.android.tools.idea.findDependenciesWithResources
import com.android.tools.idea.projectsystem.LightResourceClassService
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_BUILD_TOPIC
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.isAndroidTestModule
import com.android.tools.idea.res.ModuleRClass.SourceSet
import com.android.tools.idea.res.ResourceRepositoryRClass.Transitivity
import com.android.tools.idea.util.androidFacet
import com.android.tools.res.ResourceNamespacing
import com.android.utils.concurrency.getAndUnwrap
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.collect.Multimap
import com.google.common.collect.Multimaps
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import java.io.IOException
import org.jetbrains.android.augment.AndroidLightField.FieldModifier
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.caches.project.CachedValue

private data class ResourceClasses(val namespaced: PsiClass?, val nonNamespaced: PsiClass?) {
  companion object {
    val Empty = ResourceClasses(null, null)
  }

  val all: List<PsiClass>
    get() = listOfNotNull(namespaced, nonNamespaced)
}

/**
 * A [LightResourceClassService] that provides R classes for local modules by finding manifests of
 * all Android modules in the project. This implementation of [LightResourceClassService] is
 * intended for use with the Gradle build system.
 */
@Service(Service.Level.PROJECT)
class ProjectLightResourceClassService(private val project: Project) : LightResourceClassService {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) =
      project.getService(ProjectLightResourceClassService::class.java)!!
  }

  /** Cache of created classes for a given AAR. */
  private val aarClassesCache: Cache<ExternalAndroidLibrary, ResourceClasses> =
    CacheBuilder.newBuilder().build()

  /** Cache of created classes for a given AAR. */
  private val moduleClassesCache: Cache<AndroidFacet, ResourceClasses> =
    CacheBuilder.newBuilder().build()

  /** Cache for information that should be updated whenever the set of AAR dependencies changes. */
  private var aarInfo: CachedValue<AARInfo> =
    CachedValue(project) {
      CachedValueProvider.Result(AARInfo(project), ProjectRootManager.getInstance(project))
    }

  init {
    val connection = project.messageBus.connect()

    // Sync can remove facets or change configuration of modules in a way that affects R classes,
    // e.g. make them non-transitive.
    connection.subscribe(
      PROJECT_SYSTEM_SYNC_TOPIC,
      ProjectSystemSyncManager.SyncResultListener {
        moduleClassesCache.invalidateAll()
        runInEdt {
          if (!project.isDisposed) {
            PsiManager.getInstance(project).dropPsiCaches()
          }
        }
      },
    )

    connection.subscribe(
      PROJECT_SYSTEM_BUILD_TOPIC,
      object : ProjectSystemBuildManager.BuildListener {
        override fun buildCompleted(result: ProjectSystemBuildManager.BuildResult) {
          if (
            result.mode != ProjectSystemBuildManager.BuildMode.CLEAN &&
              result.status == ProjectSystemBuildManager.BuildStatus.SUCCESS
          ) {
            // The light R classes might use the actual IDs when available. If the project is
            // successfully compiled, new IDs might have been generated. This ensures the IDs
            // are invalidated.
            moduleClassesCache.invalidateAll()
            runInEdt { PsiManager.getInstance(project).dropPsiCaches() }
          }
        }
      },
    )

    // Light classes for AARs store a reference to the Library in UserData. These Library instances
    // can become stale during sync, which confuses Kotlin (consumer of the information in
    // UserData). Invalidate the AAR R classes cache when the library table changes.
    connection.subscribe(
      WorkspaceModelTopics.CHANGED,
      object : WorkspaceModelChangeListener {
        var invalidationScheduled = false

        override fun beforeChanged(event: VersionedStorageChange) {
          // There are already plenty of cache invalidation handlers listening on workspace model
          // change events even in base IntelliJ, and they have ordering interdependencies, so
          // don't try to dropPsiCaches until the state has settled. Otherwise, invalidating
          // some cache could make it access another cache that has already been invalidated
          // by the event that we're handling right now.
          if (invalidationScheduled) return
          invalidationScheduled = true // should already be on the EDT, no atomics required
          ApplicationManager.getApplication().invokeLater {
            invalidationScheduled = false
            if (project.isDisposed || aarClassesCache.size() == 0L) return@invokeLater
            // TODO? can actually extract affected libraries from the event for more granularity.
            //   It's easier to do by listening on `LibraryInfoListener.TOPIC` instead, which is
            //   emitted when LibraryInfoCache is partially invalidated, and contains a list of
            //   invalidated `LibraryInfo`s - just have to reverse `findIdeaLibrary` on them.
            if (aarClassesCache.size() != 0L) aarClassesCache.invalidateAll()
            PsiManager.getInstance(project).dropPsiCaches()
          }
        }
      },
    )
  }

  override fun getLightRClasses(qualifiedName: String, scope: GlobalSearchScope): List<PsiClass> {
    val packageName = qualifiedName.dropLast(2)
    return (getModuleRClasses(packageName) + getAarRClasses(packageName))
      .flatMap { classes -> classes.all }
      .filter { it.qualifiedName == qualifiedName && PsiSearchScopeUtil.isInScope(scope, it) }
      .toList()
  }

  override fun getLightRClassesAccessibleFromModule(module: Module): Collection<PsiClass> {
    val namespacing =
      StudioResourceRepositoryManager.getInstance(module)?.namespacing ?: return emptySet()
    val androidFacet = module.androidFacet ?: return emptySet()

    val result = mutableListOf<ResourceClasses>()

    result.add(getModuleRClasses(androidFacet))

    // Dependencies and libraries are sorted, but the actual order doesn't matter; this is to ensure
    // stability and prevent bugs like b/313521550.
    for (dependency in
      AndroidDependenciesCache.getAllAndroidDependencies(module, false)
        .sortedBy(AndroidFacet::getName)) {
      result.add(getModuleRClasses(dependency))
    }

    for (aarLibrary in
      findDependenciesWithResources(module).values.sortedBy(ExternalAndroidLibrary::libraryName)) {
      val packageName = aarLibrary.packageName ?: aarInfo.value.packageNames[aarLibrary] ?: continue
      result.add(getAarRClasses(aarLibrary, packageName))
    }

    return result.mapNotNull {
      when (namespacing) {
        ResourceNamespacing.REQUIRED -> it.namespaced
        ResourceNamespacing.DISABLED -> it.nonNamespaced
      }
    }
  }

  override fun getLightRClassesDefinedByModule(module: Module): Collection<PsiClass> {
    val facet = module.androidFacet ?: return emptyList()
    val moduleRClasses = getModuleRClasses(facet)
    return if (ProjectNamespacingStatusService.getInstance(module.project).namespacesUsed) {
      listOfNotNull(moduleRClasses.nonNamespaced, moduleRClasses.namespaced)
    } else {
      listOfNotNull(moduleRClasses.nonNamespaced)
    }
  }

  override fun getLightRClassesContainingModuleResources(module: Module): Collection<PsiClass> {
    val facet = module.androidFacet ?: return emptyList()
    val result = mutableSetOf<PsiClass>()

    if (ProjectNamespacingStatusService.getInstance(module.project).namespacesUsed) {
      // The namespaced class of the module itself:
      getModuleRClasses(facet).namespaced?.let(result::add)
    }

    // Non-namespaced classes of this module and all that depend on it:
    val modules = HashSet<Module>().also { ModuleUtilCore.collectModulesDependsOn(module, it) }
    modules
      .asSequence()
      .mapNotNull { it.androidFacet }
      .mapNotNull { getModuleRClasses(it).nonNamespaced }
      .forEach { result += it }

    return result
  }

  private fun getModuleRClasses(packageName: String): Sequence<ResourceClasses> {
    return findAndroidFacetsWithPackageName(packageName).asSequence().map(::getModuleRClasses)
  }

  private fun getModuleRClasses(facet: AndroidFacet): ResourceClasses {
    return moduleClassesCache.getAndUnwrap(facet) {
      val psiManager = PsiManager.getInstance(project)
      // TODO: get this from the model
      val isLibraryProject = facet.configuration.isLibraryProject

      val module = facet.module
      val moduleSystem = module.getModuleSystem()
      if (!moduleSystem.supportsAndroidResources) return@getAndUnwrap ResourceClasses.Empty

      val transitivity =
        if (moduleSystem.isRClassTransitive) Transitivity.TRANSITIVE
        else Transitivity.NON_TRANSITIVE

      val isTestModule = module.isAndroidTestModule()

      val useConstantIds =
        if (isTestModule) moduleSystem.testRClassConstantIds
        else moduleSystem.applicationRClassConstantIds

      val fieldModifier =
        if (isLibraryProject || !useConstantIds) FieldModifier.NON_FINAL else FieldModifier.FINAL

      val sourceSet = if (isTestModule) SourceSet.TEST else SourceSet.MAIN

      ResourceClasses(
        nonNamespaced = ModuleRClass(facet, psiManager, sourceSet, transitivity, fieldModifier),
        namespaced =
          ModuleRClass(facet, psiManager, sourceSet, Transitivity.NON_TRANSITIVE, fieldModifier),
      )
    }
  }

  private fun getAarRClasses(packageName: String): Sequence<ResourceClasses> {
    return aarInfo.value.librariesByPackage[packageName].asSequence().map { aarLibrary ->
      getAarRClasses(aarLibrary, packageName)
    }
  }

  private fun getAarRClasses(
    aarLibrary: ExternalAndroidLibrary,
    packageName: String,
  ): ResourceClasses {
    val ideaLibrary = findIdeaLibrary(aarLibrary) ?: return ResourceClasses.Empty

    // Build the classes from what is currently on disk. They may be null if the necessary files are
    // not there, e.g. the res.apk file is required to build the namespaced class.
    return aarClassesCache.getAndUnwrap(aarLibrary) {
      val psiManager = PsiManager.getInstance(project)

      ResourceClasses(
        namespaced =
          aarLibrary.resApkFile
            ?.toFile()
            ?.takeIf { it.exists() }
            ?.let { _ ->
              SmallAarRClass(
                psiManager,
                ideaLibrary,
                packageName,
                AarResourceRepositoryCache.instance.getProtoRepository(aarLibrary),
                ResourceNamespace.fromPackageName(packageName),
                aarLibrary.address,
              )
            },
        nonNamespaced =
          aarLibrary.symbolFile
            ?.toFile()
            ?.takeIf { it.exists() }
            ?.let { symbolFile ->
              TransitiveAarRClass(
                psiManager,
                ideaLibrary,
                packageName,
                symbolFile,
                aarLibrary.address,
              )
            },
      )
    }
  }

  private fun findIdeaLibrary(modelLibrary: ExternalAndroidLibrary): Library? {
    // TODO(b/118485835): Store this mapping at sync time and use it here.
    return LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries.firstOrNull {
      it.name?.endsWith(modelLibrary.libraryName()) == true
    }
  }

  override fun findRClassPackage(packageName: String): PsiPackage? {
    return if (
      packageName in aarInfo.value.packagesAndParents ||
        project.getProjectSystem().isNamespaceOrParentPackage(packageName)
    ) {
      AndroidLightPackage.withName(packageName, project)
    } else {
      null
    }
  }

  override fun getAllLightRClasses(): Collection<PsiClass> {
    // The classes are sorted, but the actual order doesn't matter; this is to ensure stability and
    // prevent bugs like b/313521550.
    val libraryClasses =
      aarInfo.value.librariesByPackage
        .entries()
        .sortedBy { (_, library) -> library.libraryName() }
        .asSequence()
        .map { (packageName, library) -> getAarRClasses(library, packageName) }
    val moduleClasses =
      ProjectFacetManager.getInstance(project)
        .getFacets(AndroidFacet.ID)
        .sortedBy(AndroidFacet::getName)
        .asSequence()
        .map { getModuleRClasses(it) }

    return (libraryClasses + moduleClasses).flatMap { it.all }.toList()
  }

  private fun findAndroidFacetsWithPackageName(packageName: String): Collection<AndroidFacet> {
    val facetsInferredFromPackageName = findAndroidFacetsWithExactPackageName(packageName)
    return if (packageName.endsWith(".test")) {
      facetsInferredFromPackageName +
        findAndroidFacetsWithExactPackageName(packageName.substringBeforeLast('.'))
    } else {
      facetsInferredFromPackageName
    }
  }

  private fun findAndroidFacetsWithExactPackageName(packageName: String): Collection<AndroidFacet> {
    // The facets are sorted, but the actual order doesn't matter; this is to ensure stability and
    // prevent bugs like b/313521550.
    return project
      .getProjectSystem()
      .getAndroidFacetsWithPackageName(project, packageName)
      .sortedBy(AndroidFacet::getName)
  }

  class AARInfo(project: Project) {
    /** All AAR dependencies in a project, indexed by their package name. */
    val librariesByPackage: Multimap<String, ExternalAndroidLibrary>

    /** In-memory cache of AAR package names that have been read from manifest files. */
    val packageNames: Map<ExternalAndroidLibrary, String>

    /** Names of all packages that have resource classes in AARs, directly or in subpackages. */
    val packagesAndParents: Set<String>

    init {
      val libsWithResources = findAllLibrariesWithResources(project).values
      val packageNames = mutableMapOf<ExternalAndroidLibrary, String>()
      librariesByPackage =
        Multimaps.index(libsWithResources) { aarLibrary ->
          aarLibrary.packageName
            ?: aarLibrary.readPackageNameFromManifest().also { packageName ->
              packageNames[aarLibrary] = packageName
            }
        }

      this.packageNames = packageNames
      packagesAndParents = buildSet {
        for (packageName in librariesByPackage.keys()) {
          var prefix = packageName
          while (prefix.isNotEmpty() && add(prefix)) {
            prefix = prefix.substringBeforeLast('.', missingDelimiterValue = "")
          }
        }
      }
    }

    private fun ExternalAndroidLibrary.readPackageNameFromManifest(): String {
      return try {
        manifestFile?.let(AndroidManifestPackageNameUtils::getPackageNameFromManifestFile)
      } catch (e: IOException) {
        null
      } ?: ""
    }
  }
}
