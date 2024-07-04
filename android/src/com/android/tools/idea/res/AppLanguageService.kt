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
package com.android.tools.idea.res

import com.android.annotations.concurrency.Slow
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.tools.idea.project.FacetBasedApplicationProjectContext
import com.android.tools.idea.projectsystem.ApplicationProjectContextProvider
import com.android.tools.idea.projectsystem.ApplicationProjectContextProvider.Companion.getApplicationProjectContext
import com.android.tools.idea.projectsystem.PseudoLocalesToken.Companion.isPseudoLocalesEnabled
import com.android.tools.idea.projectsystem.PseudoLocalesToken.PseudoLocalesState
import com.android.tools.idea.projectsystem.getProjectSystem
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/** Holds the [applicationId] and all supported locales [localeConfig] of an application. */
data class AppLanguageInfo(val applicationId: String, val localeConfig: Set<LocaleQualifier>)

private val enabledStates = setOf(PseudoLocalesState.ENABLED, PseudoLocalesState.BOTH)
private val pseudoLocales =
  setOf(LocaleQualifier(null, "en", "XA", null), LocaleQualifier(null, "ar", "XB", null))

/** Provides [AppLanguageInfo] for a given Project. */
fun interface AppLanguageService {
  companion object {
    @JvmStatic fun getInstance(project: Project): AppLanguageService = project.service()
  }

  /** Returns the [AppLanguageInfo] of the specified application. */
  @Slow
  fun getAppLanguageInfo(
    runningApplicationIdentity: ApplicationProjectContextProvider.RunningApplicationIdentity
  ): AppLanguageInfo?
}

@Service(Service.Level.PROJECT)
class AppLanguageServiceImpl(private val project: Project) : AppLanguageService {

  override fun getAppLanguageInfo(
    runningApplicationIdentity: ApplicationProjectContextProvider.RunningApplicationIdentity
  ): AppLanguageInfo? {
    val context =
      project.getProjectSystem().getApplicationProjectContext(runningApplicationIdentity)
        ?: return null
    val facet = (context as? FacetBasedApplicationProjectContext)?.facet ?: return null
    val pseudoLocalesEnabled =
      project.getProjectSystem().isPseudoLocalesEnabled(context) in enabledStates
    return AppLanguageInfo(
      applicationId = context.applicationId,
      localeConfig =
        getLocaleConfig(facet) + if (pseudoLocalesEnabled) pseudoLocales else emptySet(),
    )
  }
}
