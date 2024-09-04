/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.res

import com.android.ide.common.resources.ResourceRepository
import com.intellij.openapi.application.CachedSingletonsRegistry
import java.nio.file.Path
import java.util.ServiceConfigurationError
import java.util.ServiceLoader
import java.util.function.Supplier

/**
 * Provides an instance of [ResourceRepository] containing framework resources with specific parameters.
 */
fun interface FrameworkResourceRepositoryManager {
  fun getFrameworkResources(
    resourceJarFile: Path,
    useCompiled9Patches: Boolean,
    languages: Set<String>,
    overlays: List<FrameworkOverlay>
  ): ResourceRepository

  /** Interface for Java Service that provides [FrameworkResourceRepositoryManager] instance. */
  interface Provider {
    val frameworkResourceRepositoryManager: FrameworkResourceRepositoryManager
  }

  companion object {
    @Suppress("UnstableApiUsage")
    private val instanceSupplier: Supplier<FrameworkResourceRepositoryManager?> =
      CachedSingletonsRegistry.lazy {
        ServiceLoader.load(Provider::class.java, this::class.java.classLoader).firstOrNull()?.frameworkResourceRepositoryManager
      }

    @JvmStatic
    fun getInstance(): FrameworkResourceRepositoryManager {
      return instanceSupplier.get() ?: throw ServiceConfigurationError("Could not find any FrameworkResourceRepositoryManager")
    }
  }
}