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
@file:JvmName("ModuleClassLoaderUtil")
package org.jetbrains.android.uipreview

import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.rendering.classloading.ClassTransform
import com.android.tools.idea.rendering.classloading.PseudoClass
import com.android.tools.idea.rendering.classloading.PseudoClassLocator
import com.android.tools.idea.rendering.classloading.loaders.AsmTransformingLoader
import com.android.tools.idea.rendering.classloading.loaders.ClassBinaryCacheLoader
import com.android.tools.idea.rendering.classloading.loaders.ClassLoaderLoader
import com.android.tools.idea.rendering.classloading.loaders.DelegatingClassLoader
import com.android.tools.idea.rendering.classloading.loaders.ListeningLoader
import com.android.tools.idea.rendering.classloading.loaders.MultiLoader
import com.android.tools.idea.rendering.classloading.loaders.NameRemapperLoader
import com.android.tools.idea.rendering.classloading.loaders.ProjectSystemClassLoader
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.URLUtil
import com.intellij.util.lang.UrlClassLoader
import org.jetbrains.android.sdk.StudioEmbeddedRenderTarget
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import org.objectweb.asm.ClassWriter
import java.io.File
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

private val ourLoaderCachePool = UrlClassLoader.createCachePool()

@JvmOverloads
@VisibleForTesting
fun createUrlClassLoader(paths: List<Path>, allowLock: Boolean = !SystemInfo.isWindows): UrlClassLoader {
  return UrlClassLoader.build()
    .files(paths)
    .useCache(ourLoaderCachePool) { true }
    .allowLock(allowLock)
    .setLogErrorOnMissingJar(false)
    .get()
}

/**
 * [PseudoClassLocator] that uses the given [DelegatingClassLoader.Loader] to find the `.class` file.
 */
@VisibleForTesting
class PseudoClassLocatorForLoader(private val classLoader: DelegatingClassLoader.Loader) : PseudoClassLocator {
  override fun locatePseudoClass(classFqn: String): PseudoClass =
    PseudoClass.fromByteArray(classLoader.loadClass(classFqn), this)
}

private val additionalLibraries: List<Path>
  get() {
    val layoutlibDistributionPath = StudioEmbeddedRenderTarget.getEmbeddedLayoutLibPath()
                                    ?: return emptyList() // Error is already logged by getEmbeddedLayoutLibPath
    val relativeCoroutineLibPath = FileUtil.toSystemIndependentName("data/layoutlib-extensions.jar")
    return arrayListOf(File(layoutlibDistributionPath, relativeCoroutineLibPath).toPath())
  }

val Module.externalLibraries: List<Path>
  get() = additionalLibraries + getLibraryDependenciesJars()

/**
 * Package name used to "re-package" certain classes that would conflict with the ones in the Studio class loader.
 * This applies to all packages defined in [ModuleClassLoader.PACKAGES_TO_RENAME].
 */
internal const val INTERNAL_PACKAGE = "_layoutlib_._internal_."

/**
 * Method uses to remap type names using [ModuleClassLoader.INTERNAL_PACKAGE] as prefix to its original name so they original
 * class can be loaded from the file system correctly.
 */
private fun onDiskClassNameLookup(name: String): String = StringUtil.trimStart(name, INTERNAL_PACKAGE)

/**
 * Returns true if the given [fqcn] is in the form of `package.subpackage...R` or `package.subpackage...R$...`. For example, this
 * would return true for:
 * ```
 * com.android.sample.R
 * com.android.sample.R$styles
 * com.android.sample.R$layouts
 * ```
 */
internal fun isResourceClassName(fqcn: String): Boolean =
  fqcn.substringAfterLast(".").let { it == "R" || it.startsWith("R$") }

fun Module?.isSourceModified(fqcn: String, classFile: VirtualFile): Boolean = this?.let {
  AndroidModel.get(it)?.isClassFileOutOfDate(it, fqcn, classFile) ?: false
} ?: false


/**
 * [DelegatingClassLoader.Loader] providing the implementation to load classes from a project. This loader can load user defined classes
 * from the given [Module] and classes from the libraries that the [Module] depends on.
 *
 * The [projectSystemLoader] provides a [DelegatingClassLoader.Loader] responsible to load classes from the user defined classes.
 *
 * The transformation for the user defined classes are given in [projectTransforms] while the ones to apply to the classes coming from
 * libraries are given in [nonProjectTransforms].
 *
 * The [binaryCache] provides a cache where the transformed classes from the libraries are reused. The cache can be shared across multiple
 * [ModuleClassLoaderImpl] to benefit from sharing the classes already loaded.
 */
