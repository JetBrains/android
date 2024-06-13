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
package com.android.tools.rendering.classloading

import com.android.tools.rendering.classloading.loaders.DelegatingClassLoader
import com.google.common.base.Charsets
import com.google.common.hash.Hashing
import com.intellij.openapi.diagnostic.Logger

private val LOG = Logger.getInstance(ModuleClassLoader::class.java)

/**
 * Classloader used in rendering and responsible for loading classes for a specific android project
 * module, restricting and isolating access the same way it is done in the actual android
 * application.
 *
 * TODO(b/270114046): Rework this solution. Consider having a pure interface with with one of the
 *   method returning [ClassLoader] instead of extending abstract class and/or [ClassLoader] that
 *   reduces flexibility.
 */
abstract class ModuleClassLoader(parent: ClassLoader?, loader: Loader) :
  DelegatingClassLoader(parent, loader) {
  abstract val stats: ModuleClassLoaderDiagnosticsRead
  abstract val isDisposed: Boolean

  protected abstract fun isCompatibleParentClassLoader(parent: ClassLoader?): Boolean

  protected abstract fun areDependenciesUpToDate(): Boolean

  /**
   * Checks if the [ModuleClassLoader] has the same transformations and parent [ClassLoader] making
   * it compatible but not necessarily up-to-date because it does not check the state of user
   * project files. Compatibility means that the [ModuleClassLoader] can be used if it did not load
   * any classes from the user source code. This allows for pre-loading the classes from
   * dependencies (which are usually more stable than user code) and speeding up the preview update
   * when user changes the source code (but not dependencies).
   */
  fun isCompatible(
    parent: ClassLoader?,
    projectTransformations: ClassTransform,
    nonProjectTransformations: ClassTransform,
  ) =
    when {
      !this.isCompatibleParentClassLoader(parent) -> {
        LOG.debug("Parent has changed, discarding ModuleClassLoader")
        false
      }
      !this.areTransformationsUpToDate(projectTransformations, nonProjectTransformations) -> {
        LOG.debug("Transformations have changed, discarding ModuleClassLoader")
        false
      }
      !this.areDependenciesUpToDate() -> {
        LOG.debug("Files have changed, discarding ModuleClassLoader")
        false
      }
      else -> {
        LOG.debug("ModuleClassLoader is up to date")
        true
      }
    }

  /**
   * Checks whether any of the .class files loaded by this loader have changed since the creation of
   * this class loader. Always returns false if there has not been any PSI changes.
   */
  abstract val isUserCodeUpToDate: Boolean

  /** Returns if the given [fqcn] has been loaded by this [ModuleClassLoader]. */
  abstract fun hasLoadedClass(fqcn: String): Boolean

  /** Set of fqcns of loaded classes from the user code. */
  abstract val projectLoadedClasses: Set<String>

  /** Set of fqcns of loaded classes from the user code dependencies (libraries etc.) */
  abstract val nonProjectLoadedClasses: Set<String>

  /** Transforms applied to classes from the user code. */
  abstract val projectClassesTransform: ClassTransform

  /** Transforms applied to classes from the user code dependencies (libraries etc.) */
  abstract val nonProjectClassesTransform: ClassTransform

  /** Clears the internal state of [ModuleClassLoader] and makes it unusable. */
  abstract fun dispose()
}

private fun ModuleClassLoader.areTransformationsUpToDate(
  projectClassesTransformationProvider: ClassTransform,
  nonProjectClassesTransformationProvider: ClassTransform,
): Boolean {
  return (calculateTransformationsUniqueId(
    this.projectClassesTransform,
    this.nonProjectClassesTransform,
  ) ==
    calculateTransformationsUniqueId(
      projectClassesTransformationProvider,
      nonProjectClassesTransformationProvider,
    ))
}

private fun calculateTransformationsUniqueId(
  projectClassesTransformationProvider: ClassTransform,
  nonProjectClassesTransformationProvider: ClassTransform,
): String? {
  return Hashing.goodFastHash(64)
    .newHasher()
    .putString(projectClassesTransformationProvider.id, Charsets.UTF_8)
    .putString(nonProjectClassesTransformationProvider.id, Charsets.UTF_8)
    .hash()
    .toString()
}
