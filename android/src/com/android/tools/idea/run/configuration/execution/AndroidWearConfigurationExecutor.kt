/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.ddmlib.IDevice
import com.android.tools.deployer.model.component.WatchFace
import com.android.tools.deployer.model.component.WearComponent
import com.android.tools.idea.execution.common.AppRunSettings
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.editor.AndroidJavaDebugger
import com.android.tools.idea.run.editor.DeployTarget
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.xdebugger.impl.XDebugSessionImpl
import org.jetbrains.concurrency.Promise

abstract class AndroidWearConfigurationExecutor(environment: ExecutionEnvironment,
                                                deployTarget: DeployTarget,
                                                appRunSettings: AppRunSettings,
                                                applicationIdProvider: ApplicationIdProvider,
                                                apkProvider: ApkProvider) : AndroidConfigurationExecutorBase(environment, deployTarget,
                                                                                                             appRunSettings,
                                                                                                             applicationIdProvider,
                                                                                                             apkProvider) {
  override fun runAsInstantApp(): Promise<RunContentDescriptor> {
    throw RuntimeException("Unsupported operation")
  }

  override fun applyCodeChanges(): Promise<RunContentDescriptor> {
    throw RuntimeException("Unsupported operation")
  }

  override fun applyChanges(): Promise<RunContentDescriptor> {
    throw RuntimeException("Unsupported operation")
  }

  override fun startDebugSession(
    device: IDevice,
    console: ConsoleView
  ): Promise<XDebugSessionImpl> {
    checkAndroidVersionForWearDebugging(device.version, console)
    return DebugSessionStarter.attachDebuggerToStartedProcess(device, appId, environment, AndroidJavaDebugger(), AndroidJavaDebugger().createState(), getStopCallback (console, true), console)
  }

  protected fun showWatchFace(device: IDevice, console: ConsoleView) {
    ProgressIndicatorProvider.getGlobalProgressIndicator()?.apply {
      checkCanceled()
      text = "Jumping to the watch face"
    }
    val resultReceiver = WearComponent.CommandResultReceiver()
    device.executeShellCommand(WatchFace.ShellCommand.SHOW_WATCH_FACE, console, resultReceiver)
    if (resultReceiver.resultCode != WearComponent.CommandResultReceiver.SUCCESS_CODE) {
      console.printError("Warning: Launch was successful, but you may need to bring up the watch face manually")
    }
  }
}