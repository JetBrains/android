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

import com.android.builder.model.AaptOptions
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.AndroidManifestPackageNameUtils
import com.android.projectmodel.ExternalLibrary
import com.android.tools.idea.findAllLibrariesWithResources
import com.android.tools.idea.findDependenciesWithResources
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.LightResourceClassService
import com.android.tools.idea.res.ModuleRClass.SourceSet
import com.android.tools.idea.res.ModuleRClass.Transitivity
import com.android.tools.idea.util.androidFacet
import com.android.utils.concurrency.getAndUnwrap
import com.android.utils.concurrency.retainAll
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.collect.Multimap
import com.google.common.collect.Multimaps
import com.intellij.ProjectTopics
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
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
import org.jetbrains.android.dom.manifest.getPackageName
import org.jetbrains.android.dom.manifest.getTestPackageName
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidUtils
import java.io.IOException

private data class ResourceClasses(
  val namespaced: PsiClass?,
  val nonNamespaced: PsiClass?,
  val testNamespaced: PsiClass?,
  val testNonNamespaced: PsiClass?
) {
  val all = sequenceOf(namespaced, nonNamespaced, testNamespaced, testNonNamespaced)

  companion object {
    val Empty = ResourceClasses(null, null, null, null)
  }
}

/**
 * A [LightResourceClassService] that provides R classes for local modules by finding manifests of all Android modules in the project.
 */
