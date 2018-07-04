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
import com.android.projectmodel.AarLibrary
import com.android.tools.idea.findAllAarsLibraries
import com.android.tools.idea.res.aar.AarResourceRepositoryCache
import com.android.tools.idea.util.toLibraryRootVirtualFile
import com.android.utils.concurrency.CacheUtils
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.collect.Multimap
import com.google.common.collect.Multimaps
import com.intellij.ProjectTopics
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.android.dom.manifest.AndroidManifestUtils
import org.jetbrains.android.facet.AndroidFacet
import java.io.File
import java.io.IOException
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.annotation.concurrent.GuardedBy
import kotlin.concurrent.read
import kotlin.concurrent.write

private data class AarClasses(val namespaced: PsiClass?, val nonNamespaced: PsiClass?)

/**
 * A [LightResourceClassService] that provides R classes for local modules by finding manifests of all Android modules in the project.
 */
class ProjectLightResourceClassService(
  private val project: Project,
  private val psiManager: PsiManager,
  private val vfsManager: VirtualFileManager,
  private val moduleManager: ModuleManager,
  private val aarResourceRepositoryCache: AarResourceRepositoryCache,
  private val androidLightPackagesCache: AndroidLightPackage.InstanceCache
) : LightResourceClassService {

  companion object {
    private val MODULE_R_CLASS = Key<PsiClass>(ProjectLightResourceClassService::class.qualifiedName!! + ".MODULE_R_CLASS")

    @JvmStatic
    fun getInstance(project: Project) = ServiceManager.getService(project, ProjectLightResourceClassService::class.java)!!
  }

  /**
   * Cache of created classes for a given `classes.jar` file.
   */
  private val aarClassesCache: Cache<File, AarClasses> = CacheBuilder.newBuilder().softValues().build()

  private val aarLocationsLock = ReentrantReadWriteLock()

  /**
   * [Multimap] of all [AarLibrary] dependencies in the project, indexed by their package name (read from Manifest).
   */
  @GuardedBy("aarLocationsLock")
  private var aarLocationsCache: Multimap<String, AarLibrary>? = null

  init {
    project.messageBus.connect(project).subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent?) {
        clearCaches()
      }
    })
  }

  override fun getLightRClasses(qualifiedName: String, scope: GlobalSearchScope): List<PsiClass> {
    val packageName = qualifiedName.dropLast(2)
    return (getModuleRClasses(packageName, scope, qualifiedName) + getAarRClasses(packageName, scope)).toList()
  }

  private fun getModuleRClasses(
    packageName: String,
    scope: GlobalSearchScope,
    qualifiedName: String
  ): Sequence<PsiClass> {
    return findAllAndroidFacets().asSequence().mapNotNull { facet ->
      if (AndroidManifestUtils.getPackageName(facet) == packageName && scope.isSearchInModuleContent(facet.module)) {
        val cached = facet.getUserData(MODULE_R_CLASS)
        if (cached?.qualifiedName == qualifiedName) {
          cached
        }
        else {
          facet.putUserDataIfAbsent(MODULE_R_CLASS, ModulePackageRClass(psiManager, packageName, facet.module))
        }
      }
      else {
        null
      }
    }
  }

  private fun getAarRClasses(packageName: String, scope: GlobalSearchScope): Sequence<PsiClass> {
    return getAllAars().get(packageName).asSequence().flatMap { aarLibrary ->
      // It's important for classesJar to use the jar:// filesystem, because that's what is stored in modules and libraries and GlobalScope
      // is not smart enough to recognize equivalent jar:// and file:// roots.
      val classesJar = aarLibrary.classesJar.toFile()?.takeIf { it.exists() } ?: return@flatMap emptySequence<PsiClass>()

      if (classesJar.toLibraryRootVirtualFile()?.let { scope.contains(it) } != true) {
        // The AAR is not in scope.
        emptySequence<PsiClass>()
      }
      else {
        // Build the classes from what is currently on disk. They may be null if the necessary files are not there, e.g. the res.apk file
        // is required to build the namespaced class.
        val (namespaced, nonNamespaced) = CacheUtils.getAndUnwrap(aarClassesCache, classesJar) {
          AarClasses(
            namespaced = aarLibrary.resApkFile?.toFile()?.takeIf { it.exists() }?.let { resApk ->
              NamespacedAarPackageRClass(
                psiManager,
                packageName,
                aarResourceRepositoryCache.getProtoRepository(resApk, aarLibrary.address),
                ResourceNamespace.fromPackageName(packageName)
              )
            },
            nonNamespaced = aarLibrary.symbolFile.toFile()?.takeIf { it.exists() }?.let { symbolFile ->
              NonNamespacedAarPackageRClass(psiManager, packageName, symbolFile)
            }
          )
        }

        val namespacing = (scope as? ModuleWithDependenciesScope)
          ?.module
          ?.let { ResourceRepositoryManager.getOrCreateInstance(it) }
          ?.namespacing

        when (namespacing) {
          AaptOptions.Namespacing.REQUIRED -> sequenceOf(namespaced).filterNotNull()
          AaptOptions.Namespacing.DISABLED -> sequenceOf(nonNamespaced).filterNotNull()
          null -> {
            // Could not determine namespacing, maybe it's the global scope. Return both classes.
            sequenceOf(namespaced, nonNamespaced).filterNotNull()
          }
        }
      }
    }
  }

  override fun findRClassPackage(qualifiedName: String): PsiPackage? {
    return if (getAllAars().containsKey(qualifiedName) ||
               findAllAndroidFacets().any { AndroidManifestUtils.getPackageName(it) == qualifiedName }) {
      androidLightPackagesCache.get(qualifiedName)
    }
    else {
      null
    }
  }

  private fun findAllAndroidFacets(): List<AndroidFacet> {
    // TODO(b/77801019): cache this and figure out how to invalidate that cache.
    return moduleManager.modules.mapNotNull(AndroidFacet::getInstance)
  }

  private fun getAllAars(): Multimap<String, AarLibrary> {
    return aarLocationsLock.read {
      aarLocationsCache ?: aarLocationsLock.write {
        /** Check aarLocationsCache again, see [kotlin.concurrent.write]. */
        aarLocationsCache ?: run {
          Multimaps.index(findAllAarsLibraries(project).values) { aarLibrary ->
            val fromManifest = try {
              aarLibrary
                ?.manifestFile
                ?.toFile()
                ?.let(AndroidManifestUtils::getPackageNameFromManifestFile)
            }
            catch (e: IOException) {
              null
            }
            fromManifest ?: ""
          }.also {
            aarLocationsCache = it
          }
        }
      }
    }
  }

  private fun clearCaches() {
    aarLocationsLock.write {
      aarLocationsCache = null
    }
    aarClassesCache.invalidateAll()
  }
}
