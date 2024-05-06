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
package com.android.tools.idea.gradle.project.build.quickFixes

import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.SetLanguageLevel8AllQuickFix
import com.intellij.util.lang.JavaVersion
import java.util.regex.Pattern

/**
 * Add quickfixes for source and target compatibility issues. Looks for a couple of patterns related using an obsolete or no longer
 * supported language level as either source or target. The messages look like these:
 *    error: Source option 6 is no longer supported. Use 7 or later.
 *    warning: [options] target value 7 is obsolete and will be removed in a future release
 *
 * When those messages are found, it will generate the following list of quickfixes:
 *    - Open PSD to have users pick a different compatibility level
 *    - Open a link to source (or target) compatibility documentation
 *    - Open a link to Java Language Spec
 */
class JavaDeprecatedProvider : AndroidGradlePluginQuickFixProvider {
  private val obsoletePattern = Pattern.compile(
    "warning: \\[options] (source|target) value (\\S+) is obsolete and will be removed in a future release")
  private val notSupportedPattern = Pattern.compile("error: (Source|Target) option (\\S+) is no longer supported. Use (\\S+) or later.")

  override fun getQuickFixes(message: String): List<DescribedBuildIssueQuickFix> {
    val typeOfCompatibilityIssue: String?
    val obsoleteMatcher = obsoletePattern.matcher(message)
    val currentVersion: JavaVersion?
    val minimumVersion: JavaVersion?
    if (obsoleteMatcher.matches()) { // TODO: Use currently used Android SDK to suggest the minimum supported target?
      typeOfCompatibilityIssue = obsoleteMatcher.group(1)
      currentVersion = JavaVersion.tryParse(obsoleteMatcher.group(2))
      minimumVersion = null
    }
    else {
      val notSupportedMatcher = notSupportedPattern.matcher(message)
      if (notSupportedMatcher.matches()) { // TODO: Use the version in the message to suggest changing to it?
        typeOfCompatibilityIssue = notSupportedMatcher.group(1).lowercase()
        currentVersion = JavaVersion.tryParse(notSupportedMatcher.group(2))
        minimumVersion = JavaVersion.tryParse(notSupportedMatcher.group(3))
      }
      else {
        return emptyList()
      }
    }
    val fixes = mutableListOf<DescribedBuildIssueQuickFix>()
    // Apply level 8 if
    if (currentVersion == null || !currentVersion.isAtLeast(8)) {
      // Current is lower than 8, suggest to use 8 if minimum is not defined or if it is lower or equal to 8
      if (minimumVersion == null || !minimumVersion.isAtLeast(9)) {
        fixes.add(SetLanguageLevel8AllQuickFix(setJvmTarget = true))
      }
    }
    fixes.add(PickLanguageLevelInPSDQuickFix())
    if (typeOfCompatibilityIssue == "source") {
      fixes.add(OpenSourceCompatibilityLinkQuickFix())
    }
    else if (typeOfCompatibilityIssue == "target") {
      fixes.add(OpenTargetCompatibilityLinkQuickFix())
    }
    fixes.add(OpenJavaLanguageSpecQuickFix())
    return fixes
  }
}
