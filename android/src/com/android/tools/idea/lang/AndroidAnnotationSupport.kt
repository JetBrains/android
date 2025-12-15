// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.lang

import com.intellij.codeInsight.Nullability
import com.intellij.codeInsight.annoPackages.AnnotationPackageSupport

internal class AndroidAnnotationSupport : AnnotationPackageSupport {
    override fun getNullabilityAnnotations(nullability: Nullability): List<String> {
        return when (nullability) {
            Nullability.NOT_NULL -> listOf(
                "android.annotation.NonNull",
                "androidx.annotation.NonNull",
                "com.android.annotations.NonNull",
                "android.support.annotation.NonNull",
                "androidx.annotation.RecentlyNonNull"
            )

            Nullability.NULLABLE -> listOf(
                "android.annotation.Nullable",
                "androidx.annotation.Nullable",
                "com.android.annotations.Nullable",
                "android.support.annotation.Nullable",
                "androidx.annotation.RecentlyNullable"
            )

            else -> listOf()
        }
    }
}
