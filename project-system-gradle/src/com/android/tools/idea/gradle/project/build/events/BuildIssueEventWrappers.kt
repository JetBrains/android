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
package com.android.tools.idea.gradle.project.build.events

import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix

/**
 * Wrapper on [FileMessageEvent] to represent a FileMessageEvent that is also build issue.
 *
 * This class combines the properties of a `FileMessageEvent` and a `BuildIssueEvent`,
 * so that quick fixes like [BuildIssueQuickFix] can be attached to the event.
 *
 * @constructor Creates a new `[FileMessageBuildIssueEvent]`.
 * @param fileMessageEvent The original `FileMessageEvent`.
 * @param describedBuildIssueQuickFix The `BuildIssueQuickFix` associated with the file message event.
 */
@Suppress("UnstableApiUsage")
class FileMessageBuildIssueEvent(
  fileMessageEvent: FileMessageEvent,
  describedBuildIssueQuickFix: DescribedBuildIssueQuickFix
  ):
  FileMessageEvent by fileMessageEvent, BuildIssueEvent {
  private val buildIssue: BuildIssue =
    BuildIssueComposer(fileMessageEvent.description.orEmpty(), fileMessageEvent.message)
      .addQuickFix(describedBuildIssueQuickFix)
      .composeBuildIssue()

  override fun getIssue(): BuildIssue {
    return buildIssue
  }
}

/**
 * Wrapper on [MessageEvent] to represent a MessageEvent that is also build issue.
 *
 * This class extends an existing messageEvent object to support a BuildIssueEvent,
 * so that quick fixes like [BuildIssueQuickFix] can be attached to the event.
 *
 * @constructor Creates a new `[MessageBuildIssueEvent]`.
 * @param messageEvent The original `MessageEvent`.
 * @param describedBuildIssueQuickFix The `BuildIssueQuickFix` associated with the message event.
 */
@Suppress("UnstableApiUsage")
class MessageBuildIssueEvent(
  messageEvent: MessageEvent,
  describedBuildIssueQuickFix: DescribedBuildIssueQuickFix):
  MessageEvent by messageEvent, BuildIssueEvent {
  private val buildIssue: BuildIssue =
    BuildIssueComposer(messageEvent.description.orEmpty(), messageEvent.message)
      .addQuickFix(describedBuildIssueQuickFix)
      .composeBuildIssue()

    override fun getIssue(): BuildIssue {
    return buildIssue
  }
}