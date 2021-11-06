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
package com.android.tools.idea.run.activity.launch

import com.android.tools.idea.run.activity.launch.ActivityLaunchOption
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.activity.launch.LaunchOptionConfigurableContext
import com.android.tools.idea.run.activity.launch.LaunchOptionConfigurable
import com.android.tools.idea.run.activity.launch.SpecificActivityConfigurable
import com.android.tools.idea.run.activity.launch.ActivityLaunchOptionState
import org.jetbrains.android.facet.AndroidFacet
import com.android.tools.idea.run.activity.StartActivityFlagsProvider
import com.android.tools.idea.run.editor.ProfilerState
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ValidationError
import com.android.tools.idea.run.tasks.AppLaunchTask
import com.android.tools.idea.run.tasks.SpecificActivityLaunchTask
import com.android.tools.idea.run.activity.ActivityLocator.ActivityLocatorException
import com.android.tools.idea.run.activity.SpecificActivityLocator
import com.android.tools.idea.run.activity.launch.SpecificActivityLaunch
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope

class SpecificActivityLaunch : ActivityLaunchOption<SpecificActivityLaunch.State?>() {
  override fun getId(): String {
    return AndroidRunConfiguration.LAUNCH_SPECIFIC_ACTIVITY
  }

  override fun getDisplayName(): String {
    return "Specified Activity"
  }

  override fun createState(): State {
    return State()
  }

  override fun createConfigurable(project: Project, context: LaunchOptionConfigurableContext): LaunchOptionConfigurable<State?> {
    return SpecificActivityConfigurable(project, context)
  }

  open class State : ActivityLaunchOptionState() {
    @JvmField
    var ACTIVITY_CLASS = ""
    @JvmField
    var SEARCH_ACTIVITY_IN_GLOBAL_SCOPE = false
    @JvmField
    var SKIP_ACTIVITY_VALIDATION = false
    override fun getLaunchTask(
      applicationId: String,
      facet: AndroidFacet,
      startActivityFlagsProvider: StartActivityFlagsProvider,
      profilerState: ProfilerState,
      apkProvider: ApkProvider
    ): AppLaunchTask? {
      return SpecificActivityLaunchTask(applicationId, ACTIVITY_CLASS, startActivityFlagsProvider)
    }

    override fun checkConfiguration(facet: AndroidFacet): List<ValidationError> {
      return try {
        if (!SKIP_ACTIVITY_VALIDATION) {
          getActivityLocator(facet).validate()
        }
        ImmutableList.of()
      } catch (e: ActivityLocatorException) {
        // The launch will probably fail, but we allow the user to continue in case we are looking at stale data.
        ImmutableList.of(
          ValidationError.warning(
            e.message!!
          )
        )
      }
    }

    @VisibleForTesting
    protected open fun getActivityLocator(facet: AndroidFacet): SpecificActivityLocator {
      val project = facet.module.project
      val scope = if (SEARCH_ACTIVITY_IN_GLOBAL_SCOPE) GlobalSearchScope.allScope(project) else GlobalSearchScope.projectScope(project)
      return SpecificActivityLocator(facet, ACTIVITY_CLASS, scope)
    }
  }

  companion object {
    @JvmField
    val INSTANCE = SpecificActivityLaunch()
  }
}