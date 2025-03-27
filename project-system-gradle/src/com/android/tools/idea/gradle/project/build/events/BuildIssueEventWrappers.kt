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
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueDescriptionComposer
import com.android.tools.idea.gradle.project.sync.idea.issues.ErrorMessageAwareBuildIssue
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.FileMessageEventResult
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.MessageEventResult
import com.intellij.build.events.impl.AbstractBuildEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable

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
open class FileMessageBuildIssueEvent(
  private val fileMessageEvent: FileMessageEvent,
  private val additionalDescriptions: List<BuildIssueDescriptionComposer>
): FileMessageEvent, BuildIssueEvent {

  constructor(
    fileMessageBuildIssueEvent: FileMessageBuildIssueEvent,
    additionalDescription: BuildIssueDescriptionComposer
  ) : this(
    fileMessageEvent = fileMessageBuildIssueEvent.fileMessageEvent,
    additionalDescriptions = fileMessageBuildIssueEvent.additionalDescriptions + additionalDescription
  )

  constructor(
    fileMessageEvent: FileMessageEvent,
    additionalDescription: BuildIssueDescriptionComposer
  ) : this( fileMessageEvent, listOf(additionalDescription))

  private val buildIssue: BuildIssue =
    BuildIssueComposer(fileMessageEvent.description.orEmpty(), fileMessageEvent.message).let { composer ->
      additionalDescriptions.forEach { composer.addDescriptionOnNewLine(it) }
      composer.composeBuildIssue()
    }

  override fun getIssue(): BuildIssue {
    return buildIssue
  }

  override fun getId(): Any {
    return fileMessageEvent.id
  }

  override fun getParentId(): Any? {
    return fileMessageEvent.parentId
  }

  override fun getEventTime(): Long {
    return fileMessageEvent.eventTime
  }

  override fun getMessage(): String {
   return fileMessageEvent.message
  }

  override fun getHint(): String? {
    return fileMessageEvent.hint
  }

  override fun getDescription(): String {
    return buildIssue.description
  }

  override fun getKind(): MessageEvent.Kind {
    return fileMessageEvent.kind
  }

  override fun getGroup(): String {
    return fileMessageEvent.group
  }

  override fun getNavigatable(project: Project): Navigatable? {
   return fileMessageEvent.getNavigatable(project)
  }

  override fun getResult(): FileMessageEventResult {
    return object : FileMessageEventResult {
      override fun getFilePosition(): FilePosition {
        return fileMessageEvent.filePosition
      }

      override fun getKind(): MessageEvent.Kind {
        return fileMessageEvent.kind
      }

      override fun getDetails(): String? {
        return this@FileMessageBuildIssueEvent.description
      }
    }
  }

  override fun getFilePosition(): FilePosition {
    return fileMessageEvent.filePosition
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
open class MessageBuildIssueEvent(
  private val messageEvent: MessageEvent,
  private val additionalDescriptions: List<BuildIssueDescriptionComposer>
): MessageEvent, BuildIssueEvent {

  constructor(
    messageBuildIssueEvent: MessageBuildIssueEvent,
    additionalDescription: BuildIssueDescriptionComposer
  ) : this(
    messageEvent = messageBuildIssueEvent.messageEvent,
    additionalDescriptions = messageBuildIssueEvent.additionalDescriptions + additionalDescription
  )

  constructor(
    messageEvent: MessageEvent,
    additionalDescription: BuildIssueDescriptionComposer
  ) : this( messageEvent, listOf(additionalDescription))

  private val buildIssue: BuildIssue =
    BuildIssueComposer(messageEvent.description.orEmpty(), messageEvent.message).let { composer ->
      additionalDescriptions.forEach { composer.addDescriptionOnNewLine(it) }
      composer.composeBuildIssue()
    }

  override fun getId(): Any {
    return messageEvent.id
  }

  override fun getParentId(): Any? {
    return messageEvent.parentId
  }

  override fun getEventTime(): Long {
    return messageEvent.eventTime
  }

  override fun getMessage(): String {
    return messageEvent.message
  }

  override fun getHint(): String? {
    return messageEvent.hint
  }

  override fun getDescription(): String {
      return buildIssue.description
  }

  override fun getKind(): MessageEvent.Kind {
    return messageEvent.kind
  }

  override fun getGroup(): String {
    return messageEvent.group
  }

  override fun getNavigatable(project: Project): Navigatable? {
    return messageEvent.getNavigatable(project)
  }

  override fun getResult(): MessageEventResult {
    return object: MessageEventResult {
      override fun getKind(): MessageEvent.Kind {
        return messageEvent.kind
      }

      override fun getDetails(): String? {
        return this@MessageBuildIssueEvent.description
      }
    }
  }

  override fun getIssue(): BuildIssue {
    return buildIssue
  }
}

/** Copies contents of a  [BuildIssueEventImpl] to an anonymous object of [BuildIssueEvent] with quick fix added.
 *  It does this by creating a new [AbstractBuildEvent] class so that the fields like id, eventTime are copied
 *  from the original BuildIssueEvent.
 *
 * @param quickFix The `BuildIssueQuickFix` to be added.
 * @return [BuildIssueEvent] A BuildIssueEvent with all the contents copied.
 */
@Suppress("UnstableApiUsage")
fun BuildIssueEventImpl.copyWithQuickFix(additionalDescription: BuildIssueDescriptionComposer): BuildIssueEvent {
  val newBuildIssue = when (this.issue) {
    is ErrorMessageAwareBuildIssue -> (this.issue as ErrorMessageAwareBuildIssue).withAdditionalDescription(additionalDescription)
    else -> this.issue.withAdditionalDescription(additionalDescription)
  }

  val newMessageEventResult = object : MessageEventResult {
    override fun getKind(): MessageEvent.Kind {
      return this@copyWithQuickFix.kind
    }
    override fun getDetails(): String? {
      return newBuildIssue.description
    }
  }

  return object : AbstractBuildEvent(this.id, this.parentId, this.eventTime, this.message), BuildIssueEvent {
    override fun getDescription(): String {
      return newBuildIssue.description
    }
    override fun getKind(): MessageEvent.Kind {
      return this@copyWithQuickFix.kind
    }
    override fun getGroup(): String {
      return this@copyWithQuickFix.group
    }
    override fun getNavigatable(project: Project): Navigatable? {
      return newBuildIssue.getNavigatable(project)
    }
    override fun getIssue(): BuildIssue {
      return newBuildIssue
    }
    override fun getResult(): MessageEventResult {
      return newMessageEventResult
    }
  }
}

fun ErrorMessageAwareBuildIssue.withAdditionalDescription(additionalDescription: BuildIssueDescriptionComposer): ErrorMessageAwareBuildIssue = object : ErrorMessageAwareBuildIssue {
  override val title: String = this@withAdditionalDescription.title
  override val description: String = buildString {
    appendLine(this@withAdditionalDescription.description)
    append(additionalDescription.description)
  }
  override val quickFixes: List<BuildIssueQuickFix> = this@withAdditionalDescription.quickFixes + additionalDescription.quickFixes
  override fun getNavigatable(project: Project): Navigatable? = this@withAdditionalDescription.getNavigatable(project)
  override val buildErrorMessage: BuildErrorMessage = this@withAdditionalDescription.buildErrorMessage
}

fun BuildIssue.withAdditionalDescription(additionalDescription: BuildIssueDescriptionComposer): BuildIssue = object : BuildIssue {
  override val title: String = this@withAdditionalDescription.title
  override val description: String = buildString {
    appendLine(this@withAdditionalDescription.description)
    append(additionalDescription.description)
  }
  override val quickFixes: List<BuildIssueQuickFix> = this@withAdditionalDescription.quickFixes + additionalDescription.quickFixes
  override fun getNavigatable(project: Project): Navigatable? = this@withAdditionalDescription.getNavigatable(project)
}


