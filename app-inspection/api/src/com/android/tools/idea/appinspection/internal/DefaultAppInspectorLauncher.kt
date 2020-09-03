/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.appinspection.internal

import com.android.tools.idea.appinspection.inspector.api.AppInspectorLauncher
import com.android.tools.idea.appinspection.api.JarCopierCreator
import com.android.tools.idea.appinspection.inspector.api.AppInspectionProcessNoLongerExistsException
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.internal.process.toTransportImpl

internal class DefaultAppInspectorLauncher(private val targetManager: AppInspectionTargetManager,
                                           private val discovery: AppInspectionProcessDiscovery,
                                           private val createJarCopier: JarCopierCreator) : AppInspectorLauncher {
  override suspend fun launchInspector(
    params: AppInspectorLauncher.LaunchParameters
  ): AppInspectorMessenger {
    val processDescriptor = params.processDescriptor.toTransportImpl()
    val jarCopierCreator = createJarCopier(processDescriptor.stream.device) ?: throw RuntimeException("Cannot find ADB device.")
    val streamChannel = discovery.getStreamChannel(processDescriptor.stream.streamId)
                        ?: throw AppInspectionProcessNoLongerExistsException(
                          "Cannot attach to process because the device does not exist. Process: ${params.processDescriptor}")
    return targetManager
      .attachToProcess(params.processDescriptor, jarCopierCreator, streamChannel, params.projectName)
      .launchInspector(params)
  }
}