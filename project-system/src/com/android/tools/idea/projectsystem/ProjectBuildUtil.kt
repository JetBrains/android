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
package com.android.tools.idea.projectsystem

import com.intellij.openapi.project.Project

/**
 * Utility getter that indicates if the project needs a build. This is the case if the previews
 * build is not valid, like after a clean or cancelled, or if it has failed.
 */
val Project.needsBuild: Boolean
  get() {
    val lastBuildResult =
      ProjectSystemService.getInstance(project = this)
        .projectSystem
        .getBuildManager()
        .getLastBuildResult()
    return lastBuildResult.status == ProjectSystemBuildManager.BuildStatus.CANCELLED ||
           lastBuildResult.status == ProjectSystemBuildManager.BuildStatus.FAILED ||
           lastBuildResult.mode == ProjectSystemBuildManager.BuildMode.CLEAN
  }