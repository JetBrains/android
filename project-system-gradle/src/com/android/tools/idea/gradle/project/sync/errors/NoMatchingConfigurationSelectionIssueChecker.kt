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

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.project.AgpCompatibleJdkVersion

/**
 * A [RuntimeJavaCompiledVersionIssueChecker] for NoMatchingGraphVariantsException with messages following this format:
 * "No matching variant of com.android.tools.build:<Project AGP version> was found. The consumer was
 * configured to find a library for use during runtime, compatible with Java <Version used by Gradle JDK>"
 */
class NoMatchingConfigurationSelectionIssueChecker : RuntimeJavaCompiledVersionIssueChecker() {

  override val expectedErrorRegex = Regex(
    "No matching variant of com.android.tools.build:gradle:(\\d+\\.\\d+\\.\\d+[-\\w]*) was found. The consumer was " +
    "configured to find a library for use during runtime, compatible with Java (\\d+)"
  )

  override fun parseErrorRegexMatch(matchResult: MatchResult): Pair<String, String>? {
    val agpVersion = matchResult.groups[1]?.value
    val gradleJdkVersion = matchResult.groups[2]?.value
    if (agpVersion.isNullOrEmpty() || gradleJdkVersion.isNullOrEmpty()) return null

    return AgpVersion.tryParse(agpVersion)?.let {
      val agpMinCompatibleJdkVersion = AgpCompatibleJdkVersion.getCompatibleJdkVersion(it).languageLevel.feature().toString()
      Pair(agpMinCompatibleJdkVersion, gradleJdkVersion)
    }
  }
}