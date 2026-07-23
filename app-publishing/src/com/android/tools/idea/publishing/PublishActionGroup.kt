/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.publishing

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class PublishActionGroup : ActionGroup() {

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    return AppPublisher.EP_NAME.extensionList
      .filter { it.isPublisherAvailable(AppPublishingSource.BUILD_MENU).isExecutable }
      .map { publisher -> PublishAppAction(publisher) }
      .toTypedArray()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible =
      e.project != null && AppPublisher.EP_NAME.extensionList.any { it.isPublisherAvailable(AppPublishingSource.BUILD_MENU).isExecutable }
  }
}

class PublishAppAction(private val publisher: AppPublisher) : AnAction(publisher.displayName) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val context = AppPublishingContext(artifactPath = null, isRegistered = null, publishingSource = AppPublishingSource.BUILD_MENU)
    AppPublishingService.getInstance(project).publishApp(publisher.id, context)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = publisher.isPublisherAvailable(AppPublishingSource.BUILD_MENU).isExecutable
  }
}
