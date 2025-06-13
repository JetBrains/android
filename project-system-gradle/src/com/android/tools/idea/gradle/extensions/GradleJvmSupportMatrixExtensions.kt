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
package com.android.tools.idea.gradle.extensions

import com.android.tools.idea.jdk.JavaVersionLts
import com.android.tools.idea.sdk.IdeSdks
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix

/**
 * Returns the highest Java version compatible given a Gradle version and Studio support based on [GradleJvmSupportMatrix] class which
 * stores the compatibility table documented on https://docs.gradle.org/current/userguide/compatibility.html.
 */
fun GradleJvmSupportMatrix.Companion.getRecommendedJavaVersion(gradleVersion: GradleVersion, considerOnlyLts: Boolean): JavaVersion {
  return GradleJvmSupportMatrix.getSupportedJavaVersions(gradleVersion)
    .filter { it.feature <= IdeSdks.DEFAULT_JDK_VERSION.maxLanguageLevel.feature() }
    .last {
      when {
        considerOnlyLts -> JavaVersionLts.isLtsVersion(it)
        else -> true
      }
    }
}