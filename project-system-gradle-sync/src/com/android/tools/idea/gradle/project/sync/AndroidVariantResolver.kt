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

fun interface AndroidProjectPathResolver {
  /**
   * Resolves a pair of [buildId] and [projectPath] into an Android project represented by [AndroidVariantResolver] or returns `null` if the
   * given path does refer to an Android Gradle project.
   */
  fun resolve(buildId: BuildId, projectPath: String): AndroidModule?
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
