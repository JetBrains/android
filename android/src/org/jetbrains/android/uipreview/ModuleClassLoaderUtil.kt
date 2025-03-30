/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.annotations.concurrency.GuardedBy
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.rendering.classloading.loaders.AsmTransformingLoader
import com.android.tools.idea.rendering.classloading.loaders.ClassBinaryCacheLoader
import com.android.tools.idea.rendering.classloading.loaders.FakeSavedStateRegistryLoader
import com.android.tools.idea.rendering.classloading.loaders.ListeningLoader
import com.android.tools.rendering.classloading.loaders.MultiLoader
import com.android.tools.idea.rendering.classloading.loaders.MultiLoaderWithAffinity
import com.android.tools.idea.rendering.classloading.loaders.NameRemapperLoader
import com.android.tools.idea.rendering.classloading.loaders.RecyclerViewAdapterLoader
import com.android.utils.cache.ChangeTracker
import com.android.utils.cache.ChangeTrackerCachedValue
import com.android.tools.rendering.classloading.ClassBinaryCache
import com.android.tools.rendering.classloading.ClassLoaderOverlays
import com.android.tools.rendering.classloading.ClassTransform
import com.android.tools.rendering.classloading.ModuleClassLoaderDiagnosticsWrite
import com.android.tools.rendering.classloading.PseudoClassLocatorForLoader
import com.android.tools.rendering.classloading.loaders.CachingClassLoaderLoader
import com.android.tools.rendering.classloading.loaders.ClassLoaderLoader
import com.android.tools.rendering.classloading.loaders.DelegatingClassLoader
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.io.URLUtil
import com.intellij.util.lang.UrlClassLoader
import kotlinx.coroutines.runBlocking
import org.jetbrains.android.sdk.StudioEmbeddedRenderTarget
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.org.objectweb.asm.ClassWriter
import java.io.File
import java.lang.ref.WeakReference
import java.net.URL
import java.nio.file.Path
import java.util.Collections
import java.util.Enumeration
import java.util.concurrent.ConcurrentHashMap

private val ourLoaderCachePool = UrlClassLoader.createCachePool()

@JvmOverloads
@VisibleForTesting
fun createUrlClassLoader(paths: List<Path>, allowLock: Boolean = !SystemInfo.isWindows): UrlClassLoader {
  return UrlClassLoader.build()
    .files(paths)
    .useCache(ourLoaderCachePool) { true }
    .allowLock(allowLock)
    .get()
}

private val additionalLibraries: List<Path>
  get() {
    val layoutlibDistributionPath = StudioEmbeddedRenderTarget.ourEmbeddedLayoutlibPath
                                    ?: return emptyList() // Error is already logged by getEmbeddedLayoutLibPath
    val relativeCoroutineLibPath = FileUtil.toSystemIndependentName("data/layoutlib-extensions.jar")
    return arrayListOf(File(layoutlibDistributionPath, relativeCoroutineLibPath).toPath())
  }

val Module.externalLibraries: List<Path>
  get() = additionalLibraries + getLibraryDependenciesJars()

/**
 * Package name used to "re-package" certain classes that would conflict with the ones in the Studio class loader.
 * This applies to all packages defined in [StudioModuleClassLoader.PACKAGES_TO_RENAME].
 */
internal const val INTERNAL_PACKAGE = "_layoutlib_._internal_."

/**
 * Method uses to remap type names using [StudioModuleClassLoader.INTERNAL_PACKAGE] as prefix to its original name so they original
 * class can be loaded from the file system correctly.
 */
private fun onDiskClassNameLookup(name: String): String = StringUtil.trimStart(name, INTERNAL_PACKAGE)


/**
 * [DelegatingClassLoader.Loader] providing the implementation to load classes from a project. This loader can load user defined classes
 * from the given [Module] and classes from the libraries that the [Module] depends on.
 *
 * The [projectSystemLoader] provides a [CachingClassLoaderLoader] responsible to load classes from the user defined classes.
 *
 * The transformation for the user defined classes are given in [projectTransforms] while the ones to apply to the classes coming from
 * libraries are given in [nonProjectTransforms].
 *
 * The [binaryCache] provides a cache where the transformed classes from the libraries are reused. The cache can be shared across multiple
 * [ModuleClassLoaderImpl] to benefit from sharing the classes already loaded.
 *
 * The passed [ModuleClassLoaderDiagnosticsWrite] will be used to report class rewrites.
 */
