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
package com.android.tools.idea.gradle.project.sync.setup.post

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.GradleSyncListenerWithRoot
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.SystemIndependent

class RecordAgpVersionSyncListener : GradleSyncListenerWithRoot {
  override fun syncSucceeded(project: Project, rootProjectPath: @SystemIndependent String) {
    ProjectFacetManager.getInstance(project).getFacets(GradleFacet.getFacetTypeId()).forEach { facet ->
      facet.configuration.let {
        it.LAST_SUCCESSFUL_SYNC_AGP_VERSION = it.LAST_KNOWN_AGP_VERSION
      }
    }
  }
}