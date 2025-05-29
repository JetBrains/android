/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.build.output.GradleBuildFailureParser.FailureDetailsHandler
import com.android.tools.idea.gradle.project.sync.idea.issues.ErrorMessageAwareBuildIssue
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.intellij.build.FileNavigatable
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import java.util.function.Consumer

class ConfigurationCacheErrorParser : FailureDetailsHandler {

  override fun consumeFailureMessage(
    failure: GradleBuildFailureParser.ParsedFailureDetails,
    location: FilePosition?,
    parentEventId: Any,
    messageConsumer: Consumer<in BuildEvent>
  ): Boolean {
    if (failure.whatWentWrongSectionLines.firstOrNull() == "Configuration cache problems found in this build.") {
      //TODO Create bug: this should be FileMessage when possible, otherwise it is not presented under file. But this requires refactoring so do separately.
      val buildIssue = object : ErrorMessageAwareBuildIssue {
        override val description: String = failure.whatWentWrongSectionText.trimEnd()
        override val quickFixes: List<BuildIssueQuickFix> = emptyList()
        override val title: String = BUILD_ISSUE_TITLE
        override val buildErrorMessage: BuildErrorMessage
          get() = BuildErrorMessage.newBuilder().setErrorShownType(BuildErrorMessage.ErrorType.CONFIGURATION_CACHE).build()

        override fun getNavigatable(project: Project): Navigatable? = location?.let {
          return FileNavigatable(project, it)
        }
      }
      messageConsumer.accept(BuildIssueEventImpl(parentEventId, buildIssue, MessageEvent.Kind.ERROR))
      return true
    }
    return false
  }

  companion object {
    const val BUILD_ISSUE_TITLE: String = "Configuration cache problems found in this build."
  }
}