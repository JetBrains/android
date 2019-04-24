/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.daemon.analysis

import com.android.tools.idea.gradle.structure.model.PsDeclaredDependency
import com.android.tools.idea.gradle.structure.model.PsDeclaredLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsGeneralIssue
import com.android.tools.idea.gradle.structure.model.PsIssue
import com.android.tools.idea.gradle.structure.model.PsIssue.Severity.WARNING
import com.android.tools.idea.gradle.structure.model.PsIssueType.PROJECT_ANALYSIS
import com.android.tools.idea.gradle.structure.model.PsModuleType
import com.android.tools.idea.gradle.structure.model.PsQuickFix
import com.android.tools.idea.gradle.structure.quickfix.PsDependencyScopeQuickFixPath
import com.android.tools.idea.gradle.structure.quickfix.PsLibraryDependencyPlusQuickFixPath

fun analyzeDeclaredDependency(dependency: PsDeclaredLibraryDependency): Sequence<PsIssue> {
  val path = dependency.path

  val declaredSpec = dependency.spec
  val declaredVersion = declaredSpec.version
  if (declaredVersion != null && declaredVersion.endsWith("+")) {
    val message = "Avoid using '+' in version numbers; can lead to unpredictable and unrepeatable builds."
    // TODO(b/111058962): Replace "+" with the most recent version of the library.
    val issue = PsGeneralIssue(message, path, PROJECT_ANALYSIS, WARNING, PsLibraryDependencyPlusQuickFixPath(dependency))

    return sequenceOf(issue)
  }
  return emptySequence()
}

fun analyzeDependencyScope(dependency: PsDeclaredDependency): Iterable<PsIssue> {
  // TODO(xof): implement complete logic here.  cf. suggestApiConfigurationUse() in GradleDetector.kt
  fun shouldSuggestApiScopeReplacement(): Boolean {
    if (dependency.configurationName.startsWith("test") || dependency.configurationName.startsWith("androidTest")) {
      return false
    }
    val module = dependency.parent
    return when (module.projectType) {
      PsModuleType.UNKNOWN -> true
      PsModuleType.ANDROID_APP -> false // TODO(xof) see LintIdeProject.hasDynamicFeatures
      PsModuleType.ANDROID_LIBRARY -> true
      PsModuleType.ANDROID_INSTANTAPP -> false
      PsModuleType.ANDROID_FEATURE -> true
      PsModuleType.ANDROID_DYNAMIC_FEATURE -> true
      PsModuleType.ANDROID_TEST -> false
      PsModuleType.JAVA -> true
    }
  }

  fun fixesFor(configurationName: String): List<PsQuickFix> {
    val suggestApi = shouldSuggestApiScopeReplacement()
    val implementationReplacement: String
    val apiReplacement: String

    if (configurationName == "compile") {
      implementationReplacement = "implementation"
      apiReplacement = "api"
    }
    else {
      implementationReplacement = configurationName.removeSuffix("Compile") + "Implementation"
      apiReplacement = configurationName.removeSuffix("Compile") + "Api"
    }

    val implementationFix = PsDependencyScopeQuickFixPath(dependency, implementationReplacement)
    return if (suggestApi) {
      listOf(
        PsDependencyScopeQuickFixPath(dependency, apiReplacement),
        implementationFix
      )
    }
    else {
      listOf(implementationFix)
    }
  }

  val issues = mutableListOf<PsIssue>()
  val configurationName = dependency.configurationName
  if (configurationName == "compile" || configurationName.endsWith("Compile")) {
    val text = "Obsolete dependency configuration found: <b>$configurationName</b>"
    val fixes = fixesFor(configurationName)
    val path = dependency.path
    if (path != null) {
      val issue = PsGeneralIssue(text, "", path, PROJECT_ANALYSIS, WARNING, fixes)
      issues.add(issue)
    }
  }
  return issues.asIterable()
}
