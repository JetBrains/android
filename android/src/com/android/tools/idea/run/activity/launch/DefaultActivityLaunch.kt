/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ValidationError
import com.android.tools.idea.run.activity.ActivityLocator
import com.android.tools.idea.run.activity.DefaultActivityLocator
import com.android.tools.idea.run.activity.DefaultApkActivityLocator
import com.android.tools.idea.run.activity.MavenDefaultActivityLocator
import com.android.tools.idea.run.activity.StartActivityFlagsProvider
import com.android.tools.idea.run.editor.ProfilerState
import com.android.tools.idea.run.tasks.AppLaunchTask
import com.android.tools.idea.run.tasks.DefaultActivityLaunchTask
import com.google.common.collect.ImmutableList
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
import javax.swing.JComponent

class DefaultActivityLaunch : ActivityLaunchOption<DefaultActivityLaunch.State?>() {
  class State : ActivityLaunchOptionState() {
    override fun getLaunchTask(
      applicationId: String,
      facet: AndroidFacet,
      startActivityFlagsProvider: StartActivityFlagsProvider,
      profilerState: ProfilerState,
      apkProvider: ApkProvider
    ): AppLaunchTask {
      return DefaultActivityLaunchTask(applicationId, getActivityLocatorForLaunch(facet, apkProvider), startActivityFlagsProvider)
    }

    override fun checkConfiguration(facet: AndroidFacet): List<ValidationError> {
      // Neither MavenDefaultActivityLocator nor DefaultApkActivityLocator can validate
      // based on Facets. There is no point calling validate().
      return ImmutableList.of()
    }

    companion object {
      private fun getActivityLocatorForLaunch(facet: AndroidFacet, apkProvider: ApkProvider): ActivityLocator {
        if (facet.properties.USE_CUSTOM_COMPILER_MANIFEST) {
          return MavenDefaultActivityLocator(facet)
        }
        return if (StudioFlags.DEFAULT_ACTIVITY_LOCATOR_FROM_APKS.get()) {
          DefaultApkActivityLocator(apkProvider)
        }
        else {
          DefaultActivityLocator(facet)
        }
      }
    }
  }

  override fun getId(): String {
    return AndroidRunConfiguration.LAUNCH_DEFAULT_ACTIVITY
  }

  override fun getDisplayName(): String {
    return "Default Activity"
  }

  override fun createState(): State {
    // there is no state to save in this case
    return State()
  }

  override fun createConfigurable(project: Project, context: LaunchOptionConfigurableContext): LaunchOptionConfigurable<State?> {
    return object : LaunchOptionConfigurable<State?> {
      override fun createComponent(): JComponent? {
        return null
      }

      override fun resetFrom(state: State) {}
      override fun applyTo(state: State) {}
    }
  }

  companion object {
    @JvmField
    val INSTANCE = DefaultActivityLaunch()
  }
}