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
package com.android.tools.idea.nav.safeargs.psi

import com.android.ide.common.gradle.Version
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.projectsystem.getModuleSystem
import org.jetbrains.android.facet.AndroidFacet

internal val GRADLE_VERSION_ZERO = Version.prefixInfimum("0")

object SafeArgsFeatureVersions {
  val FROM_SAVED_STATE_HANDLE = Version.parse("2.4.0-alpha01")
  val TO_SAVED_STATE_HANDLE = Version.parse("2.4.0-alpha07")
  val ADJUST_PARAMS_WITH_DEFAULTS = Version.parse("2.4.0-alpha08")
}

/**
 * Returns the version of the navigation library used in this app, which is useful as the behavior
 * of safe args is tied to it.
 *
 * If for any reason the version can't be found, a zero version will be returned, which can be used
 * to indicate that only base features should be supported.
 */
internal fun AndroidFacet.findNavigationVersion(): Version {
  return module
           .getModuleSystem()
           .getResolvedDependency(GoogleMavenArtifactId.ANDROIDX_NAVIGATION_COMMON.getCoordinate("+"))
           ?.lowerBoundVersion
         ?: GRADLE_VERSION_ZERO
}
