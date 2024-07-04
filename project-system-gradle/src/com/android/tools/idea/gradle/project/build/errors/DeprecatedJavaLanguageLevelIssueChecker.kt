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
package com.android.tools.idea.gradle.project.build.errors

import com.android.tools.idea.gradle.project.build.output.JavaLanguageLevelDeprecationOutputParser
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.issue.BuildIssue
import com.intellij.util.lang.JavaVersion
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import java.util.function.Consumer

class DeprecatedJavaLanguageLevelIssueChecker : GradleIssueChecker {
  override fun check(issueData: GradleIssueData): BuildIssue? {
    /*
    Note: We do not implement check here because the issue generated from here will be converted to 'Failure' object and presented as
    an error node on the top level. Instead, we want to keep treating this as a compilation error attached to '*javac' tasks. We can do this
    from the parsed failure message below.
     */
    return null
  }

  override fun consumeBuildOutputFailureMessage(message: String,
                                                failureCause: String,
                                                stacktrace: String?,
                                                location: FilePosition?,
                                                parentEventId: Any,
                                                messageConsumer: Consumer<in BuildEvent>): Boolean {
    if (!message.startsWith("Execution failed for task ")) return false
    JavaLanguageLevelDeprecationOutputParser.javaVersionRemovedPattern.matcher(failureCause).let { matcher ->
      if (matcher.matches()) {
        val compilerVersion = JavaVersion.tryParse(matcher.group(1)) ?: return false
        val currentVersion = JavaVersion.tryParse(matcher.group(2)) ?: return false
        //In this case when error starts with 'Execution failed for task ' checked above, parentEventId is set to task name.
        val modulePath = GradleProjectSystemUtil.getParentModulePath(parentEventId as String).takeUnless { it.isBlank() } ?: return false
        val suggestedLanguageLevel = JavaLanguageLevelDeprecationOutputParser.suggestedLanguageLevelForCompiler(compilerVersion)
        val suggestedToolchainVersion = JavaLanguageLevelDeprecationOutputParser.suggestedToolchainVersionFromCurrent(currentVersion)
        val issue = JavaLanguageLevelDeprecationOutputParser.composeBuildIssue(failureCause, message, modulePath, suggestedToolchainVersion, suggestedLanguageLevel)
        messageConsumer.accept(BuildIssueEventImpl(parentEventId, issue, MessageEvent.Kind.ERROR))
        return true
      }
    }
    return false
  }


}