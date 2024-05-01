/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.output

import com.android.tools.idea.gradle.project.build.quickFixes.OpenJavaLanguageSpecQuickFix
import com.android.tools.idea.gradle.project.build.quickFixes.OpenSourceCompatibilityLinkQuickFix
import com.android.tools.idea.gradle.project.build.quickFixes.OpenTargetCompatibilityLinkQuickFix
import com.android.tools.idea.gradle.project.build.quickFixes.PickLanguageLevelInPSDQuickFix
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenGradleJdkSettingsQuickfix
import com.android.tools.idea.gradle.project.sync.quickFixes.SetJavaLanguageLevelAllQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.SetJavaToolchainQuickFix
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.lang.JavaVersion
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
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
class JavaLanguageLevelDeprecationOutputParser : BuildOutputParser {
  private val error_prefix = "error:"
  private val warning_prefix = "warning:"
  private val obsoletePattern = Pattern.compile(
    "warning: \\[options] (source|target) value (\\S+) is obsolete and will be removed in a future release")
  private val notSupportedPattern = Pattern.compile("error: (Source|Target) option (\\S+) is no longer supported. Use (\\S+) or later.")

  fun getQuickFixes(message: String, modulePath: String): List<DescribedBuildIssueQuickFix> {
    val typeOfCompatibilityIssue: String?
    val obsoleteMatcher = obsoletePattern.matcher(message)
    val currentVersion: JavaVersion?
    val minimumVersion: JavaVersion?
    val minimumLevel: LanguageLevel?
    if (obsoleteMatcher.matches()) { // TODO: Use currently used Android SDK to suggest the minimum supported target?
      typeOfCompatibilityIssue = obsoleteMatcher.group(1)
      currentVersion = JavaVersion.tryParse(obsoleteMatcher.group(2))
      minimumVersion = null
      minimumLevel = null
    }
    else {
      val notSupportedMatcher = notSupportedPattern.matcher(message)
      if (notSupportedMatcher.matches()) { // TODO: Use the version in the message to suggest changing to it?
        typeOfCompatibilityIssue = notSupportedMatcher.group(1).lowercase()
        currentVersion = JavaVersion.tryParse(notSupportedMatcher.group(2))
        minimumVersion = JavaVersion.tryParse(notSupportedMatcher.group(3))
        minimumLevel = LanguageLevel.parse(minimumVersion.toString())
      }
      else {
        return emptyList()
      }
    }
    val fixes = mutableListOf<DescribedBuildIssueQuickFix>()
    // Suggest a version if the message suggests one and it is at least 8
    if (minimumLevel != null && minimumVersion!!.isAtLeast(8)) {
      fixes.add(SetJavaLanguageLevelAllQuickFix(minimumLevel, setJvmTarget = true))
    }
    // Suggest 8 if no version is suggested and current is not at least 8 (or cannot be parsed)
    else if (currentVersion == null || !currentVersion.isAtLeast(8)) {
      fixes.add(SetJavaLanguageLevelAllQuickFix(LanguageLevel.JDK_1_8, setJvmTarget = true))
    }

    fixes.add(DescribedOpenGradleJdkSettingsQuickfix())
    SetJavaToolchainQuickFix.recommendedToolchainVersionsMap[currentVersion?.feature]?.let {
      fixes.add(SetJavaToolchainQuickFix(it, listOf(modulePath)))
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

  override fun parse(line: String, reader: BuildOutputInstantReader, messageConsumer: Consumer<in BuildEvent>): Boolean {
    val kind: MessageEvent.Kind
    val message: String
    if (warning_prefix.regionMatches(0, line, 0, warning_prefix.length,
                                                                     ignoreCase = true)) {
      message = line.substring(warning_prefix.length).trim { it <= ' ' }
      kind = MessageEvent.Kind.WARNING
    }
    else if (error_prefix.regionMatches(0, line, 0, error_prefix.length,
                                                                   ignoreCase = true)) {
      message = line.substring(error_prefix.length).trim { it <= ' ' }
      kind = MessageEvent.Kind.ERROR
    }
    else {
      return false
    }

    val taskName = extractTaskNameFromId(reader.parentEventId) ?: return false
    val modulePath = GradleProjectSystemUtil.getParentModulePath(taskName)

    val quickFixes = getQuickFixes(line, modulePath)

    if (quickFixes.isEmpty()) {
      //We did not successfully parse this.
      return false
    }

    val issueComposer = BuildIssueComposer(message, message)
    for (fix in quickFixes) {
      issueComposer.addQuickFix(fix)
    }
    messageConsumer.accept(BuildIssueEventImpl(reader.parentEventId, issueComposer.composeBuildIssue(), kind))
    return true
  }

  private fun extractTaskNameFromId(parentEventId: Any): String? {
    if (parentEventId !is String) {
      return null
    }
    //[-447475743:244193606] > [Task :app:compileDebugJavaWithJavac]
    val taskNamePattern = Pattern.compile("> \\[Task (?<gradleFullTaskName>(?::[^:]+)*)]")
    val matcher = taskNamePattern.matcher(parentEventId as String)
    if (matcher.find()) {
      return matcher.group("gradleFullTaskName")
    }
    return null
  }
}

class DescribedOpenGradleJdkSettingsQuickfix : DescribedBuildIssueQuickFix {
  val delegate = OpenGradleJdkSettingsQuickfix()
  override val id: String get() = delegate.id

  override val description: String get() = "Pick a different JDK to run Gradle..."

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    return delegate.runQuickFix(project, dataContext)
  }
}
