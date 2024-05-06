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
import com.android.tools.idea.projectsystem.isLinkedAndroidModule
import com.android.tools.idea.res.ModuleRClass.SourceSet
import com.android.tools.idea.res.ResourceRepositoryRClass.Transitivity
import com.android.tools.idea.util.androidFacet
import com.android.tools.res.ResourceNamespacing
import com.android.utils.concurrency.getAndUnwrap
import com.android.utils.concurrency.retainAll
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.collect.Multimap
import com.google.common.collect.Multimaps
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.android.augment.AndroidLightField.FieldModifier
import org.jetbrains.android.facet.AndroidFacet
import java.io.IOException

private data class ResourceClasses(
  val namespaced: PsiClass?,
  val nonNamespaced: PsiClass?,
) {
  companion object {
    val Empty = ResourceClasses(null, null)
  }

  val all = sequenceOf(namespaced, nonNamespaced)
}

/**
 * A [LightResourceClassService] that provides R classes for local modules by finding manifests of
 * all Android modules in the project. This implementation of [LightResourceClassService] is
 * intended for use with the Gradle build system.
 */
class ProjectLightResourceClassService(private val project: Project) : LightResourceClassService {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) =
      project.getService(ProjectLightResourceClassService::class.java)!!
  }

  /** Cache of AAR package names. */
  private val aarPackageNamesCache: Cache<ExternalAndroidLibrary, String> =
    CacheBuilder.newBuilder().build()

  /** Cache of created classes for a given AAR. */
  private val aarClassesCache: Cache<ExternalAndroidLibrary, ResourceClasses> =
    CacheBuilder.newBuilder().build()

  /** Cache of created classes for a given AAR. */
  private val moduleClassesCache: Cache<AndroidFacet, ResourceClasses> =
    CacheBuilder.newBuilder().build()

  /**
   * [Multimap] of all [ExternalAndroidLibrary] dependencies in the project, indexed by their
   * package name (read from Manifest).
   */
  private var aarsByPackage: CachedValue<Multimap<String, ExternalAndroidLibrary>>

  init {
    val connection = project.messageBus.connect()

    // Sync can remove facets or change configuration of modules in a way that affects R classes,
    // e.g. make them non-transitive.
    connection.subscribe(
      PROJECT_SYSTEM_SYNC_TOPIC,
      object : ProjectSystemSyncManager.SyncResultListener {
        override fun syncEnded(result: ProjectSystemSyncManager.SyncResult) {
          moduleClassesCache.invalidateAll()
          invokeAndWaitIfNeeded { PsiManager.getInstance(project).dropPsiCaches() }
        }
      }
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
            // successfully compiled,
            // new IDs might have been generated. This ensures the IDs are invalidated.
            moduleClassesCache.invalidateAll()
            invokeAndWaitIfNeeded { PsiManager.getInstance(project).dropPsiCaches() }
          }
        }
      }
    )

    aarsByPackage =
      CachedValuesManager.getManager(project)
        .createCachedValue(
          {
            val libsWithResources = findAllLibrariesWithResources(project).values
            aarPackageNamesCache.retainAll(
              libsWithResources
            ) // remove old items that are not needed anymore
            CachedValueProvider.Result<Multimap<String, ExternalAndroidLibrary>>(
              Multimaps.index(libsWithResources) { getAarPackageName(it!!) },
              ProjectRootManager.getInstance(project)
            )
          },
          false
        )

    // Light classes for AARs store a reference to the Library in UserData. These Library instances
    // can become stale during sync, which
    // confuses Kotlin (consumer of the information in UserData). Invalidate the AAR R classes cache
    // when the library table changes.
    LibraryTablesRegistrar.getInstance()
      .getLibraryTable(project)
      .addListener(
        object : LibraryTable.Listener {
          override fun afterLibraryAdded(newLibrary: Library) = dropAarClassesCache()

          override fun afterLibraryRenamed(library: Library, oldName: String?) =
            dropAarClassesCache()

          override fun afterLibraryRemoved(library: Library) = dropAarClassesCache()

          private fun dropAarClassesCache() {
            if (aarClassesCache.size() != 0L) {aarClassesCache.invalidateAll()
            PsiManager.getInstance(project).dropPsiCaches()
          }
        }
      })
  }

  override fun getLightRClasses(qualifiedName: String, scope: GlobalSearchScope): List<PsiClass> {
    val packageName = qualifiedName.dropLast(2)
    return (getModuleRClasses(packageName) + getAarRClasses(packageName))
      .flatMap { classes -> classes.all }
      .filterNotNull()
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
      result.add(getAarRClasses(aarLibrary))
    }

    return result.mapNotNull {
      when (namespacing) {
        ResourceNamespacing.REQUIRED -> it.namespaced
        ResourceNamespacing.DISABLED -> it.nonNamespaced
      }
    }
  }

  override fun getLightRClassesDefinedByModule(module: Module): Collection<PsiClass> {
    val facet = module.androidFacet ?: return emptySet()
    val moduleRClasses = getModuleRClasses(facet)
    val relevant =
      if (ProjectNamespacingStatusService.getInstance(module.project).namespacesUsed) {
        setOf(moduleRClasses.nonNamespaced, moduleRClasses.namespaced)
      } else {
        setOf(moduleRClasses.nonNamespaced)
      }

    return relevant.filterNotNull()
  }

  override fun getLightRClassesContainingModuleResources(module: Module): Collection<PsiClass> {
    val facet = module.androidFacet ?: return emptySet()
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
      val transitivity =
        if (moduleSystem.isRClassTransitive) Transitivity.TRANSITIVE
        else Transitivity.NON_TRANSITIVE

      val isTestModule = module.isLinkedAndroidModule() && module.isAndroidTestModule()

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
    return aarsByPackage.value.get(packageName).asSequence().map { aarLibrary ->
      getAarRClasses(aarLibrary, packageName)
    }
  }

  private fun getAarRClasses(
    aarLibrary: ExternalAndroidLibrary,
    packageName: String = getAarPackageName(aarLibrary)
  ): ResourceClasses {
    val ideaLibrary = findIdeaLibrary(aarLibrary) ?: return ResourceClasses.Empty

    // Build the classes from what is currently on disk. They may be null if the necessary files are
    // not there, e.g. the res.apk file
    // is required to build the namespaced class.
    return aarClassesCache.getAndUnwrap(aarLibrary) {
      val psiManager = PsiManager.getInstance(project)

      ResourceClasses(
        namespaced =
          aarLibrary.resApkFile
            ?.toFile()
            ?.takeIf { it.exists() }
            ?.let { resApk ->
              SmallAarRClass(
                psiManager,
                ideaLibrary,
                packageName,
                AarResourceRepositoryCache.instance.getProtoRepository(aarLibrary),
                ResourceNamespace.fromPackageName(packageName),
                aarLibrary.address
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
                aarLibrary.address
              )
            }
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
      aarsByPackage.value.containsKey(packageName) ||
        findAndroidFacetsWithPackageName(packageName).isNotEmpty()
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
      findAllLibrariesWithResources(project)
        .values
        .asSequence()
        .sortedBy(ExternalAndroidLibrary::libraryName)
        .map { getAarRClasses(it) }
    val moduleClasses =
      ProjectFacetManager.getInstance(project)
        .getFacets(AndroidFacet.ID)
        .asSequence()
        .sortedBy(AndroidFacet::getName)
        .map { getModuleRClasses(it) }

    return (libraryClasses + moduleClasses).flatMap { it.all }.filterNotNull().toList()
  }

  private fun findAndroidFacetsWithPackageName(packageName: String): Collection<AndroidFacet> {
    // The facets are sorted, but the actual order doesn't matter; this is to ensure stability and
    // prevent bugs like b/313521550.
    val projectSystem = project.getProjectSystem()
    val facetsInferredFromPackageName =
      projectSystem
        .getAndroidFacetsWithPackageName(project, packageName)
        .sortedBy(AndroidFacet::getName)

    return if (packageName.endsWith(".test")) {
      val facetsInferredFromTestPackageName =
        packageName
          .substringBeforeLast('.')
          .let { projectSystem.getAndroidFacetsWithPackageName(project, it) }
          .sortedBy(AndroidFacet::getName)
      facetsInferredFromPackageName + facetsInferredFromTestPackageName
    } else {
      facetsInferredFromPackageName
    }
  }

  private fun getAarPackageName(aarLibrary: ExternalAndroidLibrary): String {
    val packageName = aarLibrary.packageName
    if (packageName != null) {
      return packageName
    }
    return aarPackageNamesCache.getAndUnwrap(aarLibrary) {
      try {
        aarLibrary.manifestFile?.let(
          AndroidManifestPackageNameUtils::getPackageNameFromManifestFile
        )
      } catch (e: IOException) {
        null
      } ?: ""
    }
  }
}
