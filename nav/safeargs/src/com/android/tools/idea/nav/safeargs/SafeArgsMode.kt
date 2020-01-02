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

import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.util.Key
import org.jetbrains.android.facet.AndroidFacet

enum class SafeArgsMode {
  /**
   * Safe Args is not enabled for this module.
   */
  NONE,

  /**
   * Safe Args is enabled for this module and will generate Java classes.
   */
  JAVA,

  /**
   * Safe Args is enabled for this module and will generate Kotlin classes.
   */
  KOTLIN,
}

val AndroidFacet.safeArgsMode: SafeArgsMode
  get() {
    // TODO: Use GradleModuleModel + listener to get a real value here
    return if (StudioFlags.NAV_SAFE_ARGS_SUPPORT.get()) SafeArgsMode.JAVA else SafeArgsMode.NONE
  }

// TODO: Check the current Android model or list of dependencies to see if safe args is enabled?
fun AndroidFacet.isSafeArgsEnabled() = safeArgsMode != SafeArgsMode.NONE