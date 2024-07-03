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

import com.android.tools.idea.gradle.project.build.quickFixes.OpenBuildJdkInfoLinkQuickFix
import com.android.tools.idea.gradle.project.build.quickFixes.PickLanguageLevelInPSDQuickFix
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.android.tools.idea.gradle.project.sync.idea.issues.ErrorMessageAwareBuildIssue
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenGradleJdkSettingsQuickfix
import com.android.tools.idea.gradle.project.sync.quickFixes.SetJavaLanguageLevelAllQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.SetJavaToolchainQuickFix
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.lang.JavaVersion
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import java.util.regex.Pattern


/**
 * Add quickfixes for source and target compatibility issues.
 * Looks for a couple of patterns from javac related to using an obsolete or no longer
 * supported language level as either source or target. The messages look like these:
 *    error: Source option 6 is no longer supported. Use 7 or later.
 *    warning: [options] target value 7 is obsolete and will be removed in a future release
 *
 * Parser tries to parse all these lines from one task as one warning with single set of quickfixes.
 * We currently generate the following list of quickfixes:
 *    - Add java toolchain definition to change JDK used for compilation
 *    - Change java source/target language level in all modules
 *    - Open PSD to have users pick a different compatibility level
 *    - Open Gradle settings to pick a different Gradle JDK
 *    - Open a link to jdks compatibility documentation
 */
class JavaLanguageLevelDeprecationOutputParser : BuildOutputParser {
  private val obsoletePattern = Pattern.compile(
    "warning: \\[options] (source|target) value (\\S+) is obsolete and will be removed in a future release")
  private val notSupportedPattern = Pattern.compile("error: (Source|Target) option (\\S+) is no longer supported\\. Use (\\S+) or later\\.")

  companion object {
    // AGP 8.4 Pattern
    val javaVersionRemovedPattern = Pattern.compile(
      "Java compiler version (\\d+) has removed support for compiling with source/target version (\\d+)\\.?"
    )

    fun composeBuildIssue(
      title: String,
      message: String,
      modulePath: String,
      suggestedToolchainVersion: Int?,
      suggestedLanguageLevel: LanguageLevel
    ): BuildIssue {
      val issueComposer = BuildIssueComposer(message, title)
      if (suggestedToolchainVersion != null) {
        issueComposer.addQuickFix(SetJavaToolchainQuickFix(suggestedToolchainVersion, listOf(modulePath)))
      }
      issueComposer.addQuickFix(SetJavaLanguageLevelAllQuickFix(suggestedLanguageLevel, setJvmTarget = true))
      issueComposer.addQuickFix(PickLanguageLevelInPSDQuickFix())
      issueComposer.addQuickFix(DescribedOpenGradleJdkSettingsQuickfix())
      issueComposer.addQuickFix(OpenBuildJdkInfoLinkQuickFix())
      return issueComposer.composeErrorMessageAwareBuildIssue(
        BuildErrorMessage.newBuilder().setErrorShownType(BuildErrorMessage.ErrorType.JAVA_NOT_SUPPORTED_LANGUAGE_LEVEL).build()
      )
    }

    fun suggestedLanguageLevelForCompiler(compilerVersion: JavaVersion): LanguageLevel = when {
      compilerVersion.isAtLeast(21) -> LanguageLevel.JDK_11
      compilerVersion.isAtLeast(17) -> LanguageLevel.JDK_1_8
      else -> LanguageLevel.JDK_1_8
    }

    fun suggestedLanguageLevelFromCurrentAndMinimum(currentVersion: JavaVersion?, minimumVersion: JavaVersion?): LanguageLevel {
      val minimumLevel = LanguageLevel.parse(minimumVersion.toString())
      return when {
        minimumLevel != null && minimumVersion!!.isAtLeast(8) -> minimumLevel
        currentVersion == null -> LanguageLevel.JDK_1_8
        currentVersion.isAtLeast(8) -> LanguageLevel.JDK_11
        else -> LanguageLevel.JDK_1_8
      }
    }

    fun suggestedToolchainVersionFromCurrent(currentVersion: JavaVersion?) = when {
      currentVersion == null -> null
      currentVersion.feature == 6 -> 8
      currentVersion.feature == 7 -> 11
      currentVersion.feature == 8 -> 17
      else -> null
    }
  }

