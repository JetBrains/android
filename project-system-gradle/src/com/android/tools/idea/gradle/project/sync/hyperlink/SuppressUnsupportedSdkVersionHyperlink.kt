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
package com.android.tools.idea.gradle.project.sync.hyperlink

import com.android.tools.idea.gradle.project.sync.issues.SyncIssueNotificationHyperlink
import com.android.tools.idea.gradle.project.sync.issues.processor.GradlePropertyProcessor
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.project.Project

class SuppressUnsupportedSdkVersionHyperlink(val gradleProperty: String) : SyncIssueNotificationHyperlink(
  "android.suppressUnsupportedCompileSdk",
  "Update Gradle property to suppress warning",
  AndroidStudioEvent.GradleSyncQuickFix.SUPPRESS_UNSUPPORTED_SDK_HYPERLINK
) {
  public override fun execute(project: Project) {
    if (project.isDisposed) {
      return
    }

    val index = gradleProperty.indexOf("=")
    if (index != -1 && index < gradleProperty.length - 1) {
      val processor = GradlePropertyProcessor(
        project,
        propertyName="android.suppressUnsupportedCompileSdk",
        propertyValue=gradleProperty.substring(index + 1))
      processor.setPreviewUsages(true)
      processor.run()
    }
  }
}