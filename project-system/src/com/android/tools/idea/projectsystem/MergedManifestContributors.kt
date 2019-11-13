/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.intellij.openapi.vfs.VirtualFile

/**
 * Immutable data object responsible for determining all the files that contribute to
 * the merged manifest of a particular [AndroidFacet] at a particular moment in time.
 *
 * Note that any navigation files are also considered contributors, since you can
 * specify the <nav-graph> tag in your manifest and the navigation component will
 * replace it at merge time with intent filters derived from the module's navigation
 * files. See https://developer.android.com/guide/navigation/navigation-deep-link
 */
data class MergedManifestContributors(
  @JvmField val primaryManifest: VirtualFile?,
  @JvmField val flavorAndBuildTypeManifests: List<VirtualFile>,
  @JvmField val libraryManifests: List<VirtualFile>,
  @JvmField val navigationFiles: List<VirtualFile>,
  @JvmField val flavorAndBuildTypeManifestsOfLibs: List<VirtualFile>) {

  @JvmField
  val allFiles = flavorAndBuildTypeManifests +
                 listOfNotNull(primaryManifest) +
                 libraryManifests +
                 navigationFiles +
                 flavorAndBuildTypeManifestsOfLibs
}