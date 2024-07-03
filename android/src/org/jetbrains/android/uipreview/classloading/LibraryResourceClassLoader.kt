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
package org.jetbrains.android.uipreview.classloading

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.AndroidManifestPackageNameUtils
import com.android.ide.common.resources.ResourceRepository
import com.android.projectmodel.ExternalAndroidLibrary
import com.android.tools.idea.projectsystem.DependencyScopeType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.ResourceClassRegistry
import com.android.tools.idea.res.StudioResourceIdManager
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.rendering.classloading.loaders.DelegatingClassLoader
import com.android.tools.res.ResourceNamespacing
import com.android.tools.res.ids.ResourceIdManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import org.jetbrains.android.facet.AndroidFacet
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.regex.Pattern

/**
 * Finds the `packageName` for a given external library.
 */
private fun ExternalAndroidLibrary.getResolvedPackageName(): String? {
  if (packageName != null) {
    return packageName
  }
  return manifestFile?.let {
    return try {
      AndroidManifestPackageNameUtils.getPackageNameFromManifestFile(it)
    }
    catch (ignore: IOException) {
      null
    }
  }
}

/**
 * Register this [ExternalAndroidLibrary] with the [ResourceClassRegistry].
 */
private fun ResourceClassRegistry.registerLibraryResources(
  externalLib: ExternalAndroidLibrary,
  idManager: ResourceIdManager,
  repositoryManager: StudioResourceRepositoryManager) {

  // Choose which resources should be in the generated R class. This is described in the JavaDoc of ResourceClassGenerator.
  val (rClassContents: ResourceRepository, resourcesNamespace: ResourceNamespace, packageName: String?) =
  if (repositoryManager.namespacing === ResourceNamespacing.DISABLED) {
    val resolvedPackageName = externalLib.getResolvedPackageName() ?: return
    Triple(repositoryManager.appResources, ResourceNamespace.RES_AUTO, resolvedPackageName)
  }
  else {
    val aarResources = repositoryManager.findLibraryResources(externalLib) ?: return
    Triple(aarResources, aarResources.namespace, aarResources.packageName)
  }
  this.addLibrary(rClassContents, idManager, packageName, resourcesNamespace)
}

/**
 * Register all the [Module] resources, including libraries and dependencies with the [ResourceClassRegistry].
 */
private fun registerResources(module: Module) {
  val androidFacet: AndroidFacet = AndroidFacet.getInstance(module) ?: return
  val repositoryManager = StudioResourceRepositoryManager.getInstance(androidFacet)
  val idManager = StudioResourceIdManager.get(module)
  val classRegistry = ResourceClassRegistry.get(module.project)

  // If final ids are used, we will read the real class from disk later (in loadAndParseRClass), using this class loader. So we
  // can't treat it specially here, or we will read the wrong bytecode later.
  if (!idManager.finalIdsUsed) {
    val resourcePackageNames = runReadAction {
      androidFacet.getModuleSystem().moduleDependencies.getResourcePackageNames(false)
    }
    for (resourcePackageName in resourcePackageNames) {
      classRegistry.addLibrary(repositoryManager.appResources,
                               idManager,
                               resourcePackageName,
                               repositoryManager.namespace)
    }
  }
  module.getModuleSystem().getAndroidLibraryDependencies(DependencyScopeType.MAIN)
    .filter { it.hasResources }
    .forEach { classRegistry.registerLibraryResources(it, idManager, repositoryManager) }
}

// matches foo.bar.R or foo.bar.R$baz
private val RESOURCE_CLASS_NAME = Pattern.compile(".+\\.R(\\$[^.]+)?$")
private fun isResourceClassName(className: String): Boolean = RESOURCE_CLASS_NAME.matcher(className).matches()

/**
 * [ClassLoader] responsible for loading the `R` class from libraries and dependencies of the given module.
 */
class LibraryResourceClassLoader(
  parent: ClassLoader?,
  module: Module?,
  private val childLoader: DelegatingClassLoader.Loader
) : ClassLoader(parent) {
  private val moduleRef = WeakReference(module)

  init {
    getModule()?.let { registerResources(it) }
  }

  /**
   * Returns the [Module] to be used for the library class loading if it still exists and is not disposed.
   */
  private fun getModule(): Module? {
    val module = moduleRef.get() ?: return null
    if (module.isDisposed) return null
    return module
  }

  private fun findResourceClass(name: String): Class<*> {
    val module = getModule() ?: throw ClassNotFoundException(name)
    if (!isResourceClassName(name)) {
      throw ClassNotFoundException(name)
    }

    if (StudioResourceIdManager.get(module).finalIdsUsed) {
      // If final IDs are used, we check to see if the child loader will load the class.  If so, throw a ClassNotFoundException
      // here and let the R classes be loaded by the child class loader.
      //
      // If compiled classes are not available, there are two possible scenarios:
      //     1) We are looking for a resource class available in the ResourceClassRegistry
      //     2) We are looking for a resource class that's not available in the ResourceClassRegistry
      //
      // In the first scenario, we'll load the R class using this class loader. This covers the case where users are opening a project
      // before compiling and want to view an XML resource file, which should work.  In the second, the resource class is not available
      // anywhere, so the class loader will (correctly) fail.
      if (childLoader.loadClass(name) != null) {
        throw ClassNotFoundException(name)
      }
    }

    val facet: AndroidFacet = AndroidFacet.getInstance(module) ?: throw ClassNotFoundException(name)
    val repositoryManager = StudioResourceRepositoryManager.getInstance(facet)
    val data = ResourceClassRegistry.get(module.project).findClassDefinition(name, repositoryManager) ?: throw ClassNotFoundException(name)
    Logger.getInstance(LibraryResourceClassLoader::class.java).debug("  Defining class from AAR registry")
    return defineClass(name, data, 0, data.size)
  }

  override fun findClass(name: String): Class<*> =
    try {
      super.findClass(name)
    }
    catch (e: ClassNotFoundException) {
      findResourceClass(name)
    }
}