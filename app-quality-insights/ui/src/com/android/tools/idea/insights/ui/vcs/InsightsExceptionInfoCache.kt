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
package com.android.tools.idea.insights.ui.vcs

import com.intellij.execution.filters.ExceptionInfoCache
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope

/**
 * An App Insights specific cache which delegates to an IJ [ExceptionInfoCache].
 *
 * The underlying stored resolved info could be out of scope when contents get changed. So here a
 * new cache will be created on [clear] to avoid the staleness as we don't have a control over the
 * underlying [ExceptionInfoCache].
 */
class InsightsExceptionInfoCache(val project: Project, private val searchScope: GlobalSearchScope) {
  private var delegate = ExceptionInfoCache(project, searchScope)

  fun clear() {
    delegate = ExceptionInfoCache(project, searchScope)
  }

  fun resolveClassOrFile(
    className: String,
    fileName: String?,
  ): ExceptionInfoCache.ClassResolveInfo {
    return delegate.resolveClassOrFile(className, fileName)
  }
}
