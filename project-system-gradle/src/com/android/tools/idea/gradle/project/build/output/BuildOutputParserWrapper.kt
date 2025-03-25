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

import com.android.tools.idea.gradle.project.build.events.FileMessageBuildIssueEvent
import com.android.tools.idea.gradle.project.build.events.GradleErrorQuickFixProvider
import com.android.tools.idea.gradle.project.build.events.MessageBuildIssueEvent
import com.android.tools.idea.gradle.project.build.events.copyWithQuickFix
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueDescriptionComposer
import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.DuplicateMessageAware
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import org.jetbrains.annotations.VisibleForTesting
import java.util.function.Consumer


/**
 * A wrapper class for all the build output parsers so we can inject StudioBot help link.
 */
class BuildOutputParserWrapper(val parser: BuildOutputParser, val taskId: ExternalSystemTaskId) : BuildOutputParser {

  override fun parse(line: String?, reader: BuildOutputInstantReader?, messageConsumer: Consumer<in BuildEvent>?): Boolean {
    return parser.parse(line, reader) {
      val providers = GradleErrorQuickFixProvider.getProviders()
      val additionalQuickfixes = providers.mapNotNull { provider -> provider.createBuildIssueAdditionalQuickFix(it, taskId) }
      val event = if (additionalQuickfixes.isNotEmpty()) {
        it.toBuildIssueEventWithQuickFix(additionalQuickfixes)
      } else {
        it
      }
      messageConsumer?.accept(event)
    }
  }
}

/**
 * Extends the BuildEvent to BuildIssueEvent, so that quick fix link can be added.
 */
private fun BuildEvent.toBuildIssueEventWithQuickFix(quickFixes: List<DescribedBuildIssueQuickFix>): BuildEvent {
  if (this !is MessageEvent) return this
  val additionalDescription = BuildIssueDescriptionComposer().apply {
    quickFixes.forEach { addQuickFix(it) }
  }
  return toBuildIssueEventWithAdditionalDescription(additionalDescription)
}

@VisibleForTesting
@Suppress("UnstableApiUsage")
fun MessageEvent.toBuildIssueEventWithAdditionalDescription(additionalDescription: BuildIssueDescriptionComposer): MessageEvent {
  val duplicateMessageAware = this is DuplicateMessageAware
  return when(this) {
    // TODO(b/316057751) : Map other implementations of MessageEvents.
    is FileMessageBuildIssueEvent -> if (duplicateMessageAware)
      object : FileMessageBuildIssueEvent(this, additionalDescription),  DuplicateMessageAware {}
      else FileMessageBuildIssueEvent(this, additionalDescription)
    is MessageBuildIssueEvent -> if (duplicateMessageAware)
      object : MessageBuildIssueEvent(this, additionalDescription),  DuplicateMessageAware {}
      else MessageBuildIssueEvent(this, additionalDescription)
    is BuildIssueEventImpl -> this.copyWithQuickFix(additionalDescription)
    is FileMessageEventImpl -> if (duplicateMessageAware)
      object : FileMessageBuildIssueEvent(this, additionalDescription),  DuplicateMessageAware {}
      else FileMessageBuildIssueEvent(this, additionalDescription)
    is MessageEventImpl ->  if (duplicateMessageAware)
      object : MessageBuildIssueEvent(this, additionalDescription),  DuplicateMessageAware {}
      else MessageBuildIssueEvent(this, additionalDescription)
    else -> this
  }
}