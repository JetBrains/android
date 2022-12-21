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
package com.android.tools.idea.run

import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.application.ApplicationManager

interface DeploymentApplicationService {
  companion object {
    @JvmStatic
    val instance: DeploymentApplicationService
      get() = ApplicationManager.getApplication().getService(DeploymentApplicationService::class.java)
  }

  fun findClient(iDevice: IDevice, applicationId: String): List<Client>
  fun getVersion(iDevice: IDevice): ListenableFuture<AndroidVersion>
}