internal class ModuleClassLoaderImpl(module: Module,
                                     private val projectSystemLoader: CachingClassLoaderLoader,
                                     private val parentClassLoader: ClassLoader?,
                                     val projectTransforms: ClassTransform,
                                     val nonProjectTransforms: ClassTransform,
                                     private val binaryCache: ClassBinaryCache,
                                     private val diagnostics: ModuleClassLoaderDiagnosticsWrite) : DelegatingClassLoader.Loader, Disposable {
  private val loader: DelegatingClassLoader.Loader
  private val parentLoader = parentClassLoader?.let { ClassLoaderLoader(it) }

  private val onClassRewrite = { fqcn: String, timeMs: Long, size: Int -> diagnostics.classRewritten(fqcn, size, timeMs) }

  private val _projectLoadedClassNames: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
  private val _nonProjectLoadedClassNames: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

  /**
   * List of libraries used in this [ModuleClassLoaderImpl].
   */
  val externalLibraries = module.externalLibraries

  /**
   * Class loader for classes and resources contained in [externalLibraries].
   */
  private val externalLibrariesClassLoader = createUrlClassLoader(externalLibraries)

  /**
   * List of the FQCN of the classes loaded from the project.
   */
  val projectLoadedClassNames: Set<String> get() = _projectLoadedClassNames

  /**
   * List of the FQCN of the classes loaded from the outside the project.
   */
  val nonProjectLoadedClassNames: Set<String> get() = _nonProjectLoadedClassNames

  /**
   * [ModificationTracker] that changes every time the classes overlay has changed.
   */
  private val overlayManager: ClassLoaderOverlays = ModuleClassLoaderOverlays.getInstance(module)

  /**
   * Modification count for the overlay when this [ModuleClassLoaderImpl] was created, used for out-of-date detection.
   */
  @GuardedBy("overlayManager")
  private var initialOverlayModificationStamp = overlayManager.modificationStamp

  private fun createProjectLoader(loader: DelegatingClassLoader.Loader,
                                  dependenciesLoader: DelegatingClassLoader.Loader?,
                                  onClassRewrite: (String, Long, Int) -> Unit) = AsmTransformingLoader(
    projectTransforms,
    ListeningLoader(loader, onAfterLoad = { fqcn, _ ->
      _projectLoadedClassNames.add(fqcn) }),
    PseudoClassLocatorForLoader(
      listOfNotNull(projectSystemLoader, dependenciesLoader, parentLoader).asSequence(),
      parentClassLoader),
    ClassWriter.COMPUTE_FRAMES,
    onClassRewrite
  )

  private fun createNonProjectLoader(nonProjectTransforms: ClassTransform,
                             binaryCache: ClassBinaryCache,
                             onClassLoaded: (String) -> Unit,
                             onClassRewrite: (String, Long, Int) -> Unit): DelegatingClassLoader.Loader {
    // Non project classes loading pipeline
    val nonProjectTransformationId = nonProjectTransforms.id
    // map of fqcn -> library path used to be able to insert classes into the ClassBinaryCache
    val fqcnToLibraryPath = mutableMapOf<String, String>()
    val jarLoader = NameRemapperLoader(
      ClassLoaderLoader(externalLibrariesClassLoader) { fqcn, path, _ ->
        URLUtil.splitJarUrl(path)?.first?.let { libraryPath -> fqcnToLibraryPath[fqcn] = libraryPath }
      },
      ::onDiskClassNameLookup)
    // Loads a fake saved state registry, when [ViewTreeLifecycleOwner] requests a mocked lifecycle.
    // See also ViewTreeLifecycleTransform to check when this fake class gets created.
    val fakeSavedStateRegistryLoader = FakeSavedStateRegistryLoader(jarLoader)

    // Tree of the class Loaders:
    // Each node of this tree checks if it can load the current class, it delegates to its subtree otherwise.
    return ListeningLoader(
      delegate = ClassBinaryCacheLoader(
        delegate = ListeningLoader(
          delegate = AsmTransformingLoader(
            transform = nonProjectTransforms,
            delegate = fakeSavedStateRegistryLoader,
            pseudoClassLocator = PseudoClassLocatorForLoader(
              loaders = listOfNotNull(jarLoader, parentLoader).asSequence(),
              fallbackClassloader = parentClassLoader
            ),
            asmFlags = ClassWriter.COMPUTE_MAXS,
            onRewrite = onClassRewrite),
          onAfterLoad = { fqcn, bytes ->
            onClassLoaded(fqcn)
            // Map the fqcn to the library path and insert the class into the class binary cache
            fqcnToLibraryPath[onDiskClassNameLookup(fqcn)]?.let { libraryPath ->
              binaryCache.put(fqcn, nonProjectTransformationId, libraryPath, bytes)
            }
          }),
        transformationId = nonProjectTransformationId,
        binaryCache = binaryCache),
      onBeforeLoad = {
        if (it == "kotlinx.coroutines.android.AndroidDispatcherFactory") {
          // Hide this class to avoid the coroutines in the project loading the AndroidDispatcherFactory for now.
          // b/162056408
          //
          // Throwing an exception here (other than ClassNotFoundException) will force the FastServiceLoader to fallback
          // to the regular class loading. This allows us to inject our own DispatcherFactory, specific to Layoutlib.
          throw IllegalArgumentException("AndroidDispatcherFactory not supported by layoutlib")
        }
      }
    )
  }

  init {
    val nonProjectLoader = createNonProjectLoader(nonProjectTransforms,
                                                  binaryCache,
                                                  { _nonProjectLoadedClassNames.add(it) },
                                                  onClassRewrite)
    // Project classes loading pipeline
    val projectLoader = if (!FastPreviewManager.getInstance(module.project).isEnabled) {
      createProjectLoader(projectSystemLoader, nonProjectLoader, onClassRewrite)
    }
    else {
      MultiLoader(
        createOptionalOverlayLoader(nonProjectLoader, onClassRewrite),
        createProjectLoader(projectSystemLoader, nonProjectLoader, onClassRewrite)
      )
    }
    val allLoaders = listOfNotNull(
      projectLoader,
      nonProjectLoader,
      RecyclerViewAdapterLoader())
    loader = MultiLoaderWithAffinity(allLoaders)
  }

  /**
   * Creates an overlay loader. See [OverlayLoader].
   */
  private fun createOptionalOverlayLoader(dependenciesLoader: DelegatingClassLoader.Loader?, onClassRewrite: (String, Long, Int) -> Unit): DelegatingClassLoader.Loader {
    return createProjectLoader(OverlayLoader(overlayManager), dependenciesLoader, onClassRewrite)
  }

  override fun loadClass(fqcn: String): ByteArray? {
    if (Disposer.isDisposed(this)) {
      Logger.getInstance(ModuleClassLoaderImpl::class.java).warn("Using already disposed ModuleClassLoaderImpl",
                                                                 Throwable(Disposer.getDisposalTrace(this)))
      return null
    }

    return loader.loadClass(fqcn)
  }

  fun getResources(name: String): Enumeration<URL> = externalLibrariesClassLoader.getResources(name)
  fun getResource(name: String): URL? = externalLibrariesClassLoader.getResource(name)

  override fun dispose() {
    projectSystemLoader.invalidateCaches()
  }

  /**
   * Returns if the overlay is up-to-date.
   */
  private fun isOverlayUpToDate() = synchronized(overlayManager) {
    overlayManager.modificationStamp == initialOverlayModificationStamp
  }

  private val isUserCodeUpToDateCached: ChangeTrackerCachedValue<Boolean> = ChangeTrackerCachedValue.softReference()

  /**
   * Checks whether any of the .class files loaded by this loader have changed since the creation of this class loader.
   */
  fun isUserCodeUpToDate(module: Module?): Boolean {
    return if (module == null) {
      true
    }
    // Cache the result of isUserCodeUpToDateNonCached until any PSI modifications have happened.
    else {
      val overlayModificationTracker = ModuleClassLoaderOverlays.getInstance(module).modificationTracker
      // Avoid the cached value holding "this"
      val thisReference = WeakReference(this)
      runBlocking {
        ChangeTrackerCachedValue.get(isUserCodeUpToDateCached, {
          thisReference.get()?.isUserCodeUpToDateNonCached() ?: false
        }, ChangeTracker(
          ChangeTracker { PsiModificationTracker.getInstance(module.project).modificationCount },
          ChangeTracker { overlayModificationTracker.modificationCount }
        ))
      }
    }
  }

  /**
   * Checks whether any of the .class files loaded by this loader have changed since the creation of this class loader.
   * This method just provides the non-cached version of {@link #isUserCodeUpToDate}. {@link #isUserCodeUpToDate} will cache
   * the result of this call until a PSI modification happens.
   */
  private fun isUserCodeUpToDateNonCached() = projectSystemLoader.isUpToDate() && isOverlayUpToDate()
}
