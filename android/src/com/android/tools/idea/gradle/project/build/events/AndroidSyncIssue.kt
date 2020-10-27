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

import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.externalSystem.service.notification.NotificationData
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable

class AndroidSyncIssue(
  override val title: String,
  private val notificationData: NotificationData,
  override val quickFixes: List<BuildIssueQuickFix>
): BuildIssue {
  override val description: String = notificationData.message

  override fun getNavigatable(project: Project): Navigatable? = notificationData.navigatable
}