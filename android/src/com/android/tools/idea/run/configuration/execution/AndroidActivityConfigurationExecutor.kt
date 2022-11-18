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
package com.android.tools.idea.run.configuration.execution

import com.android.AndroidProjectTypes
import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.tools.deployer.model.App
import com.android.tools.idea.execution.common.AppRunSettings
import com.android.tools.idea.execution.common.debug.DebugSessionStarter
import com.android.tools.idea.execution.common.debug.impl.java.AndroidJavaDebugger
import com.android.tools.idea.run.AndroidLaunchTaskContributor
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.activity.InstantAppStartActivityFlagsProvider
import com.android.tools.idea.run.activity.launch.ActivityLaunchOptionState
import com.android.tools.idea.run.editor.DeployTarget
import com.android.tools.idea.util.androidFacet
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.xdebugger.impl.XDebugSessionImpl
import org.jetbrains.concurrency.Promise

class AndroidActivityConfigurationExecutor(environment: ExecutionEnvironment,
                                           deployTarget: DeployTarget,
                                           appRunSettings: AppRunSettings,
                                           applicationIdProvider: ApplicationIdProvider,
                                           apkProvider: ApkProvider) : AndroidConfigurationExecutorBase(environment, deployTarget,
                                                                                                        appRunSettings,
                                                                                                        applicationIdProvider,
                                                                                                        apkProvider) {
  private val activityLaunchOptions = appRunSettings.componentLaunchOptions as ActivityLaunchOptionState
  override fun getStopCallback(console: ConsoleView, isDebug: Boolean): (IDevice) -> Unit = { it.forceStop(appId) }

  override fun launch(device: IDevice, app: App, console: ConsoleView, isDebug: Boolean) {
    activityLaunchOptions.launch(device, app, apkProvider, isDebug, getFlags(device), console)
  }

  public override fun startDebugSession(device: IDevice, console: ConsoleView): Promise<XDebugSessionImpl> {
    return DebugSessionStarter.attachDebuggerToStartedProcess(device, appId, environment, AndroidJavaDebugger(),
                                                              AndroidJavaDebugger().createState(), { it.forceStop(appId) }, console)
  }

  override fun runAsInstantApp(): Promise<RunContentDescriptor> {
    throw RuntimeException("Unsupported operation")
  }

  override fun applyChanges(): Promise<RunContentDescriptor> {
    throw RuntimeException("Unsupported operation")
  }

  override fun applyCodeChanges(): Promise<RunContentDescriptor> {
    throw RuntimeException("Unsupported operation")
  }

  private fun StringBuilder.appendWithSpace(text: String) {
    if (text.isNotEmpty()) {
      append(if (this.isEmpty()) "" else " ")
      append(text)
    }
  }

  private fun getFlags(device: IDevice): String {
    if (appRunSettings.module!!.androidFacet?.configuration?.projectType == AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP) {
      return InstantAppStartActivityFlagsProvider().getFlags(device)
    }

    val amStartOptions = StringBuilder()
    if (configuration is AndroidRunConfiguration) {
      for (taskContributor in AndroidLaunchTaskContributor.EP_NAME.extensions) {
        val amOptions = taskContributor.getAmStartOptions(appId, configuration, device, environment.executor)
        amStartOptions.appendWithSpace(amOptions)
        // TODO: use contributors launch tasks
      }
    }

    amStartOptions.appendWithSpace(activityLaunchOptions.amFlags)
    // Default Activity behavior has changed to not show the splashscreen in Tiramisu. We need to add the splashscreen back.
    if (device.version.isGreaterOrEqualThan(AndroidVersion.VersionCodes.TIRAMISU)) {
      amStartOptions.appendWithSpace("--splashscreen-show-icon")
    }

    return amStartOptions.toString()
  }
}

