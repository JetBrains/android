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
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenStudioBotBuildIssueQuickFix
import com.android.tools.idea.studiobot.StudioBot
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import java.util.function.Consumer


/**
 * A wrapper class for all the build output parsers so we can inject StudioBot help link.
 */
class BuildOutputParserWrapper(val parser: BuildOutputParser) : BuildOutputParser {

  private val explainerAvailable
    get() = StudioBot.getInstance().isAvailable()

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
          val quickFix = OpenStudioBotBuildIssueQuickFix(it.message)
          it.toBuildIssueEventWithQuickFix(quickFix)
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
@Suppress("UnstableApiUsage")
private fun BuildEvent.toBuildIssueEventWithQuickFix(quickFix: DescribedBuildIssueQuickFix): BuildEvent {

  return when(this) {
    // TODO(b/316057751) : Map BuildIssueEvents.
    is BuildIssueEvent -> this
    is FileMessageEvent -> FileMessageBuildIssueEvent(this, quickFix)
    is MessageEvent ->  MessageBuildIssueEvent(this, quickFix)
    else -> this
  }
}