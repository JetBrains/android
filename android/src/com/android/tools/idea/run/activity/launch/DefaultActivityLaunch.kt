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

import com.android.ddmlib.IDevice
import com.android.tools.idea.execution.common.AndroidExecutionException
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ValidationError
import com.android.tools.idea.run.activity.ActivityLocator.ActivityLocatorException
import com.android.tools.idea.run.activity.DefaultApkActivityLocator
import com.google.common.collect.ImmutableList
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
import javax.swing.JComponent

class DefaultActivityLaunch : LaunchOption<DefaultActivityLaunch.State>() {

  class State : ActivityLaunchOptionState() {

    override fun checkConfiguration(facet: AndroidFacet): List<ValidationError> {
      return ImmutableList.of()
    }

   override fun getQualifiedActivityName(device: IDevice, apkProvider: ApkProvider, appId: String): String {
      return try {
        DefaultApkActivityLocator(apkProvider, appId).getQualifiedActivityName(device)
      }
      catch (e: ActivityLocatorException) {
        throw AndroidExecutionException(UNABLE_TO_DETERMINE_LAUNCH_ACTIVITY, e.message)
      }
    }

    override val id = "DEFAULT_ACTIVITY"
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