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
package com.android.tools.idea.gradle.structure.quickfix

import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.withProjectId
import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.model.PsQuickFix
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.SdkIndexLibraryDetails
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.VisibleForTesting
import java.io.Serializable

data class SdkIndexLinkQuickFixNoLog(
  override val text: String,
  val url: String,
  val browseFunction: ((String) -> Unit) = BrowserUtil::browse,
): PsQuickFix, Serializable {
  override fun execute(context: PsContext) {
    browseFunction(url)
  }
}

data class SdkIndexLinkQuickFix(
  override val text: String,
  val url: String,
  val groupId: String,
  val artifactId: String,
  val version: String,
  val browseFunction: ((String) -> Unit) = BrowserUtil::browse,
  val eventReport: ((Project?) -> Unit) = { project -> logClickEvent(groupId, artifactId, version, project)}
): PsQuickFix, Serializable {
  override fun execute(context: PsContext) {
    applyQuickfix(context.project.ideProject)
  }

  @VisibleForTesting
  fun applyQuickfix(project: Project?) {
    browseFunction(url)
    eventReport(project)
  }
}

private fun logClickEvent(groupId: String, artifactId: String, versionString: String, project: Project?) {
  val event = AndroidStudioEvent.newBuilder()
    .setCategory(AndroidStudioEvent.EventCategory.GOOGLE_PLAY_SDK_INDEX)
    .setKind(AndroidStudioEvent.EventKind.SDK_INDEX_LINK_FOLLOWED)
    .withProjectId(project)
    .setSdkIndexLibraryDetails(
      SdkIndexLibraryDetails.newBuilder().setGroupId(groupId).setArtifactId(artifactId).setVersionString(versionString))
  UsageTracker.log(event)
}
