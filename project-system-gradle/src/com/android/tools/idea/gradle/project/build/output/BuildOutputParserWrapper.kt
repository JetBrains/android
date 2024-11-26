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
import com.android.tools.idea.gradle.project.build.events.MessageBuildIssueEvent
import com.android.tools.idea.gradle.project.build.events.copyWithQuickFix
import com.android.tools.idea.gradle.project.build.events.studiobot.GradleErrorContext
import com.android.tools.idea.gradle.project.build.events.studiobot.StudioBotQuickFixProvider
import com.android.tools.idea.gradle.project.build.output.BuildOutputParserUtils.extractTaskNameFromId
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueDescriptionComposer
import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenStudioBotBuildIssueQuickFix
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.DuplicateMessageAware
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import org.jetbrains.annotations.VisibleForTesting
import java.util.function.Consumer


/**
 * A wrapper class for all the build output parsers so we can inject StudioBot help link.
 */
class BuildOutputParserWrapper(val parser: BuildOutputParser, val taskId: ExternalSystemTaskId) : BuildOutputParser {

  private val explainerAvailable
    get() = StudioBotQuickFixProvider.getInstance().isAvailable()

  override fun parse(line: String?, reader: BuildOutputInstantReader?, messageConsumer: Consumer<in BuildEvent>?): Boolean {
    if(!explainerAvailable) {
      return parser.parse(line, reader, messageConsumer)
    }
    return parser.parse(line, reader) {
      val messageEvent = it as? MessageEvent

      // We are only adding links for build events that are of severity ERROR. All other types such as warnings and info messages are
      // excluded.
      val event =
        if (messageEvent != null && messageEvent.kind == MessageEvent.Kind.ERROR
            && !it.message.startsWith("Unresolved reference:"))
        {
          val context = GradleErrorContext(
            gradleTask = extractTaskNameFromId(it.parentId?:""),
            errorMessage = it.message,
            fullErrorDetails = it.description,
            source = extractSourceFromTaskId(taskId)
          )
          val quickFix = OpenStudioBotBuildIssueQuickFix(context)
          it.toBuildIssueEventWithQuickFix(quickFix)
        } else {
          it
        }
      messageConsumer?.accept(event)
    }
  }

  private fun extractSourceFromTaskId(taskId: ExternalSystemTaskId): GradleErrorContext.Source? {
      return when(taskId.type) {
        ExternalSystemTaskType.RESOLVE_PROJECT -> GradleErrorContext.Source.SYNC
        ExternalSystemTaskType.EXECUTE_TASK -> GradleErrorContext.Source.BUILD
        else -> null
      }
  }
}

/**
 * Extends the BuildEvent to BuildIssueEvent, so that quick fix link can be added.
 */
private fun BuildEvent.toBuildIssueEventWithQuickFix(quickFix: DescribedBuildIssueQuickFix): BuildEvent {
  if (this !is MessageEvent) return this
  val additionalDescription = BuildIssueDescriptionComposer().apply {
    addQuickFix(quickFix)
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