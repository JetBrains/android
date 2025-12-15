/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.lang

import com.intellij.codeInsight.Nullability
import com.intellij.codeInsight.annoPackages.AnnotationPackageSupport

class AndroidAnnotationSupport : AnnotationPackageSupport {
  override fun getNullabilityAnnotations(nullability: Nullability) =
    // Order matters.
    // androidx.annotation: preferred
    // android.support: legacy, preferred only in apps not yet migrated to Androidx
    // android.annotation: internal annotation in the Android SDK, not recommended for apps
    // com.android.annotations: internal annotation in the dev tools, not recommended for apps
    // RecentlyNonNull,RecentlyNullable: internal annotation, not recommended for apps
    when (nullability) {
      Nullability.NOT_NULL -> mutableListOf(
        "androidx.annotation.NonNull",
        "android.support.annotation.NonNull",
        "android.annotation.NonNull",
        "com.android.annotations.NonNull",
        "androidx.annotation.RecentlyNonNull"
      )
      Nullability.NULLABLE -> mutableListOf(
        "androidx.annotation.Nullable",
        "android.support.annotation.Nullable",
        "android.annotation.Nullable",
        "com.android.annotations.Nullable",
        "androidx.annotation.RecentlyNullable"
      )
      else -> mutableListOf()
    }
}