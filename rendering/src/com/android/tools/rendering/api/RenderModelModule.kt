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
package com.android.tools.rendering.api

import com.android.ide.common.rendering.api.AssetRepository
import com.android.tools.module.AndroidModuleInfo
import com.android.tools.module.ModuleDependencies
import com.android.tools.module.ModuleKey
import com.android.tools.rendering.classloading.ClassTransform
import com.android.tools.rendering.classloading.ModuleClassLoaderManager.Reference
import com.android.tools.res.ResourceRepositoryManager
import com.android.tools.res.ids.ResourceIdManager
import com.android.tools.sdk.AndroidPlatform
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

/** Provides the name and basic state properties of the Android module for logging. */
interface RenderModelModuleLoggingId {

  val name: String

  val isDisposed: Boolean
}

/** Provides all the module-specific Android resources information required for rendering. */
interface RenderModelModule : IdeaModuleProvider, RenderModelModuleLoggingId {

  val assetRepository: AssetRepository?

  val manifest: RenderModelManifest?

  val resourceRepositoryManager: ResourceRepositoryManager

  val info: AndroidModuleInfo?

  val androidPlatform: AndroidPlatform?

  val resourceIdManager: ResourceIdManager

  /** An object uniquely identifying the module. Used for caching. */
  val moduleKey: ModuleKey

  /** If found, returns the module's resource package name. */
  val resourcePackage: String?

  val dependencies: ModuleDependencies

  /** The project current module belongs to. */
  val project: Project

  val environment: EnvironmentContext

  fun interface ClassLoaderProvider {
    fun getClassLoader(
      parent: ClassLoader?,
      additionalProjectTransformation: ClassTransform,
      additionalNonProjectTransformation: ClassTransform,
      onNewModuleClassLoader: Runnable,
    ): Reference<*>
  }

  fun getClassLoaderProvider(privateClassLoader: Boolean): ClassLoaderProvider

  /**
   * Disposable that can be used as a parent for [Disposable]s that depend on the
   * [RenderModelModule] lifecycle.
   */
  val parentDisposable: Disposable

  /** Marks this [RenderModelModule] as disposed. */
  fun dispose() {
    Disposer.dispose(parentDisposable)
  }
}
