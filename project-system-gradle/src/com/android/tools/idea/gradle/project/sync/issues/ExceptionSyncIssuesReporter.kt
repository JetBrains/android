/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.issues

import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.project.messages.MessageType
import com.android.tools.idea.project.messages.SyncMessage
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class ExceptionSyncIssuesReporter: SimpleDeduplicatingSyncIssueReporter() {
  companion object {
    const val SYNC_SUPPRESSED_EXCEPTION_GROUP = "Suppressed sync exceptions"
  }

  override fun getMessageType(syncIssues: MutableList<IdeSyncIssue>?): MessageType {
    return MessageType.ERROR
  }

  override fun setupSyncMessage(project: Project,
                                syncIssues: List<IdeSyncIssue>,
                                affectedModules: List<Module?>,
                                buildFileMap: Map<Module?, VirtualFile?>,
                                type: MessageType): SyncMessage {
    var syncMessage = super.setupSyncMessage(project, syncIssues, affectedModules, buildFileMap, type)
    val issueMessages = "$SYNC_SUPPRESSED_EXCEPTION_GROUP\n\n" + syncIssues.joinToString("\n") { it.toDisplayMessage() }
    return SyncMessage(SYNC_SUPPRESSED_EXCEPTION_GROUP, syncMessage.type, syncMessage.navigatable, issueMessages)
  }

  override fun getDeduplicationKey(issue: IdeSyncIssue): Any = "${issue.type}-${issue.severity}"

  private fun IdeSyncIssue.toDisplayMessage(): String {
    val stacktrace: String = this.multiLineMessage?.joinToString("\n") ?: ""
    return "${this.message}\n$stacktrace"
  }

  override fun getSupportedIssueType(): Int {
    return IdeSyncIssue.TYPE_EXCEPTION
  }
}
