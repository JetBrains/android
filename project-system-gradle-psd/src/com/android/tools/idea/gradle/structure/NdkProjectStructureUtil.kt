/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure

import com.android.tools.idea.gradle.util.GradleUtil
import com.intellij.openapi.project.Project

/**
 * Return true if Gradle plugin version supports side-by-side NDK
 */
fun supportsSideBySideNdk(project : Project?) : Boolean {
  if (project == null) return true
  val gradleModelNumber =
    GradleUtil.getAndroidGradleModelVersionInUse(project)
      ?: return true
  return gradleModelNumber.major >= 3 || gradleModelNumber.minor > 4
}
