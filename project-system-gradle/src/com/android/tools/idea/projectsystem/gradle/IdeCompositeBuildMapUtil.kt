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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.idea.gradle.model.IdeCompositeBuildMap
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.android.tools.idea.projectsystem.ProjectSyncModificationTracker
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.find
import com.intellij.openapi.module.Module
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.plugins.gradle.util.GradleConstants

fun Module.compositeBuildMap(): IdeCompositeBuildMap {
  return CachedValuesManager.getManager(project).getCachedValue(this) {
    fun getMap(): IdeCompositeBuildMap {
      val linkedProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(this) ?: return IdeCompositeBuildMap.EMPTY

      @Suppress("UnstableApiUsage")
      val projectDataNode =
        ExternalSystemApiUtil.findProjectData(project, GradleConstants.SYSTEM_ID, linkedProjectPath) ?: return IdeCompositeBuildMap.EMPTY

      return find(projectDataNode, AndroidProjectKeys.IDE_COMPOSITE_BUILD_MAP)?.data ?: IdeCompositeBuildMap.EMPTY
    }

    CachedValueProvider.Result(getMap(), ProjectSyncModificationTracker.getInstance(project))
  }
}