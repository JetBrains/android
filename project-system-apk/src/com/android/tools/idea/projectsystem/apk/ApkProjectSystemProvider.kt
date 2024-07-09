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
package com.android.tools.idea.projectsystem.apk

import com.android.tools.idea.apk.ApkFacet
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.AndroidProjectSystemProvider
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.project.Project

class ApkProjectSystemProvider : AndroidProjectSystemProvider {
  override val id: String = "com.android.tools.idea.ApkProjectSystem"
  override fun isApplicable(project: Project) =
    StudioFlags.ENABLE_APK_PROJECT_SYSTEM.get() &&
    ProjectFacetManager.getInstance(project).hasFacets(ApkFacet.getFacetTypeId())
  override fun projectSystemFactory(project: Project) = ApkProjectSystem(project)
}