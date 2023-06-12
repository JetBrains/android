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

import com.android.ddmlib.IDevice
import com.android.tools.deployer.model.App
import com.android.tools.deployer.model.component.ComponentType
import com.android.tools.idea.execution.common.ComponentLaunchOptions
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ValidationError
import com.android.tools.idea.run.activity.StartActivityFlagsProvider
import com.android.tools.idea.run.editor.ProfilerState
import com.android.tools.idea.run.tasks.AppLaunchTask
import com.intellij.execution.ExecutionException
import com.intellij.execution.ui.ConsoleView
import org.jetbrains.android.facet.AndroidFacet

// Each Launch Option should extend this class and add a set of public fields such that they can be saved/restored using
// DefaultJDOMExternalizer
abstract class ActivityLaunchOptionState : ComponentLaunchOptions {
  override val componentType = ComponentType.ACTIVITY
  override val userVisibleComponentTypeName = "Activity"
  var amFlags = ""
  abstract fun getLaunchTask(
    applicationId: String,
    facet: AndroidFacet,
    startActivityFlagsProvider: StartActivityFlagsProvider,
    profilerState: ProfilerState,
    apkProvider: ApkProvider
  ): AppLaunchTask?

  /**
   * Method for new execution flow that not based on LaunchTask.
   * [getLaunchTask] will be deleted after implementation of new execution pipeline.
   *
   * @throws ExecutionException
   */
  @Throws(ExecutionException::class)
  abstract fun launch(device: IDevice,
                      app: App,
                      apkProvider: ApkProvider,
                      isDebug: Boolean,
                      extraFlags: String,
                      console: ConsoleView)

  open fun checkConfiguration(facet: AndroidFacet): List<ValidationError> {
    return emptyList()
  }
}