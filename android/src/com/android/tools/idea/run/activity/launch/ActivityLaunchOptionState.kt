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
import com.android.tools.deployer.Activator
import com.android.tools.deployer.model.App
import com.android.tools.deployer.model.component.AppComponent
import com.android.tools.deployer.model.component.ComponentType
import com.android.tools.idea.execution.common.AndroidExecutionException
import com.android.tools.idea.execution.common.ComponentLaunchOptions
import com.android.tools.idea.execution.common.stats.RunStats
import com.android.tools.idea.execution.common.stats.track
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ValidationError
import com.android.tools.idea.run.configuration.AndroidBackgroundTaskReceiver
import com.intellij.execution.ExecutionException
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.android.facet.AndroidFacet
import java.util.regex.Pattern

/*
Each Launch Option should extend this class and add a set of public fields such that they can be saved/restored using
DefaultJDOMExternalizer
*/
abstract class LaunchOptionState {
  abstract val id: String

  @Throws(ExecutionException::class)
  fun launch(
    device: IDevice,
    app: App,
    apkProvider: ApkProvider,
    isDebug: Boolean,
    extraFlags: String,
    console: ConsoleView,
    stats: RunStats
  ) {
    stats.track(id) { doLaunch(device, app, apkProvider, isDebug, extraFlags, console) }
  }

  @Throws(ExecutionException::class)
  protected abstract fun doLaunch(
    device: IDevice,
    app: App,
    apkProvider: ApkProvider,
    isDebug: Boolean,
    extraFlags: String,
    console: ConsoleView
  )

  open fun checkConfiguration(facet: AndroidFacet): List<ValidationError> {
    return emptyList()
  }
}


abstract class ActivityLaunchOptionState : ComponentLaunchOptions, LaunchOptionState() {
  override val componentType = ComponentType.ACTIVITY
  override val userVisibleComponentTypeName = "Activity"

  override fun doLaunch(device: IDevice, app: App, apkProvider: ApkProvider, isDebug: Boolean, extraFlags: String, console: ConsoleView) {
    ProgressManager.checkCanceled()
    val mode = if (isDebug) AppComponent.Mode.DEBUG else AppComponent.Mode.RUN
    val activityQualifiedName = getQualifiedActivityName(device, apkProvider, app.appId)
    val receiver = AndroidBackgroundTaskReceiver(console)
    val activator = Activator(app, logger)
    activator.activate(componentType, activityQualifiedName, extraFlags, mode, receiver, device)
    val matcher = activityDoesNotExistPattern.matcher(receiver.output.joinToString())
    if (matcher.find()) {
      throw AndroidExecutionException(ACTIVITY_DOES_NOT_EXIST, matcher.group())
    }
  }


  companion object {
    @JvmField
    val INSTANCE = DefaultActivityLaunch()

    val UNABLE_TO_DETERMINE_LAUNCH_ACTIVITY = "UNABLE_TO_DETERMINE_LAUNCH_ACTIVITY"

    val ACTIVITY_DOES_NOT_EXIST = "ACTIVITY_DOES_NOT_EXIST"

    protected const val ACTIVITY_DOES_NOT_EXIST_REGEX = "Activity class \\{[^}]*} does not exist"

    @JvmStatic
    protected val activityDoesNotExistPattern = Pattern.compile(ACTIVITY_DOES_NOT_EXIST_REGEX)

    val logger = LogWrapper(Logger.getInstance(ActivityLaunchOptionState::class.java))
  }

  abstract fun getQualifiedActivityName(device: IDevice, apkProvider: ApkProvider, appId: String): String
}