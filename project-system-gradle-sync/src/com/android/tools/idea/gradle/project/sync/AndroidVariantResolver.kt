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
package com.android.tools.idea.gradle.project.sync

/**
 * Represents an Android project path which can resolve variant names given a build type and list of product flavours.
 *
 * Implements [HasLintJar] for compatibility purposes.
 */
interface ResolvedAndroidProjectPath: HasBasicGradleProject, HasAndroidVariantResolver, HasLintJar

fun interface AndroidProjectPathResolver {
  /**
   * Resolves a pair of [buildId] and [projectPath] into an Android project represented by [ResolvedAndroidProjectPath] or returns `null`
   * if the given path does not refer to an Android Gradle project.
   *
   * This exists as a functional interface to be able to resolve variants. This is not called on KMP projects (even if they have Android
   * targets), as it's a single variant anyway.
   */
  fun resolve(buildId: BuildId, projectPath: String): ResolvedAndroidProjectPath?
}

interface HasAndroidVariantResolver {
  val androidVariantResolver: AndroidVariantResolver
}

interface AndroidVariantResolver {
  /**
   * Resolves a [buildType] and [productFlavors] into a variant name.
   */
  fun resolveVariant(buildType: String?, productFlavors: (dimension: String) -> String): String?

  object NONE: AndroidVariantResolver {
    override fun resolveVariant(buildType: String?, productFlavors: (dimension: String) -> String): String? = null
  }
}
