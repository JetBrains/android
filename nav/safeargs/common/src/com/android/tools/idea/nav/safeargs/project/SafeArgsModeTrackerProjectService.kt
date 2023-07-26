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
package com.android.tools.idea.nav.safeargs.project

import com.android.tools.idea.nav.safeargs.module.SafeArgsModeModuleService
import com.android.tools.idea.nav.safeargs.safeArgsModeTracker
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import net.jcip.annotations.ThreadSafe

/**
 * Component that owns a project-wide tracker which gets updated whenever any module's
 * `safeArgsMode` is updated.
 *
 * See also: [SafeArgsModeModuleService]
 * See also: [safeArgsModeTracker]
 */
@ThreadSafe
@Service
class SafeArgsModeTrackerProjectService() {
  companion object {
    fun getInstance(project: Project) = project.getService(SafeArgsModeTrackerProjectService::class.java)!!
  }

  /**
   * A thread-safe modification tracker that should get updated
   */
  internal val tracker = SimpleModificationTracker()
}
