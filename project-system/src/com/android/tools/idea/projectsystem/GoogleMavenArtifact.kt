/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.ide.common.repository.GradleVersion

/**
 * This data class represents a google maven artifact that can be handled by the {@link AndroidProjectSystem}'s
 * dependency management APIs.
 */
data class GoogleMavenArtifact(val artifactId: GoogleMavenArtifactId, val version: Version) {

  /**
   * A build system agnostic way of representing the version of a GoogleMavenArtifact.
   * Depending on the build system the specific version information may be available.
   */
  interface Version {
    /**
     * When available, returns maven version information as a GradleVersion object.
     * This method uses GradleVersion to be consistent with {@link GoogleMavenRepository}
     */
    fun getMavenVersion(): GradleVersion?
  }
}
