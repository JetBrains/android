/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.tools.idea.appinspection.api.AppInspectionApiServices
import com.android.tools.idea.appinspection.api.checkVersion
import com.android.tools.idea.appinspection.inspector.api.launch.MinimumArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.intellij.openapi.project.Project

suspend fun findScenecoreVersion(
  project: Project,
  appInspectionApiServices: AppInspectionApiServices,
  process: ProcessDescriptor,
): String? {
  val compatibility =
    appInspectionApiServices.checkVersion(
      project = project.name,
      process = process,
      artifactCoordinate = MinimumArtifactCoordinate.JXR_SCENE_CORE,
    ) ?: return null

  return compatibility.version
}
