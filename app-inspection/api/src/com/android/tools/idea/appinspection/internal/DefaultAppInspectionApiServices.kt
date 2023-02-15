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

import com.android.tools.idea.appinspection.api.AppInspectionApiServices
import com.android.tools.idea.appinspection.api.JarCopierCreator
import com.android.tools.idea.appinspection.inspector.api.AppInspectionCannotFindAdbDeviceException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionProcessNoLongerExistsException
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.api.launch.LaunchParameters
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor

/**
 * This serves as the entry point to all public AppInspection API services, specifically: 1)
 * discover when processes start and finish. 2) launch inspectors on discovered processes.
 */
internal class DefaultAppInspectionApiServices
internal constructor(
  private val targetManager: AppInspectionTargetManager,
  private val createJarCopier: JarCopierCreator,
  private val discovery: AppInspectionProcessDiscovery
) : AppInspectionApiServices {
  override val processDiscovery = discovery

  override suspend fun disposeClients(project: String) {
    targetManager.disposeClients(project)
  }

  private suspend fun doAttachToProcess(
    process: ProcessDescriptor,
    projectName: String
  ): AppInspectionTarget {
    val jarCopierCreator =
      createJarCopier(process.device)
        ?: throw AppInspectionCannotFindAdbDeviceException("Cannot find ADB device.")
    val streamChannel =
      discovery.getStreamChannel(process.streamId)
        ?: throw AppInspectionProcessNoLongerExistsException(
          "Cannot attach to process because the device does not exist. Process: $process"
        )
    return targetManager.attachToProcess(process, jarCopierCreator, streamChannel, projectName)
  }

  override suspend fun attachToProcess(
    process: ProcessDescriptor,
    projectName: String
  ): AppInspectionTarget {
    return doAttachToProcess(process, projectName)
  }

  override suspend fun launchInspector(params: LaunchParameters): AppInspectorMessenger {
    return doAttachToProcess(params.processDescriptor, params.projectName).launchInspector(params)
  }

  override suspend fun stopInspectors(process: ProcessDescriptor) {
    targetManager.getTarget(process)?.dispose()
  }
}
