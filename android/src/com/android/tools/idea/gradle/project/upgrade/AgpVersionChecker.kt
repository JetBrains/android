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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.GradleVersion.AgpVersion

interface AgpVersionChecker {
  /**
   * Compute whether the [current] version of the Android Gradle plugin as specified by the project is incompatible with the version of
   * Studio corresponding to the given [latestKnown] version of AGP.
   */
  fun versionsAreIncompatible(current: AgpVersion, latestKnown: AgpVersion): Boolean
}