/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.templates

import com.android.annotations.concurrency.Slow
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.GradleVersionCatalogDetector
import com.intellij.openapi.project.Project
import com.intellij.platform.PlatformProjectOpenProcessor.Companion.isNewProject


/**
 * Determine if the new module's dependencies are managed by Version Catalogs.
 */
@Slow
fun determineVersionCatalogUseForNewModule(
  project: Project,
  detector: GradleVersionCatalogDetector = GradleVersionCatalogDetector.getInstance(project)): Boolean {
  return StudioFlags.NPW_ENABLE_GRADLE_VERSION_CATALOG.get() &&
         (project.isNewProject() ||
          when (detector.versionCatalogDetectorResult) {
            GradleVersionCatalogDetector.DetectorResult.IMPLICIT_LIBS_VERSIONS -> true
            GradleVersionCatalogDetector.DetectorResult.EXPLICIT_CALL -> project.baseDir?.findChild("gradle")?.findChild(
              "libs.versions.toml") != null
            else -> false
          })
}
