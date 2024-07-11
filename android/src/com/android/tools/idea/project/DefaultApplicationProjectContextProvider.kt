/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.project

import com.android.tools.idea.execution.common.debug.utils.FacetFinder
import com.android.tools.idea.projectsystem.ApplicationProjectContext
import com.android.tools.idea.projectsystem.ApplicationProjectContextProvider
import com.intellij.openapi.project.Project

/**
 * An [ApplicationProjectContextProvider] for the default project system.
 */
class DefaultApplicationProjectContextProvider: ApplicationProjectContextProvider<DefaultProjectSystem>, DefaultToken {
  override fun computeApplicationProjectContext(
    projectSystem: DefaultProjectSystem,
    info: ApplicationProjectContextProvider.RunningApplicationIdentity
  ) : ApplicationProjectContext? {
    val result = FacetFinder.tryFindFacetForProcess(projectSystem.project, info) ?: return null
    return FacetBasedApplicationProjectContext(
      result.applicationId,
      result.facet
    )
  }
}