  data class ParsingResult(
    val kind: MessageEvent.Kind,
    val title: String,
    val suggestedToolchainVersion: Int?,
    val suggestedLanguageLevel: LanguageLevel,
    val modulePath: String
  )

  override fun parse(line: String, reader: BuildOutputInstantReader, messageConsumer: Consumer<in BuildEvent>): Boolean {
    val parseResult = parseLines(line, reader) ?: return false
    val issue = composeBuildIssue(parseResult.title, parseResult.title, parseResult.modulePath, parseResult.suggestedToolchainVersion, parseResult.suggestedLanguageLevel)
    messageConsumer.accept(BuildIssueEventImpl(reader.parentEventId, issue, parseResult.kind))
    return true
  }

  @VisibleForTesting fun parseLines(firstLine: String, reader: BuildOutputInstantReader): ParsingResult? {
    fun tryParseErrorLine(line: String): Pair<String?, String?>? = notSupportedPattern
      .matcher(line)
      .takeIf { it.matches() }
      ?.let { Pair(it.group(2), it.group(3)) }
    fun tryParseWarningLine(line: String): Pair<String?, String?>? = obsoletePattern
      .matcher(line)
      .takeIf { it.matches() }
      ?.let { Pair(it.group(2), null) }
    var matchedKind: MessageEvent.Kind? = null
    var currentVersionFirstLineText: String? = null
    var currentVersionSecondLineText: String? = null
    var minimumVersionFirstLine: JavaVersion? = null
    var minimumVersionSecondLine: JavaVersion? = null

    /*
    Source version is always <= target version.
    Thus, we can only have several cases:
    - 1: source warning
    - 1: source warning, 2: target warning
    - 1: source error
    - 1: source error, 2: target warning
    - 1: source error, 2: target error
     */
    tryParseWarningLine(firstLine)?.let { firstResult ->
      matchedKind = MessageEvent.Kind.WARNING
      currentVersionFirstLineText = firstResult.first
      reader.readLine()?.let { secondLine ->
        tryParseWarningLine(secondLine)?.also { secondResult ->
          currentVersionSecondLineText = secondResult.first
        } ?: reader.pushBack()
      }
    }
    tryParseErrorLine(firstLine)?.let { firstResult ->
      matchedKind = MessageEvent.Kind.ERROR
      currentVersionFirstLineText = firstResult.first
      minimumVersionFirstLine = JavaVersion.tryParse(firstResult.second)
      reader.readLine()?.let { secondLine ->
        tryParseWarningLine(secondLine)?.also { secondResult ->
          currentVersionSecondLineText = secondResult.first
        } ?: tryParseErrorLine(secondLine)?.also { secondResult ->
          currentVersionSecondLineText = secondResult.first
          minimumVersionSecondLine= JavaVersion.tryParse(secondResult.second)
        } ?: reader.pushBack()
      }
    }

    val kind = matchedKind ?: return null

    //Try to also consume the line about warning suppression
    reader.readLine()?.let {
      if (it != "warning: [options] To suppress warnings about obsolete options, use -Xlint:-options.") {
        reader.pushBack()
      }
    }

    val suggestedToolchainVersion = suggestedToolchainVersionFromCurrent(JavaVersion.tryParse(currentVersionFirstLineText))
    val suggestedLanguageLevel = if (currentVersionSecondLineText != null) {
      suggestedLanguageLevelFromCurrentAndMinimum(JavaVersion.tryParse(currentVersionSecondLineText), minimumVersionSecondLine)
    }
    else {
      suggestedLanguageLevelFromCurrentAndMinimum(JavaVersion.tryParse(currentVersionFirstLineText), minimumVersionFirstLine)
    }
    val taskName = extractTaskNameFromId(reader.parentEventId) ?: return null
    val modulePath = GradleProjectSystemUtil.getParentModulePath(taskName)
    val title = when (kind) {
      MessageEvent.Kind.ERROR -> "Java compiler has removed support for compiling with source/target compatibility version $currentVersionFirstLineText."
      MessageEvent.Kind.WARNING -> "Java compiler has deprecated support for compiling with source/target compatibility version $currentVersionFirstLineText."
      else -> return null
    }
    return ParsingResult(kind, title, suggestedToolchainVersion, suggestedLanguageLevel, modulePath)
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
