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
package com.android.tools.idea.nav.safeargs

import com.android.tools.idea.nav.safeargs.module.SafeArgsModeModuleService
import com.android.tools.idea.nav.safeargs.project.SafeArgsModeTrackerProjectService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.TestOnly

enum class SafeArgsMode {
  /** Safe Args is not enabled for this module. */
  NONE,

  /** Safe Args is enabled for this module and will generate Java classes. */
  JAVA,

  /** Safe Args is enabled for this module and will generate Kotlin classes. */
  KOTLIN,
}

var AndroidFacet.safeArgsMode: SafeArgsMode
  get() = SafeArgsModeModuleService.getInstance(module).safeArgsMode
  /**
   * Allow tests to set the [SafeArgsMode] directly -- however, this value may get overwritten if
   * testing with a Gradle project. In that case, you should control the mode by applying the
   * appropriate safeargs plugin instead.
   */
  @TestOnly
  set(value) {
    SafeArgsModeModuleService.getInstance(module).safeArgsMode = value
  }

/**
 * A project-wide tracker which gets updated whenever [safeArgsMode] is updated on any of its
 * modules.
 */
val Project.safeArgsModeTracker: ModificationTracker
  get() = SafeArgsModeTrackerProjectService.getInstance(this)

fun AndroidFacet.isSafeArgsEnabled() = safeArgsMode != SafeArgsMode.NONE
