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

import com.android.resources.aar.FrameworkResourceRepository
import java.nio.file.Path
import java.util.ServiceConfigurationError
import java.util.ServiceLoader

/**
 * Provides an instance of [FrameworkResourceRepository] with specific parameters.
 */
fun interface FrameworkResourceRepositoryManager {
  fun getFrameworkResources(
    resourceDirectoryOrFile: Path,
    useCompiled9Patches: Boolean,
    languages: Set<String>
  ): FrameworkResourceRepository

  /** Interface for Java Service that provides [FrameworkResourceRepositoryManager] instance. */
  interface Provider {
    val frameworkResourceRepositoryManager: FrameworkResourceRepositoryManager
  }

  companion object {
    @JvmStatic
    fun getInstance(): FrameworkResourceRepositoryManager {
      val serviceLoader = ServiceLoader.load(Provider::class.java, this::class.java.classLoader)
      return serviceLoader.firstOrNull()?.frameworkResourceRepositoryManager
             ?: throw ServiceConfigurationError("Could not find any FrameworkResourceRepositoryManager")
    }
  }
}