/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.idea.npw.invokeLater
import com.android.tools.idea.project.hyperlink.NotificationHyperlink
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import java.util.concurrent.CompletableFuture
import javax.swing.event.HyperlinkEvent

class AndroidSyncIssueQuickFix(private val hyperlink: NotificationHyperlink) : BuildIssueQuickFix {
  override val id: String
    get() = hyperlink.url

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Any>()
    invokeLater {
      try {
        val source = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext) ?: dataContext
        hyperlink.executeIfClicked(project, HyperlinkEvent(source, HyperlinkEvent.EventType.ACTIVATED, null, id))
        future.complete(null)
      }
      catch (e: Exception) {
        future.completeExceptionally(e)
      }
    }
    return future
  }
}