class ProjectLightResourceClassService(
  private val project: Project,
  private val psiManager: PsiManager,
  private val projectFacetManager: ProjectFacetManager,
  private val aarResourceRepositoryCache: AarResourceRepositoryCache,
  private val androidLightPackagesCache: AndroidLightPackage.InstanceCache
) : LightResourceClassService {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) = ServiceManager.getService(project, ProjectLightResourceClassService::class.java)!!
  }

  /** Cache of AAR package names. */
  private val aarPackageNamesCache: Cache<ExternalLibrary, String> = CacheBuilder.newBuilder().build()

  /** Cache of created classes for a given AAR. */
  private val aarClassesCache: Cache<ExternalLibrary, ResourceClasses> = CacheBuilder.newBuilder().build()

  /** Cache of created classes for a given AAR. */
  private val moduleClassesCache: Cache<AndroidFacet, ResourceClasses> = CacheBuilder.newBuilder().weakKeys().build()

  /**
   * [Multimap] of all [ExternalLibrary] dependencies in the project, indexed by their package name (read from Manifest).
   */
  private var aarsByPackage: CachedValue<Multimap<String, ExternalLibrary>>

  init {
    aarsByPackage = CachedValuesManager.getManager(project).createCachedValue({
      CachedValueProvider.Result<Multimap<String, ExternalLibrary>>(
        Multimaps.index(findAllLibrariesWithResources(project).values) { getAarPackageName(it!!) },
        ProjectRootManager.getInstance(project)
      )
    }, false)

    // Currently findAllLibrariesWithResources creates new (equal) instances of ExternalLibrary every time it's called, so we have to keep
    // hard references to ExternalLibrary keys, otherwise the entries will be collected.
    project.messageBus.connect().subscribe(ProjectTopics.PROJECT_ROOTS, object: ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        val aars = aarsByPackage.value.values()
        aarPackageNamesCache.retainAll(aars)
      }
    })

    // Light classes for AARs store a reference to the Library in UserData. These Library instances can become stale during sync, which
    // confuses Kotlin (consumer of the information in UserData). Invalidate the AAR R classes cache when the library table changes.
    LibraryTablesRegistrar.getInstance().getLibraryTable(project).addListener(object : LibraryTable.Listener {
      override fun afterLibraryAdded(newLibrary: Library) = aarClassesCache.invalidateAll()
      override fun afterLibraryRenamed(library: Library) = aarClassesCache.invalidateAll()
      override fun afterLibraryRemoved(library: Library) = aarClassesCache.invalidateAll()
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
    val namespacing = ResourceRepositoryManager.getInstance(module)?.namespacing ?: return emptySet()
    val androidFacet = module.androidFacet ?: return emptySet()

    val result = mutableListOf<ResourceClasses>()

    result.add(getModuleRClasses(androidFacet))

    for (dependency in AndroidUtils.getAllAndroidDependencies(module, false)) {
      result.add(getModuleRClasses(dependency))
    }

    for (aarLibrary in findDependenciesWithResources(module).values) {
      result.add(getAarRClasses(aarLibrary))
    }

    return result.flatMap { resourceClasses ->
      when (namespacing) {
        AaptOptions.Namespacing.REQUIRED -> {
          if (includeTestClasses) {
            listOf(resourceClasses.namespaced, resourceClasses.testNamespaced)
          }
          else {
            listOf(resourceClasses.namespaced)
          }
        }
        AaptOptions.Namespacing.DISABLED -> {
          if (includeTestClasses) {
            listOf(resourceClasses.nonNamespaced, resourceClasses.testNonNamespaced)
          }
          else {
            listOf(resourceClasses.nonNamespaced)
          }
        }
      }
    }.filterNotNull()
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
      // TODO: get this from the model
      val modifier = if (facet.configuration.isLibraryProject) FieldModifier.NON_FINAL else FieldModifier.FINAL

      ResourceClasses(
        nonNamespaced = ModuleRClass(
          facet,
          psiManager,
          SourceSet.MAIN,
          if (StudioFlags.TRANSITIVE_R_CLASSES.get()) Transitivity.TRANSITIVE else Transitivity.NON_TRANSITIVE,
          modifier
        ),
        testNonNamespaced = ModuleRClass(
          facet,
          psiManager,
          SourceSet.TEST,
          if (StudioFlags.TRANSITIVE_R_CLASSES.get()) Transitivity.TRANSITIVE else Transitivity.NON_TRANSITIVE,
          modifier
        ),
        namespaced = ModuleRClass(facet, psiManager, SourceSet.MAIN, Transitivity.NON_TRANSITIVE, modifier),
        testNamespaced = ModuleRClass(facet, psiManager, SourceSet.TEST, Transitivity.NON_TRANSITIVE, modifier)
      )
    }
  }

  private fun getAarRClasses(packageName: String): Sequence<ResourceClasses> {
    return aarsByPackage.value.get(packageName).asSequence().map { aarLibrary -> getAarRClasses(aarLibrary, packageName) }
  }

  private fun getAarRClasses(aarLibrary: ExternalLibrary, packageName: String = getAarPackageName(aarLibrary)): ResourceClasses {
    val ideaLibrary = findIdeaLibrary(aarLibrary) ?: return ResourceClasses.Empty

    // Build the classes from what is currently on disk. They may be null if the necessary files are not there, e.g. the res.apk file
    // is required to build the namespaced class.
    return aarClassesCache.getAndUnwrap(aarLibrary) {
      ResourceClasses(
        namespaced = aarLibrary.resApkFile?.toFile()?.takeIf { it.exists() }?.let { resApk ->
          SmallAarRClass(
            psiManager,
            ideaLibrary,
            packageName,
            aarResourceRepositoryCache.getProtoRepository(aarLibrary),
            ResourceNamespace.fromPackageName(packageName)
          )
        },
        nonNamespaced = aarLibrary.symbolFile?.toFile()?.takeIf { it.exists() }?.let { symbolFile ->
          TransitiveAarRClass(psiManager, ideaLibrary, packageName, symbolFile)
        },
        testNamespaced = null,
        testNonNamespaced = null
      )
    }
  }

  private fun findIdeaLibrary(modelLibrary: ExternalLibrary): Library? {
    // TODO(b/118485835): Store this mapping at sync time and use it here.
    return LibraryTablesRegistrar.getInstance()
      .getLibraryTable(project)
      .libraries
      .firstOrNull { it.name?.endsWith(modelLibrary.address) == true }
  }

  override fun findRClassPackage(packageName: String): PsiPackage? {
    return if (aarsByPackage.value.containsKey(packageName) || findAndroidFacetsWithPackageName(packageName).isNotEmpty()) {
      androidLightPackagesCache.get(packageName)
    }
    else {
      null
    }
  }

  override fun getAllLightRClasses(): Collection<PsiClass> {
    val libraryClasses = findAllLibrariesWithResources(project).values.asSequence().map { getAarRClasses(it) }
    val moduleClasses = projectFacetManager.getFacets(AndroidFacet.ID).asSequence().map { getModuleRClasses(it) }

    return (libraryClasses + moduleClasses)
      .flatMap { it.all }
      .filterNotNull()
      .toList()
  }

  private fun findAndroidFacetsWithPackageName(packageName: String): List<AndroidFacet> {
    // TODO(b/110188226): cache this and figure out how to invalidate that cache.
    return projectFacetManager.getFacets(AndroidFacet.ID).filter {
      getPackageName(it) == packageName || getTestPackageName(it) == packageName
    }
  }

  private fun getAarPackageName(aarLibrary: ExternalLibrary): String {
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
