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
package com.android.tools.idea.gradle.project.sync.errors

/**
 * A [RuntimeJavaCompiledVersionIssueChecker] for [AndroidPluginBaseServices.checkMinJvmVersion] with messages following this format:
 * Android Gradle plugin requires Java <AGP Minimum supported JDK> to run. You are currently using Java <Gradle JDK version>.
 */
@Suppress("KDocUnresolvedReference")
class UnsupportedJavaVersionForAgpIssueChecker : RuntimeJavaCompiledVersionIssueChecker() {

  override val expectedErrorRegex = Regex(
    "Android Gradle plugin requires Java (\\d+\\.?\\d*) to run. You are currently using Java (\\d+\\.?\\d*)."
  )

  override fun parseErrorRegexMatch(matchResult: MatchResult): Pair<String, String>? {
    val agpMinCompatibleJdkVersion = matchResult.groups[1]?.value
    val currentGradleJdkVersion = matchResult.groups[2]?.value
    if (agpMinCompatibleJdkVersion == null || currentGradleJdkVersion == null) return null

    return Pair(agpMinCompatibleJdkVersion, currentGradleJdkVersion)
  }
}