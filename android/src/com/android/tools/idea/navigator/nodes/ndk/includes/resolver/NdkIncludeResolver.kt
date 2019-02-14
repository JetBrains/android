/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes.ndk.includes.resolver

import com.android.tools.idea.navigator.nodes.ndk.includes.model.SimpleIncludeValue
import java.io.File
import org.apache.commons.io.FilenameUtils

import com.android.tools.idea.navigator.nodes.ndk.includes.model.PackageType.NdkComponent
import java.util.regex.Pattern

/**
 * Resolver that matches various well-known NDK folder patterns.
 */
class NdkIncludeResolver(ndkFolder: File?) : IncludeResolver() {
  private val FULL_REVISION_PATTERN = ".*/ndk/[0-9]+(?:\\.[0-9]+(?:\\.[0-9]+)?)?(?:[\\s-]*)?(?:rc|alpha|beta|\\.[0-9]+)?"
  private val resolvers: List<IncludeResolver>

  init {
    val ndk = if (ndkFolder != null) {
      Pattern.quote(FilenameUtils.separatorsToUnix(ndkFolder.absolutePath))
    } else {
      null
    }
    resolvers = listOf(
      // Contains NDK platform header files
      leafNamed("^({NDKFOLDER})(/platforms/(android-.*?)/arch-.*?(/.*))$", ndk),
      // Contains STL/runtime header files
      leafNamed("^({NDKFOLDER})(/sources/cxx-stl/(.*?)(/.*))$", ndk),
      // Contains third party header files in the NDK like GoogleTest
      leafNamed("^({NDKFOLDER})(/sources/third_party/(.*?)(/.*))$", ndk),
      // Contains specialize toolchains like Rend Script
      leafNamed("^({NDKFOLDER})(/toolchains/(.*?)(/.*))$", ndk),
      // Contains NDK CPU Features header files
      literalNamed("^({NDKFOLDER})(/sources/android/cpufeatures(/.*))$", "CPU Features", ndk),
      // Contains NDK native app glue header files
      literalNamed("^({NDKFOLDER})(/sources/android/native_app_glue(/.*))$", "Native App Glue", ndk),
      // Contains NDK helper files
      literalNamed("^({NDKFOLDER})(/sources/android/ndk_helper(/.*))$", "NDK Helper", ndk),
      // Contains header files for Android sysroot
      literalNamed("^({NDKFOLDER})(/sysroot(/.*))$", "Android Sysroot", ndk))
      .flatten()
  }

  override fun resolve(includeFolder: File): SimpleIncludeValue? {
      for (resolver in resolvers) {
        val classifiedIncludeExpression = resolver.resolve(includeFolder)
        if (classifiedIncludeExpression != null) {
          return classifiedIncludeExpression
        }
      }
      return null
  }

  /**
   * Generate an NDK resolver that has a literal leaf name like "CPU Features"
   */
  private fun literalNamed(pattern: String, name: String, ndk : String?): List<IncludeResolver> {
    val result = mutableListOf<IncludeResolver>()
    if (ndk != null) {
      result += IndexedRegularExpressionIncludeResolver(NdkComponent, concreteNdkFolder(pattern, ndk), name)
    }
    result += IndexedRegularExpressionIncludeResolver(NdkComponent,  concreteNdkFolder(pattern, FULL_REVISION_PATTERN), name)
    return result
  }

  /**
   * Generate an NDK resolver that takes its leaf name from the folder path.
   */
  private fun leafNamed(pattern: String, ndk : String?): List<IncludeResolver> {
    val result = mutableListOf<IncludeResolver>()
    if (ndk != null) {
      result += IndexedRegularExpressionIncludeResolver(NdkComponent, concreteNdkFolder(pattern, ndk))
    }
    result += IndexedRegularExpressionIncludeResolver(NdkComponent, concreteNdkFolder(pattern, FULL_REVISION_PATTERN))
    return result
  }

  /**
   * Get the given myPattern with NDK folder made concrete.
   */
  private fun concreteNdkFolder(pattern: String, ndkFolder : String): String {
    return pattern.replace("{NDKFOLDER}", ndkFolder)
  }
}
