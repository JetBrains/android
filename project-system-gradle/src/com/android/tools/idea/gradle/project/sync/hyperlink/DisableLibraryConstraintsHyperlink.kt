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
package com.android.tools.idea.gradle.project.sync.hyperlink

import com.android.tools.idea.gradle.project.sync.issues.SyncIssueNotificationHyperlink
import com.android.tools.idea.gradle.project.sync.issues.processor.GradlePropertyProcessor
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.project.Project

class DisableLibraryConstraintsHyperlink: SyncIssueNotificationHyperlink(
  "disable.libraryConstraints",
  "Enable property to exclude library constraints",
  AndroidStudioEvent.GradleSyncQuickFix.DISABLE_LIBRARY_CONSTRAINTS_HYPERLINK
) {
  override fun execute(project: Project) {
    if (project.isDisposed) {
      return
    }
    val processor = GradlePropertyProcessor(project, "android.dependency.excludeLibraryComponentsFromConstraints", true.toString())
    processor.setPreviewUsages(true)
    processor.run()
  }
}