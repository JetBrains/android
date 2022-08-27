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
package com.android.tools.idea.gradle.project.build.events

import com.android.tools.idea.project.hyperlink.SyncMessageFragment
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import java.util.concurrent.CompletableFuture
import javax.swing.event.HyperlinkEvent

@Suppress("UnstableApiUsage")
class AndroidSyncIssueQuickFix private constructor(
  private val url: String,
  private val handler: (Project, HyperlinkEvent) -> Boolean
) : BuildIssueQuickFix {

  companion object {

    @JvmStatic
    fun create(hyperlink: SyncMessageFragment): List<AndroidSyncIssueQuickFix> =
      hyperlink.urls.map {
        AndroidSyncIssueQuickFix(
          url = it,
          handler = { project, event -> hyperlink.executeHandler(project, event); true }
        )
      }
  }

  override val id: String
    get() = url

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Any>()
    invokeLater {
      try {
        val source = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext) ?: dataContext
        handler(project, HyperlinkEvent(source, HyperlinkEvent.EventType.ACTIVATED, null, id))
        future.complete(null)
      }
      catch (e: Exception) {
        future.completeExceptionally(e)
      }
    }
    return future
  }
}
