/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers

import com.android.ide.common.gradle.Version
import com.android.tools.idea.common.model.NlDependencyManager
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import org.jetbrains.android.facet.AndroidFacet

internal fun AndroidFacet?.hasMaterial3Dependency(): Boolean {
  val materialLibVersion = NlDependencyManager.getInstance()
    .getModuleDependencyVersion(GoogleMavenArtifactId.ANDROIDX_DESIGN, this ?: return false)

  // Material3 starts in 1.5
  return materialLibVersion?.let { it > Version.prefixInfimum("1.5.0") } ?: false
}