/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.SdkConstants.FN_LOCAL_PROPERTIES
import com.android.ide.common.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.project.sync.hyperlink.SetSdkDirHyperlink
import com.android.tools.idea.project.hyperlink.NotificationHyperlink
import com.android.tools.idea.project.messages.MessageType
import com.intellij.openapi.externalSystem.service.notification.NotificationData
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class MissingSdkIssueReporter : SimpleDeduplicatingSyncIssueReporter() {
  override fun getCustomLinks(project: Project,
                              syncIssues: List<IdeSyncIssue>,
                              affectedModules: List<Module>,
                              buildFileMap: Map<Module, VirtualFile>): List<NotificationHyperlink> {
    val localPropertiesPaths = syncIssues.mapNotNull { it.data }.distinct()
    return if (localPropertiesPaths.isEmpty()) mutableListOf() else mutableListOf(SetSdkDirHyperlink(project, localPropertiesPaths))
  }

  override fun getSupportedIssueType(): Int = IdeSyncIssue.TYPE_SDK_NOT_SET

  // All issues of this type should be grouped together
  override fun getDeduplicationKey(issue: IdeSyncIssue): Any = supportedIssueType

  override fun setupNotificationData(project: Project,
                                     syncIssues: List<IdeSyncIssue>,
                                     affectedModules: List<Module>,
                                     buildFileMap: Map<Module, VirtualFile>,
                                     type: MessageType): NotificationData {
    val notificationData = super.setupNotificationData(project, syncIssues, affectedModules, buildFileMap, type)
    val uniqueLocalPropertiesPaths = syncIssues.map { it.data }.distinct()
    notificationData.message = "SDK location not found. Define a location by setting the ANDROID_SDK_ROOT environment variable or by " +
      "setting the sdk.dir path in your project's $FN_LOCAL_PROPERTIES file${if (uniqueLocalPropertiesPaths.size == 1) "" else "s"}."
    return notificationData
  }
}