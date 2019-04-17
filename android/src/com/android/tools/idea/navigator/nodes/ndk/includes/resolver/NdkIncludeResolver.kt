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

import com.android.tools.idea.navigator.nodes.ndk.includes.model.PackageType.NdkComponent
import com.android.tools.idea.navigator.nodes.ndk.includes.model.PackageType.NdkSxsComponent

/**
 * Resolver that matches various well-known NDK folder patterns.
 */
class NdkIncludeResolver : IncludeResolver() {
  private val SXS_REVISION_PATTERN = ".*/ndk/(?<ndk>[0-9]+(?:\\.[0-9]+(?:\\.[0-9]+)?)?(?:[\\s-]*)?(?:rc|alpha|beta|\\.[0-9]+)?)"
  private val LEGACY_REVISION_PATTERN = ".*/ndk-bundle"

  private val resolvers: List<IncludeResolver>

  init {
    resolvers = listOf(
      // Contains NDK platform header files
      leafNamed("^(?<home>{NDKFOLDER})(?<relative>/platforms/(?<library>android-.*?)/arch-.*?(/.*))$"),
      // Contains STL/runtime header files
      leafNamed("^(?<home>{NDKFOLDER})(?<relative>/sources/cxx-stl/(?<library>.*?)(/.*))$"),
      // Contains third party header files in the NDK like GoogleTest
      leafNamed("^(?<home>{NDKFOLDER})(?<relative>/sources/third_party/(?<library>.*?)(/.*))$"),
      // Contains LLVM
      literalNamed("^(?<home>{NDKFOLDER})(?<relative>/toolchains/llvm(/.*))$", "LLVM"),
      // Contains specialize toolchains like RenderScript
      leafNamed("^(?<home>{NDKFOLDER})(?<relative>/toolchains/(?<library>.*?)(/.*))$"),
      // Contains NDK CPU Features header files
      literalNamed("^(?<home>{NDKFOLDER})(?<relative>/sources/android/cpufeatures(/.*))$", "CPU Features"),
      // Contains NDK native app glue header files
      literalNamed("^(?<home>{NDKFOLDER})(?<relative>/sources/android/native_app_glue(/.*))$", "Native App Glue"),
      // Contains NDK helper files
      literalNamed("^(?<home>{NDKFOLDER})(?<relative>/sources/android/ndk_helper(/.*))$", "Helper"),
      // Contains NDK helper files
      literalNamed("^(?<home>{NDKFOLDER})(?<relative>/sources/android/support(/.*))$", "Support"),
      // Contains header files for Android sysroot
      literalNamed("^(?<home>{NDKFOLDER})(?<relative>/sysroot(/.*))$", "Sysroot"))
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
  private fun literalNamed(pattern: String, name: String): List<IncludeResolver> {
    val result = mutableListOf<IncludeResolver>()
    // NDK SxS resolver should come first because ndk.dir may have the path to a side-by-side NDK.
    result += IndexedRegularExpressionIncludeResolver(NdkSxsComponent,  concreteNdkFolder(pattern, SXS_REVISION_PATTERN), name)
    result += IndexedRegularExpressionIncludeResolver(NdkComponent,  concreteNdkFolder(pattern,LEGACY_REVISION_PATTERN), name)
    return result
  }

  /**
   * Generate an NDK resolver that takes its leaf name from the folder path.
   */
  private fun leafNamed(pattern: String): List<IncludeResolver> {
    val result = mutableListOf<IncludeResolver>()
    // NDK SxS resolver should come first because ndk.dir may have the path to a side-by-side NDK.
    result += IndexedRegularExpressionIncludeResolver(NdkSxsComponent, concreteNdkFolder(pattern, SXS_REVISION_PATTERN), null)
    result += IndexedRegularExpressionIncludeResolver(NdkComponent, concreteNdkFolder(pattern, LEGACY_REVISION_PATTERN), null)

    return result
  }

  /**
   * Get the given myPattern with NDK folder made concrete.
   */
  private fun concreteNdkFolder(pattern: String, ndkFolder : String): String {
    return pattern.replace("{NDKFOLDER}", ndkFolder)
  }
}