internal class ModuleClassLoaderImpl(module: Module,
                                     private val projectSystemLoader: ProjectSystemClassLoader,
                                     val projectTransforms: ClassTransform,
                                     val nonProjectTransforms: ClassTransform,
                                     private val binaryCache: ClassBinaryCache,
                                     onClassRewrite: (String, Long, Int) -> Unit) : UserDataHolderBase(), DelegatingClassLoader.Loader, Disposable {
  private val loader: DelegatingClassLoader.Loader

  private val _projectLoadedClassNames: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
  private val _nonProjectLoadedClassNames: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

  /**
   * List of libraries used in this [ModuleClassLoaderImpl].
   */
  val externalLibraries = module.externalLibraries

  /**
   * List of the FQCN of the classes loaded from the project.
   */
  val projectLoadedClassNames: Set<String> get() = _projectLoadedClassNames

  /**
   * List of the FQCN of the classes loaded from the outside the project.
   */
  val nonProjectLoadedClassNames: Set<String> get() = _nonProjectLoadedClassNames

  /**
   * List of the [VirtualFile] of the `.class` files loaded from the project.
   */
  val projectLoadedClassVirtualFiles get() = projectSystemLoader.loadedVirtualFiles

  init {
    // Project classes loading pipeline
    val projectLoader = AsmTransformingLoader(
      projectTransforms,
      ListeningLoader(projectSystemLoader, onAfterLoad = { fqcn, _ -> _projectLoadedClassNames.add(fqcn) }),
      PseudoClassLocatorForLoader(projectSystemLoader),
      ClassWriter.COMPUTE_FRAMES,
      onClassRewrite
    )

    // Non project classes loading pipeline
    val nonProjectTransformationId = nonProjectTransforms.id
    // map of fqcn -> library path used to be able to insert classes into the ClassBinaryCache
    val fqcnToLibraryPath = mutableMapOf<String, String>()
    val jarLoader = NameRemapperLoader(
      ClassLoaderLoader(createUrlClassLoader(externalLibraries)) { fqcn, path, _ ->
        URLUtil.splitJarUrl(path)?.first?.let { libraryPath -> fqcnToLibraryPath[fqcn] = libraryPath }
      },
      ::onDiskClassNameLookup)
    val nonProjectLoader =
      ClassBinaryCacheLoader(
        ListeningLoader(
          AsmTransformingLoader(
            nonProjectTransforms,
            jarLoader,
            PseudoClassLocatorForLoader(
              jarLoader
            ),
            ClassWriter.COMPUTE_MAXS,
            onClassRewrite),
          onAfterLoad = { fqcn, bytes ->
            _nonProjectLoadedClassNames.add(fqcn)
            // Map the fqcn to the library path and insert the class into the class binary cache
            fqcnToLibraryPath[onDiskClassNameLookup(fqcn)]?.let { libraryPath ->
              binaryCache.put(fqcn, nonProjectTransformationId, libraryPath, bytes)
            }
          }),
        nonProjectTransformationId,
        binaryCache)
    loader = MultiLoader(projectLoader, nonProjectLoader)
  }

  override fun loadClass(fqcn: String): ByteArray? {
    if (Disposer.isDisposed(this)) {
      Logger.getInstance(ModuleClassLoaderImpl::class.java).warn("Using already disposed ModuleClassLoaderImpl")
      return null
    }

    return loader.loadClass(fqcn)
  }

  /**
   * Finds the [VirtualFile] for the `.class` associated to the given [fqcn].
   */
  fun findClassVirtualFile(fqcn: String): VirtualFile? = projectSystemLoader.findClassVirtualFile(fqcn)

  /**
   * Injects the given [virtualFile] with the passed [fqcn] so it looks like loaded from the project. Only for testing.
   */
  @TestOnly
  fun injectProjectClassFile(fqcn: String, virtualFile: VirtualFile) {
    _projectLoadedClassNames.add(fqcn)
    projectSystemLoader.injectClassFile(fqcn, virtualFile)
  }

  override fun dispose() {
    _projectLoadedClassNames.clear()
    _nonProjectLoadedClassNames.clear()
    projectSystemLoader.invalidateCaches()
  }
}

/**
 * Checks whether any of the .class files loaded by this loader have changed since the creation of this class loader.
 */
internal val ModuleClassLoaderImpl.isUserCodeUpToDate: Boolean
  get() = projectLoadedClassVirtualFiles
    .all { (_, virtualFile, modificationTimestamp) ->
      virtualFile.isValid && modificationTimestamp.isUpToDate(virtualFile)
    }