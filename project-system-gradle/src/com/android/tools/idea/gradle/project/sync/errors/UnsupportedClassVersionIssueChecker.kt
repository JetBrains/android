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

import com.android.utils.JavaVersionUtil

/**
 * A [RuntimeJavaCompiledVersionIssueChecker] for [UnsupportedClassVersionError] with messages following this format:
 * "<class> has been compiled by a more recent version of the Java Runtime (class file version <JDK version used in AGP>),
 * this version of the Java Runtime only recognizes class file versions up to <Maximum version supported by Gradle JDK>"
 */
class UnsupportedClassVersionIssueChecker : RuntimeJavaCompiledVersionIssueChecker() {

  override val expectedErrorRegex = Regex(
    ".+ has been compiled by a more recent version of the Java Runtime \\(class file version (\\d+)\\.0\\), this " +
    "version of the Java Runtime only recognizes class file versions up to (\\d+)\\.\\d+"
  )

  override fun parseErrorRegexMatch(matchResult: MatchResult): Pair<String, String>? {
    val agpJdk = matchResult.groups[1]?.value?.toInt()
    val maxJdkSupported = matchResult.groups[2]?.value?.toInt()
    if (agpJdk == null || maxJdkSupported == null) return null

    val agpMinCompatibleJdkVersion = JavaVersionUtil.classVersionToJdk(agpJdk)
    val gradleJdkVersion = JavaVersionUtil.classVersionToJdk(maxJdkSupported)
    return Pair(agpMinCompatibleJdkVersion, gradleJdkVersion)
  }
}