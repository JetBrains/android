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
package com.android.tools.idea.gradle.project.sync.issues

import com.android.tools.idea.project.hyperlink.SyncMessageHyperlink
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.TestOnly

abstract class SyncIssueNotificationHyperlink(
  url: String,
  text: String,
  private val quickFixId: AndroidStudioEvent.GradleSyncQuickFix
) : SyncMessageHyperlink(url, text) {
  override val quickFixIds: List<AndroidStudioEvent.GradleSyncQuickFix> get() = listOf(quickFixId)
}

@TestOnly
class TestSyncIssueNotificationHyperlink @JvmOverloads constructor(
  url: String,
  private val text: String,
  quickFixId: AndroidStudioEvent.GradleSyncQuickFix,
  private var handler: (Project) -> Unit = {}
) : SyncIssueNotificationHyperlink(url, text, quickFixId) {
  override val urls: Collection<String> = listOf(url)

  override fun execute(project: Project) {
    handler(project)
  }

  override fun toHtml(): String {
    return if (text == "") "" else super.toHtml()
  }
}