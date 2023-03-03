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
import com.android.tools.idea.model.Namespacing
import com.android.tools.idea.projectsystem.LightResourceClassService
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.res.ModuleRClass.SourceSet
import com.android.tools.idea.res.ResourceRepositoryRClass.Transitivity
import com.android.tools.idea.util.androidFacet
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
  val testNamespaced: PsiClass?,
  val testNonNamespaced: PsiClass?
) {
  companion object {
    val Empty = ResourceClasses(null, null, null, null)
  }

  val all = sequenceOf(namespaced, nonNamespaced, testNamespaced, testNonNamespaced)

  fun pickRelevant(namespacing: Namespacing, includeTestClasses: Boolean): Set<PsiClass?> {
    return when (namespacing) {
      Namespacing.REQUIRED -> {
        if (includeTestClasses) setOf(namespaced, testNamespaced) else setOf(namespaced)
      }
      Namespacing.DISABLED -> {
        if (includeTestClasses) setOf(nonNamespaced, testNonNamespaced) else setOf(nonNamespaced)
      }
    }
  }
}

/**
 * A [LightResourceClassService] that provides R classes for local modules by finding manifests of all Android modules in the project.
 */
class ProjectLightResourceClassService(private val project: Project) : LightResourceClassService {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.getService(ProjectLightResourceClassService::class.java)!!
  }

  /** Cache of AAR package names. */
  private val aarPackageNamesCache: Cache<ExternalAndroidLibrary, String> = CacheBuilder.newBuilder().build()

  /** Cache of created classes for a given AAR. */
  private val aarClassesCache: Cache<ExternalAndroidLibrary, ResourceClasses> = CacheBuilder.newBuilder().build()

  /** Cache of created classes for a given AAR. */
  private val moduleClassesCache: Cache<AndroidFacet, ResourceClasses> = CacheBuilder.newBuilder().build()

  /**
   * [Multimap] of all [ExternalAndroidLibrary] dependencies in the project, indexed by their package name (read from Manifest).
   */
  private var aarsByPackage: CachedValue<Multimap<String, ExternalAndroidLibrary>>

  init {
    val connection = project.messageBus.connect()

    // Sync can remove facets or change configuration of modules in a way that affects R classes, e.g. make them non-transitive.
    connection.subscribe(PROJECT_SYSTEM_SYNC_TOPIC, object : ProjectSystemSyncManager.SyncResultListener {
      override fun syncEnded(result: ProjectSystemSyncManager.SyncResult) {
        moduleClassesCache.invalidateAll()
        invokeAndWaitIfNeeded { PsiManager.getInstance(project).dropPsiCaches() }
      }
    })

    aarsByPackage = CachedValuesManager.getManager(project).createCachedValue({
      val libsWithResources = findAllLibrariesWithResources(project).values
      aarPackageNamesCache.retainAll(libsWithResources) // remove old items that are not needed anymore
      CachedValueProvider.Result<Multimap<String, ExternalAndroidLibrary>>(
        Multimaps.index(libsWithResources) { getAarPackageName(it!!) },
        ProjectRootManager.getInstance(project)
      )
    }, false)


    // Light classes for AARs store a reference to the Library in UserData. These Library instances can become stale during sync, which
    // confuses Kotlin (consumer of the information in UserData). Invalidate the AAR R classes cache when the library table changes.
    LibraryTablesRegistrar.getInstance().getLibraryTable(project).addListener(object : LibraryTable.Listener {
      override fun afterLibraryAdded(newLibrary: Library) = dropAarClassesCache()
      override fun afterLibraryRenamed(library: Library, oldName: String?) = dropAarClassesCache()
      override fun afterLibraryRemoved(library: Library) = dropAarClassesCache()

      private fun dropAarClassesCache() {
        aarClassesCache.invalidateAll()
        PsiManager.getInstance(project).dropPsiCaches()
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

  override fun getLightRClassesAccessibleFromModule(module: Module, includeTestClasses: Boolean): Collection<PsiClass> {
    val namespacing = StudioResourceRepositoryManager.getInstance(module)?.namespacing ?: return emptySet()
    val androidFacet = module.androidFacet ?: return emptySet()

    val result = mutableListOf<ResourceClasses>()

    result.add(getModuleRClasses(androidFacet))

    for (dependency in AndroidDependenciesCache.getAllAndroidDependencies(module, false)) {
      result.add(getModuleRClasses(dependency))
    }

    for (aarLibrary in findDependenciesWithResources(module).values) {
      result.add(getAarRClasses(aarLibrary))
    }

    return result.flatMap { it.pickRelevant(namespacing, includeTestClasses) }.filterNotNull()
  }

  override fun getLightRClassesDefinedByModule(module: Module, includeTestClasses: Boolean): Collection<PsiClass> {
    val facet = module.androidFacet ?: return emptySet()
    val moduleRClasses = getModuleRClasses(facet)
    val relevant = if (ProjectNamespacingStatusService.getInstance(module.project).namespacesUsed) {
      moduleRClasses.pickRelevant(Namespacing.DISABLED, includeTestClasses) +
      moduleRClasses.pickRelevant(Namespacing.REQUIRED, includeTestClasses)
    } else {
      moduleRClasses.pickRelevant(Namespacing.DISABLED, includeTestClasses)
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
    modules.asSequence()
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

      val moduleSystem = facet.module.getModuleSystem()
      val transitivity = if (moduleSystem.isRClassTransitive) Transitivity.TRANSITIVE else Transitivity.NON_TRANSITIVE

      val fieldModifier =
        if (isLibraryProject || !moduleSystem.applicationRClassConstantIds) FieldModifier.NON_FINAL else FieldModifier.FINAL

      val testFieldModifier =
        if (isLibraryProject || !moduleSystem.testRClassConstantIds) FieldModifier.NON_FINAL else FieldModifier.FINAL

      ResourceClasses(
        nonNamespaced = ModuleRClass(facet, psiManager, SourceSet.MAIN, transitivity, fieldModifier),
        testNonNamespaced = ModuleRClass(facet, psiManager, SourceSet.TEST, transitivity, testFieldModifier),
        namespaced = ModuleRClass(facet, psiManager, SourceSet.MAIN, Transitivity.NON_TRANSITIVE, fieldModifier),
        testNamespaced = ModuleRClass(facet, psiManager, SourceSet.TEST, Transitivity.NON_TRANSITIVE, testFieldModifier)
      )
    }
  }

  private fun getAarRClasses(packageName: String): Sequence<ResourceClasses> {
    return aarsByPackage.value.get(packageName).asSequence().map { aarLibrary -> getAarRClasses(aarLibrary, packageName) }
  }

  private fun getAarRClasses(aarLibrary: ExternalAndroidLibrary, packageName: String = getAarPackageName(aarLibrary)): ResourceClasses {
    val ideaLibrary = findIdeaLibrary(aarLibrary) ?: return ResourceClasses.Empty

    // Build the classes from what is currently on disk. They may be null if the necessary files are not there, e.g. the res.apk file
    // is required to build the namespaced class.
    return aarClassesCache.getAndUnwrap(aarLibrary) {
      val psiManager = PsiManager.getInstance(project)

      ResourceClasses(
        namespaced = aarLibrary.resApkFile?.toFile()?.takeIf { it.exists() }?.let { resApk ->
          SmallAarRClass(
            psiManager,
            ideaLibrary,
            packageName,
            AarResourceRepositoryCache.instance.getProtoRepository(aarLibrary),
            ResourceNamespace.fromPackageName(packageName),
            aarLibrary.address
          )
        },
        nonNamespaced = aarLibrary.symbolFile?.toFile()?.takeIf { it.exists() }?.let { symbolFile ->
          TransitiveAarRClass(psiManager, ideaLibrary, packageName, symbolFile, aarLibrary.address)
        },
        testNamespaced = null,
        testNonNamespaced = null
      )
    }
  }

  private fun findIdeaLibrary(modelLibrary: ExternalAndroidLibrary): Library? {
    // TODO(b/118485835): Store this mapping at sync time and use it here.
    return LibraryTablesRegistrar.getInstance()
      .getLibraryTable(project)
      .libraries
      .firstOrNull { it.name?.endsWith(modelLibrary.address) == true }
  }

  override fun findRClassPackage(packageName: String): PsiPackage? {
    return if (aarsByPackage.value.containsKey(packageName) || findAndroidFacetsWithPackageName(packageName).isNotEmpty()) {
      AndroidLightPackage.withName(packageName, project)
    }
    else {
      null
    }
  }

  override fun getAllLightRClasses(): Collection<PsiClass> {
    val libraryClasses = findAllLibrariesWithResources(project).values.asSequence().map { getAarRClasses(it) }
    val moduleClasses = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID).asSequence().map { getModuleRClasses(it) }

    return (libraryClasses + moduleClasses)
      .flatMap { it.all }
      .filterNotNull()
      .toList()
  }

  private fun findAndroidFacetsWithPackageName(packageName: String): Collection<AndroidFacet> {
    val projectSystem = project.getProjectSystem()
    val facetsInferredFromPackageName = projectSystem.getAndroidFacetsWithPackageName(project, packageName)

    return if (packageName.endsWith(".test")) {
      val facetsInferredFromTestPackageName = packageName.substringBeforeLast('.').let {
        projectSystem.getAndroidFacetsWithPackageName(project, it)
      }
      facetsInferredFromPackageName + facetsInferredFromTestPackageName
    }
    else {
      facetsInferredFromPackageName
    }
  }

  private fun getAarPackageName(aarLibrary: ExternalAndroidLibrary): String {
    val packageName = aarLibrary.packageName
    if (packageName != null) {
      return packageName
    }
    return aarPackageNamesCache.getAndUnwrap(aarLibrary) {
      val fromManifest = try {
        aarLibrary.manifestFile?.let(AndroidManifestPackageNameUtils::getPackageNameFromManifestFile) ?: ""
      }
      catch (e: IOException) {
        null
      }
      fromManifest ?: ""
    }
  }
}
