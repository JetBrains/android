/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.qsync

import com.google.idea.blaze.android.projectview.AndroidMinSdkSection
import com.google.idea.blaze.android.projectview.AndroidSdkPlatformSection
import com.google.idea.blaze.base.projectview.ProjectViewSet
import com.google.idea.blaze.base.qsync.QuerySyncLanguageSettings
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings
import com.google.idea.blaze.java.projectview.JavaLanguageLevelSection
import com.intellij.pom.java.LanguageLevel

class QuerySyncLanguageSettingsProvider : QuerySyncLanguageSettings.Provider {
  override fun create(
    projectViewSet: ProjectViewSet,
    workspaceLanguageSettings: WorkspaceLanguageSettings,
  ): QuerySyncLanguageSettings {
    return QuerySyncLanguageSettings.from(
      projectViewSet,
      workspaceLanguageSettings,
      JavaLanguageLevelSection.getLanguageLevel(projectViewSet, LanguageLevel.JDK_21),
      projectViewSet.getScalarValue(AndroidSdkPlatformSection.KEY).orElse(null),
      projectViewSet.getScalarValue(AndroidMinSdkSection.KEY).orElse(null))
  }
}
